package jp.co.oda32.util;

import jp.co.oda32.constant.DeliveryDetailStatus;
import jp.co.oda32.constant.DeliveryStatus;
import jp.co.oda32.constant.TaxType;
import jp.co.oda32.domain.model.order.TDelivery;
import jp.co.oda32.domain.model.order.TDeliveryDetail;
import jp.co.oda32.domain.model.order.TOrderDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 出荷に関するユーティリティクラス
 *
 * @author k_oda
 * @since 2018/11/30
 */
@Service
@RequiredArgsConstructor
public class DeliveryUtil {
    private Function<List<TDeliveryDetail>, BigDecimal> getTotalPrice = (tDeliveryDetailList -> tDeliveryDetailList.stream()
            .map(tDeliveryDetail -> tDeliveryDetail.getGoodsPrice().multiply(tDeliveryDetail.getDeliveryNum()))
            .reduce(BigDecimal.ZERO, BigDecimal::add));

    private static TaxCalculationResult calculateTax(List<TDeliveryDetail> tDeliveryDetailList) {
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal totalAmountIncludingTax = BigDecimal.ZERO;

        for (TDeliveryDetail detail : tDeliveryDetailList) {
            OrderDetailCalculationResult result = calculateAmounts(detail);
            taxAmount = taxAmount.add(result.getTaxAmount());
            totalAmountIncludingTax = totalAmountIncludingTax.add(result.getAmountIncludingTax());
        }

        return new TaxCalculationResult(totalAmountIncludingTax, taxAmount);
    }

    private static OrderDetailCalculationResult calculateAmounts(TDeliveryDetail detail) {
        BigDecimal goodsPrice = detail.getGoodsPrice();
        BigDecimal deliveryNum = detail.getDeliveryNum();
        BigDecimal totalAmount = goodsPrice.multiply(deliveryNum);

        TOrderDetail orderDetail = detail.getTOrderDetail();
        TaxType taxType = TaxType.purse(orderDetail.getTaxType());
        BigDecimal taxRate = orderDetail.getTaxRate();

        BigDecimal amountExcludingTax = BigDecimal.ZERO;
        BigDecimal taxAmount = BigDecimal.ZERO;
        BigDecimal amountIncludingTax = BigDecimal.ZERO;

        switch (Objects.requireNonNull(taxType)) {
            case TAX_EXCLUDE:
                amountExcludingTax = totalAmount;
                taxAmount = amountExcludingTax.multiply(taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN))
                        .setScale(0, RoundingMode.DOWN);
                amountIncludingTax = amountExcludingTax.add(taxAmount);
                break;
            case TAXABLE_INCLUDE:
                amountIncludingTax = totalAmount;
                amountExcludingTax = amountIncludingTax.divide(
                        BigDecimal.ONE.add(taxRate.divide(BigDecimal.valueOf(100), 10, RoundingMode.DOWN)),
                        10,
                        RoundingMode.DOWN
                );
                taxAmount = amountIncludingTax.subtract(amountExcludingTax).setScale(0, RoundingMode.DOWN);
                break;
            case TAX_FREE:
                amountExcludingTax = totalAmount;
                amountIncludingTax = totalAmount;
                taxAmount = BigDecimal.ZERO;
                break;
            default:
                throw new IllegalArgumentException("未知の税区分です: " + taxType);
        }

        return new OrderDetailCalculationResult(amountExcludingTax, taxAmount, amountIncludingTax);
    }

    public TDelivery recalculateTDelivery(TDelivery tDelivery, List<TDeliveryDetail> tDeliveryDetailList) {
        BigDecimal totalPrice = getTotalPrice.apply(tDeliveryDetailList);
        TaxCalculationResult taxResult = calculateTax(tDeliveryDetailList);
        tDelivery.setTotalPrice(totalPrice);
        tDelivery.setTaxTotalPrice(taxResult.getTaxAmount());
        DeliveryStatus deliveryStatus = recalculateDeliveryStatus(tDeliveryDetailList);
        if (deliveryStatus != null) {
            tDelivery.setDeliveryStatus(deliveryStatus.getValue());
        }
        return tDelivery;
    }

    private DeliveryStatus recalculateDeliveryStatus(List<TDeliveryDetail> tDeliveryDetailList) {
        List<DeliveryDetailStatus> deliveryDetailStatusList = tDeliveryDetailList.stream()
                .map(TDeliveryDetail::getDeliveryDetailStatus)
                .map(DeliveryDetailStatus::purse)
                .distinct()
                .collect(Collectors.toList());
        if (deliveryDetailStatusList.size() == 1) {
            DeliveryDetailStatus deliveryDetailStatus = deliveryDetailStatusList.get(0);
            switch (deliveryDetailStatus) {
                case NOT_INPUT:
                    return DeliveryStatus.NOT_INPUT;
                case WAIT_SHIPPING:
                    return DeliveryStatus.WAIT_SHIPPING;
                case DELIVERED:
                    return DeliveryStatus.DELIVERED;
                case CANCEL:
                    return DeliveryStatus.CANCEL;
                case RETURN:
                    return DeliveryStatus.RETURN;
                default:
                    return null;
            }
        }
        return null;
    }

    private static class OrderDetailCalculationResult {
        private final BigDecimal amountExcludingTax;
        private final BigDecimal taxAmount;
        private final BigDecimal amountIncludingTax;

        public OrderDetailCalculationResult(BigDecimal amountExcludingTax, BigDecimal taxAmount, BigDecimal amountIncludingTax) {
            this.amountExcludingTax = amountExcludingTax;
            this.taxAmount = taxAmount;
            this.amountIncludingTax = amountIncludingTax;
        }

        public BigDecimal getAmountExcludingTax() {
            return amountExcludingTax;
        }

        public BigDecimal getTaxAmount() {
            return taxAmount;
        }

        public BigDecimal getAmountIncludingTax() {
            return amountIncludingTax;
        }
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
