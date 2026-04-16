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

    /**
     * 配下 t_delivery_detail が **すべて** '20'(出荷済) の出荷について、
     * t_delivery.delivery_status を '20' に一括更新する。
     */
    @Modifying
    @Query(value = "UPDATE t_delivery d " +
            "   SET delivery_status = '20', modify_date_time = NOW() " +
            " WHERE d.del_flg = '0' " +
            "   AND d.delivery_status <> '20' " +
            "   AND EXISTS (SELECT 1 FROM t_delivery_detail dd WHERE dd.delivery_no = d.delivery_no AND dd.del_flg = '0') " +
            "   AND NOT EXISTS (" +
            "         SELECT 1 FROM t_delivery_detail dd " +
            "          WHERE dd.delivery_no = d.delivery_no AND dd.del_flg = '0' AND dd.delivery_detail_status <> '20'" +
            "       )",
            nativeQuery = true)
    int bulkUpdateParentDeliveryToDelivered();

    /**
     * 出荷ステータスが '20'(納品済) でかつ delivery_date が未設定の出荷について、
     * delivery_date = delivery_plan_date を設定する。
     */
    @Modifying
    @Query(value = "UPDATE t_delivery d " +
            "   SET delivery_date = delivery_plan_date, modify_date_time = NOW() " +
            " WHERE d.del_flg = '0' " +
            "   AND d.delivery_status = '20' " +
            "   AND d.delivery_date IS NULL " +
            "   AND d.delivery_plan_date IS NOT NULL",
            nativeQuery = true)
    int bulkUpdateDeliveryDateForDelivered();
}
