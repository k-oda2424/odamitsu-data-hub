package jp.co.oda32.batch.finance.model;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * SMILE支払情報との照合結果を保持するクラス
 */
@Getter
public class VerificationResult {
    private final String supplierCode; // 仕入先コード
    private final BigDecimal taxRate; // 税率（全税率の合計を比較する場合はnull）
    private final BigDecimal accountsPayableAmount; // 買掛金額（税込）
    private final BigDecimal smilePaymentAmount; // SMILE支払額（税込）
    private final BigDecimal difference; // 差額
    private final boolean adjustedToSmilePayment; // SMILE支払額に調整したかどうか

    public VerificationResult(
            String supplierCode,
            BigDecimal taxRate,
            BigDecimal accountsPayableAmount,
            BigDecimal smilePaymentAmount,
            BigDecimal difference) {
        this(supplierCode, taxRate, accountsPayableAmount, smilePaymentAmount, difference, false);
    }

    public VerificationResult(
            String supplierCode,
            BigDecimal taxRate,
            BigDecimal accountsPayableAmount,
            BigDecimal smilePaymentAmount,
            BigDecimal difference,
            boolean adjustedToSmilePayment) {
        this.supplierCode = supplierCode;
        this.taxRate = taxRate;
        this.accountsPayableAmount = accountsPayableAmount;
        this.smilePaymentAmount = smilePaymentAmount;
        this.difference = difference;
        this.adjustedToSmilePayment = adjustedToSmilePayment;
    }

    /**
     * 検証結果が一致しているかどうかを返します。
     * 差額がゼロの場合、または差額が5円未満でSMILE支払額に調整された場合は一致していると判断します。
     *
     * @return 一致している場合はtrue、不一致の場合はfalse
     */
    /**
     * 検証結果が一致しているかどうかを返します。
     * 差額がゼロの場合、または差額が5円未満でSMILE支払額に調整された場合は一致していると判断します。
     *
     * @return 一致している場合はtrue、不一致の場合はfalse
     */
    public boolean isMatched() {
        return (difference != null && difference.compareTo(BigDecimal.ZERO) == 0)
                || adjustedToSmilePayment
                || (difference != null && difference.abs().compareTo(jp.co.oda32.constant.FinanceConstants.PAYMENT_VERIFICATION_TOLERANCE) < 0);
    }

    /**
     * SMILE支払額に調整されたかどうかを返します。
     *
     * @return 調整された場合はtrue、そうでない場合はfalse
     */
    public boolean isAdjustedToSmilePayment() {
        return adjustedToSmilePayment;
    }

    /**
     * 差額を返します。
     *
     * @return 差額
     */
    public BigDecimal getDifference() {
        return difference;
    }
}
