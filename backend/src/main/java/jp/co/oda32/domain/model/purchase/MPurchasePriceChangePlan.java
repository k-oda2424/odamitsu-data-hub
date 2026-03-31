package jp.co.oda32.domain.model.purchase;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.model.master.MSupplier;
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
 * 仕入価格変更予定マスタテーブルのEntityクラス
 *
 * @author k_oda
 * @since 2022/10/13
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_purchase_price_change_plan")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@ShopEntity
public class MPurchasePriceChangePlan implements IEntity {
    @Id
    @Column(name = "purchase_price_change_plan_no")
    @SequenceGenerator(name = "m_purchase_price_change_plan_purchase_price_change_plan_no_seq_gen", sequenceName = "m_purchase_price_change_plan_purchase_price_change_plan_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_purchase_price_change_plan_purchase_price_change_plan_no_seq_gen")
    private Integer purchasePriceChangePlanNo;
    @Column(name = "shop_no")
    private Integer shopNo;
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
    @Column(name = "change_plan_date")
    private LocalDate changePlanDate;
    @Column(name = "supplier_code")
    private String supplierCode;
    @Column(name = "change_reason")
    private String changeReason;
    @Column(name = "partner_price_change_plan_created")
    private boolean partnerPriceChangePlanCreated;
    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "destination_no")
    private Integer destinationNo;
    @Column(name = "purchase_price_reflect")
    private boolean purchasePriceReflect;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "shop_no", referencedColumnName = "shop_no", insertable = false, updatable = false),
            @JoinColumn(name = "supplier_code", referencedColumnName = "supplier_code", insertable = false, updatable = false)})
    private MSupplier mSupplier;
}
