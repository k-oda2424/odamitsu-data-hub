package jp.co.oda32.domain.repository.estimate;

import jp.co.oda32.domain.model.estimate.TEstimateComparison;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface TEstimateComparisonRepository extends JpaRepository<TEstimateComparison, Integer>, JpaSpecificationExecutor<TEstimateComparison> {

    @EntityGraph(attributePaths = {"comparisonGroupList", "comparisonGroupList.comparisonDetailList"})
    Optional<TEstimateComparison> findWithGroupsAndDetailsByComparisonNo(Integer comparisonNo);
}
