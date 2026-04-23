package jp.co.oda32.domain.service.finance;

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
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.IntegrityReportResponse;
import jp.co.oda32.dto.finance.IntegrityReportResponse.AmountMismatchEntry;
import jp.co.oda32.dto.finance.IntegrityReportResponse.MfOnlyEntry;
import jp.co.oda32.dto.finance.IntegrityReportResponse.SelfOnlyEntry;
import jp.co.oda32.dto.finance.IntegrityReportResponse.Summary;
import jp.co.oda32.dto.finance.IntegrityReportResponse.UnmatchedSupplierEntry;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 買掛帳 整合性検出機能 (軸 B + 軸 C)。
 * <p>
 * 期間内の全 supplier を一括診断し、self (自社 summary) と MF (/journals) を
 * supplier × 月 単位で突合して 4 カテゴリに分類:
 * <ul>
 *   <li>MF_ONLY (MfOnlyEntry): MF にあって self に無い (= CSV 出力漏れ疑い)</li>
 *   <li>SELF_ONLY (SelfOnlyEntry): self にあって MF に無い (= CSV 取込漏れ or MF 手入力漏れ)</li>
 *   <li>AMOUNT_DIFF (AmountMismatchEntry): ペアあり金額差 (MINOR: 100<diff≤1000, MAJOR: diff>1000)</li>
 *   <li>UNMATCHED_SUPPLIER (UnmatchedSupplierEntry): mf_account_master に登録無し supplier (supplier 単位)</li>
 * </ul>
 * <p>
 * 設計書: claudedocs/design-integrity-report.md §3, §5
 *
 * @since 2026-04-22
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountsPayableIntegrityService {

    private static final int MAX_PERIOD_MONTHS = 12;
    private static final String MF_ACCOUNT_PAYABLE = "買掛金";
    /** MATCH 許容差: ±¥100 以下は一致とみなす (既存 SmilePaymentVerifier 準拠)。 */
    private static final BigDecimal MATCH_TOLERANCE = BigDecimal.valueOf(100);
    /** MINOR 上限: 100 < diff ≤ 1000 → MINOR、1000 超 → MAJOR。 */
    private static final BigDecimal MINOR_UPPER = BigDecimal.valueOf(1000);

    private final MfOauthService mfOauthService;
    private final MfJournalCacheService journalCache;
    private final MfAccountMasterRepository mfAccountMasterRepository;
    private final TAccountsPayableSummaryRepository summaryRepository;
    private final MPaymentSupplierService paymentSupplierService;

    public IntegrityReportResponse generate(Integer shopNo, LocalDate fromMonth, LocalDate toMonth, boolean refresh) {
        // --- 入力検証 ---
        if (shopNo == null || fromMonth == null || toMonth == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "shopNo / fromMonth / toMonth は必須です");
        }
        if (fromMonth.isAfter(toMonth)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fromMonth は toMonth 以前である必要があります");
        }
        if (fromMonth.getDayOfMonth() != 20 || toMonth.getDayOfMonth() != 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fromMonth / toMonth は 20 日締め日 (yyyy-MM-20) で指定してください");
        }
        long months = java.time.temporal.ChronoUnit.MONTHS.between(fromMonth, toMonth);
        if (months > MAX_PERIOD_MONTHS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "期間は最大 " + MAX_PERIOD_MONTHS + " ヶ月です");
        }

        // --- 自社 summary を期間で一括取得 ---
        List<TAccountsPayableSummary> selfRows = summaryRepository
                .findByShopNoAndTransactionMonthBetweenOrderBySupplierNoAscTransactionMonthAscTaxRateAsc(
                        shopNo, fromMonth, toMonth);

        // supplier 単位 + 月単位で index 化
        // key: supplierNo × month → List of summary rows
        Map<Integer, Map<LocalDate, List<TAccountsPayableSummary>>> selfIndex = new HashMap<>();
        Set<Integer> selfSupplierNos = new HashSet<>();
        for (TAccountsPayableSummary r : selfRows) {
            selfIndex
                    .computeIfAbsent(r.getSupplierNo(), k -> new TreeMap<>())
                    .computeIfAbsent(r.getTransactionMonth(), k -> new ArrayList<>())
                    .add(r);
            selfSupplierNos.add(r.getSupplierNo());
        }

        // --- MF /journals 取得 (キャッシュ経由, 差分 fetch) ---
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();
        MfJournalCacheService.CachedResult cached = journalCache.getOrFetch(
                shopNo, client, accessToken, fromMonth, toMonth, refresh);
        List<MfJournal> allJournals = cached.journals();
        Instant fetchedAt = cached.oldestFetchedAt();
        log.info("[integrity] shopNo={}, 期間 {}〜{}, journals {} 件, fetchedAt={}",
                shopNo, fromMonth, toMonth, allJournals.size(), fetchedAt);

        // --- mf_account_master 逆引き map 構築 (sub_account_name → supplier_code(search_key)) ---
        // 同じ sub_account_name が複数 supplier_code に対応する場合は set で保持 (表記揺れ検出用)
        List<MfAccountMaster> allMasters = mfAccountMasterRepository.findAll();
        Map<String, Set<String>> mfSubToCodes = new HashMap<>();
        Set<String> matchedSupplierCodes = new HashSet<>();
        for (MfAccountMaster r : allMasters) {
            if (!MF_ACCOUNT_PAYABLE.equals(r.getAccountName())) continue;
            if (!MF_ACCOUNT_PAYABLE.equals(r.getFinancialStatementItem())) continue;
            if (r.getSubAccountName() == null || r.getSubAccountName().isBlank()) continue;
            if (r.getSearchKey() == null || r.getSearchKey().isBlank()) continue;
            mfSubToCodes.computeIfAbsent(r.getSubAccountName(), k -> new HashSet<>())
                    .add(r.getSearchKey());
            matchedSupplierCodes.add(r.getSearchKey());
        }

        // --- MF journals を supplier × 月 の bucket に集約 ---
        // key: subAccountName × 20日締め月
        Map<String, Map<LocalDate, MonthBucket>> mfBySubAccount = new HashMap<>();
        for (MfJournal j : allJournals) {
            if (j.transactionDate() == null || j.branches() == null) continue;
            LocalDate monthKey = MfJournalFetcher.toClosingMonthDay20(j.transactionDate());
            if (monthKey.isBefore(fromMonth) || monthKey.isAfter(toMonth)) continue;

            for (MfJournal.MfBranch br : j.branches()) {
                BigDecimal credit = BigDecimal.ZERO;
                BigDecimal debit = BigDecimal.ZERO;
                String subName = null;
                if (br.creditor() != null
                        && MF_ACCOUNT_PAYABLE.equals(br.creditor().accountName())
                        && br.creditor().subAccountName() != null) {
                    credit = nz(br.creditor().value());
                    subName = br.creditor().subAccountName();
                }
                if (br.debitor() != null
                        && MF_ACCOUNT_PAYABLE.equals(br.debitor().accountName())
                        && br.debitor().subAccountName() != null) {
                    debit = nz(br.debitor().value());
                    if (subName == null) subName = br.debitor().subAccountName();
                }
                if (subName == null) continue;
                MonthBucket b = mfBySubAccount
                        .computeIfAbsent(subName, k -> new TreeMap<>())
                        .computeIfAbsent(monthKey, k -> new MonthBucket());
                b.credit = b.credit.add(credit);
                b.debit = b.debit.add(debit);
                b.branchCount++;
            }
        }

        // --- supplier 情報を一括取得 ---
        // self 側 supplier + MF 逆引きで supplier_code 判明した supplier を union
        Set<String> mfSupplierCodes = new HashSet<>();
        for (Map.Entry<String, Set<String>> e : mfSubToCodes.entrySet()) {
            if (mfBySubAccount.containsKey(e.getKey())) {
                mfSupplierCodes.addAll(e.getValue());
            }
        }
        Collection<MPaymentSupplier> allSuppliers = paymentSupplierService.findByShopNo(shopNo);
        Map<Integer, MPaymentSupplier> supplierByNo = new HashMap<>();
        Map<String, MPaymentSupplier> supplierByCode = new HashMap<>();
        for (MPaymentSupplier s : allSuppliers) {
            supplierByNo.put(s.getPaymentSupplierNo(), s);
            if (s.getPaymentSupplierCode() != null) {
                supplierByCode.put(s.getPaymentSupplierCode(), s);
            }
        }

        // --- 判定ループ ---
        List<MfOnlyEntry> mfOnly = new ArrayList<>();
        List<SelfOnlyEntry> selfOnly = new ArrayList<>();
        List<AmountMismatchEntry> amountMismatch = new ArrayList<>();
        List<UnmatchedSupplierEntry> unmatchedSuppliers = new ArrayList<>();

        // ---- 1) self 側 supplier × 月 を走査 ----
        Set<String> processedSubNames = new HashSet<>();
        for (Integer supplierNo : selfSupplierNos) {
            MPaymentSupplier sup = supplierByNo.get(supplierNo);
            if (sup == null) continue;
            String supplierCode = sup.getPaymentSupplierCode();
            String supplierName = sup.getPaymentSupplierName();

            // MF 側の sub_account_name を逆引き: supplier_code で mf_account_master にあるか
            Set<String> matchedSubNames = new HashSet<>();
            for (Map.Entry<String, Set<String>> e : mfSubToCodes.entrySet()) {
                if (e.getValue().contains(supplierCode)) {
                    matchedSubNames.add(e.getKey());
                }
            }

            boolean supplierMfUnmatched = matchedSubNames.isEmpty()
                    && !matchedSupplierCodes.contains(supplierCode);

            if (supplierMfUnmatched) {
                // この supplier は mf_account_master に買掛金 sub_account として未登録
                unmatchedSuppliers.add(UnmatchedSupplierEntry.builder()
                        .supplierNo(supplierNo)
                        .supplierCode(supplierCode)
                        .supplierName(supplierName)
                        .reason("mf_account_master に『買掛金』sub_account として未登録")
                        .build());
                // unmatched でも月ごとの突合は続行 (MF 側 0 として扱う)
            }

            // 月ごとに self_delta / mf_delta を計算して判定
            Map<LocalDate, List<TAccountsPayableSummary>> monthMap = selfIndex.get(supplierNo);
            if (monthMap == null) continue;
            for (Map.Entry<LocalDate, List<TAccountsPayableSummary>> me : monthMap.entrySet()) {
                LocalDate month = me.getKey();
                List<TAccountsPayableSummary> rows = me.getValue();
                // self_delta = Σ (effectiveChange - payment_settled)
                BigDecimal selfDelta = BigDecimal.ZERO;
                BigDecimal effectiveChange = BigDecimal.ZERO;
                BigDecimal changeTaxIncluded = BigDecimal.ZERO;
                BigDecimal paymentSettled = BigDecimal.ZERO;
                for (TAccountsPayableSummary row : rows) {
                    BigDecimal eff = PayableBalanceCalculator.effectiveChangeTaxIncluded(row);
                    selfDelta = selfDelta.add(eff)
                            .subtract(nz(row.getPaymentAmountSettledTaxIncluded()));
                    effectiveChange = effectiveChange.add(eff);
                    changeTaxIncluded = changeTaxIncluded.add(nz(row.getTaxIncludedAmountChange()));
                    paymentSettled = paymentSettled.add(nz(row.getPaymentAmountSettledTaxIncluded()));
                }

                // MF 側 bucket から月分を集計 (matchedSubNames すべての sum)
                BigDecimal mfCredit = BigDecimal.ZERO;
                BigDecimal mfDebit = BigDecimal.ZERO;
                int mfBranchCount = 0;
                for (String sn : matchedSubNames) {
                    Map<LocalDate, MonthBucket> mfMap = mfBySubAccount.get(sn);
                    if (mfMap == null) continue;
                    MonthBucket b = mfMap.get(month);
                    if (b == null) continue;
                    mfCredit = mfCredit.add(b.credit);
                    mfDebit = mfDebit.add(b.debit);
                    mfBranchCount += b.branchCount;
                    processedSubNames.add(sn + "|" + month);
                }
                BigDecimal mfDelta = mfCredit.subtract(mfDebit);
                boolean selfHasActivity = !rows.isEmpty()
                        && (effectiveChange.signum() != 0 || paymentSettled.signum() != 0);
                boolean mfHasActivity = mfBranchCount > 0 && (mfCredit.signum() != 0 || mfDebit.signum() != 0);

                // 判定 (§3.2)
                if (!selfHasActivity && !mfHasActivity) {
                    // NO_ACTIVITY (R1 反映): スキップ
                    continue;
                }
                if (selfHasActivity && !mfHasActivity) {
                    // MF_MISSING → SelfOnlyEntry
                    selfOnly.add(SelfOnlyEntry.builder()
                            .transactionMonth(month)
                            .supplierNo(supplierNo)
                            .supplierCode(supplierCode)
                            .supplierName(supplierName)
                            .selfDelta(selfDelta)
                            .changeTaxIncluded(changeTaxIncluded)
                            .paymentSettledTaxIncluded(paymentSettled)
                            .taxRateRowCount(rows.size())
                            .reason(supplierMfUnmatched
                                    ? "MF 未登録 supplier / MF CSV 出力漏れ"
                                    : "MF CSV 出力漏れ or 未反映")
                            .build());
                    continue;
                }
                if (!selfHasActivity && mfHasActivity) {
                    // SELF_MISSING → MfOnlyEntry (self 側に行あるが activity なし、稀ケース)
                    // matchedSubNames が空 (supplierMfUnmatched) のときは 2) MF 側ループに委ねる
                    if (matchedSubNames.isEmpty()) {
                        continue;
                    }
                    mfOnly.add(MfOnlyEntry.builder()
                            .transactionMonth(month)
                            .subAccountName(matchedSubNames.iterator().next())
                            .creditAmount(mfCredit)
                            .debitAmount(mfDebit)
                            .periodDelta(mfDelta)
                            .branchCount(mfBranchCount)
                            .guessedSupplierNo(supplierNo)
                            .guessedSupplierCode(supplierCode)
                            .reason("MF 側手入力 or 自社取込漏れ")
                            .build());
                    continue;
                }
                // 両方 activity あり: 金額比較
                BigDecimal diff = selfDelta.subtract(mfDelta);
                BigDecimal diffAbs = diff.abs();
                if (diffAbs.compareTo(MATCH_TOLERANCE) <= 0) {
                    continue; // MATCH (Entry 無し)
                }
                String severity = diffAbs.compareTo(MINOR_UPPER) <= 0 ? "MINOR" : "MAJOR";
                amountMismatch.add(AmountMismatchEntry.builder()
                        .transactionMonth(month)
                        .supplierNo(supplierNo)
                        .supplierCode(supplierCode)
                        .supplierName(supplierName)
                        .selfDelta(selfDelta)
                        .mfDelta(mfDelta)
                        .diff(diff)
                        .severity(severity)
                        .build());
            }
        }

        // ---- 2) MF 側 subAccount × 月 で self にないものを列挙 ----
        for (Map.Entry<String, Map<LocalDate, MonthBucket>> e : mfBySubAccount.entrySet()) {
            String subName = e.getKey();
            Set<String> codes = mfSubToCodes.getOrDefault(subName, Collections.emptySet());
            // この sub_account に対応する supplier を 1 件決める (複数なら最初)
            Integer guessedNo = null;
            String guessedCode = null;
            for (String code : codes) {
                MPaymentSupplier s = supplierByCode.get(code);
                if (s != null) {
                    guessedNo = s.getPaymentSupplierNo();
                    guessedCode = code;
                    break;
                }
            }
            for (Map.Entry<LocalDate, MonthBucket> me : e.getValue().entrySet()) {
                LocalDate month = me.getKey();
                // 既に self 側ループで処理されたなら skip
                if (processedSubNames.contains(subName + "|" + month)) continue;
                MonthBucket b = me.getValue();
                BigDecimal mfDelta = b.credit.subtract(b.debit);
                if (b.branchCount == 0 || (b.credit.signum() == 0 && b.debit.signum() == 0)) continue;
                mfOnly.add(MfOnlyEntry.builder()
                        .transactionMonth(month)
                        .subAccountName(subName)
                        .creditAmount(b.credit)
                        .debitAmount(b.debit)
                        .periodDelta(mfDelta)
                        .branchCount(b.branchCount)
                        .guessedSupplierNo(guessedNo)
                        .guessedSupplierCode(guessedCode)
                        .reason(guessedNo != null
                                ? "MF にあって自社に無い (自社取込漏れ疑い)"
                                : "MF 手入力または未登録 supplier")
                        .build());
            }
        }

        // ---- Summary 集計 ----
        BigDecimal totalMfOnly = mfOnly.stream()
                .map(MfOnlyEntry::getPeriodDelta)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalSelfOnly = selfOnly.stream()
                .map(SelfOnlyEntry::getSelfDelta)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMismatch = amountMismatch.stream()
                .map(AmountMismatchEntry::getDiff)
                .map(BigDecimal::abs)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Set<Integer> supplierUnion = new HashSet<>(selfSupplierNos);
        for (MfOnlyEntry en : mfOnly) {
            if (en.getGuessedSupplierNo() != null) supplierUnion.add(en.getGuessedSupplierNo());
        }

        Summary summary = Summary.builder()
                .mfOnlyCount(mfOnly.size())
                .selfOnlyCount(selfOnly.size())
                .amountMismatchCount(amountMismatch.size())
                .unmatchedSupplierCount(unmatchedSuppliers.size())
                .totalMfOnlyAmount(totalMfOnly)
                .totalSelfOnlyAmount(totalSelfOnly)
                .totalMismatchAmount(totalMismatch)
                .build();

        return IntegrityReportResponse.builder()
                .shopNo(shopNo)
                .fromMonth(fromMonth)
                .toMonth(toMonth)
                .fetchedAt(fetchedAt != null ? fetchedAt : Instant.now())
                .totalJournalCount(allJournals.size())
                .supplierCount(supplierUnion.size())
                .mfOnly(mfOnly)
                .selfOnly(selfOnly)
                .amountMismatch(amountMismatch)
                .unmatchedSuppliers(unmatchedSuppliers)
                .summary(summary)
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static class MonthBucket {
        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal debit = BigDecimal.ZERO;
        int branchCount = 0;
    }
}
