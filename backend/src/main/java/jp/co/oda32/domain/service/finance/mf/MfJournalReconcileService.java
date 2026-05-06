package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
import jp.co.oda32.domain.model.finance.MfAccountMaster;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.MfAccountMasterRepository;
import jp.co.oda32.domain.repository.finance.TPaymentMfAuxRowRepository;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import jp.co.oda32.domain.service.finance.TAccountsReceivableSummaryService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 取引月 × 仕訳種別（PURCHASE/SALES/PAYMENT）で自社 CSV 出力と MF 側の journal を突合する Service。
 *
 * <p>MF 側: {@code GET /api/v3/journals?start_date=:m&end_date=:m} で 1 日分の journal を取得し、
 * 各 branch を {@code (debitor.account_name, creditor.account_name)} で分類。
 *
 * <p>自社側:
 * <ul>
 *   <li>PURCHASE: {@code t_accounts_payable_summary} で mf_export_enabled=true かつ変更額 ≠0 の
 *       (shopNo, supplierNo, taxRate) 組合せが件数、税込金額合計が合計</li>
 *   <li>SALES: 同様に {@code t_accounts_receivable_summary}</li>
 *   <li>PAYMENT: 検証済み PAYABLE（supplier 単位集約） + {@code t_payment_mf_aux_row} の合計</li>
 * </ul>
 *
 * @since 2026/04/21
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class MfJournalReconcileService {

    private static final long AMOUNT_TOLERANCE = 0L; // 完全一致を基本（将来的に調整可）

    // 分類ルール（account_name で判定）
    private static final String PURCHASE_DEBIT = "仕入高";
    private static final String PURCHASE_CREDIT = "買掛金";
    private static final Set<String> SALES_DEBIT = Set.of("売掛金", "未収入金");
    // SALES の credit は MF で「売上高」親科目単一（sub_account で物販/クリーンラボ等に分岐）なのと、
    // ゴミ袋の場合「仮払金」を使う既存仕様がある。
    private static final Set<String> SALES_CREDIT = Set.of("売上高", "仮払金");
    // PAYMENT 貸方（一般的な預金/現金系）: 借方は {買掛金, 仕入高, 支払手数料} を許可
    private static final Set<String> PAYMENT_CREDIT_GENERAL = Set.of(
            "普通預金", "当座預金", "現金", "資金移動", "現金複合"
    );
    private static final Set<String> PAYMENT_DEBIT_GENERAL_ALLOWED = Set.of(
            "買掛金", "仕入高", "支払手数料"
    );
    // PAYMENT 貸方（仕入系の中間集約勘定）: 借方=買掛金 のみ許可。
    // 「マネーフォワード資金複合」は本システム関与外なので除外。
    // 旧「資金複合」は過去データ互換のため残す。
    private static final Set<String> PAYMENT_CREDIT_PAYABLE_ONLY = Set.of(
            "仕入資金複合", "資金複合"
    );

    private final MfOauthService mfOauthService;
    private final MfJournalFetcher mfJournalFetcher;
    private final TAccountsPayableSummaryService payableSummaryService;
    private final TAccountsReceivableSummaryService receivableSummaryService;
    private final TPaymentMfAuxRowRepository paymentAuxRowRepository;
    private final MPaymentSupplierService paymentSupplierService;
    private final MPartnerService partnerService;
    private final MfAccountMasterRepository mfAccountMasterRepository;

    public ReconcileReport reconcile(LocalDate transactionMonth) {
        // ---- MF 側集計 ----
        // SF-09 + MA-04: 1 日分のみ必要なので fiscal year fallback ありの広範 fetch ではなく
        // fetchJournalsSingleDay を利用 (start_date == end_date == transactionMonth)。
        // 後段の filter は念のための sanity check として残す。
        MMfOauthClient client = mfOauthService.findActiveClient()
                .orElseThrow(() -> new IllegalStateException("MF クライアント設定が未登録です"));
        String accessToken = mfOauthService.getValidAccessToken();

        List<MfJournal> mfJournals = mfJournalFetcher
                .fetchJournalsSingleDay(client, accessToken, transactionMonth)
                .items().stream()
                .filter(j -> j.transactionDate() != null && j.transactionDate().equals(transactionMonth))
                .toList();
        Bucket purchaseMf = new Bucket();
        Bucket salesMf = new Bucket();
        Bucket paymentMf = new Bucket();
        List<MfBranchSummary> purchaseMfItems = new ArrayList<>();
        List<MfBranchSummary> salesMfItems = new ArrayList<>();
        List<MfBranchSummary> paymentMfItems = new ArrayList<>();
        Map<String, UnclassifiedGroup> unclassifiedGroups = new LinkedHashMap<>();
        long unknownCount = 0;

        // 現金フィルタ用: 本システムで扱っている仕入先名（MF sub_account_name）の集合
        Set<String> knownSupplierNames = new HashSet<>(buildMfSubAccountMap("買掛金", "買掛金").values());

        for (MfJournal j : mfJournals) {
            if (j.branches() == null) continue;
            for (MfJournal.MfBranch b : j.branches()) {
                String debAcc = b.debitor() != null ? b.debitor().accountName() : null;
                String creAcc = b.creditor() != null ? b.creditor().accountName() : null;
                String debSub = b.debitor() != null ? b.debitor().subAccountName() : null;

                if (PURCHASE_DEBIT.equals(debAcc) && PURCHASE_CREDIT.equals(creAcc)) {
                    BigDecimal amt = b.creditor() != null ? b.creditor().value() : null;
                    addAmount(purchaseMf, amt);
                    purchaseMf.count++;
                    purchaseMfItems.add(MfBranchSummary.of(j, b, amt));
                } else if (debAcc != null && SALES_DEBIT.contains(debAcc)
                        && creAcc != null && SALES_CREDIT.contains(creAcc)) {
                    BigDecimal amt = b.debitor() != null ? b.debitor().value() : null;
                    addAmount(salesMf, amt);
                    salesMf.count++;
                    salesMfItems.add(MfBranchSummary.of(j, b, amt));
                } else if (isPayment(debAcc, creAcc, debSub, knownSupplierNames)) {
                    BigDecimal amt = b.creditor() != null ? b.creditor().value() : null;
                    addAmount(paymentMf, amt);
                    paymentMf.count++;
                    paymentMfItems.add(MfBranchSummary.of(j, b, amt));
                } else {
                    unknownCount++;
                    String key = (debAcc == null ? "(null)" : debAcc) + "||" + (creAcc == null ? "(null)" : creAcc);
                    BigDecimal amt = b.debitor() != null ? b.debitor().value() : null;
                    if (amt == null && b.creditor() != null) amt = b.creditor().value();
                    UnclassifiedGroup g = unclassifiedGroups.computeIfAbsent(key,
                            k -> new UnclassifiedGroup(debAcc, creAcc));
                    g.count++;
                    if (amt != null) g.totalAmount = g.totalAmount.add(amt);
                }
            }
        }

        // ---- 自社側集計 ----
        PayableAgg payable = aggregatePayable(transactionMonth);
        ReceivableAgg receivable = aggregateReceivable(transactionMonth);
        PaymentAgg payment = aggregatePayment(transactionMonth);

        // ---- マッチング（仕入先名 / 得意先名ベース） ----
        Matched purchaseM = match("PURCHASE", payable.items, purchaseMfItems);
        Matched salesM = match("SALES", receivable.items, salesMfItems);
        Matched paymentM = match("PAYMENT", payment.items, paymentMfItems);

        List<Row> rows = List.of(
                Row.of("PURCHASE", "仕入仕訳", payable.bucket, purchaseMf, purchaseM.local, purchaseM.mf),
                Row.of("SALES", "売上仕訳", receivable.bucket, salesMf, salesM.local, salesM.mf),
                Row.of("PAYMENT", "買掛支払", payment.bucket, paymentMf, paymentM.local, paymentM.mf)
        );

        List<UnclassifiedBreakdown> unclassified = unclassifiedGroups.values().stream()
                .sorted(Comparator.comparingInt((UnclassifiedGroup g) -> g.count).reversed())
                .map(g -> new UnclassifiedBreakdown(g.debitAccount, g.creditAccount, g.count, g.totalAmount))
                .toList();

        log.info("MF 仕訳突合: transactionMonth={}, mfTotal={}件, unknownBranches={}",
                transactionMonth, mfJournals.size(), unknownCount);
        return new ReconcileReport(transactionMonth, Instant.now(), rows, unknownCount, unclassified);
    }

    private static void addAmount(Bucket b, BigDecimal value) {
        if (value != null) b.amount = b.amount.add(value);
    }

    /**
     * 買掛支払分類ルール。
     * <ol>
     *   <li>貸方が預金/現金系: 借方が {買掛金, 仕入高, 支払手数料} のいずれか。
     *       かつ貸方が「現金」の場合は、借方補助科目が本システムの仕入先名と一致することが必須。</li>
     *   <li>貸方が仕入資金複合/資金複合: 借方=買掛金 のみ許可。
     *       マネーフォワード資金複合は本システム関与外で常に除外。</li>
     * </ol>
     */
    private static boolean isPayment(String debAcc, String creAcc, String debSub,
                                     Set<String> knownSupplierNames) {
        if (creAcc == null || debAcc == null) return false;
        if (PAYMENT_CREDIT_GENERAL.contains(creAcc)) {
            if (!PAYMENT_DEBIT_GENERAL_ALLOWED.contains(debAcc)) return false;
            if ("現金".equals(creAcc)) {
                return debSub != null && knownSupplierNames.contains(debSub);
            }
            return true;
        }
        if (PAYMENT_CREDIT_PAYABLE_ONLY.contains(creAcc)) {
            return "買掛金".equals(debAcc);
        }
        return false;
    }

    // ==== 自社側集計 ====

    private PayableAgg aggregatePayable(LocalDate transactionMonth) {
        List<TAccountsPayableSummary> summaries = payableSummaryService.findByTransactionMonth(transactionMonth);
        Bucket b = new Bucket();
        Set<String> uniqueKeys = new HashSet<>();
        List<LocalRowSummary> items = new ArrayList<>();
        // supplier 名の fallback 用
        Set<Integer> supplierNos = summaries.stream().map(TAccountsPayableSummary::getSupplierNo)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> supplierNameMap = paymentSupplierService.findAllByPaymentSupplierNos(supplierNos).stream()
                .collect(Collectors.toMap(MPaymentSupplier::getPaymentSupplierNo,
                        MPaymentSupplier::getPaymentSupplierName, (a, c) -> a));
        // MF sub_account_name 解決マップ: search_key (supplier_code) → sub_account_name
        Map<String, String> mfSubByCode = buildMfSubAccountMap("買掛金", "買掛金");
        for (TAccountsPayableSummary s : summaries) {
            // mf_export_enabled=true の CSV 出力分 + verified_manually=true の手動 MF 登録分を含める
            boolean include = Boolean.TRUE.equals(s.getMfExportEnabled())
                    || Boolean.TRUE.equals(s.getVerifiedManually());
            if (!include) continue;
            BigDecimal change = s.getTaxIncludedAmountChange();
            if (change == null || change.signum() == 0) continue;
            String key = s.getShopNo() + "|" + s.getSupplierNo() + "|" + s.getTaxRate();
            if (uniqueKeys.add(key)) b.count++;
            b.amount = b.amount.add(change);
            // MF での呼び名を優先: mf_account_master.sub_account_name (search_key=supplier_code)
            String mfName = s.getSupplierCode() != null ? mfSubByCode.get(s.getSupplierCode()) : null;
            String displayName = mfName != null ? mfName : supplierNameMap.get(s.getSupplierNo());
            items.add(new LocalRowSummary(
                    "PURCHASE",
                    s.getShopNo(),
                    s.getSupplierNo(),
                    s.getSupplierCode(),
                    displayName,
                    s.getTaxRate(),
                    change,
                    null,
                    false));
        }
        items.sort(Comparator.comparing(LocalRowSummary::amount, Comparator.reverseOrder()));
        return new PayableAgg(b, items);
    }

    /**
     * mf_account_master から (account_name, financial_statement_item) に属する行の
     * search_key → sub_account_name マップを作る。
     * search_key 形式: 買掛金は supplier_code (例 "002200") / 売掛金は "shopNo_partnerCode" (例 "1_301491")。
     */
    private Map<String, String> buildMfSubAccountMap(String accountName, String financialStatementItem) {
        List<MfAccountMaster> list = mfAccountMasterRepository.findAll();
        Map<String, String> m = new HashMap<>();
        for (MfAccountMaster r : list) {
            if (!accountName.equals(r.getAccountName())) continue;
            if (!financialStatementItem.equals(r.getFinancialStatementItem())) continue;
            if (r.getSearchKey() == null || r.getSearchKey().isBlank()) continue;
            if (r.getSubAccountName() == null || r.getSubAccountName().isBlank()) continue;
            m.putIfAbsent(r.getSearchKey(), r.getSubAccountName());
        }
        return m;
    }

    private ReceivableAgg aggregateReceivable(LocalDate transactionMonth) {
        List<TAccountsReceivableSummary> summaries = receivableSummaryService.findByTransactionMonth(transactionMonth);
        Bucket b = new Bucket();
        Set<String> uniqueKeys = new HashSet<>();
        List<LocalRowSummary> items = new ArrayList<>();
        Set<Integer> partnerNos = summaries.stream().map(TAccountsReceivableSummary::getPartnerNo)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> partnerNameMap = new HashMap<>();
        for (Integer pn : partnerNos) {
            MPartner p = partnerService.getByPartnerNo(pn);
            if (p != null && p.getPartnerName() != null) partnerNameMap.put(pn, p.getPartnerName());
        }
        // MF sub_account_name 解決マップ: 売掛金 の search_key = "shopNo_partnerCode"、未収入金 = ゴミ袋系
        Map<String, String> mfSubReceivable = buildMfSubAccountMap("売掛金", "売掛金");
        Map<String, String> mfSubGarbageBag = buildMfSubAccountMap("未収入金", "未収入金");
        for (TAccountsReceivableSummary s : summaries) {
            // mf_export_enabled=true の CSV 出力分 + verified_manually=true の手動 MF 登録分を含める
            boolean include = Boolean.TRUE.equals(s.getMfExportEnabled())
                    || Boolean.TRUE.equals(s.getVerifiedManually());
            if (!include) continue;
            BigDecimal change = s.getTaxIncludedAmountChange();
            if (change == null || change.signum() == 0) continue;
            String key = s.getShopNo() + "|" + s.getPartnerNo() + "|" + s.getTaxRate() + "|" + s.isOtakeGarbageBag();
            if (uniqueKeys.add(key)) b.count++;
            b.amount = b.amount.add(change);
            // 売掛系は "shopNo_partnerCode" search_key 形式 (SalesJournalCsvService 参照)
            String skey = s.getShopNo() + "_" + (s.getPartnerCode() != null ? s.getPartnerCode() : "");
            String mfName = s.isOtakeGarbageBag()
                    ? mfSubGarbageBag.get("g_" + skey)  // ゴミ袋マーカー（SalesJournalCsvService が "g_" prefix を付ける）
                    : mfSubReceivable.get(skey);
            String displayName = mfName != null ? mfName : partnerNameMap.get(s.getPartnerNo());
            items.add(new LocalRowSummary(
                    "SALES",
                    s.getShopNo(),
                    s.getPartnerNo(),
                    s.getPartnerCode(),
                    displayName,
                    s.getTaxRate(),
                    change,
                    s.isOtakeGarbageBag() ? "ゴミ袋" : null,
                    false));
        }
        items.sort(Comparator.comparing(LocalRowSummary::amount, Comparator.reverseOrder()));
        return new ReceivableAgg(b, items);
    }

    private PaymentAgg aggregatePayment(LocalDate transactionMonth) {
        Bucket b = new Bucket();
        List<LocalRowSummary> items = new ArrayList<>();

        // PAYABLE: supplier_no 単位でグルーピング
        List<TAccountsPayableSummary> payables = payableSummaryService.findByTransactionMonth(transactionMonth);
        Set<Integer> supplierNos = payables.stream().map(TAccountsPayableSummary::getSupplierNo)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Integer, String> supplierNameMap = paymentSupplierService.findAllByPaymentSupplierNos(supplierNos).stream()
                .collect(Collectors.toMap(MPaymentSupplier::getPaymentSupplierNo,
                        MPaymentSupplier::getPaymentSupplierName, (a, c) -> a));
        Map<String, String> mfSubByCode = buildMfSubAccountMap("買掛金", "買掛金");
        Map<String, List<TAccountsPayableSummary>> bySupplier = payables.stream()
                .filter(s -> Boolean.TRUE.equals(s.getMfExportEnabled())
                        || Boolean.TRUE.equals(s.getVerifiedManually()))
                .filter(s -> Integer.valueOf(1).equals(s.getVerificationResult()))
                .filter(s -> s.getVerifiedAmount() != null && s.getVerifiedAmount().signum() != 0)
                .collect(Collectors.groupingBy(s -> s.getShopNo() + "|" + s.getSupplierNo()));
        for (Map.Entry<String, List<TAccountsPayableSummary>> e : bySupplier.entrySet()) {
            BigDecimal sum = BigDecimal.ZERO;
            for (TAccountsPayableSummary s : e.getValue()) {
                sum = sum.add(Objects.requireNonNullElse(s.getVerifiedAmount(), BigDecimal.ZERO));
            }
            b.count++;
            b.amount = b.amount.add(sum);
            TAccountsPayableSummary first = e.getValue().get(0);
            String mfName = first.getSupplierCode() != null ? mfSubByCode.get(first.getSupplierCode()) : null;
            String displayName = mfName != null ? mfName : supplierNameMap.get(first.getSupplierNo());
            items.add(new LocalRowSummary(
                    "PAYMENT_PAYABLE",
                    first.getShopNo(),
                    first.getSupplierNo(),
                    first.getSupplierCode(),
                    displayName,
                    null,
                    sum,
                    null,
                    false));
        }

        // AUX: EXPENSE は本システムで勘定科目を入力しないため、突合対象外（MF 側も除外済み）
        List<TPaymentMfAuxRow> auxRows = paymentAuxRowRepository
                .findByShopNoAndTransactionMonthOrderByTransferDateAscSequenceNoAsc(1, transactionMonth);
        for (TPaymentMfAuxRow r : auxRows) {
            if ("EXPENSE".equals(r.getRuleKind())) continue;
            b.count++;
            if (r.getAmount() != null) b.amount = b.amount.add(r.getAmount());
            items.add(new LocalRowSummary(
                    "PAYMENT_AUX_" + r.getRuleKind(),
                    r.getShopNo(),
                    null,
                    r.getPaymentSupplierCode(),
                    r.getSourceName(),
                    null,
                    Objects.requireNonNullElse(r.getAmount(), BigDecimal.ZERO),
                    r.getTransferDate() != null ? r.getTransferDate().toString() : null,
                    false));
        }
        items.sort(Comparator.comparing(LocalRowSummary::amount, Comparator.reverseOrder()));
        return new PaymentAgg(b, items);
    }

    // ==== record / 内部型 ====

    static class Bucket {
        int count = 0;
        BigDecimal amount = BigDecimal.ZERO;
    }

    private static class UnclassifiedGroup {
        final String debitAccount;
        final String creditAccount;
        int count = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;

        UnclassifiedGroup(String d, String c) { this.debitAccount = d; this.creditAccount = c; }
    }

    private record PayableAgg(Bucket bucket, List<LocalRowSummary> items) {}
    private record ReceivableAgg(Bucket bucket, List<LocalRowSummary> items) {}
    private record PaymentAgg(Bucket bucket, List<LocalRowSummary> items) {}

    private record Matched(List<LocalRowSummary> local, List<MfBranchSummary> mf) {}

    /**
     * 仕入先名/得意先名キーで local と MF を突合し、どちらか片側にしか無い行に unmatched=true を付ける。
     * 多対多の対応（同一名が複数税率で local 側に出る等）はキー単位で集合扱い: どちらかに存在すれば一致扱い。
     */
    private static Matched match(String kind, List<LocalRowSummary> localItems, List<MfBranchSummary> mfItems) {
        Set<String> localKeys = localItems.stream()
                .map(LocalRowSummary::matchKey)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        Set<String> mfKeys = mfItems.stream()
                .map(m -> m.matchKey(kind))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        List<LocalRowSummary> newLocal = localItems.stream()
                .map(l -> l.withUnmatched(!mfKeys.contains(l.matchKey())))
                .toList();
        List<MfBranchSummary> newMf = mfItems.stream()
                .map(m -> m.withUnmatched(!localKeys.contains(m.matchKey(kind))))
                .toList();
        return new Matched(newLocal, newMf);
    }

    public record ReconcileReport(
            LocalDate transactionMonth,
            Instant fetchedAt,
            List<Row> rows,
            long mfUnknownBranchCount,
            List<UnclassifiedBreakdown> unclassified
    ) {}

    public record UnclassifiedBreakdown(
            String debitAccount,
            String creditAccount,
            int count,
            BigDecimal totalAmount
    ) {}

    public record Row(
            String kind,
            String kindLabel,
            int localCount,
            BigDecimal localAmount,
            int mfCount,
            BigDecimal mfAmount,
            int countDiff,
            BigDecimal amountDiff,
            boolean matched,
            List<LocalRowSummary> localItems,
            List<MfBranchSummary> mfItems
    ) {
        static Row of(String kind, String label, Bucket local, Bucket mf,
                      List<LocalRowSummary> localItems, List<MfBranchSummary> mfItems) {
            int cd = mf.count - local.count;
            BigDecimal ad = mf.amount.subtract(local.amount);
            boolean matched = cd == 0 && ad.abs().longValue() <= AMOUNT_TOLERANCE;
            return new Row(kind, label, local.count, local.amount, mf.count, mf.amount, cd, ad, matched,
                    localItems, mfItems);
        }
    }

    /** 自社 DB 側の 1 行要約。画面で一覧表示用。 */
    public record LocalRowSummary(
            String source,
            Integer shopNo,
            Integer partyNo,
            String partyCode,
            String partyName,
            BigDecimal taxRate,
            BigDecimal amount,
            String note,
            boolean unmatched
    ) {
        public LocalRowSummary withUnmatched(boolean u) {
            return new LocalRowSummary(source, shopNo, partyNo, partyCode, partyName, taxRate, amount, note, u);
        }

        /** マッチング用のキー（仕入先/得意先名。名前が null なら partyCode）。 */
        public String matchKey() {
            if (partyName != null && !partyName.isBlank()) return normalize(partyName);
            if (partyCode != null && !partyCode.isBlank()) return normalize(partyCode);
            return "";
        }
    }

    /** MF branch の 1 件要約。画面で一覧表示用。 */
    public record MfBranchSummary(
            String journalId,
            Integer journalNumber,
            String transactionDate,
            String debitAccount,
            String debitSubAccount,
            String creditAccount,
            String creditSubAccount,
            String tradePartnerName,
            String taxName,
            BigDecimal amount,
            String enteredBy,
            boolean unmatched
    ) {
        static MfBranchSummary of(MfJournal j, MfJournal.MfBranch b, BigDecimal amount) {
            MfJournal.MfSide deb = b.debitor();
            MfJournal.MfSide cre = b.creditor();
            String partner = null;
            String debSub = deb != null ? deb.subAccountName() : null;
            String creSub = cre != null ? cre.subAccountName() : null;
            if (debSub != null && !debSub.isBlank()) partner = debSub;
            else if (creSub != null && !creSub.isBlank()) partner = creSub;
            String taxName = null;
            if (deb != null && deb.taxName() != null && !"対象外".equals(deb.taxName())) taxName = deb.taxName();
            else if (cre != null && cre.taxName() != null && !"対象外".equals(cre.taxName())) taxName = cre.taxName();
            return new MfBranchSummary(
                    j.id(),
                    j.number(),
                    j.transactionDate() != null ? j.transactionDate().toString() : null,
                    deb != null ? deb.accountName() : null,
                    debSub,
                    cre != null ? cre.accountName() : null,
                    creSub,
                    partner,
                    taxName,
                    amount,
                    j.journalType(),
                    false);
        }

        public MfBranchSummary withUnmatched(boolean u) {
            return new MfBranchSummary(journalId, journalNumber, transactionDate,
                    debitAccount, debitSubAccount, creditAccount, creditSubAccount,
                    tradePartnerName, taxName, amount, enteredBy, u);
        }

        /**
         * マッチング用のキー。種別ごとに該当する sub_account_name を採用。
         * PURCHASE: credit (買掛金) 側の sub_account = 仕入先名
         * SALES:    debit  (売掛金) 側の sub_account = 得意先名
         * PAYMENT:  debit  (買掛金) 側の sub_account = 仕入先名 / もしくは tradePartnerName
         */
        public String matchKey(String kind) {
            String v = switch (kind) {
                case "PURCHASE" -> creditSubAccount;
                case "SALES"    -> debitSubAccount;
                case "PAYMENT"  -> debitSubAccount != null && !debitSubAccount.isBlank() ? debitSubAccount : tradePartnerName;
                default -> tradePartnerName;
            };
            return v == null ? "" : normalize(v);
        }
    }

    /** 名称の表記ゆれを吸収（前後空白除去 + 全角カッコ等は無視しない単純正規化）。 */
    private static String normalize(String s) {
        return s == null ? "" : s.trim();
    }
}
