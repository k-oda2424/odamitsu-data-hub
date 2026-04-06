package jp.co.oda32.dto.bcart;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.co.oda32.domain.model.bcart.BCartChangeHistory;
import lombok.Builder;
import lombok.Data;

import java.sql.Timestamp;

@Data
@Builder
public class BCartChangeHistoryResponse {
    private Long id;
    private String targetType;
    private Long targetId;
    private String changeType;
    private String fieldName;
    private String beforeValue;
    private String afterValue;
    private Integer changedBy;
    private Timestamp changedAt;
    @JsonProperty("bCartReflected")
    private boolean bCartReflected;
    @JsonProperty("bCartReflectedAt")
    private Timestamp bCartReflectedAt;

    public static BCartChangeHistoryResponse from(BCartChangeHistory entity) {
        return BCartChangeHistoryResponse.builder()
                .id(entity.getId())
                .targetType(entity.getTargetType())
                .targetId(entity.getTargetId())
                .changeType(entity.getChangeType())
                .fieldName(entity.getFieldName())
                .beforeValue(entity.getBeforeValue())
                .afterValue(entity.getAfterValue())
                .changedBy(entity.getChangedBy())
                .changedAt(entity.getChangedAt())
                .bCartReflected(entity.isBCartReflected())
                .bCartReflectedAt(entity.getBCartReflectedAt())
                .build();
    }
}
