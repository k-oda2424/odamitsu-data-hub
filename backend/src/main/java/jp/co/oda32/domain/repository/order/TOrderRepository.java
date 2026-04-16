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

    /**
     * 配下 t_order_detail が **すべて** '20'(納品済) の注文について、
     * t_order.order_status を '20' に一括更新する。
     * 既に '20' のものは対象外。
     */
    @Modifying
    @Query(value = "UPDATE t_order o " +
            "   SET order_status = '20', modify_date_time = NOW() " +
            " WHERE o.del_flg = '0' " +
            "   AND o.order_status <> '20' " +
            "   AND EXISTS (SELECT 1 FROM t_order_detail d WHERE d.order_no = o.order_no AND d.del_flg = '0') " +
            "   AND NOT EXISTS (" +
            "         SELECT 1 FROM t_order_detail d " +
            "          WHERE d.order_no = o.order_no AND d.del_flg = '0' AND d.order_detail_status <> '20'" +
            "       )",
            nativeQuery = true)
    int bulkUpdateParentOrderToDelivered();

    /**
     * 配下 t_order_detail に '20'(納品済) と未納品が **混在** している注文について、
     * t_order.order_status を '10'(出荷待ち) に一括更新する。
     * 全キャンセル/全返品 ('90'/'99') は対象外。
     */
    @Modifying
    @Query(value = "UPDATE t_order o " +
            "   SET order_status = '10', modify_date_time = NOW() " +
            " WHERE o.del_flg = '0' " +
            "   AND o.order_status NOT IN ('10','20','90','99') " +
            "   AND EXISTS (" +
            "         SELECT 1 FROM t_order_detail d " +
            "          WHERE d.order_no = o.order_no AND d.del_flg = '0' AND d.order_detail_status = '20'" +
            "       ) " +
            "   AND EXISTS (" +
            "         SELECT 1 FROM t_order_detail d " +
            "          WHERE d.order_no = o.order_no AND d.del_flg = '0' AND d.order_detail_status <> '20'" +
            "       )",
            nativeQuery = true)
    int bulkUpdateParentOrderToWaitShipping();
}
