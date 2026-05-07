package jp.co.oda32.domain.service.finance;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jp.co.oda32.audit.AuditLog;
import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.embeddable.TConsistencyReviewPK;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TConsistencyReview;
import jp.co.oda32.domain.model.master.MLoginUser;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.repository.finance.TConsistencyReviewRepository;
import jp.co.oda32.domain.repository.master.LoginUserRepository;
import jp.co.oda32.dto.finance.ConsistencyReviewRequest;
import jp.co.oda32.dto.finance.ConsistencyReviewResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 整合性レポート 差分確認機能 Service (案 X + Y)。
 * <p>
 * IGNORE: review 作成のみ、副作用なし。
 * MF_APPLY: 対象 summary 行の verified_amount を MF 金額に合わせる。税率別に按分。
 * DELETE / IGNORE 切替: MF_APPLY で書き換えた verified_amount を previous から復元。
 * <p>
 * 設計書: claudedocs/design-consistency-review.md
 *
 * @since 2026-04-23
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional
public class ConsistencyReviewService {

    private static final String ENTRY_MF_ONLY = "mfOnly";
    private static final String ENTRY_SELF_ONLY = "selfOnly";
    private static final String ENTRY_AMOUNT_MISMATCH = "amountMismatch";
    private static final String ACTION_IGNORE = "IGNORE";
    private static final String ACTION_MF_APPLY = "MF_APPLY";

    private final TConsistencyReviewRepository reviewRepository;
    private final TAccountsPayableSummaryRepository summaryRepository;
    private final LoginUserRepository loginUserRepository;

    /**
     * Codex Major #2 (2026-05-06): {@link #applyMfOverride} / {@link #rollbackVerifiedAmounts} で
     * {@link FinancePayableLock} を取得する用。BULK / MANUAL / MF_OVERRIDE の 3 経路で
     * 同一 (shop, transaction_month) lock を共有する。
     */
    @PersistenceContext
    private EntityManager entityManager;

    @AuditLog(table = "t_consistency_review", operation = "upsert",
            pkExpression = "{'shopNo': #a0.shopNo, 'entryType': #a0.entryType, 'entryKey': #a0.entryKey, 'transactionMonth': #a0.transactionMonth}",
            captureArgsAsAfter = true)
    public ConsistencyReviewResponse upsert(ConsistencyReviewRequest req, Integer userNo) {
        validateRequest(req);

        // Codex Major #2 (2026-05-06): BULK / MANUAL / MF_OVERRIDE の 3 書込経路で同一 advisory lock を取り、
        // last-write-wins race を排除する。upsert 内で rollback (= 副作用剥がし) と
        // applyMfOverride (= MF_OVERRIDE 書込) を順に行うため、最初にまとめて lock を取る。
        // shopNo / transactionMonth は req から取れるため、ここで取得して保存処理全体を直列化する。
        FinancePayableLock.acquire(entityManager, req.getShopNo(), req.getTransactionMonth());

        TConsistencyReviewPK pk = new TConsistencyReviewPK(
                req.getShopNo(), req.getEntryType(), req.getEntryKey(), req.getTransactionMonth());

        Optional<TConsistencyReview> existingOpt = reviewRepository.findById(pk);
        // 旧 review に previous snapshot が残っていれば先にロールバック (副作用を剥がす)。
        // SF-04: action 種別ではなく snapshot 有無で判定 (IGNORE で previous 残置時もロールバック可能)。
        if (existingOpt.isPresent()) {
            TConsistencyReview old = existingOpt.get();
            if (old.getPreviousVerifiedAmounts() != null && !old.getPreviousVerifiedAmounts().isEmpty()) {
                rollbackVerifiedAmounts(req.getShopNo(), req.getEntryKey(),
                        req.getTransactionMonth(), old.getPreviousVerifiedAmounts());
            }
        }

        // SF-04 補足: IGNORE 経路では previous=null を明示的に保存し、過去の MF_APPLY snapshot を完全クリア。
        // (上の rollback で副作用は剥がしてあるので、再ロールバックを誘発しないよう null で上書き)
        Map<String, BigDecimal> previous = null;
        boolean verifiedUpdated = false;
        if (ACTION_MF_APPLY.equals(req.getActionType())) {
            previous = applyMfOverride(req);
            verifiedUpdated = true;
        }

        TConsistencyReview review = TConsistencyReview.builder()
                .pk(pk)
                .actionType(req.getActionType())
                .selfSnapshot(req.getSelfSnapshot())
                .mfSnapshot(req.getMfSnapshot())
                .previousVerifiedAmounts(previous) // IGNORE 時は null (snapshot クリア)
                .reviewedBy(userNo)
                .reviewedAt(Instant.now())
                .note(req.getNote())
                .build();
        reviewRepository.save(review);

        return buildResponse(review, verifiedUpdated);
    }

