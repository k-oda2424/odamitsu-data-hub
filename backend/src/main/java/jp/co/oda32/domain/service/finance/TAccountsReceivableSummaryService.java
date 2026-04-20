package jp.co.oda32.domain.service.finance;

import jakarta.persistence.criteria.Predicate;
import jp.co.oda32.annotation.SkipShopCheck;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.repository.finance.TAccountsReceivableSummaryRepository;
import jp.co.oda32.dto.finance.AccountsReceivableSummaryResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 20日締め売掛金テーブルのサービスクラス
 *
 * @author k_oda
 * @since 2024/08/31
 * @modified 2025/04/30 - 指定年月の締め月データを取得するメソッドを追加
 * @modified 2026/04/17 - ページング・検証フィルタ・手動確定/解除/MF出力切替を追加（設計書 design-accounts-receivable-mf.md）
 */
@Service
public class TAccountsReceivableSummaryService {

    private final TAccountsReceivableSummaryRepository repository;

    @Autowired
    public TAccountsReceivableSummaryService(TAccountsReceivableSummaryRepository repository) {
        this.repository = repository;
    }

    public List<TAccountsReceivableSummary> findAll() {
        return repository.findAll();
    }

    public TAccountsReceivableSummary save(TAccountsReceivableSummary summary) {
        return repository.save(summary);
    }

    /**
     * 複数の AR サマリを 1 トランザクションで一括保存する。
     * {@code bulkVerify} の最終保存で使用し、途中例外時の部分コミット破綻を防ぐ。
     */
    @Transactional
    public List<TAccountsReceivableSummary> saveAll(List<TAccountsReceivableSummary> summaries) {
        return repository.saveAll(summaries);
    }

    /**
     * ページング検索。画面一覧のフィルタで使用。
     *
     * @param shopNo             店舗番号（nullなら全店舗）
     * @param partnerNo          得意先番号（nullならすべて）
     * @param fromDate           取引月開始（nullなら制限なし）
     * @param toDate             取引月終了（nullなら制限なし）
     * @param verificationFilter "all" / "unverified" / "unmatched" / "matched"
     * @param pageable           ページ指定
     */
    @SkipShopCheck
    public Page<TAccountsReceivableSummary> findPaged(
            Integer shopNo,
            Integer partnerNo,
            String partnerCode,
            LocalDate fromDate,
            LocalDate toDate,
            String verificationFilter,
            Pageable pageable) {
        Specification<TAccountsReceivableSummary> spec = buildSpec(shopNo, partnerNo, partnerCode, fromDate, toDate, verificationFilter);
        return repository.findAll(spec, pageable);
    }

