package jp.co.oda32.domain.model.finance;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jp.co.oda32.domain.model.embeddable.TConsistencyReviewPK;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * 整合性レポート差分確認履歴。
 * <p>
 * 設計書: claudedocs/design-consistency-review.md
 *
 * @since 2026-04-23
 */
@Entity
@Table(name = "t_consistency_review")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TConsistencyReview {

    @EmbeddedId
    private TConsistencyReviewPK pk;

    /** IGNORE | MF_APPLY */
    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    @Column(name = "self_snapshot")
    private BigDecimal selfSnapshot;

    @Column(name = "mf_snapshot")
    private BigDecimal mfSnapshot;

    /**
     * MF_APPLY 実行時の verified_amount 退避値 (税率 String → 金額 Map)。
     * DELETE / IGNORE 切替時にロールバックするため。
     */
    @Type(JsonBinaryType.class)
    @Column(name = "previous_verified_amounts", columnDefinition = "jsonb")
    private Map<String, BigDecimal> previousVerifiedAmounts;

    @Column(name = "reviewed_by", nullable = false)
    private Integer reviewedBy;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @Column(name = "note", length = 500)
    private String note;
}
