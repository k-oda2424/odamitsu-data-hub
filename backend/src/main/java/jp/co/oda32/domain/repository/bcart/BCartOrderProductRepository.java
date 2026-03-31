package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.BCartOrderProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * BCartのOrdersAPIのレスポンスを保持するテーブルに対するリポジトリクラス
 *
 * @author k_oda
 * @since 2023/03/20
 */
@Repository
public interface BCartOrderProductRepository extends JpaRepository<BCartOrderProduct, Long>, JpaSpecificationExecutor<BCartOrderProduct> {
    @Query("SELECT bp FROM BCartOrderProduct bp LEFT JOIN FETCH bp.bCartOrder WHERE bp.logisticsId IN (:logisticsIds)")
    List<BCartOrderProduct> findByLogisticsIdIn(@Param("logisticsIds") List<Long> logisticsIds);
}