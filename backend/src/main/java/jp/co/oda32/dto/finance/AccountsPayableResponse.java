package jp.co.oda32.dto.finance;

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

    public static AccountsPayableResponse from(TAccountsPayableSummary ap, MPaymentSupplier ps) {
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
                .verifiedManually(Boolean.TRUE.equals(ap.getVerifiedManually()))
                .verificationNote(ap.getVerificationNote())
                .build();
    }

    public static AccountsPayableResponse from(TAccountsPayableSummary ap) {
        return from(ap, null);
    }
}
