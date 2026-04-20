package jp.co.oda32.batch.finance.service;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.constant.TaxType;
import jp.co.oda32.domain.model.finance.TAccountsReceivableSummary;
import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.repository.finance.TAccountsReceivableSummaryRepository;
import jp.co.oda32.domain.service.finance.TInvoiceService;
import jp.co.oda32.domain.service.master.MPartnerService;
import jp.co.oda32.domain.service.order.TOrderDetailService;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 売掛金の締め日を <b>請求書 (t_invoice) の {@code closing_date}</b> に合わせて自動再集計する。
 *
 * <p>目的: マスタ {@code m_partner.cutoff_date} と実際の SMILE 請求書の締め日が食い違う
 * ケース（例: ENEOS は 20 日締めから月末締めに変更済だがマスタ未反映）に自動追従する。
 *
 * <p>挙動:
 * <ol>
 *   <li>対象期間の売掛金サマリを partner_code + shop_no でグルーピング</li>
 *   <li>各得意先の請求書 (closing_date が 末/20/15) を検索</li>
 *   <li>請求書の closing_date に対応する transaction_month と AR 行が一致していなければ再集計対象</li>
 *   <li>旧 AR 行を削除（{@code verified_manually=true} 行は保護）</li>
 *   <li>請求書の締め日に応じた期間で t_order_detail を再集計し、新 AR 行を INSERT</li>
 * </ol>
 *
 * <p>スコープ外（マスタと請求書の整合性を単純に判定できないため従来通り）:
 * <ul>
 *   <li>上様 partner_code = {@code "999999"} / partner_no = {@code -999999}</li>
 *   <li>イズミ四半期特殊 (partner_code {@code "000231"})</li>
 *   <li>クリーンラボ (partner_code {@code "301491"})</li>
 *   <li>都度現金払い (master cutoff_date = -1): 元から月次集約なので再集計不要</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountsReceivableCutoffReconciler {

    /** 大竹市のゴミ袋のgoods_code (tasklet と同一定義)。 */
    private static final Set<String> OTAKE_GARBAGE_BAG_GOODS_CODES = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList(
                    "00100001", "00100003", "00100005",
                    "00100007", "00100009", "00100011"
            ))
    );

    private static final String JOSAMA_PARTNER_CODE = "999999";
    private static final String QUARTERLY_BILLING_PARTNER_CODE = "000231";
    private static final String CLEAN_LAB_PARTNER_CODE = "301491";

    private static final Pattern CLOSING_DATE_PATTERN = Pattern.compile("^(\\d{4})/(\\d{2})/(末|\\d{2})$");

    private final TInvoiceService tInvoiceService;
    private final TOrderDetailService tOrderDetailService;
    private final MPartnerService mPartnerService;
    private final TAccountsReceivableSummaryRepository summaryRepository;

    /**
     * {@code summaries} をスキャンし、各得意先について対応する請求書の締め日を調べ、
     * AR の {@code transaction_month} と食い違っていれば当該得意先の AR 行を再集計する。
     *
     * @param summaries 現在の AR サマリ (検索結果)
     * @param fromDate  検索期間の開始日 (UI で指定された {@code fromDate})
     * @param toDate    検索期間の終了日
     * @return 実行結果サマリ
     */
    @Transactional
    public ReconcileResult reconcile(
            List<TAccountsReceivableSummary> summaries, LocalDate fromDate, LocalDate toDate) {

        // 全 MPartner を 1 回ロードしてインデックス構築
        PartnerIndex index = buildPartnerIndex();

        // (shopNo, partner_code) 単位で集約して走査
        Map<PartnerKey, List<TAccountsReceivableSummary>> byPartner = summaries.stream()
                .filter(s -> !isExcludedPartner(s.getPartnerCode()))
                .collect(Collectors.groupingBy(
                        s -> new PartnerKey(s.getShopNo(), s.getPartnerCode())));

        int reconciledPartners = 0;
        int deletedRows = 0;
        int insertedRows = 0;
        int skippedManualPartners = 0;
        List<String> reconciledPartnerCodes = new ArrayList<>();

        for (Map.Entry<PartnerKey, List<TAccountsReceivableSummary>> entry : byPartner.entrySet()) {
            PartnerKey pk = entry.getKey();
            List<TAccountsReceivableSummary> partnerSummaries = entry.getValue();

            // 対象月ごとに請求書を検索 (fromDate〜toDate にまたがる月を列挙)
            for (YearMonth ym : monthsInRange(fromDate, toDate)) {
                Optional<TInvoice> invoiceOpt = findInvoiceForPartner(pk.shopNo, pk.partnerCode, ym);
                if (invoiceOpt.isEmpty()) continue;

                ClosingDateInfo closing = parseClosingDate(invoiceOpt.get().getClosingDate()).orElse(null);
                if (closing == null) continue;

                // Izumi (000231) 四半期特殊: 前月が四半期月 (2/5/8/11) なら集計期間を
                // 「当月 1日〜15日」に短縮する（前月 16〜末日分は四半期締めで別計上済みのため）。
                boolean izumiShortPeriod = isIzumiQuarterlyShortPeriod(pk.partnerCode, ym);
                if (izumiShortPeriod) {
                    closing = new ClosingDateInfo(
                            closing.transactionMonth,
                            closing.cutoffCode,
                            ym.atDay(1),                // ← 前月16日 ではなく 当月1日
                            closing.periodEnd);
                }

                // この月に請求書がある → AR 側が一致する transaction_month を持っているか確認。
                // Izumi 四半期は transaction_month が同じでも期間が違う (金額が狂う) ため常に再集計する。
                LocalDate expectedTxMonth = closing.transactionMonth;
                if (!izumiShortPeriod) {
                    boolean alreadyMatched = partnerSummaries.stream()
                            .anyMatch(s -> expectedTxMonth.equals(s.getTransactionMonth()));
                    if (alreadyMatched) continue; // 既に正しい締め日で集計されている
                }

                // 旧 AR 行を検出。
                // - 非 Izumi: その月に属する全行 (transaction_month の YearMonth が一致)
                // - Izumi 四半期: 期待 transaction_month と完全一致する行のみ（他月と混ざる可能性は無いがコード上の意図明確化）
                List<TAccountsReceivableSummary> stale = izumiShortPeriod
                        ? partnerSummaries.stream()
                            .filter(s -> expectedTxMonth.equals(s.getTransactionMonth()))
                            .toList()
                        : partnerSummaries.stream()
                            .filter(s -> YearMonth.from(s.getTransactionMonth()).equals(ym))
                            .toList();

                // 手動確定が 1 件でもあれば、運用者の判断を尊重してスキップ
                boolean anyManual = stale.stream()
                        .anyMatch(s -> Boolean.TRUE.equals(s.getVerifiedManually()));
                if (anyManual) {
                    skippedManualPartners++;
                    log.warn("再集計をスキップ (手動確定済み行あり): shop={} partner_code={} month={}",
                            pk.shopNo, pk.partnerCode, ym);
                    continue;
                }

                // 再集計対象の partner_no を解決: billing partner 本人 + 同じ invoice_partner_code を持つ子得意先
                PartnerResolution resolution = resolveBillingPartner(index, pk.shopNo, pk.partnerCode);
                if (resolution == null) {
                    log.warn("billing partner が見つかりません: shop={} partner_code={}", pk.shopNo, pk.partnerCode);
                    continue;
                }

                // 旧 AR 削除
                if (!stale.isEmpty()) {
                    summaryRepository.deleteAllInBatch(stale);
                    deletedRows += stale.size();
                }

                // 新 AR 集計 (まだ保存しない。差分判定のため)
                List<TAccountsReceivableSummary> newRows = aggregateForPartner(
                        pk.shopNo,
                        resolution,
                        closing,
                        pk.partnerCode);

                // Izumi 四半期: 常に再集計を走らせるため、既存行と同じ金額なら
                // DELETE+INSERT せずスキップ（無駄な書き込み・reconcile カウント回避）。
                if (izumiShortPeriod && sameTotals(stale, newRows)) {
                    continue;
                }

                // 旧 AR 削除
                if (!stale.isEmpty()) {
                    summaryRepository.deleteAllInBatch(stale);
                    deletedRows += stale.size();
                }

                if (!newRows.isEmpty()) {
                    summaryRepository.saveAll(newRows);
                    insertedRows += newRows.size();
                }

                reconciledPartners++;
                String label = izumiShortPeriod ? "Izumi四半期(1日〜15日)" : closing.asLabel();
                reconciledPartnerCodes.add(pk.partnerCode + " (" + ym + ": " + label + ")");
                log.info("再集計: shop={} partner_code={} month={} 旧{}件→新{}件 (cutoff→{}{})",
                        pk.shopNo, pk.partnerCode, ym,
                        stale.size(), newRows.size(), closing.cutoffCode,
                        izumiShortPeriod ? " [Izumi四半期]" : "");
            }
        }

        return ReconcileResult.builder()
                .reconciledPartners(reconciledPartners)
                .deletedRows(deletedRows)
                .insertedRows(insertedRows)
                .skippedManualPartners(skippedManualPartners)
                .reconciledDetails(reconciledPartnerCodes)
                .build();
    }

    // ===========================================================
    // 請求書→締め日情報
    // ===========================================================

    /**
     * 指定 (shop, partner_code, 年月) の請求書を "YYYY/MM/末", "YYYY/MM/20", "YYYY/MM/15" の順で検索。
     */
    private Optional<TInvoice> findInvoiceForPartner(Integer shopNo, String partnerCode, YearMonth ym) {
        String yyyymm = String.format("%d/%02d", ym.getYear(), ym.getMonthValue());
        String[] candidates = {yyyymm + "/末", yyyymm + "/20", yyyymm + "/15"};
        for (String cd : candidates) {
            Optional<TInvoice> inv = tInvoiceService.findByShopNoAndPartnerCodeAndClosingDate(shopNo, partnerCode, cd);
            if (inv.isPresent()) return inv;
        }
        return Optional.empty();
    }

    /** "2026/03/末" → transactionMonth=2026-03-31, cutoffCode=30, 期間=(2026-03-01, 2026-03-31) */
    static Optional<ClosingDateInfo> parseClosingDate(String closingDate) {
        if (closingDate == null) return Optional.empty();
        Matcher m = CLOSING_DATE_PATTERN.matcher(closingDate);
        if (!m.matches()) return Optional.empty();
        int year = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        String dayPart = m.group(3);
        YearMonth ym = YearMonth.of(year, month);
        if ("末".equals(dayPart)) {
            LocalDate end = ym.atEndOfMonth();
            LocalDate start = ym.atDay(1);
            return Optional.of(new ClosingDateInfo(end, 30, start, end));
        }
        int day = Integer.parseInt(dayPart);
        LocalDate end = ym.atDay(day);
        // "前月(day+1)"〜"当月 day"
        LocalDate start = ym.minusMonths(1).atDay(day + 1);
        return Optional.of(new ClosingDateInfo(end, day, start, end));
    }

    // ===========================================================
    // Partner 解決
    // ===========================================================

    private PartnerIndex buildPartnerIndex() {
        List<MPartner> all = mPartnerService.findAll();
        Map<Integer, MPartner> byPartnerNo = new HashMap<>();
        // invoice_partner_code (effective) + shop → billing partner_no
        Map<String, Integer> billingPartnerNoByKey = new HashMap<>();
        // invoice_partner_code (effective) + shop → 配下 raw partner_no リスト
        Map<String, List<Integer>> rawPartnerNosByKey = new HashMap<>();
        for (MPartner p : all) {
            byPartnerNo.put(p.getPartnerNo(), p);
            String effective = (p.getInvoicePartnerCode() == null || p.getInvoicePartnerCode().isEmpty())
                    ? p.getPartnerCode() : p.getInvoicePartnerCode();
            String key = p.getShopNo() + "|" + effective;
            rawPartnerNosByKey.computeIfAbsent(key, k -> new ArrayList<>()).add(p.getPartnerNo());
            // billing partner: partner_code == effective である MPartner
            if (Objects.equals(p.getPartnerCode(), effective)) {
                billingPartnerNoByKey.putIfAbsent(key, p.getPartnerNo());
            }
        }
        return new PartnerIndex(byPartnerNo, billingPartnerNoByKey, rawPartnerNosByKey);
    }

    private PartnerResolution resolveBillingPartner(PartnerIndex index, Integer shopNo, String billingPartnerCode) {
        String key = shopNo + "|" + billingPartnerCode;
        Integer billingPartnerNo = index.billingPartnerNoByKey.get(key);
        List<Integer> rawPartnerNos = index.rawPartnerNosByKey.get(key);
        if (billingPartnerNo == null || rawPartnerNos == null || rawPartnerNos.isEmpty()) {
            return null;
        }
        return new PartnerResolution(billingPartnerNo, rawPartnerNos);
    }

    private static boolean isExcludedPartner(String partnerCode) {
        if (partnerCode == null) return true;
        if (partnerCode.length() >= 7) return true; // 上様系 (長コード)
        return JOSAMA_PARTNER_CODE.equals(partnerCode)
                || CLEAN_LAB_PARTNER_CODE.equals(partnerCode);
        // NOTE: Izumi (000231) はここで除外せず、reconcile() 内で四半期判定して特別扱い。
    }

    /**
     * Izumi (000231) の四半期特殊処理判定。
     * 四半期月 (2/5/8/11 月) の末日に締めが走り、その直後の 15日締め請求書は
     * <b>1日〜15日</b> だけをカバーする。通常の「前月16日〜当月15日」で集計すると
     * 前月 16〜末日分がダブルカウントになるため、期間を短縮する必要がある。
     *
     * @param targetMonth 対象年月（請求書の締め日から導いた transaction_month の年月）
     * @return {@code true} なら当月 1日 から集計すべき
     */
    private static boolean isIzumiQuarterlyShortPeriod(String partnerCode, YearMonth targetMonth) {
        if (!QUARTERLY_BILLING_PARTNER_CODE.equals(partnerCode)) return false;
        int prev = targetMonth.getMonthValue() - 1;
        if (prev == 0) prev = 12;
        return prev == 2 || prev == 5 || prev == 8 || prev == 11;
    }

    // ===========================================================
    // 再集計（単一 billing partner 向け簡易版）
    // ===========================================================

    /**
     * {@code billing partner} に紐づく raw partner_no の注文を期間内で集計して
     * {@link TAccountsReceivableSummary} を生成する。税率 × ゴミ袋フラグ で行を分ける。
     */
    private List<TAccountsReceivableSummary> aggregateForPartner(
            Integer shopNo,
            PartnerResolution resolution,
            ClosingDateInfo closing,
            String billingPartnerCode) {

        List<TOrderDetail> details = tOrderDetailService.findByPartnerNosAndDateRange(
                resolution.rawPartnerNos, closing.periodStart, closing.periodEnd, Flag.NO);
        if (details.isEmpty()) return List.of();

        Map<AggregationKey, List<TOrderDetail>> groups = details.stream()
                .filter(d -> shopNo.equals(d.getShopNo()))
                .collect(Collectors.groupingBy(d -> new AggregationKey(
                        d.getTaxRate() != null ? d.getTaxRate() : BigDecimal.ZERO,
                        OTAKE_GARBAGE_BAG_GOODS_CODES.contains(d.getGoodsCode()))));

        List<TAccountsReceivableSummary> result = new ArrayList<>();
        for (Map.Entry<AggregationKey, List<TOrderDetail>> e : groups.entrySet()) {
            AggregationKey k = e.getKey();
            List<TOrderDetail> group = e.getValue();

            BigDecimal totalExcludingTax = BigDecimal.ZERO;
            BigDecimal totalOriginal = BigDecimal.ZERO;
            boolean allTaxableInclude = true;

            for (TOrderDetail d : group) {
                totalExcludingTax = totalExcludingTax.add(calculateAmountExcludingTax(d));
                BigDecimal original = d.getTotalAmount();
                if (original != null) totalOriginal = totalOriginal.add(original);
                if (!TaxType.TAXABLE_INCLUDE.equals(TaxType.purse(d.getTaxType()))) {
                    allTaxableInclude = false;
                }
            }

            BigDecimal taxAmount = BigDecimal.ZERO;
            if (k.taxRate.compareTo(BigDecimal.ZERO) > 0) {
                taxAmount = totalExcludingTax
                        .multiply(k.taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN))
                        .setScale(0, RoundingMode.DOWN);
            }

            BigDecimal taxExcludedTrunc = totalExcludingTax.setScale(0, RoundingMode.DOWN);
            BigDecimal taxIncludedTrunc = allTaxableInclude
                    ? totalOriginal.setScale(0, RoundingMode.DOWN)
                    : taxExcludedTrunc.add(taxAmount.setScale(0, RoundingMode.DOWN));

            result.add(TAccountsReceivableSummary.builder()
                    .shopNo(shopNo)
                    .partnerNo(resolution.billingPartnerNo)
                    .partnerCode(billingPartnerCode)
                    .transactionMonth(closing.transactionMonth)
                    .taxRate(k.taxRate)
                    .isOtakeGarbageBag(k.isOtakeGarbageBag)
                    .taxIncludedAmountChange(taxIncludedTrunc)
                    .taxExcludedAmountChange(taxExcludedTrunc)
                    .cutoffDate(closing.cutoffCode)
                    .mfExportEnabled(false)
                    .verifiedManually(false)
                    .build());
        }
        return result;
    }

    /**
     * 旧 AR 群と新 AR 群が「税率×ゴミ袋フラグ単位」で同一の税込金額を持つかを判定。
     * Izumi 四半期のように「常に再集計する」パスで、差分がないのに DELETE+INSERT を走らせないようにする。
     */
    private static boolean sameTotals(List<TAccountsReceivableSummary> stale,
                                      List<TAccountsReceivableSummary> fresh) {
        Map<String, BigDecimal> staleMap = totalsByGroupKey(stale);
        Map<String, BigDecimal> freshMap = totalsByGroupKey(fresh);
        if (!staleMap.keySet().equals(freshMap.keySet())) return false;
        for (Map.Entry<String, BigDecimal> e : staleMap.entrySet()) {
            BigDecimal a = e.getValue();
            BigDecimal b = freshMap.get(e.getKey());
            if (a == null || b == null) return false;
            if (a.compareTo(b) != 0) return false;
        }
        return true;
    }

    private static Map<String, BigDecimal> totalsByGroupKey(List<TAccountsReceivableSummary> rows) {
        Map<String, BigDecimal> out = new HashMap<>();
        for (TAccountsReceivableSummary s : rows) {
            String key = (s.getTaxRate() != null ? s.getTaxRate().stripTrailingZeros().toPlainString() : "null")
                    + "|" + s.isOtakeGarbageBag();
            BigDecimal v = s.getTaxIncludedAmountChange() != null
                    ? s.getTaxIncludedAmountChange()
                    : BigDecimal.ZERO;
            out.merge(key, v, BigDecimal::add);
        }
        return out;
    }

    private BigDecimal calculateAmountExcludingTax(TOrderDetail detail) {
        BigDecimal totalAmount = detail.getTotalAmount();
        TaxType taxType = TaxType.purse(detail.getTaxType());
        BigDecimal taxRate = detail.getTaxRate();
        if (totalAmount == null) return BigDecimal.ZERO;
        if (taxRate == null) taxRate = BigDecimal.ZERO;
        switch (Objects.requireNonNull(taxType, "TaxType が決定できません: " + detail.getTaxType())) {
            case TAX_EXCLUDE:
            case TAX_FREE:
                return totalAmount;
            case TAXABLE_INCLUDE:
                if (taxRate.compareTo(BigDecimal.ZERO) == 0) return totalAmount;
                BigDecimal divider = BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN));
                return totalAmount.divide(divider, 10, RoundingMode.DOWN);
            default:
                log.error("未知の税区分: OrderDetail ID={}, TaxType={}", detail.getOrderDetailNo(), taxType);
                return totalAmount;
        }
    }

    private static List<YearMonth> monthsInRange(LocalDate from, LocalDate to) {
        List<YearMonth> out = new ArrayList<>();
        YearMonth cur = YearMonth.from(from);
        YearMonth end = YearMonth.from(to);
        while (!cur.isAfter(end)) {
            out.add(cur);
            cur = cur.plusMonths(1);
        }
        return out;
    }

    // ===========================================================
    // Data classes
    // ===========================================================

    @Data
    @Builder
    public static class ReconcileResult {
        private int reconciledPartners;
        private int deletedRows;
        private int insertedRows;
        private int skippedManualPartners;
        private List<String> reconciledDetails;
    }

    @Value
    static class ClosingDateInfo {
        LocalDate transactionMonth; // AR に入れる transaction_month
        int cutoffCode;             // 15, 20, 30 (月末)
        LocalDate periodStart;      // 集計開始
        LocalDate periodEnd;        // 集計終了 (= transactionMonth)

        String asLabel() {
            return cutoffCode == 30 ? "月末" : cutoffCode + "日";
        }
    }

    @Value
    static class PartnerKey {
        Integer shopNo;
        String partnerCode;
    }

    @Value
    static class AggregationKey {
        BigDecimal taxRate;
        boolean isOtakeGarbageBag;
    }

    @Value
    private static class PartnerIndex {
        Map<Integer, MPartner> byPartnerNo;
        Map<String, Integer> billingPartnerNoByKey;      // (shop|invoice_partner_code) → billing partner_no
        Map<String, List<Integer>> rawPartnerNosByKey;   // (shop|invoice_partner_code) → 配下 partner_no
    }

    @Value
    private static class PartnerResolution {
        Integer billingPartnerNo;
        List<Integer> rawPartnerNos;
    }
}
