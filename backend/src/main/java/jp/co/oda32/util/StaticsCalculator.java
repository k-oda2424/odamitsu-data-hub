package jp.co.oda32.util;

/**
 * 統計に関する計算クラス
 *
 * @author k_oda
 * @since 2019/05/22
 */
public class StaticsCalculator {

    public double getStandardDeviation(Double[] data) {
        double sum = 0;
        double vars = 0;
        for (double datum : data) {
            sum += datum;
        }
        double ave = sum / data.length;
        for (double datum : data) {
            vars += ((datum - ave) * (datum - ave));
        }
        return Math.sqrt(vars / data.length);
    }
}
