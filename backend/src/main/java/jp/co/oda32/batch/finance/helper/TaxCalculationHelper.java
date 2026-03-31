package jp.co.oda32.batch.finance.helper;

import jp.co.oda32.batch.finance.model.TaxBreakdown;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * 税計算に関連するヘルパークラス
 */
@Log4j2
public class TaxCalculationHelper {

    /**
     * 税率ごとの内訳から、再計算した税込金額を取得します。
     * 税率ごとに税抜金額から税込金額を計算し、より正確な税込合計を求めます。
     *
     * @param taxBreakdownMap 税率ごとの内訳マップ
     * @return 再計算した税込金額
     * @throws IllegalArgumentException taxBreakdownMapがnullまたは空の場合
     */
    public static BigDecimal calculateTaxIncludedAmount(Map<BigDecimal, TaxBreakdown> taxBreakdownMap) {
        if (taxBreakdownMap == null || taxBreakdownMap.isEmpty()) {
            log.error("税率ごとの内訳マップがnullまたは空です。正確な税込金額を計算できません。");
            throw new IllegalArgumentException("税率ごとの内訳マップがnullまたは空です");
        }

        BigDecimal totalTaxIncluded = BigDecimal.ZERO;

        for (Map.Entry<BigDecimal, TaxBreakdown> entry : taxBreakdownMap.entrySet()) {
            BigDecimal taxRate = entry.getKey();
            TaxBreakdown breakdown = entry.getValue();

            if (taxRate == null) {
                log.error("税率がnullです。正確な税込金額を計算できません。");
                throw new IllegalArgumentException("税率がnullです");
            }

            if (breakdown == null) {
                log.error("税率{}%の内訳がnullです。正確な税込金額を計算できません。", taxRate);
                throw new IllegalArgumentException("税率" + taxRate + "%の内訳がnullです");
            }

            // 税抜金額に税率を適用して税込金額を計算
            BigDecimal taxExcluded = breakdown.getTaxExcludedAmount();
            if (taxExcluded == null) {
                log.error("税率{}%の税抜金額がnullです。正確な税込金額を計算できません。", taxRate);
                throw new IllegalArgumentException("税率" + taxRate + "%の税抜金額がnullです");
            }

            // 税額を計算（小数点以下切り捨て）
            BigDecimal taxAmount = taxExcluded.multiply(taxRate)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);

            // 税込金額を計算
            BigDecimal taxIncluded = taxExcluded.add(taxAmount);

            // 合計に加算
            totalTaxIncluded = totalTaxIncluded.add(taxIncluded);
        }

        if (totalTaxIncluded == null) {
            log.error("計算された税込金額の合計がnullです。何らかの計算エラーが発生しています。");
            throw new IllegalStateException("計算された税込金額の合計がnullです");
        }

        return totalTaxIncluded;
    }

    /**
     * 税抜価格から消費税額を計算します
     *
     * @param baseAmount 税抜金額
     * @param taxRate    税率
     * @return 消費税額
     */
    public static BigDecimal calculateTaxAmount(BigDecimal baseAmount, BigDecimal taxRate) {
        if (baseAmount == null || baseAmount.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        if (taxRate == null) {
            taxRate = BigDecimal.TEN; // デフォルト10%
        }

        return baseAmount.multiply(taxRate)
                .divide(BigDecimal.valueOf(100), 0, RoundingMode.DOWN);
    }
}
