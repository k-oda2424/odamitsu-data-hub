package jp.co.oda32.domain.model.embeddable;

import java.io.Serializable;
import java.util.Objects;

/**
 * BCartGroupPriceテーブルのキー
 *
 * @author k_oda
 * @since 2023/03/21
 */
public class BCartGroupPricePK implements Serializable {
    private Long productSetId; // 商品セットID
    private String groupId; // グループID

    public BCartGroupPricePK() {
    }

    public BCartGroupPricePK(Long productSetId, String groupId) {
        this.productSetId = productSetId;
        this.groupId = groupId;
    }

    // hashCode() and equals() methods
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BCartGroupPricePK that = (BCartGroupPricePK) o;
        return Objects.equals(productSetId, that.productSetId) &&
                Objects.equals(groupId, that.groupId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(productSetId, groupId);
    }
}
