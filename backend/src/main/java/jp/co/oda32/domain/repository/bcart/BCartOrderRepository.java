package jp.co.oda32.domain.repository.bcart;

import jp.co.oda32.domain.model.bcart.BCartOrder;
import org.springframework.data.jpa.repository.EntityGraph;
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

    /**
     * 出荷情報入力画面のステータス連動用。
     * {@code orderProductList} を EAGER に取得することで {@code syncBCartOrderStatus} 内での N+1 を回避する。
     */
    @EntityGraph(attributePaths = {"orderProductList", "orderProductList.bCartLogistics"})
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT o FROM BCartOrder o WHERE o.id IN :idList")
    List<BCartOrder> findWithProductsByIdIn(@org.springframework.data.repository.query.Param("idList") List<Long> idList);
}