package jp.co.oda32.domain.model.purchase;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.master.MShop;
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
 * 仕入価格マスタテーブルのEntityクラス
 *
 * @author k_oda
 * @since 2020/01/21
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_purchase_price_log")
@ShopEntity
public class MPurchasePriceLog implements IEntity {
    @Id
    @Column(name = "purchase_price_log_no")
    @SequenceGenerator(name = "m_purchase_price_log_purchase_price_log_no_seq_gen", sequenceName = "m_purchase_price_log_purchase_price_log_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_purchase_price_log_purchase_price_log_no_seq_gen")
    private Integer purchasePriceLogNo;
    @Column(name = "supplier_no")
    private Integer supplierNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "partner_no")
    private Integer partnerNo;

    @Column(name = "goods_price")
    private BigDecimal goodsPrice;
    @Column(name = "include_tax_goods_price")
    private BigDecimal includeTaxGoodsPrice;
    @Column(name = "tax_type")
    private String taxType;
    @Column(name = "tax_rate")
    private BigDecimal taxRate;
    @Column(name = "include_tax_flg")
    private String includeTaxFlg;
    @Column(name = "note")
    private String note;
    @Column(name = "last_purchase_date")
    private LocalDate lastPurchaseDate;

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
    @JoinColumn(name = "goods_no", insertable = false, updatable = false)
    private MGoods mGoods;
    @OneToOne
    @JoinColumn(name = "shop_no", insertable = false, updatable = false)
    private MShop mShop;

    @OneToOne
    @JoinColumn(name = "supplier_no", insertable = false, updatable = false)
    private MSupplier mSupplier;
}
