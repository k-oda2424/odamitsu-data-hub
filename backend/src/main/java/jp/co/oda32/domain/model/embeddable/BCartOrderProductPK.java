package jp.co.oda32.domain.model.embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * BCartOrderProductテーブルのキー
 *
 * @author k_oda
 * @since 2023/03/21
 */
public class BCartOrderProductPK implements Serializable {
    private Long id; // 受注商品ID (整数, 最大11桁)
    private Long orderId; // 受注ID (整数, 最大11桁)

    public BCartOrderProductPK() {
    }

    public BCartOrderProductPK(Long id, Long orderId) {
        this.id = id;
        this.orderId = orderId;
    }

    // hashCode() and equals() methods
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BCartOrderProductPK that = (BCartOrderProductPK) o;
        return Objects.equals(id, that.id) &&
                Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, orderId);
    }
}
