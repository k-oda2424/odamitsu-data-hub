package jp.co.oda32.domain.repository.order;

import jp.co.oda32.domain.model.order.TOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 注文(t_order_detail)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/11/23
 */
public interface TOrderDetailRepository extends JpaRepository<TOrderDetail, Integer>, JpaSpecificationExecutor<TOrderDetail> {

    List<TOrderDetail> findByDeliveryNo(@Param("deliveryNo") Integer deliveryNo);

    List<TOrderDetail> findByOrderDetailStatus(@Param("orderDetailStatus") String orderDetailStatus);
    TOrderDetail getByOrderNoAndOrderDetailNo(@Param("orderNo") Integer orderNo, @Param("orderDetailNo") Integer orderDetailNo);

    /**
     * 注文番号から注文明細を検索します
     *
     * @param orderNo 注文番号
     * @return 注文明細リスト
     */
    @Query("SELECT od FROM TOrderDetail od WHERE od.orderNo = :orderNo AND od.delFlg = '0'")
    List<TOrderDetail> findByOrderNo(@Param("orderNo") Integer orderNo);

    /**
     * 出荷番号リストに関連する注文明細を検索します
     *
     * @param deliveryNos 出荷番号リスト
     * @return 注文明細リスト
     */
    @Query("SELECT od FROM TOrderDetail od WHERE od.deliveryNo IN :deliveryNos AND od.delFlg = '0'")
    List<TOrderDetail> findByDeliveryNos(@Param("deliveryNos") List<Integer> deliveryNos);
}
