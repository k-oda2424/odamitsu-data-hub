package jp.co.oda32.domain.repository.stock;

import jp.co.oda32.domain.model.stock.TWarehouseAppropriateStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

/**
 * 倉庫適正在庫(t_warehouse_appropriate_stock)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2019/05/24
 */
public interface TWarehouseAppropriateStockRepository extends JpaRepository<TWarehouseAppropriateStock, Integer>, JpaSpecificationExecutor<TWarehouseAppropriateStock> {
    TWarehouseAppropriateStock getByGoodsNoAndWarehouseNo(@Param("goodsNo") Integer goodsNo, @Param("warehouseNo") Integer warehouseNo);
}
