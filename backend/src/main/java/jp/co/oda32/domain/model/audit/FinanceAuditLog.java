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
}
