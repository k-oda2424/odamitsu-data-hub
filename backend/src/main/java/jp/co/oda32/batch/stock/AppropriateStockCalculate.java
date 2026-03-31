package jp.co.oda32.batch.stock;

import jp.co.oda32.dto.stock.AppropriateStockEntity;
import jp.co.oda32.util.StaticsCalculator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 適正在庫計算用クラス
 *
 * @author k_oda
 * @since 2019/05/22
 */
public class AppropriateStockCalculate implements IAppropriateStock {

    private StaticsCalculator staticsCalculator = new StaticsCalculator();

    private static final BigDecimal SERVICE95 = new BigDecimal("1.645"); // 95%のサービスレベルに相当する安全係数
    private final int leadTime;

    public AppropriateStockCalculate(int leadTime) {
        this.leadTime = leadTime;
    }

    /**
     * 標準偏差を算出して返します。
     *
     * @param targetList 1日毎にまとめた商品移動実績の配列
     * @param periodDays 期間日数
     * @return 標準偏差
     */
    public BigDecimal calculateStandardDeviation(List<AppropriateStockEntity> targetList, int periodDays) {
        List<BigDecimal> dataList = targetList.stream()
                .map(AppropriateStockEntity::getGoodsNum)
                .collect(Collectors.toList());

        int targetDataNum = dataList.size();
        for (int i = 1; i <= periodDays - targetDataNum; i++) {
            dataList.add(BigDecimal.ZERO);
        }

        // BigDecimalをDoubleに変換
        Double[] dataArray = dataList.stream()
                .map(BigDecimal::doubleValue)
                .toArray(Double[]::new);

        // StaticsCalculatorのメソッドを呼び出し
        double stdDev = this.staticsCalculator.getStandardDeviation(dataArray);

        // 結果をBigDecimalに変換して返す
        return BigDecimal.valueOf(stdDev);
    }

    /**
     * 安全在庫を算出して返します。
     * 安全在庫 = 安全係数 × 1日受注標準偏差 × √リードタイム
     *
     * @return 安全在庫
     */
    public BigDecimal calculateSafetyStock(BigDecimal standardDeviation) {
        // リードタイムの平方根を BigDecimal に変換して計算
        BigDecimal sqrtLeadTime = BigDecimal.valueOf(Math.sqrt(this.leadTime));

        // 安全在庫の計算：標準偏差にサービスレベル（安全係数）と平方根を掛ける
        return SERVICE95.multiply(standardDeviation)
                .multiply(sqrtLeadTime)
                .setScale(2, RoundingMode.HALF_UP); // 小数点以下2位まで四捨五入
    }

    /**
     * 適正在庫 = リードタイム×1日平均出荷数量＋安全在庫(変動対応在庫)
     *
     * @return 適正在庫
     */
    public BigDecimal calculateAppropriateStock(BigDecimal averageDailyShipment, BigDecimal safetyStock) {
        // リードタイム×1日平均出荷数量
        BigDecimal leadTimeStock = averageDailyShipment.multiply(new BigDecimal(this.leadTime));

        // 適正在庫 = リードタイム×1日平均出荷数量 + 安全在庫
        return leadTimeStock.add(safetyStock).setScale(2, RoundingMode.HALF_UP);
    }
}
