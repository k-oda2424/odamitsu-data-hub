package jp.co.oda32.domain.repository.order;

import jp.co.oda32.domain.model.order.TDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DeletedOrderRepository extends JpaRepository<TDelivery, Integer> {

    /**
     * SMILEで削除された出荷を検出します。
     * w_smile_order_outputに存在する伝票日付（現在日付以前）を基準に、
     * SMILEから削除された出荷を検索します。
     *
     * @param shopNo ショップ番号
     * @return 削除された出荷のリスト
     */
    @Query(nativeQuery = true, value =
            "WITH target_dates AS (" +
                    "    SELECT DISTINCT denpyou_hizuke, shop_no" +
                    "    FROM w_smile_order_output_file" +
                    "    WHERE denpyou_hizuke <= CURRENT_DATE" +
                    ")" +
                    "SELECT d.* " +
                    "FROM t_delivery d " +
                    "JOIN target_dates td ON d.slip_date = td.denpyou_hizuke AND d.shop_no = td.shop_no " +
                    "WHERE d.del_flg = '0' " +
                    "AND d.processing_serial_number IS NOT NULL " +
                    "AND d.slip_date <= CURRENT_DATE " +
                    "AND (:shopNo IS NULL OR d.shop_no = :shopNo) " +
                    "AND NOT EXISTS (" +
                    "    SELECT 1 FROM w_smile_order_output_file w" +
                    "    WHERE w.shori_renban = d.processing_serial_number" +
                    "    AND w.shop_no = d.shop_no" +
                    ")")
    List<TDelivery> findDeletedDeliveries(@Param("shopNo") Integer shopNo);
}