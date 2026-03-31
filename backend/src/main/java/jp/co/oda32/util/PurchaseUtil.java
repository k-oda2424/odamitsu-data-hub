package jp.co.oda32.util;


import jp.co.oda32.domain.model.purchase.TPurchase;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 仕入に関するユーティリティクラス
 *
 * @author k_oda
 * @since 2024/09/11
 */
public class PurchaseUtil {

    private static final Function<List<TPurchaseDetail>, BigDecimal> getTotalPrice = (tPurchaseDetailList -> tPurchaseDetailList.stream()
            .map(TPurchaseDetail::getSubtotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add));

    public static TPurchase recalculateTPurchase(TPurchase tPurchase, List<TPurchaseDetail> tPurchaseDetailList) {
        BigDecimal totalPrice = getTotalPrice.apply(tPurchaseDetailList);
        tPurchase.setPurchaseAmount(totalPrice);
        return tPurchase;
    }

    private static TaxCalculationResult calculateTax(List<TPurchaseDetail> tPurchaseDetailList) {
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal totalAmountIncludingTax = BigDecimal.ZERO;

        List<BigDecimal> taxRates = tPurchaseDetailList.stream()
                .map(TPurchaseDetail::getTaxRate)
                .distinct()
                .collect(Collectors.toList());

        for (BigDecimal taxRate : taxRates) {
            List<TPurchaseDetail> detailsForRate = tPurchaseDetailList.stream()
                    .filter(detail -> detail.getTaxRate().compareTo(taxRate) == 0)
                    .collect(Collectors.toList());

            TaxCalculationResult result = calculateForTaxRate(detailsForRate, taxRate);
            taxAmount = taxAmount.add(result.getTaxAmount());
            totalAmountIncludingTax = totalAmountIncludingTax.add(result.getTotalAmountIncludingTax());
        }

        return new TaxCalculationResult(totalAmountIncludingTax, taxAmount);
    }

    private static TaxCalculationResult calculateForTaxRate(List<TPurchaseDetail> details, BigDecimal taxRate) {
        BigDecimal totalAmountIncludingTax = details.stream()
                .map(detail -> {
                    BigDecimal effectivePurchaseNum = detail.getGoodsNum();
                    BigDecimal lineAmount = detail.getGoodsPrice().multiply(detail.getGoodsNum().abs());
                    return effectivePurchaseNum.compareTo(BigDecimal.ZERO) < 0 ? lineAmount.negate() : lineAmount;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAmountExcludingTax = totalAmountIncludingTax
                .divide(BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 2, RoundingMode.DOWN)), 0, RoundingMode.CEILING);

        BigDecimal taxAmount = totalAmountIncludingTax.subtract(totalAmountExcludingTax).setScale(0, RoundingMode.DOWN);

        return new TaxCalculationResult(totalAmountIncludingTax, taxAmount);
    }

    @Getter
    private static class TaxCalculationResult {
        private final BigDecimal totalAmountIncludingTax;
        private final BigDecimal taxAmount;

        public TaxCalculationResult(BigDecimal totalAmountIncludingTax, BigDecimal taxAmount) {
            this.totalAmountIncludingTax = totalAmountIncludingTax;
            this.taxAmount = taxAmount;
        }

    }
}
