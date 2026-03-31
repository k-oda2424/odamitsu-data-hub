package jp.co.oda32.domain.model.embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * BCartSpecialPriceテーブルのキー
 *
 * @author k_oda
 * @since 2023/04/28
 */
public class BCartSpecialPricePK implements Serializable {
    private Long productSetId; // 商品セットID (整数, 最大11桁)
    private Long customerId; // 会員ID (整数, 最大11桁)

    public BCartSpecialPricePK() {
    }

    public BCartSpecialPricePK(Long productSetId, Long orderId) {
        this.productSetId = productSetId;
        this.customerId = orderId;
    }

    // hashCode() and equals() methods
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BCartSpecialPricePK that = (BCartSpecialPricePK) o;
        return Objects.equals(productSetId, that.productSetId) &&
                Objects.equals(customerId, that.customerId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productSetId, customerId);
    }
}
