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
    /**
     * 締日。フォーマットは <code>YYYY/MM/末</code> または <code>YYYY/MM/DD</code> の文字列。
     * (DB CHECK 制約: V031 {@code chk_t_invoice_closing_date_format})
     *
     * <p>SF-24 / DD-13: 中期では {@code LocalDate + is_month_end:boolean} に分割する予定 (DEF-05)。
     * 当面は文字列のまま返却する。
     */
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
