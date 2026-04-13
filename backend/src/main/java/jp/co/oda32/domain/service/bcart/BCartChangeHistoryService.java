package jp.co.oda32.domain.service.bcart;

import com.google.gson.Gson;
import jp.co.oda32.domain.model.bcart.BCartChangeHistory;
import jp.co.oda32.domain.repository.bcart.BCartChangeHistoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BCartChangeHistoryService {

    private final BCartChangeHistoryRepository repository;
    private final Gson gson = new Gson();

    public List<BCartChangeHistory> findByTarget(String targetType, Long targetId) {
        return repository.findByTargetTypeAndTargetIdOrderByChangedAtDesc(targetType, targetId);
    }

    public List<BCartChangeHistory> findUnreflectedByType(String targetType) {
        return repository.findBybCartReflectedIsFalseAndTargetType(targetType);
    }

    @Transactional
    public BCartChangeHistory recordChange(String targetType, Long targetId, String changeType,
                                           String fieldName, String beforeValue, String afterValue,
                                           Object beforeSnapshot, Integer changedBy) {
        BCartChangeHistory history = BCartChangeHistory.builder()
                .targetType(targetType)
                .targetId(targetId)
                .changeType(changeType)
                .fieldName(fieldName)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .beforeSnapshot(beforeSnapshot != null ? gson.toJson(beforeSnapshot) : null)
                .changedBy(changedBy)
                .changedAt(new Timestamp(System.currentTimeMillis()))
                .bCartReflected(false)
                .build();
        return repository.save(history);
    }

    @Transactional
    public void markReflected(Long historyId) {
        repository.findById(historyId).ifPresent(h -> {
            h.setBCartReflected(true);
            h.setBCartReflectedAt(new Timestamp(System.currentTimeMillis()));
            repository.save(h);
        });
    }
}
