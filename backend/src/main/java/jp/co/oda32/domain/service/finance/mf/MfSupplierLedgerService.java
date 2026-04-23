package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.MfSupplierLedgerResponse;
import jp.co.oda32.dto.finance.MfSupplierLedgerResponse.MfLedgerRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * MF 仕入先別 ledger API `/accounts-payable/ledger/mf`。
 * MF /journals を期間で取得し、買掛金 account の sub_account_name が
 * 指定 supplier に一致する branch の credit/debit を月次で集計する。
 * <p>
 * 2026-04-22 refactor (R3 反映): journals 取得 logic は {@link MfJournalFetcher} に委譲。
 * sub_account_name 解決は {@code mf_account_master.search_key == supplier_code} 経由。
 * <p>
 * 設計書: claudedocs/design-accounts-payable-ledger.md §4.2 / §5.2,
 *         claudedocs/design-integrity-report.md §5.2 (R3)
 *
 * @since 2026-04-22
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MfSupplierLedgerService {

    private static final int MAX_PERIOD_MONTHS = 24;
    private static final String MF_ACCOUNT_PAYABLE = "買掛金";
    /** MF 会計期首 (累積計算の起点)。軸 D の SupplierBalancesService と揃える。 */
    private static final LocalDate MF_PERIOD_START = LocalDate.of(2025, 5, 20);

    private final MfOauthService mfOauthService;
    private final MfJournalCacheService journalCache;
    private final MPaymentSupplierService paymentSupplierService;
    private final MfAccountMasterRepository mfAccountMasterRepository;

    public MfSupplierLedgerResponse getSupplierLedger(
            Integer shopNo, Integer supplierNo,
            LocalDate fromMonth, LocalDate toMonth, boolean refresh) {

        if (shopNo == null || supplierNo == null || fromMonth == null || toMonth == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "shopNo / supplierNo / fromMonth / toMonth は必須です");
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

        MPaymentSupplier supplier = paymentSupplierService.getByPaymentSupplierNo(supplierNo);
        if (supplier == null || !shopNo.equals(supplier.getShopNo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "仕入先が見つかりません: supplierNo=" + supplierNo);
        }

        // --- sub_account_name 候補解決 (mf_account_master.search_key == supplier_code) ---
        ResolvedSubAccount resolved = resolveSubAccountNames(
                supplier.getPaymentSupplierCode(), supplier.getPaymentSupplierName());

        // --- MF OAuth ---
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();

        // --- MF /journals 取得 (キャッシュ経由)。累積残の計算のため **期首 (MF_PERIOD_START) 〜 toMonth** で取得。
        //     fromMonth が期首より後でも期首からの累積が必要なので全期間を引き、UI では fromMonth〜toMonth のみ出力。
        MfJournalCacheService.CachedResult cached = journalCache.getOrFetch(
                shopNo, client, accessToken, MF_PERIOD_START, toMonth, refresh);
        List<MfJournal> allJournals = cached.journals();
        Instant fetchedAt = cached.oldestFetchedAt();
        log.info("[mf-ledger] supplierNo={}, 期首〜{} journals {} 件, sub候補 {}, fetchedAt={}",
                supplierNo, toMonth, allJournals.size(), resolved.matched, fetchedAt);

        // --- 月次 bucket (20日締め月基準、全期間) ---
        TreeMap<LocalDate, MonthBucket> buckets = new TreeMap<>();
        for (MfJournal j : allJournals) {
            LocalDate jDate = j.transactionDate();
            if (jDate == null || j.branches() == null) continue;
            LocalDate monthKey = MfJournalFetcher.toClosingMonthDay20(jDate);
            if (monthKey.isBefore(MF_PERIOD_START) || monthKey.isAfter(toMonth)) continue;

            for (MfJournal.MfBranch br : j.branches()) {
                BigDecimal credit = BigDecimal.ZERO;
                BigDecimal debit = BigDecimal.ZERO;
                var cr = br.creditor();
                var de = br.debitor();
                if (cr != null && MF_ACCOUNT_PAYABLE.equals(cr.accountName())
                        && cr.subAccountName() != null
                        && resolved.matched.contains(cr.subAccountName())) {
                    credit = nz(cr.value());
                }
                if (de != null && MF_ACCOUNT_PAYABLE.equals(de.accountName())
                        && de.subAccountName() != null
                        && resolved.matched.contains(de.subAccountName())) {
                    debit = nz(de.value());
                }
                if (credit.signum() != 0 || debit.signum() != 0) {
                    MonthBucket b = buckets.computeIfAbsent(monthKey, k -> new MonthBucket());
                    b.credit = b.credit.add(credit);
                    b.debit = b.debit.add(debit);
                }
            }
        }

        // --- 月次 row 生成 (fromMonth 〜 toMonth を 20日締め月単位で列挙)
        //     mfCumulativeBalance は期首から当月までの Σ(credit − debit)。 ---
        List<MfLedgerRow> rows = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        // 期首〜fromMonth 前月までの累積を先行計算
        LocalDate cur = MF_PERIOD_START;
        while (cur.isBefore(fromMonth)) {
            MonthBucket b = buckets.getOrDefault(cur, new MonthBucket());
            cumulative = cumulative.add(b.credit).subtract(b.debit);
            cur = YearMonth.from(cur).plusMonths(1).atDay(20);
        }
        // fromMonth 〜 toMonth の各月 row を生成
        LocalDate cursor = fromMonth;
        while (!cursor.isAfter(toMonth)) {
            MonthBucket b = buckets.getOrDefault(cursor, new MonthBucket());
            cumulative = cumulative.add(b.credit).subtract(b.debit);
            rows.add(MfLedgerRow.builder()
                    .transactionMonth(cursor)
                    .mfCreditInMonth(b.credit)
                    .mfDebitInMonth(b.debit)
                    .mfPeriodDelta(b.credit.subtract(b.debit))
                    .mfCumulativeBalance(cumulative)
                    .build());
            cursor = YearMonth.from(cursor).plusMonths(1).atDay(20);
        }

        return MfSupplierLedgerResponse.builder()
                .shopNo(shopNo)
                .supplierNo(supplierNo)
                .supplierName(supplier.getPaymentSupplierName())
                .fromMonth(fromMonth)
                .toMonth(toMonth)
                .matchedSubAccountNames(new ArrayList<>(resolved.matched))
                .unmatchedCandidates(resolved.unmatched)
                .rows(rows)
                .fetchedAt(fetchedAt != null ? fetchedAt : Instant.now())
                .totalJournalCount(allJournals.size())
                .mfStartDate(MF_PERIOD_START)
                .mfEndDate(toMonth)
                .build();
    }

    /**
     * sub_account_name 候補を解決。
     * 1) mf_account_master.search_key == supplier_code & account_name == "買掛金"
     * 2) 見つからない場合は supplier_name をフォールバックで試行 (exact match)
     * 3) どちらも候補無しなら unmatchedCandidates に supplier_name を含めて返す
     */
    ResolvedSubAccount resolveSubAccountNames(String supplierCode, String supplierName) {
        List<MfAccountMaster> list = mfAccountMasterRepository.findAll();
        Set<String> matched = new HashSet<>();
        for (MfAccountMaster r : list) {
            if (!MF_ACCOUNT_PAYABLE.equals(r.getAccountName())) continue;
            if (!MF_ACCOUNT_PAYABLE.equals(r.getFinancialStatementItem())) continue;
            if (r.getSearchKey() == null || r.getSearchKey().isBlank()) continue;
            if (r.getSubAccountName() == null || r.getSubAccountName().isBlank()) continue;
            if (r.getSearchKey().equals(supplierCode)) {
                matched.add(r.getSubAccountName());
            }
        }
        if (matched.isEmpty() && supplierName != null && !supplierName.isBlank()) {
            for (MfAccountMaster r : list) {
                if (!MF_ACCOUNT_PAYABLE.equals(r.getAccountName())) continue;
                if (supplierName.equals(r.getSubAccountName())) {
                    matched.add(r.getSubAccountName());
                }
            }
        }
        List<String> unmatched = matched.isEmpty() && supplierName != null
                ? List.of(supplierName)
                : Collections.emptyList();
        return new ResolvedSubAccount(matched, unmatched);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static class MonthBucket {
        BigDecimal credit = BigDecimal.ZERO;
        BigDecimal debit = BigDecimal.ZERO;
    }

    record ResolvedSubAccount(Set<String> matched, List<String> unmatched) {}
}
