package jp.co.oda32.domain.repository.order;

import jp.co.oda32.domain.model.order.TDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 出荷(t_delivery)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/11/23
 */
public interface TDeliveryRepository extends JpaRepository<TDelivery, Integer>, JpaSpecificationExecutor<TDelivery> {
    TDelivery getByShopNoAndPartnerCodeAndSlipNo(@Param("shopNo") Integer shopNo, @Param("companyNo") String partnerCode, @Param("slipNo") String slipNo);

    TDelivery getByShopNoAndProcessingSerialNumber(int shopNo, long processingSerialNumber);

    @Modifying
    @Query("update TDelivery set "
            + " deliveryStatus = :deliveryStatus"
            + " where deliveryNo in :deliveryNoList")
    int updateDeliveryStatusByDeliveryNoList(@Param("deliveryStatus") String deliveryStatus, @Param("deliveryNoList") List<Integer> deliveryNoList);

    @Modifying
    @Query("update TDelivery set "
            + " deliveryDate = deliveryPlanDate"
            + " where deliveryNo in :deliveryNoList"
            + " and deliveryDate is null")
    int updateDeliveryDateByDeliveryNoList(@Param("deliveryNoList") List<Integer> deliveryNoList);
}
