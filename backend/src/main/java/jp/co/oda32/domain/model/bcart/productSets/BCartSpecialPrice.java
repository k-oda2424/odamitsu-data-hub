package jp.co.oda32.domain.model.bcart.productSets;

import com.google.gson.annotations.SerializedName;
import jp.co.oda32.domain.model.bcart.BCartProductSets;
import jp.co.oda32.domain.model.embeddable.BCartSpecialPricePK;
import lombok.*;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Map;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
@Entity
@IdClass(BCartSpecialPricePK.class)
@Table(name = "b_cart_special_price")
public class BCartSpecialPrice {
    @Id
    @Column(name = "product_set_id")
    private Long productSetId;
    @Id
    @Column(name = "customer_id")
    private Long customerId;

    @SerializedName("unit_price")
    @Column(name = "unit_price")
    private BigDecimal unitPrice;

    @SerializedName("volume_discount")
    @Transient
    @Type(jp.co.oda32.util.gson.GsonJsonElementType.class)
    private Map<String, BigDecimal> volumeDiscountMap;//(API受け取り用)
    @Column(name = "volume_discount_ids")
    private String volumeDiscountIds;
    @Column(name = "b_cart_price_reflected")
    private boolean bCartPriceReflected;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_set_id", referencedColumnName = "id", insertable = false, updatable = false)
    private BCartProductSets productSet;
}