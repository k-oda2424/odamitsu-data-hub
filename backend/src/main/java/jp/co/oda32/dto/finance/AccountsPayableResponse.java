package jp.co.oda32.dto.finance;

import jp.co.oda32.constant.FinanceConstants;
import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class AccountsPayableResponse {
    private Integer shopNo;
    private Integer supplierNo;
    private String supplierCode;
    private String supplierName;
    private LocalDate transactionMonth;
    private BigDecimal taxRate;
    private BigDecimal taxIncludedAmount;
    private BigDecimal taxExcludedAmount;
    private BigDecimal taxIncludedAmountChange;
    private BigDecimal taxExcludedAmountChange;
    private BigDecimal paymentDifference;
    private Integer verificationResult;
    private Boolean mfExportEnabled;
    private Boolean verifiedManually;
    private String verificationNote;
    /** 検証時の請求額（振込明細 or 手入力）。 */
    private BigDecimal verifiedAmount;
    /**
     * 検証経路の区分（UIバッジ表示用の計算フィールド。DB には保存されていない）。
     * <ul>
     *   <li>{@code BULK}   — 振込明細Excel での一括検証。{@code verification_note} が
     *       {@link FinanceConstants#VERIFICATION_NOTE_BULK_PREFIX} で始まる行</li>
     *   <li>{@code MANUAL} — 買掛金一覧の詳細ダイアログで手入力検証された行</li>
     *   <li>{@code null}   — verifiedManually=false（SMILE自動検証など）</li>
     * </ul>
     */
    private String verificationSource;

    public static AccountsPayableResponse from(TAccountsPayableSummary ap, MPaymentSupplier ps) {
        boolean isManuallyVerified = Boolean.TRUE.equals(ap.getVerifiedManually());
        String source = null;
        if (isManuallyVerified) {
            String note = ap.getVerificationNote();
            source = (note != null && note.startsWith(FinanceConstants.VERIFICATION_NOTE_BULK_PREFIX))
                    ? "BULK" : "MANUAL";
        }
        return AccountsPayableResponse.builder()
                .shopNo(ap.getShopNo())
                .supplierNo(ap.getSupplierNo())
                .supplierCode(ps != null ? ps.getPaymentSupplierCode() : ap.getSupplierCode())
                .supplierName(ps != null ? ps.getPaymentSupplierName() : null)
                .transactionMonth(ap.getTransactionMonth())
                .taxRate(ap.getTaxRate())
                .taxIncludedAmount(ap.getTaxIncludedAmount())
                .taxExcludedAmount(ap.getTaxExcludedAmount())
                .taxIncludedAmountChange(ap.getTaxIncludedAmountChange())
                .taxExcludedAmountChange(ap.getTaxExcludedAmountChange())
                .paymentDifference(ap.getPaymentDifference())
                .verificationResult(ap.getVerificationResult())
                .mfExportEnabled(ap.getMfExportEnabled())
                .verifiedManually(isManuallyVerified)
                .verificationNote(ap.getVerificationNote())
                .verifiedAmount(ap.getVerifiedAmount())
                .verificationSource(source)
                .build();
    }

    public static AccountsPayableResponse from(TAccountsPayableSummary ap) {
        return from(ap, null);
    }
}
