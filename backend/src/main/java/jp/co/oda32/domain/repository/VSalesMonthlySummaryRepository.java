package jp.co.oda32.domain.repository;

import jp.co.oda32.domain.model.VSalesMonthlySummary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 月間売上金額Viewリポジトリインターフェース
 *
 * @author k_oda
 * @since 2020/03/13
 */
public interface VSalesMonthlySummaryRepository extends JpaRepository<VSalesMonthlySummary, Integer>, JpaSpecificationExecutor<VSalesMonthlySummary> {

    List<VSalesMonthlySummary> findByShopNo(@Param("shopNo") Integer shopNo);

    /**
     * マテリアライズドビュー更新
     */
    @Modifying
    @Query(value = "REFRESH MATERIALIZED VIEW v_sales_monthly_summary;", nativeQuery = true)
    void refreshMaterializedView();
}
