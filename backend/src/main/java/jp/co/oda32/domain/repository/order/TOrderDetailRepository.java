package jp.co.oda32.domain.repository.order;

import jp.co.oda32.domain.model.order.TOrderDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
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

    /**
     * 得意先・商品コードの最終納品単価を取得します。
     */
    /**
     * 指定ショップ・商品コードの、得意先ごとの過去2年以内の最終納品単価を一括取得します。
     * 戻り値: [partner_no, goods_price] の配列リスト
     */
    @Query(value = "SELECT DISTINCT ON (o.partner_no) o.partner_no, od.goods_price " +
            "FROM t_order_detail od " +
            "JOIN t_order o ON od.order_no = o.order_no AND od.shop_no = o.shop_no " +
            "WHERE o.shop_no = :shopNo AND od.goods_code = :goodsCode AND od.del_flg = '0' " +
            "AND o.order_date_time >= NOW() - INTERVAL '2 years' " +
            "ORDER BY o.partner_no, o.order_date_time DESC", nativeQuery = true)
    List<Object[]> findLastDeliveredPricesByGoodsCode(@Param("shopNo") Integer shopNo, @Param("goodsCode") String goodsCode);

    /**
     * 注文明細のうち、受付/入荷待ち/在庫引当 状態かつ紐づく伝票日付が過去または当日の行を
     * 一括で納品済（order_detail_status='20'）に更新する。
     * 初回 catch-up 時の 528k 件規模でもメモリを消費せず単発 SQL で処理する。
     */
    @Modifying
    @Query(value = "UPDATE t_order_detail " +
            "   SET order_detail_status = '20', modify_date_time = NOW() " +
            " WHERE order_detail_status IN ('00','01','10') " +
            "   AND del_flg = '0' " +
            "   AND delivery_no IN (" +
            "         SELECT delivery_no FROM t_delivery " +
            "          WHERE slip_date <= CURRENT_DATE AND del_flg = '0'" +
            "       )",
            nativeQuery = true)
    int bulkUpdatePastDetailsToDelivered();
}
