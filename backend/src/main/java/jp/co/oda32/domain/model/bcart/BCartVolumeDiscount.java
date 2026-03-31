package jp.co.oda32.domain.model.bcart;

import com.google.gson.annotations.SerializedName;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
@Entity
@Table(name = "b_cart_volume_discount")
public class BCartVolumeDiscount {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "b_cart_volume_discount_seq_gen")
    @SequenceGenerator(name = "b_cart_volume_discount_seq_gen", sequenceName = "b_cart_volume_discount_seq", allocationSize = 1)
    @Column(name = "volume_discount_id")
    private Long volumeDiscountId;
    @SerializedName("set_num")
    @Column(name = "set_num")
    private BigDecimal setNum;
    @SerializedName("unit_price")
    @Column(name = "unit_price")
    private BigDecimal unitPrice;
    @Column(name = "product_set_id")
    private Long productSetId;
    @Column(name = "customer_id")
    private Long customerId;
    @Column(name = "del_flg")
    private String delFlg;
    @Column(name = "b_cart_price_reflected")
    private boolean bCartPriceReflected;
}