package jp.co.oda32.domain.service.finance;

import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;

import java.math.BigDecimal;

/**
 * 買掛金 summary 行の closing / effectiveChange を算出する共通 util。
 * <p>
 * Phase B' (2026-04-22) で新設。以下 4 箇所で同じ計算式を使うため集約:
 * <ul>
 *   <li>{@code AccountsPayableResponse.from()} — DTO 層</li>
 *   <li>{@code AccountsPayableAggregationTasklet.buildPrevClosingMap} — 前月 closing 繰越</li>
 *   <li>{@code AccountsPayableBackfillTasklet.buildPrevClosingMap} — 過去データ再集計</li>
 *   <li>{@code MfBalanceReconcileService.closingOf} — 残高突合</li>
 * </ul>
 * <p>
 * closing 定義: {@code opening + effectiveChange - payment_settled} (T 勘定)
 * <p>
 * effectiveChange: 手動確定行 (verified_manually=true) なら verified_amount 優先、
 * それ以外は tax_included_amount_change。税抜側は tax_excluded_amount_change をそのまま使用
 * (手動確定時も税抜側は自動集計値のままで微小非対称あり、税込ベース突合で許容)。
 * <p>
 * 設計書: claudedocs/design-phase-b-prime-payment-settled.md §5
 *
 * @since 2026-04-22 (Phase B')
 */
public final class PayableBalanceCalculator {

    private PayableBalanceCalculator() {}

    /**
     * 税込の effectiveChange。手動確定行なら verifiedAmount 優先。
     */
    public static BigDecimal effectiveChangeTaxIncluded(TAccountsPayableSummary r) {
        boolean manual = Boolean.TRUE.equals(r.getVerifiedManually());
        return manual && r.getVerifiedAmount() != null
                ? r.getVerifiedAmount()
                : nz(r.getTaxIncludedAmountChange());
    }

    /**
     * 税込 closing = opening + effectiveChange - payment_settled。
     */
    public static BigDecimal closingTaxIncluded(TAccountsPayableSummary r) {
        return nz(r.getOpeningBalanceTaxIncluded())
                .add(effectiveChangeTaxIncluded(r))
                .subtract(nz(r.getPaymentAmountSettledTaxIncluded()));
    }

    /**
     * 税抜 closing = opening_excl + change_excl - payment_settled_excl。
     * 手動確定時の税抜側は tax_excluded_amount_change のままで税込側とは微小非対称あり。
     */
    public static BigDecimal closingTaxExcluded(TAccountsPayableSummary r) {
        return nz(r.getOpeningBalanceTaxExcluded())
                .add(nz(r.getTaxExcludedAmountChange()))
                .subtract(nz(r.getPaymentAmountSettledTaxExcluded()));
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
