package jp.co.oda32.domain.repository.stock;

import jp.co.oda32.domain.model.stock.TShopAppropriateStock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * ショップ適正在庫(t_shop_appropriate_stock)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2019/05/24
 */
public interface TShopAppropriateStockRepository extends JpaRepository<TShopAppropriateStock, Integer>, JpaSpecificationExecutor<TShopAppropriateStock> {
    TShopAppropriateStock getByGoodsNoAndShopNo(@Param("goodsNo") Integer goodsNo, @Param("shopNo") Integer shopNo);

    @Modifying
    @Transactional
    @Query(value = "truncate table t_shop_appropriate_stock", nativeQuery = true)
    void truncateTShopAppropriateStock();
}
