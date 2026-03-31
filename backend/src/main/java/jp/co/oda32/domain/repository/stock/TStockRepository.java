package jp.co.oda32.domain.repository.stock;

import jp.co.oda32.domain.model.stock.TStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

/**
 * 在庫(t_stock)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2019/04/23
 */
public interface TStockRepository extends JpaRepository<TStock, Integer>, JpaSpecificationExecutor<TStock> {
    TStock getByGoodsNoAndWarehouseNo(@Param("goodsNo") Integer goodsNo, @Param("warehouseNo") Integer warehouseNo);
}
