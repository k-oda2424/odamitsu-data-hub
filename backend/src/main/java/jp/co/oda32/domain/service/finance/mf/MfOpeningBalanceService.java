package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.audit.AuditLog;
import jp.co.oda32.domain.model.embeddable.MSupplierOpeningBalancePK;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MSupplierOpeningBalance;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MSupplierOpeningBalanceRepository;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.MfOpeningBalanceFetchResponse;
import jp.co.oda32.dto.finance.MfOpeningBalanceFetchResponse.UnmatchedBranch;
import jp.co.oda32.dto.finance.SupplierOpeningBalanceResponse;
import jp.co.oda32.dto.finance.SupplierOpeningBalanceResponse.Row;
import jp.co.oda32.dto.finance.SupplierOpeningBalanceResponse.Summary;
import jp.co.oda32.dto.finance.SupplierOpeningBalanceUpdateRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * supplier 毎の前期繰越 (期首残) 管理サービス。
 * <p>
 * MF /api/v3/journals を fiscal year 開始日 (opening_date + 1) で引き、
 * 買掛金 credit-only な opening balance journal の各 branch を
 * sub_account_name → supplier_no にマッピングして {@code m_supplier_opening_balance} に UPSERT する。
 * 手動補正 ({@code manual_adjustment}) は再取得時も保持される。
 * <p>
 * 設計書: claudedocs/design-supplier-opening-balance.md
 *
 * @since 2026-04-24
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MfOpeningBalanceService {

    /** MATCH 判定の閾値 (円)。合計差がこれ以内なら OK。 */
    private static final BigDecimal MATCH_TOLERANCE = new BigDecimal("100");
    /** MINOR 判定の閾値 (円)。これを超えたら MAJOR。 */
    private static final BigDecimal MINOR_TOLERANCE = new BigDecimal("1000");
    private static final String MF_ACCOUNT_PAYABLE = "買掛金";

    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;
    private final MSupplierOpeningBalanceRepository repository;
    private final MfAccountMasterRepository mfAccountMasterRepository;
    private final MPaymentSupplierService paymentSupplierService;

    /**
     * MF から journal #1 (買掛金 opening balance journal) を取得し、
     * supplier 単位で upsert する。{@code manual_adjustment} は保持。
     *
     * @param shopNo      対象ショップ (B-CART shop=1 が通常)
     * @param openingDate 基準日 (20 日締めバケット日、例: 2025-06-20)
     * @param userNo      実行ユーザー (監査フィールド用)
     */
    @Transactional
    @AuditLog(table = "m_supplier_opening_balance", operation = "mf_fetch",
            pkExpression = "{'shopNo': #a0, 'openingDate': #a1, 'userNo': #a2}",
            captureArgsAsAfter = true)
    public MfOpeningBalanceFetchResponse fetchFromMfJournalOne(
            Integer shopNo, LocalDate openingDate, Integer userNo) {
        if (shopNo == null || openingDate == null || userNo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "shopNo / openingDate / userNo は必須です");
        }
        LocalDate journalDate = openingDate.plusDays(1);

        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();

        // 1 日分だけなので 1 ページで十分 (per_page=1000)
        var res = mfApiClient.listJournals(client, accessToken,
                journalDate.toString(), journalDate.toString(), 1, 1000);
        List<MfJournal> journals = res.items();

        MfJournal openingJournal = MfOpeningJournalDetector.findBest(journals);
        if (openingJournal == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "MF " + journalDate + " に 買掛金 opening balance journal が見つかりません。"
                            + " MF UI で期首残高仕訳を登録してから再取得してください。");
        }

        // trial_balance_bs で期待値 (validation target)
        BigDecimal mfClosing;
        try {
            var tb = mfApiClient.getTrialBalanceBs(client, accessToken, openingDate.toString());
            mfClosing = tb.closingOf(tb.findAccount(MF_ACCOUNT_PAYABLE));
        } catch (Exception e) {
            log.warn("[mf-opening] trial_balance_bs 取得失敗、validation skip: {}", e.getMessage());
            mfClosing = null;
        }

        Map<String, String> subToCode = buildSubToCode();
        Map<String, MPaymentSupplier> codeToSupplier = buildCodeToSupplier(shopNo);

        // SF-G06: ループ毎の findById を回避するため、(shop, openingDate) の全行を 1 query で
        // bulk fetch して Map<supplierNo, entity> を構築。zombie (del_flg='1') も含めて lookup する。
        Map<Integer, MSupplierOpeningBalance> existingByNo = new HashMap<>();
        for (MSupplierOpeningBalance e : repository.findByPkShopNoAndPkOpeningDate(shopNo, openingDate)) {
            existingByNo.put(e.getPk().getSupplierNo(), e);
        }

        List<UnmatchedBranch> unmatched = new ArrayList<>();
        BigDecimal creditSum = BigDecimal.ZERO;
        int matched = 0;
        int upserted = 0;
        int preservedManual = 0;
        Instant now = Instant.now();
        int branchTotal = openingJournal.branches() == null ? 0 : openingJournal.branches().size();

        if (openingJournal.branches() != null) {
            for (MfJournal.MfBranch br : openingJournal.branches()) {
                MfJournal.MfSide cr = br.creditor();
                if (cr == null || !MF_ACCOUNT_PAYABLE.equals(cr.accountName())) continue;
                BigDecimal value = nz(cr.value());
                creditSum = creditSum.add(value);
                String subName = cr.subAccountName();
                if (subName == null || subName.isBlank()) {
                    unmatched.add(UnmatchedBranch.builder()
                            .subAccountName("(sub_account_name なし)").amount(value).build());
                    continue;
                }

                String code = subToCode.get(subName);
                MPaymentSupplier supplier = code == null ? null : codeToSupplier.get(code);
                if (supplier == null) {
                    unmatched.add(UnmatchedBranch.builder()
                            .subAccountName(subName).amount(value).build());
                    continue;
                }
                matched++;

                MSupplierOpeningBalancePK pk = new MSupplierOpeningBalancePK(
                        shopNo, openingDate, supplier.getPaymentSupplierNo());
                MSupplierOpeningBalance existingEntity = existingByNo.get(supplier.getPaymentSupplierNo());
                MSupplierOpeningBalance entity;
                if (existingEntity != null) {
                    entity = existingEntity;
                    if (entity.getManualAdjustment() != null && entity.getManualAdjustment().signum() != 0) {
                        preservedManual++;
                    }
                    // SF-G03: 論理削除されていた行 (zombie) を復活させる。
                    // 旧実装は del_flg='1' のまま fetch すると getEffectiveBalanceMap が
                    // 取得できず累積残算定から落ちる silent zombie を発生させていた。
                    if ("1".equals(entity.getDelFlg())) {
                        entity.setDelFlg("0");
                    }
                    entity.setMfBalance(value);
                    entity.setSourceJournalNumber(openingJournal.number());
                    entity.setSourceSubAccountName(subName);
                    entity.setLastMfFetchedAt(now);
                    entity.setModifyUserNo(userNo);
                    entity.setModifyDateTime(now);
                } else {
                    entity = MSupplierOpeningBalance.builder()
                            .pk(pk)
                            .mfBalance(value)
                            .manualAdjustment(BigDecimal.ZERO)
                            .sourceJournalNumber(openingJournal.number())
                            .sourceSubAccountName(subName)
                            .lastMfFetchedAt(now)
                            .addUserNo(userNo)
                            .modifyUserNo(userNo)
                            .modifyDateTime(now)
                            .delFlg("0")
                            .build();
                }
                repository.save(entity);
                upserted++;
            }
        }

        repository.flush();
        BigDecimal totalEffective = sumEffectiveBalance(shopNo, openingDate);
        BigDecimal diff = mfClosing == null
                ? null
                : totalEffective.subtract(mfClosing);
        String validationLevel = classifyValidation(diff);

        log.info("[mf-opening] shopNo={} openingDate={} journal#={} branches={} matched={} unmatched={} creditSum={} totalEffective={} mfClosing={} diff={} level={}",
                shopNo, openingDate, openingJournal.number(), branchTotal,
                matched, unmatched.size(), creditSum, totalEffective, mfClosing, diff, validationLevel);

        return MfOpeningBalanceFetchResponse.builder()
                .shopNo(shopNo)
                .openingDate(openingDate)
                .journalTransactionDate(journalDate)
                .journalNumber(openingJournal.number())
                .journalCreditSum(creditSum)
                .branchCount(branchTotal)
                .matchedCount(matched)
                .upsertedCount(upserted)
                .preservedManualCount(preservedManual)
                .unmatchedBranches(unmatched)
                .mfTrialBalanceClosing(mfClosing)
                .validationDiff(diff)
                .validationLevel(validationLevel)
                .fetchedAt(now)
                .build();
    }

    /**
     * 手動補正額を更新。再取得では manual_adjustment は保持される。
     * journal #1 に含まれない supplier (shop=2 太幸等) は mf_balance=NULL のまま manual_adjustment で登録可能。
     */
    @Transactional
    @AuditLog(table = "m_supplier_opening_balance", operation = "manual_adjust",
            pkExpression = "{'shopNo': #a0.shopNo, 'openingDate': #a0.openingDate, 'supplierNo': #a0.supplierNo}",
            captureArgsAsAfter = true)
    public void updateManualAdjustment(SupplierOpeningBalanceUpdateRequest req, Integer userNo) {
        if (userNo == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "userNo が取得できません");
        }
        BigDecimal adj = req.manualAdjustment() == null ? BigDecimal.ZERO : req.manualAdjustment();
        if (adj.signum() != 0 && (req.adjustmentReason() == null || req.adjustmentReason().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "manualAdjustment が 0 でない場合は adjustmentReason を入力してください");
        }

        // supplier が shopNo 配下に存在するか検証
        MPaymentSupplier supplier = paymentSupplierService.getByPaymentSupplierNo(req.supplierNo());
        if (supplier == null || !req.shopNo().equals(supplier.getShopNo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "仕入先が見つかりません: supplierNo=" + req.supplierNo());
        }

        MSupplierOpeningBalancePK pk = new MSupplierOpeningBalancePK(
                req.shopNo(), req.openingDate(), req.supplierNo());
        Instant now = Instant.now();
        MSupplierOpeningBalance entity = repository.findById(pk).orElseGet(() ->
                MSupplierOpeningBalance.builder()
                        .pk(pk)
                        .mfBalance(null)
                        .manualAdjustment(BigDecimal.ZERO)
                        .addUserNo(userNo)
                        .modifyUserNo(userNo)
                        .modifyDateTime(now)
                        .delFlg("0")
                        .build());
        entity.setManualAdjustment(adj);
        entity.setAdjustmentReason(req.adjustmentReason());
        entity.setNote(req.note());
        entity.setModifyUserNo(userNo);
        entity.setModifyDateTime(now);
        repository.save(entity);
        log.info("[mf-opening] manual adjustment 更新: shopNo={} date={} supplierNo={} adj={}",
                req.shopNo(), req.openingDate(), req.supplierNo(), adj);
    }

    /** 指定 (shop, openingDate) の全 row を supplier 情報付きで返す。 */
    public SupplierOpeningBalanceResponse list(Integer shopNo, LocalDate openingDate) {
        List<MSupplierOpeningBalance> all = repository.findByPkShopNoAndPkOpeningDateAndDelFlg(
                shopNo, openingDate, "0");

        Collection<MPaymentSupplier> suppliers = paymentSupplierService.findByShopNo(shopNo);
        Map<Integer, MPaymentSupplier> byNo = new HashMap<>();
        for (MPaymentSupplier s : suppliers) byNo.put(s.getPaymentSupplierNo(), s);

        List<Row> rows = new ArrayList<>();
        BigDecimal totalMf = BigDecimal.ZERO;
        BigDecimal totalAdj = BigDecimal.ZERO;
        BigDecimal totalEff = BigDecimal.ZERO;
        int mfSourced = 0;
        int adjusted = 0;
        int unmatched = 0;

        for (MSupplierOpeningBalance e : all) {
            MPaymentSupplier sup = byNo.get(e.getPk().getSupplierNo());
            boolean unmatchedFlag = sup == null;
            if (unmatchedFlag) unmatched++;
            BigDecimal mf = e.getMfBalance();
            BigDecimal adj = nz(e.getManualAdjustment());
            // SF-G07: DB GENERATED 列 effective_balance を直接利用 (SF-G02 で UPDATE 後も refresh される)。
            // 旧実装の Java 側 add 計算は GENERATED 列との二重計算で乖離リスクがあった。
            BigDecimal eff = nz(e.getEffectiveBalance());
            if (mf != null) mfSourced++;
            if (adj.signum() != 0) adjusted++;
            totalMf = totalMf.add(nz(mf));
            totalAdj = totalAdj.add(adj);
            totalEff = totalEff.add(eff);

            rows.add(Row.builder()
                    .supplierNo(e.getPk().getSupplierNo())
                    .supplierCode(sup == null ? null : sup.getPaymentSupplierCode())
                    .supplierName(sup == null ? null : sup.getPaymentSupplierName())
                    .mfBalance(mf)
                    .manualAdjustment(adj)
                    .effectiveBalance(eff)
                    .sourceJournalNumber(e.getSourceJournalNumber())
                    .sourceSubAccountName(e.getSourceSubAccountName())
                    .lastMfFetchedAt(e.getLastMfFetchedAt())
                    .adjustmentReason(e.getAdjustmentReason())
                    .note(e.getNote())
                    .unmatched(unmatchedFlag)
                    .build());
        }
        rows.sort(Comparator.comparing((Row r) -> nz(r.effectiveBalance())).reversed());

        // trial_balance_bs で合計検証 (token 未取得なら null)
        BigDecimal mfClosing = tryGetMfTrialBalanceClosing(openingDate);
        BigDecimal diff = mfClosing == null ? null : totalEff.subtract(mfClosing);

        Summary summary = Summary.builder()
                .totalRowCount(rows.size())
                .mfSourcedCount(mfSourced)
                .manuallyAdjustedCount(adjusted)
                .unmatchedCount(unmatched)
                .totalMfBalance(totalMf)
                .totalManualAdjustment(totalAdj)
                .totalEffectiveBalance(totalEff)
                .mfTrialBalanceClosing(mfClosing)
                .validationDiff(diff)
                .validationLevel(classifyValidation(diff))
                .build();

        return SupplierOpeningBalanceResponse.builder()
                .shopNo(shopNo)
                .openingDate(openingDate)
                .rows(rows)
                .summary(summary)
                .build();
    }

    /** (shop, openingDate) の effective_balance を supplierNo → 金額 Map で返す。下流 service が累積注入に使用。 */
    public Map<Integer, BigDecimal> getEffectiveBalanceMap(Integer shopNo, LocalDate openingDate) {
        if (shopNo == null || openingDate == null) return Collections.emptyMap();
        List<MSupplierOpeningBalance> all = repository.findByPkShopNoAndPkOpeningDateAndDelFlg(
                shopNo, openingDate, "0");
        Map<Integer, BigDecimal> out = new HashMap<>();
        for (MSupplierOpeningBalance e : all) {
            // SF-G07: DB GENERATED 列 effective_balance を直接参照 (SF-G02 で UPDATE 後も refresh される)。
            BigDecimal v = nz(e.getEffectiveBalance());
            if (v.signum() != 0) out.put(e.getPk().getSupplierNo(), v);
        }
        return out;
    }

    private Map<String, String> buildSubToCode() {
        List<MfAccountMaster> all = mfAccountMasterRepository.findAll();
        Map<String, String> m = new HashMap<>();
        for (MfAccountMaster r : all) {
            if (!MF_ACCOUNT_PAYABLE.equals(r.getAccountName())) continue;
            if (!MF_ACCOUNT_PAYABLE.equals(r.getFinancialStatementItem())) continue;
            if (r.getSubAccountName() == null || r.getSearchKey() == null) continue;
            // 同一 sub_account に複数 search_key (supplier_code) がマッピングされるケースは先勝ち。
            // list() / fetch() 両方で一貫動作させる。
            m.putIfAbsent(r.getSubAccountName(), r.getSearchKey());
        }
        return m;
    }

    private Map<String, MPaymentSupplier> buildCodeToSupplier(Integer shopNo) {
        Map<String, MPaymentSupplier> m = new HashMap<>();
        for (MPaymentSupplier s : paymentSupplierService.findByShopNo(shopNo)) {
            if (s.getPaymentSupplierCode() != null) {
                m.putIfAbsent(s.getPaymentSupplierCode(), s);
            }
        }
        return m;
    }

    private BigDecimal sumEffectiveBalance(Integer shopNo, LocalDate openingDate) {
        BigDecimal sum = BigDecimal.ZERO;
        for (MSupplierOpeningBalance e : repository.findByPkShopNoAndPkOpeningDateAndDelFlg(
                shopNo, openingDate, "0")) {
            // SF-G07: DB GENERATED 列 effective_balance を集計 (fetchFromMfJournalOne の repository.flush() 後に呼ばれる)。
            sum = sum.add(nz(e.getEffectiveBalance()));
        }
        return sum;
    }

    private BigDecimal tryGetMfTrialBalanceClosing(LocalDate openingDate) {
        try {
            MMfOauthClient client = mfOauthService.findActiveClient().orElse(null);
            if (client == null) return null;
            String accessToken = mfOauthService.getValidAccessToken();
            var tb = mfApiClient.getTrialBalanceBs(client, accessToken, openingDate.toString());
            return tb.closingOf(tb.findAccount(MF_ACCOUNT_PAYABLE));
        } catch (Exception e) {
            log.warn("[mf-opening] trial_balance_bs validation skip: {}", e.getMessage());
            return null;
        }
    }

    private static String classifyValidation(BigDecimal diff) {
        if (diff == null) return "UNKNOWN";
        BigDecimal abs = diff.abs();
        if (abs.compareTo(MATCH_TOLERANCE) <= 0) return "MATCH";
        if (abs.compareTo(MINOR_TOLERANCE) <= 0) return "MINOR";
        return "MAJOR";
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
