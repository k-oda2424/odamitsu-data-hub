package jp.co.oda32.domain.repository.stock;

import jp.co.oda32.domain.model.stock.TStockLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

/**
 * 在庫履歴(t_stock_log)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2019/04/23
 */
public interface TStockLogRepository extends JpaRepository<TStockLog, Integer>, JpaSpecificationExecutor<TStockLog> {
    @Modifying
    @Query("delete from TStockLog " +
            " where moveTime = :moveTime and reason = 'inventory'")
    Integer deleteForInventory(@Param("moveTime") LocalDateTime moveTime);

    TStockLog getByGoodsNoAndWarehouseNoAndMoveTimeAndDeliveryNoAndPurchaseNo(@Param("goodsNo") Integer goodsNo, @Param("warehouseNo") Integer warehouseNo, @Param("moveTime") LocalDateTime moveTime, @Param("delivery_no") Integer deliveryNo, @Param("purchaseNo") Integer purchaseNo);
}
