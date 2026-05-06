package jp.co.oda32.domain.service.finance;

import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.finance.TAccountsPayableSummaryRepository;
import jp.co.oda32.domain.service.master.MPaymentSupplierService;
import jp.co.oda32.dto.finance.AccountsPayableLedgerResponse;
import jp.co.oda32.dto.finance.AccountsPayableLedgerResponse.Anomaly;
import jp.co.oda32.dto.finance.AccountsPayableLedgerResponse.LedgerRow;
import jp.co.oda32.dto.finance.AccountsPayableLedgerResponse.LedgerSummary;
import jp.co.oda32.dto.finance.AccountsPayableLedgerResponse.SupplierInfo;
import jp.co.oda32.dto.finance.AccountsPayableLedgerResponse.TaxRateInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 買掛帳 API (`/accounts-payable/ledger`) の backend ロジック。
 * <p>
 * 1 仕入先について月次推移を返却。税率別複数行を supplier 単位で集約し、
 * anomaly (UNVERIFIED, VERIFY_DIFF, NEGATIVE_CLOSING, PAYMENT_OVER,
 * CONTINUITY_BREAK, MONTH_GAP) を検出する。
 * <p>
 * closing 算出は {@link PayableBalanceCalculator} (Phase B' 共通 util) に委譲。
 * verified_amount 集約は Phase B' の「全行同値なら代表値、不一致なら SUM」準拠。
 * <p>
 * 設計書: claudedocs/design-accounts-payable-ledger.md §5
 *
 * @since 2026-04-22
 */
@Service
@Log4j2
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountsPayableLedgerService {

    private static final int MAX_PERIOD_MONTHS = 24;
    /** CONTINUITY_BREAK の許容差 (丸めによる 1 円ズレを許容)。 */
    private static final BigDecimal CONTINUITY_TOLERANCE = BigDecimal.ONE;

    private final TAccountsPayableSummaryRepository summaryRepository;
    private final MPaymentSupplierService paymentSupplierService;

    public AccountsPayableLedgerResponse getLedger(
            Integer shopNo, Integer supplierNo,
            LocalDate fromMonth, LocalDate toMonth) {

        // --- 入力検証 ---
        if (shopNo == null || supplierNo == null || fromMonth == null || toMonth == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "shopNo / supplierNo / fromMonth / toMonth は必須です");
        }
        if (fromMonth.isAfter(toMonth)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fromMonth は toMonth 以前である必要があります");
        }
        // C1 対応: 20日締め運用の契約を explicit に (transaction_month は常に *-20)
        if (fromMonth.getDayOfMonth() != 20 || toMonth.getDayOfMonth() != 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "fromMonth / toMonth は 20 日締め日 (yyyy-MM-20) で指定してください");
        }
        long totalMonths = java.time.temporal.ChronoUnit.MONTHS.between(fromMonth, toMonth);
        if (totalMonths > MAX_PERIOD_MONTHS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "期間は最大 " + MAX_PERIOD_MONTHS + " ヶ月です");
        }

        // --- 仕入先存在確認 (自 shop 限定、R7 反映) ---
        MPaymentSupplier supplier = paymentSupplierService.getByPaymentSupplierNo(supplierNo);
        if (supplier == null || !shopNo.equals(supplier.getShopNo())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "仕入先が見つかりません: supplierNo=" + supplierNo);
        }

        // --- 期間内 summary 取得 ---
        List<TAccountsPayableSummary> allRows = summaryRepository
                .findByShopNoAndSupplierNoAndTransactionMonthBetweenOrderByTransactionMonthAscTaxRateAsc(
                        shopNo, supplierNo, fromMonth, toMonth);

        // --- 期間外直前月取得 (R6 反映: 起点月の continuity 判定用) ---
        LocalDate prevToFrom = fromMonth.minusMonths(1);
        List<TAccountsPayableSummary> prevMonthRows = summaryRepository
                .findByShopNoAndSupplierNoAndTransactionMonth(shopNo, supplierNo, prevToFrom);
        LedgerRow prevMonthAggregated = prevMonthRows.isEmpty() ? null
                : aggregateMonth(prevMonthRows, null);

        // --- 月別に集約 ---
        TreeMap<LocalDate, List<TAccountsPayableSummary>> byMonth = new TreeMap<>();
        for (TAccountsPayableSummary r : allRows) {
            byMonth.computeIfAbsent(r.getTransactionMonth(), k -> new ArrayList<>()).add(r);
        }

        List<LedgerRow> rows = new ArrayList<>();
        LedgerRow previousRow = prevMonthAggregated; // 起点月判定に使う
        for (Map.Entry<LocalDate, List<TAccountsPayableSummary>> entry : byMonth.entrySet()) {
            LedgerRow row = aggregateMonth(entry.getValue(), previousRow);
            rows.add(row);
            previousRow = row;
        }

        LedgerSummary summary = buildSummary(rows);
        return AccountsPayableLedgerResponse.builder()
                .supplier(SupplierInfo.builder()
                        .shopNo(shopNo)
                        .supplierNo(supplier.getPaymentSupplierNo())
                        .supplierCode(supplier.getPaymentSupplierCode())
                        .supplierName(supplier.getPaymentSupplierName())
                        .build())
                .fromMonth(fromMonth)
                .toMonth(toMonth)
                .rows(rows)
                .summary(summary)
                .build();
    }

    /**
     * 1 ヶ月分の税率別行を supplier 単位に集約して LedgerRow を生成。
     * previousMonth が指定された場合のみ continuity チェックを実施。
     */
    private LedgerRow aggregateMonth(List<TAccountsPayableSummary> group, LedgerRow previousMonth) {
        if (group.isEmpty()) {
            throw new IllegalStateException("aggregateMonth: empty group");
        }
        LocalDate month = group.get(0).getTransactionMonth();

        // opening / change / payment_settled は単純和
        // effectiveChange は Phase B' の effectiveChange 合算 (closing と整合、C2 反映)
        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal change = BigDecimal.ZERO;
        BigDecimal effectiveChange = BigDecimal.ZERO;
        BigDecimal paymentSettled = BigDecimal.ZERO;
        BigDecimal closing = BigDecimal.ZERO;
        BigDecimal autoAdjusted = BigDecimal.ZERO;
        boolean hasPaymentOnly = false;
        boolean hasVerifiedManually = false;
        List<TaxRateInfo> breakdown = new ArrayList<>();

        for (TAccountsPayableSummary r : group) {
            opening = opening.add(nz(r.getOpeningBalanceTaxIncluded()));
            change = change.add(nz(r.getTaxIncludedAmountChange()));
            effectiveChange = effectiveChange.add(PayableBalanceCalculator.effectiveChangeTaxIncluded(r));
            paymentSettled = paymentSettled.add(nz(r.getPaymentAmountSettledTaxIncluded()));
            autoAdjusted = autoAdjusted.add(nz(r.getAutoAdjustedAmount()));
            // closing は PayableBalanceCalculator (Phase B' 流用、R3 反映)
            closing = closing.add(PayableBalanceCalculator.closingTaxIncluded(r));
            if (Boolean.TRUE.equals(r.getIsPaymentOnly())) hasPaymentOnly = true;
            if (Boolean.TRUE.equals(r.getVerifiedManually())) hasVerifiedManually = true;
            breakdown.add(TaxRateInfo.builder()
                    .taxRate(r.getTaxRate())
                    .verifiedManually(r.getVerifiedManually())
                    .verificationResult(r.getVerificationResult())
                    .isPaymentOnly(r.getIsPaymentOnly())
                    .mfExportEnabled(r.getMfExportEnabled())
                    .mfTransferDate(r.getMfTransferDate())
                    .build());
        }

        // verified は「全行同値なら代表値、不一致なら SUM」(Phase B' 準拠)
        BigDecimal verified = aggregateVerified(group);

        List<Anomaly> anomalies = detectAnomalies(
                month, opening, change, verified, paymentSettled, closing,
                hasPaymentOnly, previousMonth);
        boolean continuityOk = anomalies.stream()
                .noneMatch(a -> "CONTINUITY_BREAK".equals(a.getCode())
                        || "MONTH_GAP".equals(a.getCode()));

        // 税率別複数行で autoAdjusted が重複計上される (applyVerification は全税率行に同額書き込み)。
        // 代表値として 1 行分に戻す: 行数で割る。単一税率なら変わらない。
        BigDecimal autoAdjustedAvg = breakdown.isEmpty()
                ? BigDecimal.ZERO
                : autoAdjusted.divide(BigDecimal.valueOf(breakdown.size()), 0, java.math.RoundingMode.DOWN);

        return LedgerRow.builder()
                .transactionMonth(month)
                .openingBalanceTaxIncluded(opening)
                .changeTaxIncluded(change)
                .effectiveChangeTaxIncluded(effectiveChange)
                .verifiedAmount(verified)
                .autoAdjustedAmount(autoAdjustedAvg)
                .paymentSettledTaxIncluded(paymentSettled)
                .closingBalanceTaxIncluded(closing)
                .taxRateCount(breakdown.size())
                .taxRateBreakdown(breakdown)
                .hasPaymentOnly(hasPaymentOnly)
                .hasVerifiedManually(hasVerifiedManually)
                .anomalies(anomalies)
                .continuityOk(continuityOk)
                .build();
    }

    /**
     * verified_amount 集約: 全行同値なら代表値、不一致なら SUM (Phase B' 準拠)。
     * null は 0 として扱う。
     */
    static BigDecimal aggregateVerified(List<TAccountsPayableSummary> group) {
        List<BigDecimal> perRow = new ArrayList<>(group.size());
        for (TAccountsPayableSummary r : group) {
            perRow.add(r.getVerifiedAmount() != null ? r.getVerifiedAmount() : BigDecimal.ZERO);
        }
        BigDecimal first = perRow.get(0);
        boolean allSame = perRow.stream().allMatch(v -> v.compareTo(first) == 0);
        return allSame ? first : perRow.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Anomaly> detectAnomalies(
            LocalDate month, BigDecimal opening, BigDecimal change,
            BigDecimal verified, BigDecimal paymentSettled, BigDecimal closing,
            boolean hasPaymentOnly, LedgerRow previousMonth) {
        List<Anomaly> out = new ArrayList<>();

        // UNVERIFIED: 仕入あり・検証なし
        if (change.signum() > 0 && verified.signum() == 0) {
            out.add(Anomaly.builder()
                    .code("UNVERIFIED")
                    .severity("WARN")
                    .message("当月仕入あり・検証金額なし (振込明細 Excel 未取込の疑い)")
                    .build());
        }

        // VERIFY_DIFF: |change - verified| > 100 かつ verified > 0
        if (verified.signum() != 0) {
            BigDecimal diff = change.subtract(verified).abs();
            if (diff.compareTo(FinanceConstants.MATCH_TOLERANCE) > 0) {
                out.add(Anomaly.builder()
                        .code("VERIFY_DIFF")
                        .severity("WARN")
                        .message("仕入と検証額に差: ¥" + diff + " (値引 or 取込差異)")
                        .build());
            }
        }

        // NEGATIVE_CLOSING / PAYMENT_OVER: closing < 0 (R10 反映: PAYMENT_ONLY は anomaly から外す)
        if (closing.signum() < 0) {
            if (hasPaymentOnly) {
                out.add(Anomaly.builder()
                        .code("PAYMENT_OVER")
                        .severity("INFO")
                        .message("支払超過 (前月までの残高を超える支払)")
                        .build());
            } else {
                out.add(Anomaly.builder()
                        .code("NEGATIVE_CLOSING")
                        .severity("INFO")
                        .message("累積残が負 (値引繰越)")
                        .build());
            }
        }

        // CONTINUITY_BREAK / MONTH_GAP (R5, R6 反映)
        if (previousMonth != null) {
            LocalDate expectedPrev = month.minusMonths(1);
            if (!expectedPrev.equals(previousMonth.getTransactionMonth())) {
                // 前月が連続でない = 月抜け
                out.add(Anomaly.builder()
                        .code("MONTH_GAP")
                        .severity("WARN")
                        .message("前月 " + expectedPrev + " のデータ無し (集計バッチ未実行疑い)")
                        .build());
            } else {
                BigDecimal diff = opening.subtract(previousMonth.getClosingBalanceTaxIncluded()).abs();
                if (diff.compareTo(CONTINUITY_TOLERANCE) > 0) {
                    out.add(Anomaly.builder()
                            .code("CONTINUITY_BREAK")
                            .severity("CRITICAL")
                            .message("前月末 ¥" + previousMonth.getClosingBalanceTaxIncluded()
                                    + " ≠ 当月 opening ¥" + opening + " (差 ¥" + diff + ")")
                            .build());
                }
            }
        }

        return out;
    }

    private LedgerSummary buildSummary(List<LedgerRow> rows) {
        BigDecimal totalChange = BigDecimal.ZERO;
        BigDecimal totalVerified = BigDecimal.ZERO;
        BigDecimal totalPayment = BigDecimal.ZERO;
        int unverifiedCount = 0;
        int continuityBreakCount = 0;
        int negativeClosingCount = 0;
        int paymentOnlyCount = 0;
        int monthGapCount = 0;

        for (LedgerRow r : rows) {
            totalChange = totalChange.add(nz(r.getChangeTaxIncluded()));
            totalVerified = totalVerified.add(nz(r.getVerifiedAmount()));
            totalPayment = totalPayment.add(nz(r.getPaymentSettledTaxIncluded()));
            for (Anomaly a : r.getAnomalies()) {
                switch (a.getCode()) {
                    case "UNVERIFIED" -> unverifiedCount++;
                    case "CONTINUITY_BREAK" -> continuityBreakCount++;
                    case "NEGATIVE_CLOSING", "PAYMENT_OVER" -> negativeClosingCount++;
                    case "MONTH_GAP" -> monthGapCount++;
                    default -> { /* skip */ }
                }
            }
            if (r.isHasPaymentOnly()) paymentOnlyCount++;
        }

        BigDecimal finalClosing = rows.isEmpty() ? BigDecimal.ZERO
                : rows.get(rows.size() - 1).getClosingBalanceTaxIncluded();

        return LedgerSummary.builder()
                .totalChangeTaxIncluded(totalChange)
                .totalVerified(totalVerified)
                .totalPaymentSettled(totalPayment)
                .finalClosing(finalClosing)
                .unverifiedMonthCount(unverifiedCount)
                .continuityBreakCount(continuityBreakCount)
                .negativeClosingMonthCount(negativeClosingCount)
                .paymentOnlyMonthCount(paymentOnlyCount)
                .monthGapCount(monthGapCount)
                .build();
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
