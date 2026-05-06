package jp.co.oda32.dto.bcart;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * B-CART商品セットの未反映変更点を商品セット単位で集約したレスポンス。
 *
 * `b_cart_change_history` の `target_type='PRODUCT_SET' AND b_cart_reflected=false` を
 * (target_id, field_name) でグループ化して取得する。
 */
@Data
@Builder
public class BCartPendingChangeResponse {
    private Long productSetId;
    private Long productId;
    private String productName;
    private String setName;
    private String productNo;
    private String janCode;
    private List<Change> changes;
    private LocalDateTime lastChangedAt;

    @Data
    @Builder
    public static class Change {
        private String field;        // "unit_price" | "shipping_size"
        private String before;       // 最古 before_value
        private String after;        // 最新 after_value
        private LocalDateTime changedAt;
    }
}
