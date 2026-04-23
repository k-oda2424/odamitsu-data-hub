package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MF 買掛金 debit (支払取崩) を supplier × 月 単位に集計して返す。
 * <p>
 * 案 A (2026-04-23): paymentSettled のソースを「前月 verified_amount」から
 * 「MF journal の当月 debit (買掛金取崩)」に切り替えるための service。
 * <p>
 * MF 期首 (2025-06-21) 以降の bucket でのみデータが取れるため、期首前は空 Map を返し
 * 呼び出し側で verified_amount ベースに fallback する。
 *
 * @since 2026-04-23
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class MfPaymentAggregator {

    /** MF 会計期首 (全期間累積の起点、軸 D と揃え)。 */
    private static final LocalDate MF_PERIOD_START = LocalDate.of(2025, 5, 20);
    /**
     * MF journal debit が取得可能になる最初の 20日締め bucket。
     * fiscal year 2025-06-21 以降の取引 → toClosingMonthDay20 で 2025-07-20 bucket。
     */
    private static final LocalDate MF_FIRST_BUCKET = LocalDate.of(2025, 7, 20);
    private static final String MF_ACCOUNT_PAYABLE = "買掛金";

    private final MfOauthService mfOauthService;
    private final MfJournalCacheService journalCache;
    private final MfAccountMasterRepository mfAccountMasterRepository;
    private final MPaymentSupplierService paymentSupplierService;

    /**
     * 指定 shop × transactionMonth の MF debit を supplier 単位で集計。
     * 期首前 bucket or MF client 未設定 or token 取得失敗時は空 Map を返す (呼び出し側で fallback)。
     */
    public Map<Integer, BigDecimal> getMfDebitBySupplierForMonth(
            Integer shopNo, LocalDate transactionMonth) {
        if (transactionMonth == null || transactionMonth.isBefore(MF_FIRST_BUCKET)) {
            return Map.of();
        }

        MMfOauthClient client = mfOauthService.findActiveClient().orElse(null);
        if (client == null) {
            log.warn("[mf-payment] MF client 未設定。paymentSettled は verified_amount で fallback");
            return Map.of();
        }
        String accessToken;
        try {
            accessToken = mfOauthService.getValidAccessToken();
        } catch (Exception e) {
            log.warn("[mf-payment] access_token 取得失敗。paymentSettled は verified_amount で fallback: {}", e.getMessage());
            return Map.of();
        }

        MfJournalCacheService.CachedResult cached;
        try {
            cached = journalCache.getOrFetch(
                    shopNo, client, accessToken, MF_PERIOD_START, transactionMonth, false);
        } catch (Exception e) {
            log.warn("[mf-payment] journals 取得失敗。paymentSettled は verified_amount で fallback: {}", e.getMessage());
            return Map.of();
        }
        List<MfJournal> allJournals = cached.journals();

        // sub_account_name → supplier_code 逆引き
        Map<String, Set<String>> subToCodes = buildMfSubToCodes();

        // supplier_code → supplier_no
        Collection<MPaymentSupplier> suppliers = paymentSupplierService.findByShopNo(shopNo);
        Map<String, Integer> codeToSupplierNo = new HashMap<>();
        for (MPaymentSupplier s : suppliers) {
            if (s.getPaymentSupplierCode() != null) {
                codeToSupplierNo.put(s.getPaymentSupplierCode(), s.getPaymentSupplierNo());
            }
        }

        // transactionMonth bucket の買掛金 debit を sub_account 単位に集計
        Map<String, BigDecimal> debitBySub = new HashMap<>();
        for (MfJournal j : allJournals) {
            if (j.transactionDate() == null || j.branches() == null) continue;
            LocalDate monthKey = MfJournalFetcher.toClosingMonthDay20(j.transactionDate());
            if (!monthKey.equals(transactionMonth)) continue;

            for (MfJournal.MfBranch br : j.branches()) {
                var de = br.debitor();
                if (de != null && MF_ACCOUNT_PAYABLE.equals(de.accountName())
                        && de.subAccountName() != null) {
                    debitBySub.merge(de.subAccountName(), nz(de.value()), BigDecimal::add);
                }
            }
        }

        // sub_account_name → supplier_no に変換
        Map<Integer, BigDecimal> result = new HashMap<>();
        for (Map.Entry<String, BigDecimal> e : debitBySub.entrySet()) {
            Set<String> codes = subToCodes.get(e.getKey());
            if (codes == null) continue;
            for (String code : codes) {
                Integer no = codeToSupplierNo.get(code);
                if (no != null) {
                    result.merge(no, e.getValue(), BigDecimal::add);
                    break; // 複数 supplier_code が同一 sub_account にマッピングされる稀ケースは先頭採用
                }
            }
        }
        log.info("[mf-payment] shopNo={} month={} MF debit supplier 数={}, journals {} 件",
                shopNo, transactionMonth, result.size(), allJournals.size());
        return result;
    }

    private Map<String, Set<String>> buildMfSubToCodes() {
        List<MfAccountMaster> all = mfAccountMasterRepository.findAll();
        Map<String, Set<String>> m = new HashMap<>();
        for (MfAccountMaster r : all) {
            if (!MF_ACCOUNT_PAYABLE.equals(r.getAccountName())) continue;
            if (!MF_ACCOUNT_PAYABLE.equals(r.getFinancialStatementItem())) continue;
            if (r.getSubAccountName() == null || r.getSubAccountName().isBlank()) continue;
            if (r.getSearchKey() == null || r.getSearchKey().isBlank()) continue;
            m.computeIfAbsent(r.getSubAccountName(), k -> new HashSet<>())
                    .add(r.getSearchKey());
        }
        return m;
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
