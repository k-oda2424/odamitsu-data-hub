package jp.co.oda32.domain.model.goods;

import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * 得意先商品価格変更予定マスタテーブルのEntityクラス
 *
 * @author k_oda
 * @since 2022/10/13
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_partner_goods_price_change_plan")
@ShopEntity
public class MPartnerGoodsPriceChangePlan implements IPartnerGoodsPriceChangePlan {
    @Id
    @Column(name = "partner_goods_price_change_plan_no")
    @SequenceGenerator(name = "m_partner_goods_price_change_plan_no_seq_gen", sequenceName = "m_partner_goods_price_change_plan_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_partner_goods_price_change_plan_no_seq_gen")
    private Integer partnerGoodsPriceChangePlanNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "partner_code")
    private String partnerCode;
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "goods_code")
    private String goodsCode;
    @Column(name = "jan_code")
    private String janCode;
    @Column(name = "before_price")
    private BigDecimal beforePrice;
    @Column(name = "after_price")
    private BigDecimal afterPrice;
    @Column(name = "goods_name")
    private String goodsName;
    @Column(name = "change_contain_num")
    private BigDecimal changeContainNum;
    // 得意先の届け先ごとに仕入金額がことなる場合に入力する
    @Column(name = "destination_no")
    private Integer destinationNo;
    @Column(name = "change_plan_date")
    private LocalDate changePlanDate;
    @Column(name = "change_reason")
    private String changeReason;
    @Column(name = "before_purchase_price")
    private BigDecimal beforePurchasePrice;
    @Column(name = "after_purchase_price")
    private BigDecimal afterPurchasePrice;
    @Column(name = "estimate_created")
    private boolean estimateCreated;
    @Column(name = "estimate_no")
    private Integer estimateNo;
    @Column(name = "estimate_detail_no")
    private Integer estimateDetailNo;
    @Column(name = "partner_price_reflect")
    private boolean partnerPriceReflect;
    @Column(name = "parent_change_plan_no")
    private Integer parentChangePlanNo;
    // 赤字フラグ
    @Column(name = "deficit_flg")
    private boolean deficitFlg;
    @Column(name = "note")
    private String note;

    @Column(name = "del_flg")
    private String delFlg;
    @Column(name = "add_date_time")
    private Timestamp addDateTime;
    @Column(name = "add_user_no")
    private Integer addUserNo;
    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;
    @Column(name = "modify_user_no")
    private Integer modifyUserNo;

    @OneToOne
    @JoinColumn(name = "partner_no", insertable = false, updatable = false)
    private MPartner mPartner;
}
