package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.purchase.TSendOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 発注明細(t_send_order_detail)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2019/07/24
 */
public interface TSendOrderDetailRepository extends JpaRepository<TSendOrderDetail, Integer>, JpaSpecificationExecutor<TSendOrderDetail> {
    List<TSendOrderDetail> findBySendOrderNo(@Param("sendOrderNo") Integer sendOrderNo);

    List<TSendOrderDetail> findBySendOrderDetailStatus(@Param("sendOrderDetailStatus") String sendOrderDetailStatus);

    TSendOrderDetail getBySendOrderNoAndSendOrderDetailNo(@Param("sendOrderNo") Integer sendOrderNo, @Param("orderDetailNo") Integer orderDetailNo);
}
