package jp.co.oda32.domain.model.finance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * SMILEの請求実績を登録したテーブルのEntityクラス
 *
 * @since 2024/10/29
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_invoice", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"partner_code", "closing_date", "shop_no"})
})
public class TInvoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "invoice_id")
    private Integer invoiceId;  // 請求ID

    @Column(name = "partner_code", nullable = false)
    private String partnerCode;  // 得意先コード

    @Column(name = "partner_name", nullable = false)
    private String partnerName;  // 顧客名

    @Column(name = "closing_date", nullable = false)
    private String closingDate;  // 締め日

    @Column(name = "previous_balance")
    private BigDecimal previousBalance;  // 前回請求残高

    @Column(name = "total_payment")
    private BigDecimal totalPayment;  // 入金合計

    @Column(name = "carry_over_balance")
    private BigDecimal carryOverBalance;  // 繰越残高

    @Column(name = "net_sales")
    private BigDecimal netSales;  // 純売上

    @Column(name = "tax_price")
    private BigDecimal taxPrice;  // 消費税額

    @Column(name = "net_sales_including_tax")
    private BigDecimal netSalesIncludingTax;  // 純売上額（税込）

    @Column(name = "current_billing_amount")
    private BigDecimal currentBillingAmount;  // 今回請求額

    @Column(name = "shop_no")
    private Integer shopNo;  // ショップ番号

    @Column(name = "payment_date")
    private LocalDate paymentDate;  // 入金日
}
