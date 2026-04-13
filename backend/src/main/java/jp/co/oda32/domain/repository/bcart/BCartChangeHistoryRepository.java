package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.BCartChangeHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BCartChangeHistoryRepository extends JpaRepository<BCartChangeHistory, Long> {
    List<BCartChangeHistory> findByTargetTypeAndTargetIdOrderByChangedAtDesc(String targetType, Long targetId);

    List<BCartChangeHistory> findBybCartReflectedIsFalseAndTargetType(String targetType);
}
