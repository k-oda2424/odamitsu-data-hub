package jp.co.oda32.domain.repository.order;

import jp.co.oda32.domain.model.order.TReturnDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 返品明細(t_return_detail)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/11/29
 */
public interface TReturnDetailRepository extends JpaRepository<TReturnDetail, Integer>, JpaSpecificationExecutor<TReturnDetail> {
    List<TReturnDetail> findByOrderNo(@Param("orderNo") Integer orderNo);

    List<TReturnDetail> findByReturnNo(@Param("returnNo") Integer returnNo);

    List<TReturnDetail> findByDeliveryNoAndDeliveryDetailNo(@Param("deliveryNo") Integer deliveryNo, @Param("deliveryDetailNo") Integer deliveryDetailNo);

    TReturnDetail getByReturnNoAndReturnDetailNo(@Param("returnNo") Integer returnNo, @Param("returnDetailNo") Integer returnDetailNo);
}
