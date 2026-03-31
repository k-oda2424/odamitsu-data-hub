package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.BCartOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
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
public interface BCartOrderRepository extends JpaRepository<BCartOrder, Long>, JpaSpecificationExecutor<BCartOrder> {
    List<BCartOrder> findByStatus(@Param("status") String status);

    List<BCartOrder> findByIdIn(List<Long> idList);
}