package jp.co.oda32.audit;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.domain.model.audit.FinanceAuditLog;
import jp.co.oda32.domain.repository.audit.FinanceAuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * T2: 監査ログを別 tx ({@code REQUIRES_NEW}) で書き込む writer。
 * <p>
 * Aspect 自身に {@code @Transactional} を付けても同クラス内 self-call では
 * proxy を通らず {@code REQUIRES_NEW} が効かないため、別 Bean に切り出す。
 * <p>
 * 別 tx にしておくことで、業務 tx が rollback しても "失敗操作" の監査ログだけは残せる。
 *
 * @since 2026-05-04 (T2)
 */
@Component
@RequiredArgsConstructor
public class FinanceAuditWriter {

    private final FinanceAuditLogRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(String table, String operation, Integer userNo, String actorType,
                      JsonNode targetPk, JsonNode beforeValues, JsonNode afterValues,
                      String reason, String sourceIp, String userAgent) {
        FinanceAuditLog entry = FinanceAuditLog.builder()
                .occurredAt(LocalDateTime.now())
                .actorUserNo(userNo)
                .actorType(actorType)
                .operation(operation)
                .targetTable(table)
                .targetPk(targetPk)
                .beforeValues(beforeValues)
                .afterValues(afterValues)
                .reason(reason)
                .sourceIp(sourceIp)
                .userAgent(userAgent)
                .build();
        repository.save(entry);
    }
}
