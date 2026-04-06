package jp.co.oda32.domain.model.bcart;

import lombok.*;

import jakarta.persistence.*;
import java.sql.Timestamp;

/**
 * B-CART商品/カテゴリ変更履歴（バックアップ）
 */
@Getter
@Setter
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "b_cart_change_history")
public class BCartChangeHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "target_type", length = 30, nullable = false)
    private String targetType; // 'PRODUCT', 'PRODUCT_SET', 'CATEGORY'

    @Column(name = "target_id", nullable = false)
    private Long targetId;

    @Column(name = "change_type", length = 20, nullable = false)
    private String changeType; // 'PRICE', 'DESCRIPTION', 'STATUS', 'CATEGORY', 'BULK'

    @Column(name = "field_name", length = 100)
    private String fieldName;

    @Column(name = "before_value", columnDefinition = "TEXT")
    private String beforeValue;

    @Column(name = "after_value", columnDefinition = "TEXT")
    private String afterValue;

    @Column(name = "before_snapshot", columnDefinition = "jsonb")
    private String beforeSnapshot;

    @Column(name = "change_reason", length = 500)
    private String changeReason;

    @Column(name = "changed_by", nullable = false)
    private Integer changedBy;

    @Column(name = "changed_at", nullable = false)
    private Timestamp changedAt;

    @Column(name = "b_cart_reflected", nullable = false)
    private boolean bCartReflected;

    @Column(name = "b_cart_reflected_at")
    private Timestamp bCartReflectedAt;

    @PrePersist
    protected void onCreate() {
        if (changedAt == null) {
            changedAt = new Timestamp(System.currentTimeMillis());
        }
    }
}
