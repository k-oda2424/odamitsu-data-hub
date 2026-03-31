package jp.co.oda32.batch.finance.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 取り込んだ明細の税抜金額の合計を保持するクラス
 * 消費税額は集計後に一括計算するため、ここでは0で保持
 */
@Getter
@AllArgsConstructor
public class TaxAggregationResult {
    // 取り込んだデータの税抜金額（基本額）の合計
    private final BigDecimal baseAmount;
    // 消費税額（集計後に一括計算するため、ここでは使用しない）
    private final BigDecimal taxAmount;

    /**
     * 二つの集計結果を合算します
     *
     * @param other 合算する集計結果
     * @return 合算した新しい集計結果
     */
    public TaxAggregationResult add(TaxAggregationResult other) {
        return new TaxAggregationResult(
                this.baseAmount.add(other.baseAmount),
                this.taxAmount.add(other.taxAmount)
        );
    }
}
