package jp.co.oda32.domain.model.bcart;

import com.google.gson.annotations.SerializedName;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import jp.co.oda32.domain.model.bcart.products.BCartProductCustom;
import jp.co.oda32.domain.model.bcart.products.BCartProductSetCustom;
import jp.co.oda32.domain.model.embeddable.BCartOrderProductPK;
import lombok.*;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * @author k_oda
 * @since 2023/03/21
 */

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "b_cart_order_product")
@IdClass(BCartOrderProductPK.class)
public class BCartOrderProduct {

    @Id
    @Column(name = "id")
    @SerializedName("id")
    private Long id; // 受注商品ID (整数, 最大11桁)

    @Id
    @Column(name = "order_id")
    @SerializedName("order_id")
    private Long orderId; // 受注ID (整数, 最大11桁)

    @Column(name = "logistics_id")
    @SerializedName("logistics_id")
    private Long logisticsId; // Bカート発送ID（出荷ID） (整数, 最大11桁)

    @Column(name = "product_id")
    @SerializedName("product_id")
    private Long productId; // 商品ID (整数, 最大11桁)

    @Column(name = "main_no", length = 255)
    @SerializedName("main_no")
    private String mainNo; // 商品管理番号 (文字列, 最大255文字)
    @Column(name = "product_no", length = 255)
    @SerializedName("product_no")
    private String productNo; // 品番 (文字列, 最大255文字)

    @Column(name = "jan_code", length = 255)
    @SerializedName("jan_code")
    private String janCode; // JANコード (文字列, 最大255文字)

    @Column(name = "location_no", length = 255)
    @SerializedName("location_no")
    private String locationNo; // ロケーション番号 (文字列, 最大255文字)

    @Column(name = "product_name", length = 255)
    @SerializedName("product_name")
    private String productName; // 商品名 (文字列, 最大255文字)

    @Column(name = "product_set_id")
    @SerializedName("product_set_id")
    private Long productSetId; // 商品セットID (整数, 最大11桁)

    @Column(name = "set_name", length = 255)
    @SerializedName("set_name")
    private String setName; // 商品セット名 (文字列, 最大255文字)

    @Column(name = "unit_price")
    @SerializedName("unit_price")
    private BigDecimal unitPrice; // 単価 (数値, 最大11桁)

    @Column(name = "set_quantity")
    @SerializedName("set_quantity")
    private BigDecimal setQuantity; // 入数 (数値, 最大64桁)

    @Column(name = "set_unit", length = 255)
    @SerializedName("set_unit")
    private String setUnit; // 単位 (文字列, 最大255文字)

    @Column(name = "order_pro_count")
    @SerializedName("order_pro_count")
    private BigDecimal orderProCount; // 注文数 (整数, 最大8桁)

    @Column(name = "shipping_size")
    @SerializedName("shipping_size")
    private BigDecimal shippingSize; // 配送サイズ (数値, 最大11桁)

    @Column(name = "tax_rate")
    @SerializedName("tax_rate")
    private BigDecimal taxRate; // 税率 (数値, 最大255桁, 10%は0.1、8%は0.08)

    @Column(name = "tax_type_id")
    @SerializedName("tax_type_id")
    private Integer taxTypeId; // 税区分 (整数, 最大1桁, 1=標準税率, 2=軽減税率)

    @Column(name = "tax_incl")
    @SerializedName("tax_incl")
    private Integer taxIncl; // 税込フラグ (整数, 最大1桁, 0=税別, 1=税込)

    @Column(name = "item_type", length = 255)
    @SerializedName("item_type")
    private String itemType; // 商品区分 (文字列, 最大255文字, 'product', 'shipping', 'payment_fees', 'point')
    @OneToMany(mappedBy = "bCartOrderProduct")
    private List<BCartProductCustom> bCartProductCustoms;

    @OneToMany(mappedBy = "bCartOrderProduct")
    private List<BCartProductSetCustom> bCartProductSetCustoms;

    @Type(value = ListArrayType.class, parameters = {
            @org.hibernate.annotations.Parameter(name = ListArrayType.SQL_ARRAY_TYPE, value = "text")
    })
    @Column(name = "options", columnDefinition = "text[]")
    @SerializedName("options")
    private List<String> options; // 商品オプションのリスト

    // カスタム項目で下代（仕入値を設定している）
    @Column(name = "set_custom3")
    @SerializedName("set_custom3")
    private String setCustom3;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", insertable = false, updatable = false)
    private BCartOrder bCartOrder;
    @ManyToOne
    @JoinColumn(name = "logistics_id", insertable = false, updatable = false)
    private BCartLogistics bCartLogistics;
}