    /**
     * 画面表示用のサマリカウント（未検証/不一致/一致 件数と差額合計）。
     */
    @SkipShopCheck
    public AccountsReceivableSummaryResponse summary(Integer shopNo, String partnerCode, LocalDate fromDate, LocalDate toDate) {
        Specification<TAccountsReceivableSummary> spec = buildSpec(shopNo, null, partnerCode, fromDate, toDate, "all");
        List<TAccountsReceivableSummary> list = repository.findAll(spec);
        long total = list.size();
        long unverified = list.stream().filter(s -> s.getVerificationResult() == null).count();
        long unmatched = list.stream().filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 0).count();
        long matched = list.stream().filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 1).count();
        BigDecimal diffSum = list.stream()
                .filter(s -> s.getVerificationResult() != null && s.getVerificationResult() == 0)
                .map(s -> s.getVerificationDifference() != null ? s.getVerificationDifference() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return AccountsReceivableSummaryResponse.builder()
                .fromDate(fromDate)
                .toDate(toDate)
                .totalCount(total)
                .unverifiedCount(unverified)
                .unmatchedCount(unmatched)
                .matchedCount(matched)
                .unmatchedDifferenceSum(diffSum)
                .build();
    }

    private Specification<TAccountsReceivableSummary> buildSpec(
            Integer shopNo, Integer partnerNo, String partnerCode,
            LocalDate fromDate, LocalDate toDate,
            String verificationFilter) {
        return (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (shopNo != null) {
                preds.add(cb.equal(root.get("shopNo"), shopNo));
            }
            if (partnerNo != null) {
                preds.add(cb.equal(root.get("partnerNo"), partnerNo));
            }
            if (partnerCode != null && !partnerCode.isBlank()) {
                // 部分一致でも絞り込める方が使いやすい (前方一致)。
                preds.add(cb.like(root.get("partnerCode"), partnerCode.trim() + "%"));
            }
            if (fromDate != null) {
                preds.add(cb.greaterThanOrEqualTo(root.get("transactionMonth"), fromDate));
            }
            if (toDate != null) {
                preds.add(cb.lessThanOrEqualTo(root.get("transactionMonth"), toDate));
            }
            if (verificationFilter != null) {
                switch (verificationFilter) {
                    case "unverified":
                        preds.add(cb.isNull(root.get("verificationResult")));
                        break;
                    case "unmatched":
                        preds.add(cb.equal(root.get("verificationResult"), 0));
                        break;
                    case "matched":
                        preds.add(cb.equal(root.get("verificationResult"), 1));
                        break;
                    default:
                        // all: no filter
                }
            }
            return cb.and(preds.toArray(new Predicate[0]));
        };
    }

    /**
     * 指定された取引月に対応するTAccountsReceivableSummaryのリストを取得します。
     */
    public List<TAccountsReceivableSummary> findByTransactionMonth(LocalDate transactionMonth) {
        return repository.findByTransactionMonth(transactionMonth);
    }

    /**
     * 指定された年月で締め月が該当する売掛金サマリーを取得します。
     */
    public List<TAccountsReceivableSummary> findByCutoffYearMonth(YearMonth yearMonth) {
        LocalDate endOfMonth = yearMonth.atEndOfMonth();
        LocalDate day15 = yearMonth.atDay(15);
        LocalDate day20 = yearMonth.atDay(20);
        return repository.findByTransactionMonthIn(Arrays.asList(day15, day20, endOfMonth));
    }

    public TAccountsReceivableSummary getByPK(int shopNo, int partnerNo, LocalDate transactionMonth,
                                              BigDecimal taxRate, boolean isOtakeGarbageBag) {
        BigDecimal rate = taxRate != null ? taxRate.setScale(2, RoundingMode.HALF_UP) : null;
        return repository.getByShopNoAndPartnerNoAndTransactionMonthAndTaxRateAndIsOtakeGarbageBag(
                shopNo, partnerNo, transactionMonth, rate, isOtakeGarbageBag);
    }

    /**
     * 指定された日付範囲内の売掛金サマリーを取得します。
     */
    public List<TAccountsReceivableSummary> findByDateRange(LocalDate fromDate, LocalDate toDate) {
        return repository.findByTransactionMonthBetween(fromDate, toDate);
    }

    /**
     * 指定された日付範囲内かつ MF出力可否フラグが一致する売掛金サマリーを取得します。
     * 検証済みCSV出力で使用。
     */
    public List<TAccountsReceivableSummary> findByDateRangeAndMfExportEnabled(
            LocalDate fromDate, LocalDate toDate, boolean mfExportEnabled) {
        return repository.findByTransactionMonthBetweenAndMfExportEnabled(fromDate, toDate, mfExportEnabled);
    }

    /**
     * 店舗＋期間で検索（一括検証用）。
     */
    public List<TAccountsReceivableSummary> findByShopAndDateRange(
            Integer shopNo, LocalDate fromDate, LocalDate toDate) {
        if (shopNo == null) {
            return repository.findByTransactionMonthBetween(fromDate, toDate);
        }
        return repository.findByShopNoAndTransactionMonthBetween(shopNo, fromDate, toDate);
    }

    /**
     * 手動で売掛金額を確定します。
     * verified_manually=true をセットし、次回再集計・再検証バッチで上書きされないようにします。
     */
    @Transactional
    public TAccountsReceivableSummary verify(
            int shopNo, int partnerNo, LocalDate transactionMonth, BigDecimal taxRate, boolean isOtakeGarbageBag,
            BigDecimal taxIncludedAmount, BigDecimal taxExcludedAmount, String note, boolean mfExportEnabled) {
        TAccountsReceivableSummary summary = getByPK(shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag);
        if (summary == null) {
            throw new IllegalArgumentException("対象の売掛金集計が見つかりません");
        }
        // 手動確定値を *_amount に確定。*_change は集計結果のまま保持。
        summary.setTaxIncludedAmount(taxIncludedAmount);
        summary.setTaxExcludedAmount(taxExcludedAmount);
        summary.setVerifiedManually(Boolean.TRUE);
        summary.setVerificationNote(note);
        // 手動確定は「一致」扱い
        summary.setVerificationResult(1);
        summary.setMfExportEnabled(mfExportEnabled);
        return save(summary);
    }

    /**
     * 手動確定を解除します。次回再検証バッチで上書きされるようになります。
     */
    @Transactional
    public TAccountsReceivableSummary releaseManualLock(
            int shopNo, int partnerNo, LocalDate transactionMonth,
            BigDecimal taxRate, boolean isOtakeGarbageBag) {
        TAccountsReceivableSummary summary = getByPK(shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag);
        if (summary == null) {
            throw new IllegalArgumentException("対象の売掛金集計が見つかりません");
        }
        summary.setVerifiedManually(Boolean.FALSE);
        return repository.save(summary);
    }

    /**
     * MF出力フラグを更新します。
     */
    @Transactional
    public TAccountsReceivableSummary updateMfExport(
            int shopNo, int partnerNo, LocalDate transactionMonth,
            BigDecimal taxRate, boolean isOtakeGarbageBag, boolean enabled) {
        TAccountsReceivableSummary summary = getByPK(shopNo, partnerNo, transactionMonth, taxRate, isOtakeGarbageBag);
        if (summary == null) {
            throw new IllegalArgumentException("対象の売掛金集計が見つかりません");
        }
        summary.setMfExportEnabled(enabled);
        // MF 出力対象に "する" 意思表示は「運用者がこの行を CSV に含めると確定した」ことを意味する。
        // バッチの一括検証が applyMismatch/applyNotFound で mf_export_enabled を false に戻すのを防ぐため、
        // verified_manually=true を立てておく (既に true なら触らない)。
        // 例) 301491 クリーンラボのように隔月請求で一致判定できないが CSV には含めたい行の運用対応。
        if (enabled && !Boolean.TRUE.equals(summary.getVerifiedManually())) {
            summary.setVerifiedManually(Boolean.TRUE);
            if (summary.getVerificationResult() == null || summary.getVerificationResult() != 1) {
                summary.setVerificationResult(1);
            }
        }
        return repository.save(summary);
    }
}
