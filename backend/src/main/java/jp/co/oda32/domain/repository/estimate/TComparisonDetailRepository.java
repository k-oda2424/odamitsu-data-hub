package jp.co.oda32.domain.repository.estimate;

import jp.co.oda32.domain.model.embeddable.TComparisonDetailPK;
import jp.co.oda32.domain.model.estimate.TComparisonDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TComparisonDetailRepository extends JpaRepository<TComparisonDetail, TComparisonDetailPK> {
    List<TComparisonDetail> findByComparisonNo(@Param("comparisonNo") Integer comparisonNo);

    @Modifying
    @Query(value = "UPDATE t_comparison_detail SET del_flg = '1', modify_date_time = CURRENT_TIMESTAMP WHERE comparison_no = :comparisonNo", nativeQuery = true)
    int softDeleteByComparisonNo(@Param("comparisonNo") int comparisonNo);
}
