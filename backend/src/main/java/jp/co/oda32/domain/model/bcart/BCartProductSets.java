package jp.co.oda32.domain.model.bcart;

import com.google.gson.annotations.SerializedName;
import jp.co.oda32.domain.model.bcart.productSets.BCartGroupPrice;
import jp.co.oda32.domain.model.bcart.productSets.BCartSpecialPrice;
import jp.co.oda32.domain.model.bcart.productSets.Customs;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * @author k_oda
 * @since 2023/04/21
 */
@Getter
@Setter
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "b_cart_product_sets")
public class BCartProductSets {
    @Id
    @Column(name = "id", nullable = false)
    private Long id; // 商品セットID

    @Column(name = "product_id")
    @SerializedName("product_id")
    private Long productId; // 商品ID

    @Column(name = "product_no", length = 255)
    @SerializedName("product_no")
    private String productNo; // 品番

    @Column(name = "jan_code", length = 255)
    @SerializedName("jan_code")
    private String janCode; // JANコード

    @Column(name = "location_no", length = 255)
    @SerializedName("location_no")
    private String locationNo; // ロケーション番号

    @Column(name = "jodai_type", length = 50)
    @SerializedName("jodai_type")
    private String jodaiType; // 参考タイプ

    @Column(name = "jodai")
    @SerializedName("jodai")
    private BigDecimal jodai; // 参考上代

    @Column(name = "name", length = 255)
    @SerializedName("name")
    private String name; // セット名

    @Column(name = "unit_price")
    @SerializedName("unit_price")
    private BigDecimal unitPrice; // 単価

    @Column(name = "min_order")
    @SerializedName("min_order")
    private BigDecimal minOrder; // 最小注文可能数

    @Column(name = "max_order")
    @SerializedName("max_order")
    private BigDecimal maxOrder; // 最大注文可能数

    @SerializedName("group_price")
    @Transient
    private Map<String, BCartGroupPrice> groupPriceMap; // 会員グループ別価格設定(API受け取り用カラム）

    @Column(name = "group_price")
    private String groupPrice; // 会員グループ別価格設定（DB保存用カラム）

    @SerializedName("special_price")
    @Transient
    private Map<String, BCartSpecialPrice> specialPriceMap; // 特別価格(API受け取り用カラム）

    @Column(name = "special_price")
    private String specialPrice; // 特別価格（DB保存用カラム）

    @SerializedName("volume_discount")
    @Transient
    private Map<String, BigDecimal> volumeDiscountMap;// 数量割引(API受け取り用カラム）
    @Column(name = "volume_discount")
    private String volumeDiscount;// 数量割引（DB保存用カラム）

    @Column(name = "quantity")
    @SerializedName("quantity")
    private Integer quantity; // 入数

    @Column(name = "unit", length = 255)
    @SerializedName("unit")
    private String unit; // 単位

    @Column(name = "description", length = 65535)
    @SerializedName("description")
    private String description; // セット説明

    @Column(name = "stock")
    @SerializedName("stock")
    private Integer stock; // 在庫

    @Column(name = "stock_flag")
    @SerializedName("stock_flag")
    private Integer stockFlag; // 在庫フラグ

    @SerializedName("stock_parent")
    @Transient
    private Map<String, Integer> stockParentMap;// 参照在庫(API受け取り用カラム）

    @Column(name = "stock_parent")
    private String stockParent;// 参照在庫（DB保存用カラム）

    @Column(name = "stock_view_id")
    @SerializedName("stock_view_id")
    private Integer stockViewId; // 在庫表示パターン

    @Column(name = "stock_few")
    @SerializedName("stock_few")
    private BigDecimal stockFew; // 在庫わずかになる数量

    @Column(name = "view_group_filter", length = 255)
    @SerializedName("view_group_filter")
    private String viewGroupFilter; // 非表示フィルタ

    @Column(name = "visible_customer_id", length = 65535)
    @SerializedName("visible_customer_id")
    private String visibleCustomerId; // 例外的に表示させる会員ID

    @SerializedName("customs")
    @Transient
    private List<Customs> customsList; //  カスタム項目(API受け取り用カラム）

    @Column(name = "customs")
    private String customsForDB; //  カスタム項目（DB保存用カラム）

    @Column(name = "purchase_price")
    private BigDecimal purchasePrice;

    @Column(name = "option_ids")
    @SerializedName("option_ids")
    private String optionIds; // 商品オプションID

    @Column(name = "shipping_group_id")
    @SerializedName("shipping_group_id")
    private Integer shippingGroupId; // 配送グループID

    @Column(name = "shipping_size")
    @SerializedName("shipping_size")
    private BigDecimal shippingSize; // 配送サイズ

    @Column(name = "priority")
    @SerializedName("priority")
    private Integer priority; // 表示優先度

    @Column(name = "set_flag", length = 3)
    @SerializedName("set_flag")
    private String setFlag; // セットフラグ

    @Column(name = "tax_type_id")
    @SerializedName("tax_type_id")
    private Integer taxTypeId; // 税区分ID

    @Column(name = "updated_at")
    @SerializedName("updated_at")
    private Date updatedAt; // 更新日時

    @Column(name = "volume_discount_ids")
    private String volumeDiscountIds;
    @Column(name = "b_cart_price_reflected")
    private boolean bCartPriceReflected;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", referencedColumnName = "id", insertable = false, updatable = false)
    private BCartProducts bCartProducts;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "productSet")
    private List<BCartGroupPrice> groupPrices;

    @OneToMany(fetch = FetchType.LAZY, mappedBy = "productSet")
    private List<BCartSpecialPrice> specialPrices;
}
