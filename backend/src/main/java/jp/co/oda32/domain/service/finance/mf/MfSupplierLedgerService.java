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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;

/**
 * MF 仕入先別 ledger API `/accounts-payable/ledger/mf`。
 * MF /journals を期間でページング全件取得し、買掛金 account の sub_account_name が
 * 指定 supplier に一致する branch の credit/debit を月次で集計する。
 * <p>
 * sub_account_name 解決は既存 {@link MfJournalReconcileService#buildMfSubAccountMap}
 * と同じく {@code mf_account_master.search_key == supplier_code} 経由。
 * <p>
 * 設計書: claudedocs/design-accounts-payable-ledger.md §4.2 / §5.2
 *
 * @since 2026-04-22
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MfSupplierLedgerService {

    private static final int MAX_PERIOD_MONTHS = 24;
    private static final int PER_PAGE = 1000;
    private static final int MAX_PAGES = 50;
    private static final String MF_ACCOUNT_PAYABLE = "買掛金";

    private final MfOauthService mfOauthService;
    private final MfApiClient mfApiClient;
    private final MPaymentSupplierService paymentSupplierService;
    private final MfAccountMasterRepository mfAccountMasterRepository;

    public MfSupplierLedgerResponse getSupplierLedger(
            Integer shopNo, Integer supplierNo,
            LocalDate fromMonth, LocalDate toMonth) {

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

        // 自社 fromMonth (20日締め月) の bucket には「前月 21日 〜 当月 20日」の仕入が含まれる。
        // MF 仕訳を bucket 化するには前月 21日 から取得が理想だが、MF fiscal year 境界で
        // "Given date is not matching any accounting periods" 400 が返るケースがある。
        // 複数候補を段階的に試して最初に成功した start_date を採用する:
        //   1) 前月 21日 (前 fiscal year 内であれば OK)
        //   2) fromMonth + 1 日 (fiscal year 2025 開始日と想定)
        //   3) fromMonth + 11 日 (中旬、fiscal year 中盤にはかかる想定)
        //   4) fromMonth の翌月 1 日
        //   5) fromMonth の翌月 21日 (最後の砦、当月 bucket は空になる)
        List<LocalDate> candidates = List.of(
                fromMonth.minusMonths(1).plusDays(1), // 1
                fromMonth.plusDays(1),                // 2
                fromMonth.plusDays(11),               // 3
                fromMonth.plusDays(1).withDayOfMonth(1).plusMonths(1), // 4
                fromMonth.plusMonths(1).plusDays(1)   // 5
        );
        List<MfJournal> allJournals = null;
        LocalDate actualStart = null;
        Exception lastError = null;
        for (LocalDate candidate : candidates) {
            if (candidate.isAfter(toMonth)) continue;
            try {
                allJournals = fetchAllJournals(client, accessToken, candidate, toMonth);
                actualStart = candidate;
                break;
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                if (e.getStatusCode().value() == 400
                        && e.getResponseBodyAsString() != null
                        && e.getResponseBodyAsString().contains("accounting periods")) {
                    log.info("[mf-ledger] MF accounting periods 範囲外: start_date={} → 次候補へ", candidate);
                    lastError = e;
                    continue;
                }
                throw e;
            }
        }
        if (allJournals == null) {
            throw new IllegalStateException(
                    "MF fiscal year 境界エラー: どの start_date 候補でも journals を取得できませんでした。"
                            + " 取引月の期間を見直すか、MF 事業年度設定を確認してください。"
                            + (lastError != null ? " 最終エラー: " + lastError.getMessage() : ""));
        }
        log.info("[mf-ledger] supplierNo={}, 期間 {}〜{}, 取得 journals {} 件, sub候補 {}",
                supplierNo, actualStart, toMonth, allJournals.size(), resolved.matched);

        // --- 月次 bucket (20日締め月基準) ---
        TreeMap<LocalDate, MonthBucket> buckets = new TreeMap<>();
        for (MfJournal j : allJournals) {
            LocalDate jDate = j.transactionDate();
            if (jDate == null || j.branches() == null) continue;
            LocalDate monthKey = toClosingMonthDay20(jDate);
            if (monthKey.isBefore(fromMonth) || monthKey.isAfter(toMonth)) continue;

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

        // --- 月次 row 生成 (fromMonth 〜 toMonth を 20日締め月単位で列挙) ---
        List<MfLedgerRow> rows = new ArrayList<>();
        LocalDate cursor = fromMonth;
        while (!cursor.isAfter(toMonth)) {
            MonthBucket b = buckets.getOrDefault(cursor, new MonthBucket());
            rows.add(MfLedgerRow.builder()
                    .transactionMonth(cursor)
                    .mfCreditInMonth(b.credit)
                    .mfDebitInMonth(b.debit)
                    .mfPeriodDelta(b.credit.subtract(b.debit))
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
                .fetchedAt(Instant.now())
                .totalJournalCount(allJournals.size())
                .build();
    }

    /**
     * MF /journals をページング全件取得。
     */
    private List<MfJournal> fetchAllJournals(MMfOauthClient client, String accessToken,
                                               LocalDate startDate, LocalDate endDate) {
        List<MfJournal> all = new ArrayList<>();
        String sd = startDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        String ed = endDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        int page = 1;
        while (true) {
            MfJournalsResponse res = mfApiClient.listJournals(client, accessToken, sd, ed, page, PER_PAGE);
            List<MfJournal> items = res.items();
            all.addAll(items);
            if (items.size() < PER_PAGE) break;
            page++;
            if (page > MAX_PAGES) {
                throw new IllegalStateException("MF journals ページング safeguard を超過しました (50 pages)");
            }
        }
        return all;
    }

    /**
     * transactionDate を 20日締め月 (LocalDate of 20日) に寄せる。
     * day <= 20 → 当月20日、day > 20 → 翌月20日。
     */
    static LocalDate toClosingMonthDay20(LocalDate date) {
        if (date.getDayOfMonth() <= 20) {
            return YearMonth.from(date).atDay(20);
        }
        return YearMonth.from(date).plusMonths(1).atDay(20);
    }

    /**
     * sub_account_name 候補を解決。
     * 1) mf_account_master.search_key == supplier_code & account_name/financial_statement_item == "買掛金" で検索
     * 2) 見つからない場合は supplier_name をフォールバックで試行 (exact match)
     * 3) どちらも候補無しの場合、unmatchedCandidates に supplier_name を含めて返す
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
        // フォールバック: supplier_name で exact match
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
