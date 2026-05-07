package jp.co.oda32.domain.model.audit;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

import java.time.LocalDateTime;

/**
 * T2: finance Service 層の監査証跡。
 * <p>
 * {@code @AuditLog} アノテーション付き Service メソッド呼び出しごとに 1 行記録される。
 * before/after はそれぞれ JSONB で保存し、@AuditExclude フィールドは出力されない。
 *
 * @since 2026-05-04 (T2)
 */
@Entity
@Table(name = "finance_audit_log")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FinanceAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    /** 操作実行者 (m_login_user.login_user_no)。NULL = SYSTEM/BATCH。 */
    @Column(name = "actor_user_no")
    private Integer actorUserNo;

    /** USER / SYSTEM / BATCH。 */
    @Column(name = "actor_type", nullable = false, length = 20)
    private String actorType;

    @Column(name = "operation", nullable = false, length = 50)
    private String operation;

    @Column(name = "target_table", nullable = false, length = 100)
    private String targetTable;

    @Type(JsonBinaryType.class)
    @Column(name = "target_pk", nullable = false, columnDefinition = "jsonb")
    private JsonNode targetPk;

    @Type(JsonBinaryType.class)
    @Column(name = "before_values", columnDefinition = "jsonb")
    private JsonNode beforeValues;

    @Type(JsonBinaryType.class)
    @Column(name = "after_values", columnDefinition = "jsonb")
    private JsonNode afterValues;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "source_ip", length = 45)
    private String sourceIp;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * G2-M2 fix (Codex Major #3, V043, 2026-05-06): force=true 時の per-supplier mismatch 全件 JSON。
     * <p>{@code reason} 列の 50 件切り詰め (容量節約) との乖離防止のため、
     * 監査追跡用に全件を構造化保存する。force=false の通常 audit 行や、
     * force=true でも mismatch 0 件の場合は NULL のまま。
     * <p>形式: {@code [{"line": "[5日払い] supplier=10001 ..."}, ...]} 等の文字列 entries 配列。
     */
    @Type(JsonBinaryType.class)
    @Column(name = "force_mismatch_details", columnDefinition = "jsonb")
    private JsonNode forceMismatchDetails;
}
