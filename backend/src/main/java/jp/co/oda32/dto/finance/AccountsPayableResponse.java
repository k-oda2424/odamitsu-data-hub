package jp.co.oda32.dto.finance;

import jp.co.oda32.domain.model.finance.TAccountsPayableSummary;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class AccountsPayableResponse {
    private Integer shopNo;
    private Integer supplierNo;
    private LocalDate transactionMonth;
    private BigDecimal taxRate;
    private BigDecimal taxIncludedAmount;
    private BigDecimal taxExcludedAmount;
    private BigDecimal taxIncludedAmountChange;
    private BigDecimal taxExcludedAmountChange;
    private BigDecimal paymentDifference;

    public static AccountsPayableResponse from(TAccountsPayableSummary ap) {
        return AccountsPayableResponse.builder()
                .shopNo(ap.getShopNo())
                .supplierNo(ap.getSupplierNo())
                .transactionMonth(ap.getTransactionMonth())
                .taxRate(ap.getTaxRate())
                .taxIncludedAmount(ap.getTaxIncludedAmount())
                .taxExcludedAmount(ap.getTaxExcludedAmount())
                .taxIncludedAmountChange(ap.getTaxIncludedAmountChange())
                .taxExcludedAmountChange(ap.getTaxExcludedAmountChange())
                .paymentDifference(ap.getPaymentDifference())
                .build();
    }
}
