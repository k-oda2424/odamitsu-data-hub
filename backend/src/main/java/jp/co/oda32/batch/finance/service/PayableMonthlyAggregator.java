package jp.co.oda32.batch.finance.service;

import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.service.finance.PayableBalanceCalculator;
import jp.co.oda32.domain.service.finance.TAccountsPayableSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Phase B' 買掛金月次集計の共通ロジック。
 * <p>
 * {@link jp.co.oda32.batch.finance.AccountsPayableAggregationTasklet} と
 * {@link jp.co.oda32.batch.finance.AccountsPayableBackfillTasklet} 両方から呼ばれる。
 * 設計レビュー R7 (ロジック二重化)・R2 (PK 衝突) を解消するため集約。
 * <p>
 * 責務:
 * <ul>
 *   <li>前月 data 構築 (row 単位 closing + supplier 単位 agg)</li>
 *   <li>当月行への opening 繰越</li>
 *   <li>当月行への payment_settled 按分 (supplier 単位 → 税率別)</li>
 *   <li>payment-only 行の生成 (前月 paid>0 かつ 当月 supplier 仕入無し)</li>
 * </ul>
 * <p>
 * payment-only 行の属性 (R4 反映):
 * <ul>
 *   <li>verifiedAmount = null (次月 paid 計算に影響させない)</li>
 *   <li>verifiedManually = false</li>
 *   <li>verificationNote = "[payment-only] {period}"</li>
 *   <li>verificationResult = null</li>
 *   <li>isPaymentOnly = true</li>
 * </ul>
 *
 * @since 2026-04-22 (Phase B')
 */
@Service
@Log4j2
@RequiredArgsConstructor
public class PayableMonthlyAggregator {

    private final TAccountsPayableSummaryService summaryService;

    /** 前月 data 構築: 行単位 closing + supplier 単位集計。 */
    public PrevMonthData buildPrevMonthData(LocalDate periodEndDate) {
        LocalDate prevMonthEnd = periodEndDate.minusMonths(1);
        List<TAccountsPayableSummary> prev = summaryService.findByTransactionMonth(prevMonthEnd);

        Map<String, BigDecimal[]> closingPerRow = new HashMap<>();
        Map<String, List<TAccountsPayableSummary>> bySupplier = new HashMap<>();

        for (TAccountsPayableSummary p : prev) {
            BigDecimal closingIncl = PayableBalanceCalculator.closingTaxIncluded(p);
            BigDecimal closingExcl = PayableBalanceCalculator.closingTaxExcluded(p);
            closingPerRow.put(prevRowKey(p), new BigDecimal[]{closingIncl, closingExcl});
            bySupplier.computeIfAbsent(supplierKey(p), k -> new ArrayList<>()).add(p);
        }

        Map<String, SupplierAgg> supplierAgg = new HashMap<>();
        for (Map.Entry<String, List<TAccountsPayableSummary>> entry : bySupplier.entrySet()) {
            supplierAgg.put(entry.getKey(), computeSupplierAgg(entry.getValue()));
        }
        return new PrevMonthData(closingPerRow, supplierAgg);
    }

    /**
     * supplier 単位の paid / change / closing 集計。
     * paid: verified_amount が「全行同値なら代表値、不一致なら SUM」
     *       (既存 PaymentMfImportService.sumVerifiedAmountForGroup 準拠、ただし null は 0 扱い)
     * is_payment_only=true 行は自分が前月に支払計上済みなので paid 計算からスキップ (次月巻込防止)。
     */
    static SupplierAgg computeSupplierAgg(List<TAccountsPayableSummary> group) {
        // payment-only 行は paid 算出から除外 (R4 防御)
        List<TAccountsPayableSummary> paidRelevant = group.stream()
                .filter(r -> !Boolean.TRUE.equals(r.getIsPaymentOnly()))
                .toList();

        BigDecimal paidIncl;
        if (paidRelevant.isEmpty()) {
            paidIncl = BigDecimal.ZERO;
        } else {
            List<BigDecimal> perRowVerified = new ArrayList<>(paidRelevant.size());
            for (TAccountsPayableSummary r : paidRelevant) {
                perRowVerified.add(r.getVerifiedAmount() != null ? r.getVerifiedAmount() : BigDecimal.ZERO);
            }
            BigDecimal first = perRowVerified.get(0);
            boolean allSame = perRowVerified.stream().allMatch(v -> v.compareTo(first) == 0);
            paidIncl = allSame ? first
                    : perRowVerified.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal changeInclTotal = BigDecimal.ZERO;
        BigDecimal changeExclTotal = BigDecimal.ZERO;
        BigDecimal closingInclTotal = BigDecimal.ZERO;
        BigDecimal closingExclTotal = BigDecimal.ZERO;
        BigDecimal maxTaxRate = BigDecimal.ZERO;
        String supplierCode = null;
        Integer supplierNo = null;
        Integer shopNo = null;
        for (TAccountsPayableSummary r : group) {
            changeInclTotal = changeInclTotal.add(nz(r.getTaxIncludedAmountChange()));
            changeExclTotal = changeExclTotal.add(nz(r.getTaxExcludedAmountChange()));
            closingInclTotal = closingInclTotal.add(PayableBalanceCalculator.closingTaxIncluded(r));
            closingExclTotal = closingExclTotal.add(PayableBalanceCalculator.closingTaxExcluded(r));
            BigDecimal tr = r.getTaxRate() != null ? r.getTaxRate() : BigDecimal.ZERO;
            if (tr.compareTo(maxTaxRate) > 0) maxTaxRate = tr;
            if (supplierCode == null) supplierCode = r.getSupplierCode();
            if (supplierNo == null) supplierNo = r.getSupplierNo();
            if (shopNo == null) shopNo = r.getShopNo();
        }

        BigDecimal paidExcl;
        if (paidIncl.signum() == 0) {
            paidExcl = BigDecimal.ZERO;
        } else if (changeInclTotal.signum() != 0) {
            // 2 段階切り捨て (supplier 合計 → 行按分) は最終行で吸収
            paidExcl = paidIncl.multiply(changeExclTotal)
                    .divide(changeInclTotal, 0, RoundingMode.DOWN);
        } else {
            paidExcl = paidIncl;
        }

        return new SupplierAgg(shopNo, supplierNo, supplierCode, paidIncl, paidExcl,
                changeInclTotal, changeExclTotal, closingInclTotal, closingExclTotal, maxTaxRate);
    }

    /** 各行に前月 closing から opening を set。 */
    public void applyOpenings(List<TAccountsPayableSummary> rows, PrevMonthData prev) {
        int warnCount = 0;
        for (TAccountsPayableSummary row : rows) {
            BigDecimal[] closing = prev.closingPerRow().get(prevRowKey(row));
            if (closing != null) {
                row.setOpeningBalanceTaxIncluded(closing[0]);
                row.setOpeningBalanceTaxExcluded(closing[1]);
            } else {
                row.setOpeningBalanceTaxIncluded(BigDecimal.ZERO);
                row.setOpeningBalanceTaxExcluded(BigDecimal.ZERO);
                warnCount++;
            }
        }
        if (warnCount > 0) {
            log.info("[opening] 前月 closing なし (期首/新規 supplier): {} 行", warnCount);
        }
    }

    /**
     * supplier 内の当月 change 比で payment_settled を按分。
     * 決定論化のため group を tax_rate 昇順でソートして最終行 (最高税率) で端数吸収 (R6 対応)。
     * change 合計=0 の supplier は payment-only 行側で処理するため skip。
     */
    public void applyPaymentSettled(List<TAccountsPayableSummary> rows, PrevMonthData prev) {
        Map<String, List<TAccountsPayableSummary>> bySupplier = new HashMap<>();
        for (TAccountsPayableSummary r : rows) {
            bySupplier.computeIfAbsent(supplierKey(r), k -> new ArrayList<>()).add(r);
        }
        int skippedNoPaid = 0;
        int skippedZeroChange = 0;
        int applied = 0;
        for (Map.Entry<String, List<TAccountsPayableSummary>> entry : bySupplier.entrySet()) {
            SupplierAgg agg = prev.supplierAgg().get(entry.getKey());
            List<TAccountsPayableSummary> group = new ArrayList<>(entry.getValue());
            group.sort(Comparator.comparing(
                    r -> r.getTaxRate() != null ? r.getTaxRate() : BigDecimal.ZERO));

            // まず 0 クリア (再集計時の安全策)
            for (TAccountsPayableSummary r : group) {
                r.setPaymentAmountSettledTaxIncluded(BigDecimal.ZERO);
                r.setPaymentAmountSettledTaxExcluded(BigDecimal.ZERO);
            }
            if (agg == null || agg.paidIncl().signum() == 0) {
                if (agg != null) skippedNoPaid++;
                continue;
            }

            BigDecimal changeInclTotal = BigDecimal.ZERO;
            BigDecimal changeExclTotal = BigDecimal.ZERO;
            for (TAccountsPayableSummary r : group) {
                changeInclTotal = changeInclTotal.add(nz(r.getTaxIncludedAmountChange()));
                changeExclTotal = changeExclTotal.add(nz(r.getTaxExcludedAmountChange()));
            }
            if (changeInclTotal.signum() == 0) {
                skippedZeroChange++;
                continue; // payment-only 行側で処理
            }

            BigDecimal cumIncl = BigDecimal.ZERO;
            BigDecimal cumExcl = BigDecimal.ZERO;
            for (int i = 0; i < group.size(); i++) {
                TAccountsPayableSummary r = group.get(i);
                boolean isLast = (i == group.size() - 1);
                BigDecimal paidInclRow;
                BigDecimal paidExclRow;
                if (isLast) {
                    paidInclRow = agg.paidIncl().subtract(cumIncl);
                    paidExclRow = agg.paidExcl().subtract(cumExcl);
                } else {
                    BigDecimal changeIncl = nz(r.getTaxIncludedAmountChange());
                    BigDecimal changeExcl = nz(r.getTaxExcludedAmountChange());
                    paidInclRow = agg.paidIncl().multiply(changeIncl)
                            .divide(changeInclTotal, 0, RoundingMode.DOWN);
                    paidExclRow = changeExclTotal.signum() == 0 ? BigDecimal.ZERO
                            : agg.paidExcl().multiply(changeExcl)
                                    .divide(changeExclTotal, 0, RoundingMode.DOWN);
                    cumIncl = cumIncl.add(paidInclRow);
                    cumExcl = cumExcl.add(paidExclRow);
                }
                r.setPaymentAmountSettledTaxIncluded(paidInclRow);
                r.setPaymentAmountSettledTaxExcluded(paidExclRow);
            }
            applied++;
        }
        if (skippedNoPaid > 0 || skippedZeroChange > 0 || applied > 0) {
            log.info("[payment_settled] 適用 supplier={}, 支払なしで skip={}, change=0 supplier (payment-only 対象)={}",
                    applied, skippedNoPaid, skippedZeroChange);
        }
    }

    /**
     * 前月 paid>0 かつ 当月 supplier 仕入無し (OR change 合計=0) の supplier に payment-only 行を生成。
     * 既存 payment-only 行があれば上書き、無ければ新規作成。
     * 判定基準: activeSupplierKeys (当月行の supplier 集合) + supplierChangeIncl (supplier の当月 change 合計)。
     * 設計レビュー R2 修正: activeSupplierKeys のみでなく change 合計も確認。
     *
     * @param currRows  当月の全行 (toSave + preservedManual などマージ済み)
     * @param currMap   key=(shop, supplier, tx, tax_rate) の Map (upsert 先 lookup 用)
     */
    public List<TAccountsPayableSummary> generatePaymentOnlyRows(
            PrevMonthData prev,
            List<TAccountsPayableSummary> currRows,
            LocalDate periodEndDate,
            Map<String, TAccountsPayableSummary> currMap) {
        // 当月 supplier 単位の change 合計
        Map<String, BigDecimal> supplierChangeIncl = new HashMap<>();
        for (TAccountsPayableSummary r : currRows) {
            supplierChangeIncl.merge(supplierKey(r), nz(r.getTaxIncludedAmountChange()), BigDecimal::add);
        }

        List<TAccountsPayableSummary> out = new ArrayList<>();
        int paidButNoChange = 0;
        for (Map.Entry<String, SupplierAgg> e : prev.supplierAgg().entrySet()) {
            SupplierAgg agg = e.getValue();
            if (agg.paidIncl().signum() == 0) continue;
            BigDecimal changeTotal = supplierChangeIncl.getOrDefault(e.getKey(), BigDecimal.ZERO);
            if (changeTotal.signum() != 0) continue; // 通常按分で処理済

            String k = buildRowKey(agg.shopNo(), agg.supplierNo(), periodEndDate, agg.maxTaxRate());
            TAccountsPayableSummary row = currMap.get(k);
            if (row == null) {
                row = TAccountsPayableSummary.builder()
                        .shopNo(agg.shopNo())
                        .supplierNo(agg.supplierNo())
                        .supplierCode(agg.supplierCode())
                        .transactionMonth(periodEndDate)
                        .taxRate(agg.maxTaxRate())
                        .taxIncludedAmountChange(BigDecimal.ZERO)
                        .taxExcludedAmountChange(BigDecimal.ZERO)
                        .build();
            } else {
                row.setTaxIncludedAmountChange(BigDecimal.ZERO);
                row.setTaxExcludedAmountChange(BigDecimal.ZERO);
            }
            row.setOpeningBalanceTaxIncluded(agg.closingInclTotal());
            row.setOpeningBalanceTaxExcluded(agg.closingExclTotal());
            row.setPaymentAmountSettledTaxIncluded(agg.paidIncl());
            row.setPaymentAmountSettledTaxExcluded(agg.paidExcl());
            // payment-only マーカー群 (R4)
            row.setIsPaymentOnly(true);
            row.setVerifiedAmount(null);
            row.setVerifiedManually(false);
            row.setVerificationNote("[payment-only] " + periodEndDate);
            row.setVerificationResult(null);
            row.setPaymentDifference(null);
            if (row.getMfExportEnabled() == null) row.setMfExportEnabled(true);
            out.add(row);
            paidButNoChange++;
        }
        if (paidButNoChange > 0) {
            log.info("[payment-only] 生成: {} 件 (前月 paid>0 / 当月 change=0 supplier)", paidButNoChange);
        }
        return out;
    }

    // ================================================================
    // キー util
    // ================================================================

    public static String rowKey(TAccountsPayableSummary r) {
        return buildRowKey(r.getShopNo(), r.getSupplierNo(), r.getTransactionMonth(), r.getTaxRate());
    }

    public static String buildRowKey(Integer shopNo, Integer supplierNo, LocalDate tm, BigDecimal taxRate) {
        return shopNo + "|" + supplierNo + "|" + tm + "|" + (taxRate != null ? taxRate.toPlainString() : "null");
    }

    public static String supplierKey(TAccountsPayableSummary r) {
        return r.getShopNo() + "|" + r.getSupplierNo();
    }

    public static String prevRowKey(TAccountsPayableSummary r) {
        return r.getShopNo() + "|" + r.getSupplierNo() + "|"
                + (r.getTaxRate() != null ? r.getTaxRate().toPlainString() : "null");
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    // ================================================================
    // 公開 records
    // ================================================================

    public record PrevMonthData(
            Map<String, BigDecimal[]> closingPerRow,
            Map<String, SupplierAgg> supplierAgg
    ) {}

    public record SupplierAgg(
            Integer shopNo,
            Integer supplierNo,
            String supplierCode,
            BigDecimal paidIncl,
            BigDecimal paidExcl,
            BigDecimal changeInclTotal,
            BigDecimal changeExclTotal,
            BigDecimal closingInclTotal,
            BigDecimal closingExclTotal,
            BigDecimal maxTaxRate
    ) {}
}