    @AuditLog(table = "t_consistency_review", operation = "delete",
            pkExpression = "{'shopNo': #a0, 'entryType': #a1, 'entryKey': #a2, 'transactionMonth': #a3}",
            captureArgsAsAfter = true)
    public void delete(Integer shopNo, String entryType, String entryKey, LocalDate transactionMonth) {
        // Codex Major #2 (2026-05-06): rollbackVerifiedAmounts も MF_OVERRIDE 経路の書込なので
        // 同一 advisory lock を取得する。
        FinancePayableLock.acquire(entityManager, shopNo, transactionMonth);

        TConsistencyReviewPK pk = new TConsistencyReviewPK(shopNo, entryType, entryKey, transactionMonth);
        Optional<TConsistencyReview> existing = reviewRepository.findById(pk);
        if (existing.isEmpty()) return;
        TConsistencyReview r = existing.get();
        // SF-04: action 種別ではなく snapshot 有無で判定 (IGNORE で previous 残置時もロールバック可能)。
        if (r.getPreviousVerifiedAmounts() != null && !r.getPreviousVerifiedAmounts().isEmpty()) {
            rollbackVerifiedAmounts(shopNo, entryKey, transactionMonth, r.getPreviousVerifiedAmounts());
        }
        reviewRepository.deleteById(pk);
    }

    /** 整合性レポートサービスから呼ばれる: 期間内 review を PK Map で返す。reviewer 名も付与。 */
    @Transactional(readOnly = true)
    public Map<ReviewKey, ReviewInfo> findForPeriod(Integer shopNo, LocalDate fromMonth, LocalDate toMonth) {
        List<Object[]> rows = reviewRepository.findWithReviewerNameForPeriod(shopNo, fromMonth, toMonth);
        Map<ReviewKey, ReviewInfo> map = new HashMap<>();
        for (Object[] row : rows) {
            TConsistencyReview r = (TConsistencyReview) row[0];
            String reviewerName = (String) row[1];
            ReviewKey key = new ReviewKey(r.getPk().getEntryType(), r.getPk().getEntryKey(), r.getPk().getTransactionMonth());
            map.put(key, new ReviewInfo(
                    r.getActionType(),
                    r.getSelfSnapshot(),
                    r.getMfSnapshot(),
                    r.getReviewedBy(),
                    reviewerName,
                    r.getReviewedAt(),
                    r.getNote()));
        }
        return map;
    }

    // ==================== private helpers ====================

