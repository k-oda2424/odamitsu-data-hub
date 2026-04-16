package jp.co.oda32.util;

import jp.co.oda32.constant.OrderDetailStatus;
import jp.co.oda32.constant.OrderStatus;
import jp.co.oda32.domain.model.order.TOrder;
import jp.co.oda32.domain.model.order.TOrderDetail;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 注文に関するユーティリティクラス
 *
 * @author k_oda
 * @since 2018/11/30
 */
@Slf4j
public class OrderUtil {

    private static final Function<List<TOrderDetail>, BigDecimal> getTotalPrice = (tOrderDetailList -> tOrderDetailList.stream()
            .map(tOrderDetail -> tOrderDetail.getGoodsPrice().multiply(tOrderDetail.getOrderNum().subtract(tOrderDetail.getCancelNum())))
            .reduce(BigDecimal.ZERO, BigDecimal::add));

    private static final Function<List<TOrderDetail>, BigDecimal> getReturnTotalPrice = (tOrderDetailList -> tOrderDetailList.stream()
            .map(tOrderDetail -> tOrderDetail.getGoodsPrice().multiply(tOrderDetail.getReturnNum()))
            .reduce(BigDecimal.ZERO, BigDecimal::add));

    public static TOrder recalculateTOrder(TOrder tOrder, List<TOrderDetail> tOrderDetailList) {
        BigDecimal totalPrice = getTotalPrice.apply(tOrderDetailList);
        totalPrice = BigDecimalUtil.subtract(totalPrice, getReturnTotalPrice.apply(tOrderDetailList));
        TaxCalculationResult taxResult = calculateTax(tOrderDetailList);
        tOrder.setTotalPrice(totalPrice);
        tOrder.setTaxTotalPrice(taxResult.getTaxAmount());
        OrderStatus orderStatus = recalculateOrderStatus(tOrderDetailList);
        if (orderStatus != null) {
            tOrder.setOrderStatus(orderStatus.getValue());
        }
        return tOrder;
    }

    private static OrderStatus recalculateOrderStatus(List<TOrderDetail> tOrderDetailList) {
        List<OrderDetailStatus> orderDetailStatusList = tOrderDetailList.stream()
                .map(TOrderDetail::getOrderDetailStatus)
                .map(OrderDetailStatus::purse)
                .distinct()
                .collect(Collectors.toList());
        if (orderDetailStatusList.size() == 1) {
            OrderDetailStatus orderDetailStatus = orderDetailStatusList.get(0);
            switch (orderDetailStatus) {
                case BACK_ORDERED:
                    return OrderStatus.RECEIPT;
                case ALLOCATION:
                    return OrderStatus.WAIT_SHIPPING;
                case DELIVERED:
                    return OrderStatus.DELIVERED;
                case CANCEL:
                    return OrderStatus.CANCEL;
                case RETURN:
                    return OrderStatus.RETURN;
                default:
                    return null;
            }
        }
        return null;
    }

    private static TaxCalculationResult calculateTax(List<TOrderDetail> tOrderDetailList) {
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal totalAmountIncludingTax = BigDecimal.ZERO;

        // tax_rate が NULL の明細（旧 stock-app 移行時の埋め忘れ）は税計算から除外して WARN 出力
        List<TOrderDetail> validDetails = tOrderDetailList.stream()
                .filter(detail -> {
                    if (detail.getTaxRate() == null) {
                        log.warn("tax_rate が NULL の注文明細を税計算から除外します。order_no={}, order_detail_no={}, goods_code={}",
                                detail.getOrderNo(), detail.getOrderDetailNo(), detail.getGoodsCode());
                        return false;
                    }
                    return true;
                })
                .collect(Collectors.toList());

        List<BigDecimal> taxRates = validDetails.stream()
                .map(TOrderDetail::getTaxRate)
                .distinct()
                .collect(Collectors.toList());

        for (BigDecimal taxRate : taxRates) {
            List<TOrderDetail> detailsForRate = validDetails.stream()
                    .filter(detail -> detail.getTaxRate().compareTo(taxRate) == 0)
                    .collect(Collectors.toList());

            TaxCalculationResult result = calculateForTaxRate(detailsForRate, taxRate);
            taxAmount = taxAmount.add(result.getTaxAmount());
            totalAmountIncludingTax = totalAmountIncludingTax.add(result.getTotalAmountIncludingTax());
        }

        return new TaxCalculationResult(totalAmountIncludingTax, taxAmount);
    }

    private static TaxCalculationResult calculateForTaxRate(List<TOrderDetail> details, BigDecimal taxRate) {
        BigDecimal totalAmountIncludingTax = details.stream()
                .map(detail -> {
                    BigDecimal effectiveOrderNum = detail.getOrderNum().subtract(detail.getCancelNum()).subtract(detail.getReturnNum());
                    BigDecimal lineAmount = detail.getGoodsPrice().multiply(effectiveOrderNum.abs());
                    return effectiveOrderNum.compareTo(BigDecimal.ZERO) < 0 ? lineAmount.negate() : lineAmount;
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAmountExcludingTax = totalAmountIncludingTax
                .divide(BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.DOWN)), 0, java.math.RoundingMode.CEILING);

        BigDecimal taxAmount = totalAmountIncludingTax.subtract(totalAmountExcludingTax).setScale(0, java.math.RoundingMode.DOWN);

        return new TaxCalculationResult(totalAmountIncludingTax, taxAmount);
    }

    private static class TaxCalculationResult {
        private final BigDecimal totalAmountIncludingTax;
        private final BigDecimal taxAmount;

        public TaxCalculationResult(BigDecimal totalAmountIncludingTax, BigDecimal taxAmount) {
            this.totalAmountIncludingTax = totalAmountIncludingTax;
            this.taxAmount = taxAmount;
        }

        public BigDecimal getTotalAmountIncludingTax() {
            return totalAmountIncludingTax;
        }

        public BigDecimal getTaxAmount() {
            return taxAmount;
        }
    }
}
