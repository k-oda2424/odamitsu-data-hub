package jp.co.oda32.domain.repository.purchase;

import jp.co.oda32.domain.model.purchase.TSendOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * 発注(t_send_order)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2019/07/24
 */
public interface TSendOrderRepository extends JpaRepository<TSendOrder, Integer>, JpaSpecificationExecutor<TSendOrder> {

}
