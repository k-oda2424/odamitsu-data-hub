package jp.co.oda32.domain.model;

import jp.co.oda32.domain.model.embeddable.VSalesMonthlySummaryPK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;

/**
 * 適正在庫計算用Entity
 *
 * @author k_oda
 * @since 2019/05/22
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "v_sales_monthly_summary")
@IdClass(VSalesMonthlySummaryPK.class)
public class VSalesMonthlySummary {
    @Id
    @Column(name = "shop_no")
    protected Integer shopNo;
    @Id
    @Column(name = "month")
    protected String month;
    @Column(name = "sales_total")
    protected BigDecimal salesTotal;
}