    private void validateRequest(ConsistencyReviewRequest req) {
        if (!ACTION_IGNORE.equals(req.getActionType()) && !ACTION_MF_APPLY.equals(req.getActionType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "actionType は IGNORE か MF_APPLY のみ");
        }
        if (!ENTRY_MF_ONLY.equals(req.getEntryType())
                && !ENTRY_SELF_ONLY.equals(req.getEntryType())
                && !ENTRY_AMOUNT_MISMATCH.equals(req.getEntryType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "entryType は mfOnly / selfOnly / amountMismatch");
        }
        if (ENTRY_MF_ONLY.equals(req.getEntryType()) && ACTION_MF_APPLY.equals(req.getActionType())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "mfOnly の MF_APPLY は未対応 (自社側 supplier 行がないため)");
        }
    }

    /**
     * MF_APPLY の副作用: 対象 summary 行の verified_amount を MF 金額に合わせる。
     * <ul>
     *   <li>selfOnly: 全税率行 verified_amount=0 (自社取消)</li>
     *   <li>amountMismatch: 税率別 change 比で target = mfSnapshot+payment_settled を按分</li>
     * </ul>
     *
     * @return 更新前の verified_amount 退避 (税率 → 金額 Map)
     */
    private Map<String, BigDecimal> applyMfOverride(ConsistencyReviewRequest req) {
        Integer supplierNo = parseSupplierNo(req.getEntryKey());
        if (supplierNo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "entryKey が supplier_no 数値ではありません: " + req.getEntryKey());
        }
        List<TAccountsPayableSummary> rows = summaryRepository
                .findByShopNoAndSupplierNoAndTransactionMonthBetweenOrderByTransactionMonthAscTaxRateAsc(
                        req.getShopNo(), supplierNo, req.getTransactionMonth(), req.getTransactionMonth());
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "対象 summary 行が見つかりません: shopNo=" + req.getShopNo()
                            + ", supplierNo=" + supplierNo + ", month=" + req.getTransactionMonth());
        }

        Map<String, BigDecimal> previous = new HashMap<>();
        for (TAccountsPayableSummary r : rows) {
            previous.put(r.getTaxRate().toPlainString(),
                    r.getVerifiedAmount() != null ? r.getVerifiedAmount() : BigDecimal.ZERO);
        }

        if (ENTRY_SELF_ONLY.equals(req.getEntryType())) {
            // 自社取消: 全税率行を 0 に
            for (TAccountsPayableSummary r : rows) {
                r.setVerifiedAmount(BigDecimal.ZERO);
                r.setVerifiedAmountTaxExcluded(BigDecimal.ZERO);
                r.setVerifiedManually(true);
                r.setVerificationResult(1);
                r.setMfExportEnabled(false);
                // SF-03: V026 列の振込明細 Excel 由来 stale 値をクリア (UI バッジ誤表示防止)。
                r.setAutoAdjustedAmount(BigDecimal.ZERO);
                r.setMfTransferDate(null);
                // G2-M1/M10 (V040): 書込経路を MF_OVERRIDE として明示記録。
                r.setVerificationSource(FinanceConstants.VERIFICATION_SOURCE_MF_OVERRIDE);
                // paymentDifference = verifiedAmount(=0) - taxIncludedAmountChange
                r.setPaymentDifference(BigDecimal.ZERO.subtract(nz(r.getTaxIncludedAmountChange())));
            }
            // SF-15: ループ後に saveAll で 1 回だけ永続化
            summaryRepository.saveAll(rows);
        } else if (ENTRY_AMOUNT_MISMATCH.equals(req.getEntryType())) {
            // 税率別 change 比で target 按分
            BigDecimal paymentSettled = rows.stream()
                    .map(r -> nz(r.getPaymentAmountSettledTaxIncluded()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal target = nz(req.getMfSnapshot()).add(paymentSettled);
            BigDecimal changeSum = rows.stream()
                    .map(r -> nz(r.getTaxIncludedAmountChange()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal assigned = BigDecimal.ZERO;
            TAccountsPayableSummary largest = null;
            BigDecimal largestChange = BigDecimal.valueOf(Long.MIN_VALUE);
            for (TAccountsPayableSummary r : rows) {
                BigDecimal ch = nz(r.getTaxIncludedAmountChange());
                if (ch.compareTo(largestChange) > 0) { largestChange = ch; largest = r; }
            }
            for (TAccountsPayableSummary r : rows) {
                BigDecimal allocated;
                if (changeSum.signum() == 0) {
                    // 全 change 0 の場合は代表行に一括
                    allocated = r == largest ? target : BigDecimal.ZERO;
                } else {
                    BigDecimal ch = nz(r.getTaxIncludedAmountChange());
                    allocated = target.multiply(ch).divide(changeSum, 0, RoundingMode.DOWN);
                }
                assigned = assigned.add(allocated);
                r.setVerifiedAmount(allocated);
                BigDecimal divisor = BigDecimal.valueOf(100).add(nz(r.getTaxRate()));
                r.setVerifiedAmountTaxExcluded(
                        allocated.multiply(BigDecimal.valueOf(100)).divide(divisor, 0, RoundingMode.DOWN));
                r.setVerifiedManually(true);
                r.setVerificationResult(1);
                r.setMfExportEnabled(true);
                // SF-03: V026 列の振込明細 Excel 由来 stale 値をクリア (UI バッジ誤表示防止)。
                r.setAutoAdjustedAmount(BigDecimal.ZERO);
                r.setMfTransferDate(null);
                // G2-M1/M10 (V040): 書込経路を MF_OVERRIDE として明示記録。
                r.setVerificationSource(FinanceConstants.VERIFICATION_SOURCE_MF_OVERRIDE);
            }
            // 端数誤差は最大行で吸収
            BigDecimal diff = target.subtract(assigned);
            if (diff.signum() != 0 && largest != null) {
                largest.setVerifiedAmount(nz(largest.getVerifiedAmount()).add(diff));
                BigDecimal divisor = BigDecimal.valueOf(100).add(nz(largest.getTaxRate()));
                largest.setVerifiedAmountTaxExcluded(
                        largest.getVerifiedAmount().multiply(BigDecimal.valueOf(100))
                                .divide(divisor, 0, RoundingMode.DOWN));
            }
            // SF-03: paymentDifference を上書き後の verifiedAmount で再計算
            // (式は TAccountsPayableSummaryService と同一: verifiedAmount - taxIncludedAmountChange)
            for (TAccountsPayableSummary r : rows) {
                r.setPaymentDifference(nz(r.getVerifiedAmount()).subtract(nz(r.getTaxIncludedAmountChange())));
            }
            // SF-15: ループ後に saveAll で 1 回だけ永続化 (端数吸収も含めて漏れなし)
            summaryRepository.saveAll(rows);
        }
        log.info("[consistency-review] MF_APPLY shopNo={} supplier={} month={} type={} target={}",
                req.getShopNo(), supplierNo, req.getTransactionMonth(), req.getEntryType(), req.getMfSnapshot());
        return previous;
    }

    private void rollbackVerifiedAmounts(Integer shopNo, String entryKey, LocalDate month,
                                          Map<String, BigDecimal> previous) {
        if (previous == null || previous.isEmpty()) return;
        Integer supplierNo = parseSupplierNo(entryKey);
        if (supplierNo == null) return;
        List<TAccountsPayableSummary> rows = summaryRepository
                .findByShopNoAndSupplierNoAndTransactionMonthBetweenOrderByTransactionMonthAscTaxRateAsc(
                        shopNo, supplierNo, month, month);
        for (TAccountsPayableSummary r : rows) {
            String taxKey = r.getTaxRate().toPlainString();
            if (previous.containsKey(taxKey)) {
                BigDecimal prev = previous.get(taxKey);
                r.setVerifiedAmount(prev);
                // 税抜は逆算で復元 (元値を持っていないため)
                if (prev != null && prev.signum() != 0) {
                    BigDecimal divisor = BigDecimal.valueOf(100).add(nz(r.getTaxRate()));
                    r.setVerifiedAmountTaxExcluded(
                            prev.multiply(BigDecimal.valueOf(100)).divide(divisor, 0, RoundingMode.DOWN));
                } else {
                    r.setVerifiedAmountTaxExcluded(null);
                    // G2-M1/M10 (V040): 復元値が 0/null = 元々未検証 だったとみなし、source も NULL に戻す。
                    // 元の経路 (BULK/MANUAL/NULL) は previous snapshot に含まれないため、
                    // 非ゼロ復元時は source 列を MF_OVERRIDE のまま残す (best-effort)。
                    // 厳密に元の source を復元するには previous_verification_sources 列を追加する
                    // 必要があり、そこまで踏み込まない (レアケース、ユーザが再 verify で上書き可能)。
                    r.setVerificationSource(null);
                }
            }
        }
        // SF-15: ループ後に saveAll で 1 回だけ永続化
        summaryRepository.saveAll(rows);
        log.info("[consistency-review] ROLLBACK shopNo={} supplier={} month={} rows={}",
                shopNo, supplierNo, month, rows.size());
    }

    private Integer parseSupplierNo(String entryKey) {
        try { return Integer.parseInt(entryKey); } catch (NumberFormatException e) { return null; }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private ConsistencyReviewResponse buildResponse(TConsistencyReview r, boolean verifiedUpdated) {
        String reviewerName = loginUserRepository.findById(r.getReviewedBy())
                .map(MLoginUser::getUserName).orElse(null);
        return ConsistencyReviewResponse.builder()
                .shopNo(r.getPk().getShopNo())
                .entryType(r.getPk().getEntryType())
                .entryKey(r.getPk().getEntryKey())
                .transactionMonth(r.getPk().getTransactionMonth())
                .actionType(r.getActionType())
                .reviewStatus(ACTION_IGNORE.equals(r.getActionType()) ? "IGNORED" : "MF_APPLIED")
                .selfSnapshot(r.getSelfSnapshot())
                .mfSnapshot(r.getMfSnapshot())
                .reviewedBy(r.getReviewedBy())
                .reviewedByName(reviewerName)
                .reviewedAt(r.getReviewedAt())
                .note(r.getNote())
                .verifiedAmountUpdated(verifiedUpdated)
                .build();
    }

    public record ReviewKey(String entryType, String entryKey, LocalDate transactionMonth) {}

    public record ReviewInfo(String actionType, BigDecimal selfSnapshot, BigDecimal mfSnapshot,
                              Integer reviewedBy, String reviewedByName,
                              Instant reviewedAt, String note) {
        public String reviewStatus() {
            return "IGNORE".equals(actionType) ? "IGNORED" : "MF_APPLIED";
        }
    }
}
