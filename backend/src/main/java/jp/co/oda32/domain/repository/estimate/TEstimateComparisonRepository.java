package jp.co.oda32.domain.repository.estimate;

import jp.co.oda32.domain.model.estimate.TEstimateComparison;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TEstimateComparisonRepository extends JpaRepository<TEstimateComparison, Integer>, JpaSpecificationExecutor<TEstimateComparison> {
}
