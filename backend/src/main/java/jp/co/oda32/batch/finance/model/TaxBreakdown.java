package jp.co.oda32.batch.finance.model;

import lombok.Getter;

import java.math.BigDecimal;

/**
 * 税率ごとの内訳を保持するクラス
 */
@Getter
public class TaxBreakdown {
    private BigDecimal taxExcludedAmount; // 税抜金額
    private BigDecimal taxIncludedAmount; // 税込金額

    public TaxBreakdown(BigDecimal taxExcludedAmount, BigDecimal taxIncludedAmount) {
        this.taxExcludedAmount = taxExcludedAmount;
        this.taxIncludedAmount = taxIncludedAmount;
    }

    /**
     * 税抜金額を追加します
     *
     * @param amount 追加する金額
     */
    public void addTaxExcludedAmount(BigDecimal amount) {
        this.taxExcludedAmount = this.taxExcludedAmount.add(amount != null ? amount : BigDecimal.ZERO);
    }

    /**
     * 税込金額を追加します
     *
     * @param amount 追加する金額
     */
    public void addTaxIncludedAmount(BigDecimal amount) {
        this.taxIncludedAmount = this.taxIncludedAmount.add(amount != null ? amount : BigDecimal.ZERO);
    }
}
