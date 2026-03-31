package jp.co.oda32.domain.model.goods;

import jp.co.oda32.domain.model.embeddable.MSalesGoodsPK;
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

/**
 * 販売商品テーブルのEntityクラス
 *
 * @author k_oda
 * @since 2018/07/20
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_sales_goods")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@IdClass(MSalesGoodsPK.class)
@ShopEntity
public class MSalesGoods implements ISalesGoods {
    @Id
    @Column(name = "shop_no")
    private Integer shopNo;
    @Id
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "goods_code")
    private String goodsCode;
    @Column(name = "goods_sku_code")
    private String goodsSkuCode;
    @Column(name = "goods_name")
    private String goodsName;
    @Column(name = "category_no")
    private Integer categoryNo;
    @Column(name = "reference_price")
    private BigDecimal referencePrice;
    @Column(name = "purchase_price")
    private BigDecimal purchasePrice;
    @Column(name = "goods_price")
    private BigDecimal goodsPrice;
    @Column(name = "supplier_no")
    private Integer supplierNo;
    @Column(name = "catchphrase")
    private String catchphrase;
    @Column(name = "goods_introduction")
    private String goodsIntroduction;
    @Column(name = "goods_description1")
    private String goodsDescription1;
    @Column(name = "goods_description2")
    private String goodsDescription2;
    @Column(name = "del_flg")
    private String delFlg;
    @Column(name = "keyword")
    private String keyword;
    @Column(name = "direct_shipping_flg")
    private String directShippingFlg;
    @Column(name = "lead_time")
    private Integer leadTime;

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

    @Override
    public boolean getIsWork() {
        return false;
    }
}
