package jp.co.oda32.domain.repository.order;

import jp.co.oda32.domain.model.order.TOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 注文(t_order)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/11/23
 */
public interface TOrderRepository extends JpaRepository<TOrder, Integer>, JpaSpecificationExecutor<TOrder> {
    @Modifying
    @Query("update TOrder set "
            + " orderStatus = :orderStatus"
            + " where orderNo in :orderNoList")
    int updateOrderStatusByOrderNoList(@Param("orderStatus") String orderStatus, @Param("orderNoList") List<Integer> orderNoList);

    TOrder getByShopNoAndProcessingSerialNumber(int shopNo, long processingSerialNumber);

    /**
     * 注文番号リストから注文を検索します
     *
     * @param orderNos 注文番号リスト
     * @return 注文リスト
     */
    @Query("SELECT o FROM TOrder o WHERE o.orderNo IN :orderNos AND o.delFlg = '0'")
    List<TOrder> findByOrderNoIn(@Param("orderNos") List<Integer> orderNos);
}
