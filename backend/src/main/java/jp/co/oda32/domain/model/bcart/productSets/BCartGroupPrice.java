package jp.co.oda32.domain.model.bcart.productSets;


import com.google.gson.annotations.SerializedName;
import jp.co.oda32.domain.model.bcart.BCartProductSets;
import jp.co.oda32.domain.model.embeddable.BCartGroupPricePK;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Map;

/**
 * @author k_oda
 * @since 2023/04/22
 */
@Getter
@Setter
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
@Entity
@IdClass(BCartGroupPricePK.class)
@Table(name = "b_cart_group_price")
public class BCartGroupPrice {
    @Id
    @Column(name = "product_set_id")
    private Long productSetId;
    @Id
    @Column(name = "group_id")
    private String groupId;

    @SerializedName("name")
    @Column(name = "name")
    private String name;
    @SerializedName("rate")
    @Column(name = "rate")
    private BigDecimal rate;
    @SerializedName("unit_price")
    @Column(name = "unit_price")
    private BigDecimal unitPrice;
    @SerializedName("fixed_price")
    @Column(name = "fixed_price")
    private BigDecimal fixedPrice;

    @SerializedName("volume_discount")
    @Transient
    private Map<String, BigDecimal> volumeDiscount;
    @Column(name = "volume_discount_ids")
    private String volumeDiscountIds;

    @Column(name = "b_cart_price_reflected")
    private boolean bCartPriceReflected;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_set_id", referencedColumnName = "id", insertable = false, updatable = false)
    private BCartProductSets productSet;
}
