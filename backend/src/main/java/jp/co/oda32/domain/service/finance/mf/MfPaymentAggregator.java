package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import jp.co.oda32.domain.service.finance.MfPeriodConstants;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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

    private static final String MF_ACCOUNT_PAYABLE = "買掛金";
    /** mf_account_master / m_payment_supplier の reverse index TTL (5 分)。 */
    private static final Duration REVERSE_INDEX_TTL = Duration.ofMinutes(5);

    private final MfOauthService mfOauthService;
    private final MfJournalCacheService journalCache;
    private final MfAccountMasterRepository mfAccountMasterRepository;
    private final MPaymentSupplierService paymentSupplierService;

    /**
     * shopNo → reverse index cache。backfill 12 ヶ月などで毎月 fetch される
     * mfSubToCodes() / paymentSupplierService.findByShopNo を TTL 5 分で memoize。
     * Spring @Cacheable を導入していない (依存追加回避) ので private field で十分。
     */
    private final Map<Integer, MfReverseIndexCache> reverseIndexCacheByShop = new ConcurrentHashMap<>();

    /**
     * 指定 shop × transactionMonth の MF debit を supplier 単位で集計。
     * 期首前 bucket or MF client 未設定 or token 取得失敗時は空 Map を返す (呼び出し側で fallback)。
     */
    public Map<Integer, BigDecimal> getMfDebitBySupplierForMonth(
            Integer shopNo, LocalDate transactionMonth) {
        if (transactionMonth == null || transactionMonth.isBefore(MfPeriodConstants.FIRST_PAYABLE_BUCKET)) {
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
                    shopNo, client, accessToken, MfPeriodConstants.MF_JOURNALS_FETCH_FROM, transactionMonth, false);
        } catch (Exception e) {
            log.warn("[mf-payment] journals 取得失敗。paymentSettled は verified_amount で fallback: {}", e.getMessage());
            return Map.of();
        }
        List<MfJournal> allJournals = cached.journals();

        // sub_account_name → supplier_code 逆引き と supplier_code → supplier_no を TTL 5 分でキャッシュ
        // backfill 12 ヶ月では月ごとに同じ master/supplier を取り直していたので集約 (SF-23)
        MfReverseIndexCache cache = getOrComputeReverseIndex(shopNo);
        Map<String, Set<String>> subToCodes = cache.mfSubToCodes();
        Map<String, Integer> codeToSupplierNo = cache.codeToSupplierNo();

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
            if (codes.size() > 1) {
                log.warn("[mf-payment] MF subAccountName={} に複数の supplier_code がヒット: {} (先頭採用)",
                        e.getKey(), codes);
            }
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

    /**
     * shopNo 単位で reverse index (mfSubToCodes + codeToSupplierNo) を TTL 付きで取得。
     * 同一 shopNo の連続呼び出し (backfill 12 ヶ月など) で master/supplier 取得を 1 回に集約。
     */
    private MfReverseIndexCache getOrComputeReverseIndex(Integer shopNo) {
        MfReverseIndexCache existing = reverseIndexCacheByShop.get(shopNo);
        Instant now = Instant.now();
        if (existing != null && Duration.between(existing.computedAt(), now).compareTo(REVERSE_INDEX_TTL) < 0) {
            return existing;
        }
        Map<String, Set<String>> mfSubToCodes = buildMfSubToCodes();
        Collection<MPaymentSupplier> suppliers = paymentSupplierService.findByShopNo(shopNo);
        Map<String, Integer> codeToSupplierNo = new HashMap<>();
        for (MPaymentSupplier s : suppliers) {
            if (s.getPaymentSupplierCode() != null) {
                codeToSupplierNo.put(s.getPaymentSupplierCode(), s.getPaymentSupplierNo());
            }
        }
        MfReverseIndexCache fresh = new MfReverseIndexCache(shopNo, now, mfSubToCodes, codeToSupplierNo);
        reverseIndexCacheByShop.put(shopNo, fresh);
        return fresh;
    }

    /** mf_account_master / m_payment_supplier の reverse index snapshot (TTL 5 分)。 */
    record MfReverseIndexCache(
            Integer shopNo,
            Instant computedAt,
            Map<String, Set<String>> mfSubToCodes,
            Map<String, Integer> codeToSupplierNo
    ) {}
}
