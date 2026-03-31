package jp.co.oda32.dto.finance;

import jp.co.oda32.domain.model.finance.TInvoice;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class InvoiceResponse {
    private Integer invoiceId;
    private String partnerCode;
    private String partnerName;
    private String closingDate;
    private BigDecimal previousBalance;
    private BigDecimal totalPayment;
    private BigDecimal carryOverBalance;
    private BigDecimal netSales;
    private BigDecimal taxPrice;
    private BigDecimal netSalesIncludingTax;
    private BigDecimal currentBillingAmount;
    private Integer shopNo;
    private LocalDate paymentDate;

    public static InvoiceResponse from(TInvoice inv) {
        return InvoiceResponse.builder()
                .invoiceId(inv.getInvoiceId())
                .partnerCode(inv.getPartnerCode())
                .partnerName(inv.getPartnerName())
                .closingDate(inv.getClosingDate())
                .previousBalance(inv.getPreviousBalance())
                .totalPayment(inv.getTotalPayment())
                .carryOverBalance(inv.getCarryOverBalance())
                .netSales(inv.getNetSales())
                .taxPrice(inv.getTaxPrice())
                .netSalesIncludingTax(inv.getNetSalesIncludingTax())
                .currentBillingAmount(inv.getCurrentBillingAmount())
                .shopNo(inv.getShopNo())
                .paymentDate(inv.getPaymentDate())
                .build();
    }
}
