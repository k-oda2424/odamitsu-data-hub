package jp.co.oda32.domain.model.finance;

import jp.co.oda32.domain.model.embeddable.TAccountsReceivableSummaryPK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 20日締め売掛金テーブルのEntityクラス
 *
 * @author k_oda
 * @since 2024/08/31
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "t_accounts_receivable_summary")
@IdClass(TAccountsReceivableSummaryPK.class)
public class TAccountsReceivableSummary {
    @Id
    @Column(name = "shop_no")
    private Integer shopNo;
    @Id
    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "partner_code")
    private String partnerCode;
    @Id
    @Column(name = "transaction_month")
    private LocalDate transactionMonth;
    @Id
    @Column(name = "tax_rate")
    private BigDecimal taxRate;

    @Column(name = "tax_included_amount")
    private BigDecimal taxIncludedAmount;

    @Column(name = "tax_excluded_amount")
    private BigDecimal taxExcludedAmount;

    @Column(name = "tax_included_amount_change")
    private BigDecimal taxIncludedAmountChange;

    @Column(name = "tax_excluded_amount_change")
    private BigDecimal taxExcludedAmountChange;

    @Id
    @Column(name = "is_otake_garbage_bag")
    private boolean isOtakeGarbageBag;

    @Column(name = "cutoff_date")
    private Integer cutoffDate;

    @Column(name = "order_no")
    private Integer orderNo;  // 都度現金払い用の注文番号
}
