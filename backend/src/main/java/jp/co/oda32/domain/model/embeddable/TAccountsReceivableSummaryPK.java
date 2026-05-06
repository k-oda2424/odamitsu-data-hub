package jp.co.oda32.domain.model.embeddable;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 20日締め売掛金テーブルのPKカラム設定
 *
 * @author k_oda
 * @since 2024/08/31
 */
@Data
@Builder
@Embeddable
@AllArgsConstructor
@NoArgsConstructor
public class TAccountsReceivableSummaryPK implements Serializable {
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "transaction_month")
    private LocalDate transactionMonth;
    @Column(name = "tax_rate")
    private BigDecimal taxRate;
    @Column(name = "is_otake_garbage_bag")
    private boolean isOtakeGarbageBag;
}
