package jp.co.oda32.domain.service.finance;

import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.service.finance.mf.MfJournal;
import jp.co.oda32.domain.service.finance.mf.MfJournalCacheService;
import jp.co.oda32.domain.service.finance.mf.MfJournalFetcher;
import jp.co.oda32.domain.service.finance.mf.MfOauthService;
import jp.co.oda32.domain.service.finance.mf.MfOpeningBalanceService;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.SupplierBalancesResponse;
import jp.co.oda32.dto.finance.SupplierBalancesResponse.SupplierBalanceRow;
import jp.co.oda32.dto.finance.SupplierBalancesResponse.Summary;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 買掛 supplier 累積残一覧サービス (軸 D)。
 * <p>
 * 期首 (2025-05-20) 〜 asOfMonth の全 supplier 累積残を自社 summary と MF /journals で突合。
 * MATCH / MINOR / MAJOR / MF_MISSING / SELF_MISSING で分類。
 * <p>
 * 設計書: claudedocs/design-supplier-balances-health.md §3, §5
 *
 * @since 2026-04-23
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SupplierBalancesService {

    private static final String MF_ACCOUNT_PAYABLE = "買掛金";
    private static final BigDecimal MINOR_UPPER = BigDecimal.valueOf(1000);

    private final MfOauthService mfOauthService;
    private final MfJournalCacheService journalCache;
    private final MfAccountMasterRepository mfAccountMasterRepository;
    private final TAccountsPayableSummaryRepository summaryRepository;
    private final MPaymentSupplierService paymentSupplierService;
    private final MfOpeningBalanceService openingBalanceService;

    public SupplierBalancesResponse generate(Integer shopNo, LocalDate asOfMonth, boolean refresh) {
        if (shopNo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "shopNo は必須です");
        }

        // asOfMonth 省略時は findLatestTransactionMonth、summary が空の場合は空レスポンス
        LocalDate resolvedMonth = asOfMonth != null
                ? asOfMonth
                : summaryRepository.findLatestTransactionMonth(shopNo).orElse(null);
        if (resolvedMonth == null) {
            return emptyResponse(shopNo);
        }
        if (resolvedMonth.getDayOfMonth() != 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "asOfMonth は 20 日締め日 (yyyy-MM-20) で指定してください");
        }
        if (resolvedMonth.isBefore(MfPeriodConstants.MF_JOURNALS_FETCH_FROM)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "asOfMonth は MF 会計期首 " + MfPeriodConstants.MF_JOURNALS_FETCH_FROM + " 以降を指定してください");
        }

        // --- 自社 summary 期間取得 (期首〜asOfMonth) ---
        List<TAccountsPayableSummary> selfRows = summaryRepository
                .findByShopNoAndTransactionMonthBetweenOrderBySupplierNoAscTransactionMonthAscTaxRateAsc(
                        shopNo, MfPeriodConstants.MF_JOURNALS_FETCH_FROM, resolvedMonth);

        Map<Integer, List<TAccountsPayableSummary>> selfBySupplier = new HashMap<>();
        for (TAccountsPayableSummary r : selfRows) {
            selfBySupplier.computeIfAbsent(r.getSupplierNo(), k -> new ArrayList<>()).add(r);
        }

        // --- MF journals 取得 (キャッシュ経由) ---
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();
        MfJournalCacheService.CachedResult cached = journalCache.getOrFetch(
                shopNo, client, accessToken, MfPeriodConstants.MF_JOURNALS_FETCH_FROM, resolvedMonth, refresh);
        List<MfJournal> allJournals = cached.journals();
        Instant fetchedAt = cached.oldestFetchedAt();
        log.info("[supplier-balances] shopNo={} asOfMonth={} journals={} fetchedAt={}",
                shopNo, resolvedMonth, allJournals.size(), fetchedAt);

        // --- mf_account_master 逆引き map (sub_account_name → supplier_code set) ---
        Map<String, Set<String>> mfSubToCodes = buildMfSubToCodes();

        // --- MF journals を sub_account 単位 credit/debit 累積 ---
        Map<String, MfAccum> mfBySub = accumulateMfJournals(allJournals, resolvedMonth);

        // --- supplier 情報取得 ---
        Collection<MPaymentSupplier> allSuppliers = paymentSupplierService.findByShopNo(shopNo);
        Map<Integer, MPaymentSupplier> supplierByNo = new HashMap<>();
        Map<String, MPaymentSupplier> supplierByCode = new HashMap<>();
        for (MPaymentSupplier s : allSuppliers) {
            supplierByNo.put(s.getPaymentSupplierNo(), s);
            if (s.getPaymentSupplierCode() != null) supplierByCode.put(s.getPaymentSupplierCode(), s);
        }

        // --- 前期繰越 (supplier 単位期首残) — 自社側 opening/closing に加算 ---
        // SF-G01 で MF 側 accumulation から journal #1 (期首残仕訳) を除外。
        // self/MF 両側ともに opening は m_supplier_opening_balance.effectiveBalance 経由で注入する。
        // self 側のみ openingMap 注入を行い、t_accounts_payable_summary の transactionMonth=2025-05-20 行は
        // accumulateSelf() 内で opening_balance_tax_included を読まない (CR-G01 二重計上防止)。
        Map<Integer, BigDecimal> openingMap = new HashMap<>(
                openingBalanceService.getEffectiveBalanceMap(shopNo, MfPeriodConstants.SELF_BACKFILL_START));

        // --- 突合 ---
        List<SupplierBalanceRow> rows = new ArrayList<>();
        Set<String> processedSubs = new HashSet<>();

        // 1) self side (supplier が自社 summary にある)
        for (Map.Entry<Integer, List<TAccountsPayableSummary>> e : selfBySupplier.entrySet()) {
            Integer supplierNo = e.getKey();
            MPaymentSupplier sup = supplierByNo.get(supplierNo);
            if (sup == null) continue;
            SelfAccum self = accumulateSelf(e.getValue(), resolvedMonth);
            BigDecimal opening = nz(openingMap.remove(supplierNo));
            self.opening = self.opening.add(opening);
            self.closing = self.closing.add(opening);

            Set<String> matchedSubs = resolveMatchedSubs(mfSubToCodes, sup.getPaymentSupplierCode(), mfBySub.keySet());
            MfAccum mf = sumMfFor(mfBySub, matchedSubs, processedSubs);
            boolean masterRegistered = !matchedSubs.isEmpty();

            rows.add(buildRow(supplierNo, sup, self, mf, matchedSubs, masterRegistered));
        }

        // 2) MF side (sub_account が self 未登録)
        for (Map.Entry<String, MfAccum> e : mfBySub.entrySet()) {
            String subName = e.getKey();
            if (processedSubs.contains(subName)) continue;
            MfAccum mf = e.getValue();
            if (mf.credit.signum() == 0 && mf.debit.signum() == 0) continue;

            Set<String> codes = mfSubToCodes.getOrDefault(subName, Set.of());
            Integer guessedNo = null;
            String guessedCode = null;
            String guessedName = subName;
            for (String code : codes) {
                MPaymentSupplier s = supplierByCode.get(code);
                if (s != null) {
                    guessedNo = s.getPaymentSupplierNo();
                    guessedCode = code;
                    guessedName = s.getPaymentSupplierName();
                    break;
                }
            }
            // guessedNo が解決されていれば opening をここでも注入 (self 未登録だが期首残を持つ supplier)
            BigDecimal opening = guessedNo == null ? BigDecimal.ZERO : nz(openingMap.remove(guessedNo));
            rows.add(SupplierBalanceRow.builder()
                    .supplierNo(guessedNo)
                    .supplierCode(guessedCode)
                    .supplierName(guessedName)
                    .selfBalance(opening)
                    .mfBalance(mf.credit.subtract(mf.debit))
                    .diff(opening.subtract(mf.credit.subtract(mf.debit)))
                    .status("SELF_MISSING")
                    .masterRegistered(!codes.isEmpty())
                    .selfOpening(opening)
                    .selfChangeCumulative(BigDecimal.ZERO)
                    .selfPaymentCumulative(BigDecimal.ZERO)
                    .mfCreditCumulative(mf.credit)
                    .mfDebitCumulative(mf.debit)
                    .mfSubAccountNames(List.of(subName))
                    .build());
        }

        // 3) opening のみある supplier (self 未登録 かつ MF 側 activity なし) を残り処理
        for (Map.Entry<Integer, BigDecimal> e : openingMap.entrySet()) {
            Integer supplierNo = e.getKey();
            BigDecimal opening = e.getValue();
            if (opening.signum() == 0) continue;
            MPaymentSupplier sup = supplierByNo.get(supplierNo);
            if (sup == null) continue;
            SelfAccum self = new SelfAccum();
            self.opening = opening;
            self.closing = opening;
            Set<String> matchedSubs = resolveMatchedSubs(mfSubToCodes, sup.getPaymentSupplierCode(), mfBySub.keySet());
            rows.add(buildRow(supplierNo, sup, self, new MfAccum(), matchedSubs, !matchedSubs.isEmpty()));
        }

        rows.sort((a, b) -> b.getDiff().abs().compareTo(a.getDiff().abs()));
        Summary summary = buildSummary(rows);

        return SupplierBalancesResponse.builder()
                .shopNo(shopNo)
                .asOfMonth(resolvedMonth)
                .mfStartDate(MfPeriodConstants.MF_JOURNALS_FETCH_FROM)
                .fetchedAt(fetchedAt != null ? fetchedAt : Instant.now())
                .totalJournalCount(allJournals.size())
                .rows(rows)
                .summary(summary)
                .build();
    }

    private Map<String, Set<String>> buildMfSubToCodes() {
        List<MfAccountMaster> all = mfAccountMasterRepository.findAll();
        Map<String, Set<String>> m = new HashMap<>();
        for (MfAccountMaster r : all) {
            if (!MF_ACCOUNT_PAYABLE.equals(r.getAccountName())) continue;
            if (!MF_ACCOUNT_PAYABLE.equals(r.getFinancialStatementItem())) continue;
            if (r.getSubAccountName() == null || r.getSubAccountName().isBlank()) continue;
            if (r.getSearchKey() == null || r.getSearchKey().isBlank()) continue;
            m.computeIfAbsent(r.getSubAccountName(), k -> new HashSet<>()).add(r.getSearchKey());
        }
        return m;
    }

    private Map<String, MfAccum> accumulateMfJournals(List<MfJournal> journals, LocalDate asOfMonth) {
        // SF-G01: 期首残高仕訳 (journal #1) は m_supplier_opening_balance 経由で別途注入するため
        // ここでも除外する。これにより MfSupplierLedgerService / AccountsPayableIntegrityService /
        // SupplierBalancesService の 3 サービスで journal #1 除外を統一し、二重計上を防止する。
        // opening は SupplierBalancesService.generate() の openingMap (effectiveBalanceMap) で
        // self.opening + buildRow への注入として加算される。
        Map<String, MfAccum> map = new TreeMap<>();
        for (MfJournal j : journals) {
            if (j.transactionDate() == null || j.branches() == null) continue;
            if (MfJournalFetcher.isPayableOpeningJournal(j)) continue; // 期首残高仕訳は除外 (前期繰越として別管理)
            LocalDate monthKey = MfJournalFetcher.toClosingMonthDay20(j.transactionDate());
            if (monthKey.isAfter(asOfMonth) || monthKey.isBefore(MfPeriodConstants.MF_JOURNALS_FETCH_FROM)) continue;
            for (MfJournal.MfBranch br : j.branches()) {
                var cr = br.creditor();
                var de = br.debitor();
                if (cr != null && MF_ACCOUNT_PAYABLE.equals(cr.accountName())
                        && cr.subAccountName() != null) {
                    MfAccum accum = map.computeIfAbsent(cr.subAccountName(), k -> new MfAccum());
                    accum.credit = accum.credit.add(nz(cr.value()));
                }
                if (de != null && MF_ACCOUNT_PAYABLE.equals(de.accountName())
                        && de.subAccountName() != null) {
                    MfAccum accum = map.computeIfAbsent(de.subAccountName(), k -> new MfAccum());
                    accum.debit = accum.debit.add(nz(de.value()));
                }
            }
        }
        return map;
    }

    private SelfAccum accumulateSelf(List<TAccountsPayableSummary> rows, LocalDate asOfMonth) {
        // CR-G01: opening の取得先は m_supplier_opening_balance.effectiveBalance に統一。
        // 旧実装は transactionMonth == MF_JOURNALS_FETCH_FROM (=2025-05-20) 行から
        // opening_balance_tax_included を累積していたが、generate() 側の openingMap 注入と
        // 二重計上になるため除外。closing は asOfMonth 行のみ加算 (期間内最終月の残高)。
        SelfAccum a = new SelfAccum();
        for (TAccountsPayableSummary r : rows) {
            a.change = a.change.add(PayableBalanceCalculator.effectiveChangeTaxIncluded(r));
            a.payment = a.payment.add(nz(r.getPaymentAmountSettledTaxIncluded()));
            if (asOfMonth.equals(r.getTransactionMonth())) {
                a.closing = a.closing.add(PayableBalanceCalculator.closingTaxIncluded(r));
            }
        }
        return a;
    }

    private Set<String> resolveMatchedSubs(Map<String, Set<String>> mfSubToCodes,
                                           String supplierCode, Set<String> mfSubKeys) {
        Set<String> matched = new HashSet<>();
        if (supplierCode == null) return matched;
        for (Map.Entry<String, Set<String>> e : mfSubToCodes.entrySet()) {
            if (e.getValue().contains(supplierCode) && mfSubKeys.contains(e.getKey())) {
                matched.add(e.getKey());
            }
        }
        // master 登録あるが activity 0 のケースも含めて返す
        for (Map.Entry<String, Set<String>> e : mfSubToCodes.entrySet()) {
            if (e.getValue().contains(supplierCode)) matched.add(e.getKey());
        }
        return matched;
    }

    private MfAccum sumMfFor(Map<String, MfAccum> mfBySub, Set<String> matchedSubs, Set<String> processed) {
        MfAccum sum = new MfAccum();
        for (String sn : matchedSubs) {
            MfAccum a = mfBySub.get(sn);
            if (a != null) {
                sum.credit = sum.credit.add(a.credit);
                sum.debit = sum.debit.add(a.debit);
                processed.add(sn);
            }
        }
        return sum;
    }

    private SupplierBalanceRow buildRow(Integer supplierNo, MPaymentSupplier sup,
                                        SelfAccum self, MfAccum mf, Set<String> matchedSubs,
                                        boolean masterRegistered) {
        BigDecimal mfBalance = mf.credit.subtract(mf.debit);
        BigDecimal diff = self.closing.subtract(mfBalance);
        String status = classify(self, mf, diff, masterRegistered);
        return SupplierBalanceRow.builder()
                .supplierNo(supplierNo)
                .supplierCode(sup.getPaymentSupplierCode())
                .supplierName(sup.getPaymentSupplierName())
                .selfBalance(self.closing)
                .mfBalance(mfBalance)
                .diff(diff)
                .status(status)
                .masterRegistered(masterRegistered)
                .selfOpening(self.opening)
                .selfChangeCumulative(self.change)
                .selfPaymentCumulative(self.payment)
                .mfCreditCumulative(mf.credit)
                .mfDebitCumulative(mf.debit)
                .mfSubAccountNames(new ArrayList<>(matchedSubs))
                .build();
    }

    private String classify(SelfAccum self, MfAccum mf, BigDecimal diff, boolean masterRegistered) {
        boolean selfActive = self.closing.signum() != 0 || self.change.signum() != 0 || self.payment.signum() != 0;
        boolean mfActive = mf.credit.signum() != 0 || mf.debit.signum() != 0;
        if (selfActive && !mfActive) return "MF_MISSING";
        if (!selfActive && mfActive) return "SELF_MISSING";
        if (!selfActive && !mfActive) return "MATCH";
        BigDecimal abs = diff.abs();
        if (abs.compareTo(FinanceConstants.MATCH_TOLERANCE) <= 0) return "MATCH";
        if (abs.compareTo(MINOR_UPPER) <= 0) return "MINOR";
        return "MAJOR";
    }

    private Summary buildSummary(List<SupplierBalanceRow> rows) {
        int matched = 0, minor = 0, major = 0, mfMissing = 0, selfMissing = 0;
        BigDecimal totalSelf = BigDecimal.ZERO;
        BigDecimal totalMf = BigDecimal.ZERO;
        BigDecimal totalDiff = BigDecimal.ZERO;
        for (SupplierBalanceRow r : rows) {
            switch (r.getStatus()) {
                case "MATCH" -> matched++;
                case "MINOR" -> minor++;
                case "MAJOR" -> major++;
                case "MF_MISSING" -> mfMissing++;
                case "SELF_MISSING" -> selfMissing++;
            }
            totalSelf = totalSelf.add(r.getSelfBalance());
            totalMf = totalMf.add(r.getMfBalance());
            totalDiff = totalDiff.add(r.getDiff());
        }
        return Summary.builder()
                .totalSuppliers(rows.size())
                .matchedCount(matched)
                .minorCount(minor)
                .majorCount(major)
                .mfMissingCount(mfMissing)
                .selfMissingCount(selfMissing)
                .totalSelfBalance(totalSelf)
                .totalMfBalance(totalMf)
                .totalDiff(totalDiff)
                .build();
    }

    private SupplierBalancesResponse emptyResponse(Integer shopNo) {
        return SupplierBalancesResponse.builder()
                .shopNo(shopNo)
                .asOfMonth(null)
                .mfStartDate(MfPeriodConstants.MF_JOURNALS_FETCH_FROM)
                .fetchedAt(Instant.now())
                .totalJournalCount(0)
                .rows(List.of())
                .summary(Summary.builder()
                        .totalSuppliers(0).matchedCount(0).minorCount(0).majorCount(0)
                        .mfMissingCount(0).selfMissingCount(0)
                        .totalSelfBalance(BigDecimal.ZERO)
                        .totalMfBalance(BigDecimal.ZERO)
                        .totalDiff(BigDecimal.ZERO)
                        .build())
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static class SelfAccum {
        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal change = BigDecimal.ZERO;
        BigDecimal payment = BigDecimal.ZERO;
        BigDecimal closing = BigDecimal.ZERO;
    }

    private static class MfAccum {
        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal debit = BigDecimal.ZERO;
    }
}
