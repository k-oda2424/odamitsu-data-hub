package jp.co.oda32.dto.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.domain.model.audit.FinanceAuditLog;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * T2: 監査ログ閲覧 API のレスポンス DTO。
 *
 * @since 2026-05-04 (T2)
 */
@Data
@Builder
public class AuditLogResponse {
    private Long id;
    private LocalDateTime occurredAt;
    private Integer actorUserNo;
    /** m_login_user.user_name から JOIN 解決 (NULL = SYSTEM/BATCH もしくは退職等で削除済 user)。 */
    private String actorUserName;
    private String actorType;
    private String operation;
    private String targetTable;
    private JsonNode targetPk;
    private JsonNode beforeValues;
    private JsonNode afterValues;
    private String reason;
    private String sourceIp;
    private String userAgent;

    public static AuditLogResponse from(FinanceAuditLog entity, String actorUserName) {
        return AuditLogResponse.builder()
                .id(entity.getId())
                .occurredAt(entity.getOccurredAt())
                .actorUserNo(entity.getActorUserNo())
                .actorUserName(actorUserName)
                .actorType(entity.getActorType())
                .operation(entity.getOperation())
                .targetTable(entity.getTargetTable())
                .targetPk(entity.getTargetPk())
                .beforeValues(entity.getBeforeValues())
                .afterValues(entity.getAfterValues())
                .reason(entity.getReason())
                .sourceIp(entity.getSourceIp())
                .userAgent(entity.getUserAgent())
                .build();
    }

    /** 一覧表示用 (before/after を除外して payload を軽く)。 */
    public static AuditLogResponse summaryFrom(FinanceAuditLog entity, String actorUserName) {
        return AuditLogResponse.builder()
                .id(entity.getId())
                .occurredAt(entity.getOccurredAt())
                .actorUserNo(entity.getActorUserNo())
                .actorUserName(actorUserName)
                .actorType(entity.getActorType())
                .operation(entity.getOperation())
                .targetTable(entity.getTargetTable())
                .targetPk(entity.getTargetPk())
                .reason(entity.getReason())
                .sourceIp(entity.getSourceIp())
                .build();
    }
}
