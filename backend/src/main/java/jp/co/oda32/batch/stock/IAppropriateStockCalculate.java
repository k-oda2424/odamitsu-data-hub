package jp.co.oda32.batch.stock;

import jp.co.oda32.dto.stock.AppropriateStockEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

/**
 * 適正在庫計算用インターフェース
 *
 * @author k_oda
 * @since 2019/05/23
 */
public interface IAppropriateStockCalculate {
    /**
     * リードタイムを取得します。
     *
     * @param goodsNo 商品番号
     * @param pk      販売商品から検索する場合shopNo,在庫から検索する場合はwarehouseNo
     * @return 商品のリードタイム
     */
    int getLeadTime(int goodsNo, int pk);

    Map<Integer, Map<LocalDate, Optional<AppropriateStockEntity>>> getTargetMap();

    void truncateAppropriateStock();

    /**
     * 適正在庫を更新します
     *
     * @param goodsNo          商品番号
     * @param key              ショップ適正在庫の場合：shopNo,倉庫適正在庫の場合：warehouseNo
     * @param appropriateStock 適正在庫
     * @param safetyStock      安全在庫
     */
    void updateAppropriateStock(int goodsNo, int key, BigDecimal appropriateStock, BigDecimal safetyStock) throws Exception;
}
