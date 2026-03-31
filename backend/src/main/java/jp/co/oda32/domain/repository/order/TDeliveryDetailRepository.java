package jp.co.oda32.domain.repository.order;

import jp.co.oda32.domain.model.order.TDeliveryDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 出荷(t_delivery_detail)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/11/23
 */
public interface TDeliveryDetailRepository extends JpaRepository<TDeliveryDetail, Integer>, JpaSpecificationExecutor<TDeliveryDetail> {
    List<TDeliveryDetail> findByDeliveryNo(@Param("deliveryNo") Integer deliveryNo);

    TDeliveryDetail getByDeliveryNoAndDeliveryDetailNo(@Param("deliveryNo") Integer deliveryNo, @Param("deliveryDetailNo") Integer deliveryDetailNo);

    TDeliveryDetail getByShopNoAndSlipNoAndDeliveryDetailNo(@Param("shopNo") Integer shopNo, @Param("slipNo") String slipNo, @Param("deliveryDetailNo") Integer deliveryDetailNo);

    @Query(value = "SELECT tdd.* FROM t_delivery_detail tdd " +
            "WHERE EXISTS (" +
            "    SELECT 1 FROM w_smile_order_output_file wsoof " +
            "    WHERE wsoof.shori_renban = tdd.processing_serial_number" +
            "    and wsoof.shop_no = tdd.shop_no) " +
            "AND NOT EXISTS (" +
            "    SELECT 1 FROM w_smile_order_output_file wsoof " +
            "    WHERE wsoof.shori_renban = tdd.processing_serial_number " +
            "      AND wsoof.gyou = tdd.order_detail_no " +
            "      AND wsoof.shop_no = tdd.shop_no" +
            ") " +
            "ORDER BY tdd.processing_serial_number",
            nativeQuery = true)
    List<TDeliveryDetail> findDeletTDeliveryList();

    /**
     * 出荷番号と注文明細番号に関連する出荷明細を検索します
     *
     * @param deliveryNo    出荷番号
     * @param orderDetailNo 注文明細番号
     * @return 出荷明細リスト
     */
    @Query("SELECT dd FROM TDeliveryDetail dd WHERE dd.deliveryNo = :deliveryNo " +
            "AND dd.orderDetailNo = :orderDetailNo AND dd.delFlg = '0'")
    List<TDeliveryDetail> findByDeliveryNoAndOrderDetailNo(
            @Param("deliveryNo") Integer deliveryNo,
            @Param("orderDetailNo") Integer orderDetailNo);
}
