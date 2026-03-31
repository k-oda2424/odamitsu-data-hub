package jp.co.oda32.domain.model.estimate;

import jp.co.oda32.domain.model.embeddable.VEstimateGoodsPK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 通常価格見積商品情報収集Entity
 *
 * @author k_oda
 * @since 2022/10/28
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@IdClass(VEstimateGoodsPK.class)
@Table(name = "v_estimate_goods")
public class VEstimateGoods implements IVEstimateGoods {
    @Id
    @Column(name = "shop_no")
    protected Integer shopNo;
    @Id
    @Column(name = "goods_no")
    protected Integer goodsNo;
    @Column(name = "goods_code")
    protected String goodsCode;
    @Column(name = "case_contain_num")
    protected BigDecimal caseContainNum;
    @Column(name = "purchase_price")
    protected BigDecimal purchasePrice;
    @Column(name = "company_no")
    protected Integer companyNo;
    @Column(name = "now_goods_price")
    protected BigDecimal nowGoodsPrice;
    @Column(name = "change_plan_date")
    protected LocalDate changePlanDate;
    @Column(name = "before_price")
    protected BigDecimal beforePrice;
    @Column(name = "after_price")
    protected BigDecimal afterPrice;
    @Column(name = "goods_name")
    protected String goodsName;
    @Column(name = "change_contain_num")
    protected BigDecimal changeContainNum;
}
