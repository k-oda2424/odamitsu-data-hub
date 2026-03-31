package jp.co.oda32.dto.order;

import jp.co.oda32.domain.model.order.TOrderDetail;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderDetailResponse {
    private Integer orderNo;
    private Integer orderDetailNo;
    private Integer shopNo;
    private String companyName;
    private String partnerCode;
    private Integer partnerNo;
    private String orderDetailStatus;
    private LocalDateTime orderDateTime;
    private String goodsCode;
    private String goodsName;
    private BigDecimal goodsPrice;
    private BigDecimal orderNum;
    private BigDecimal cancelNum;
    private BigDecimal returnNum;
    private Integer unitContainNum;
    private Integer unitNum;
    private BigDecimal subtotal;
    private String slipNo;
    private LocalDate slipDate;
    private Integer deliveryNo;

    public static OrderDetailResponse from(TOrderDetail od) {
        BigDecimal effectiveQty = od.getOrderNum() != null
                ? od.getOrderNum().subtract(od.getCancelNum()).subtract(od.getReturnNum())
                : BigDecimal.ZERO;
        BigDecimal subtotal = od.getGoodsPrice() != null
                ? od.getGoodsPrice().multiply(effectiveQty)
                : BigDecimal.ZERO;

        return OrderDetailResponse.builder()
                .orderNo(od.getOrderNo())
                .orderDetailNo(od.getOrderDetailNo())
                .shopNo(od.getShopNo())
                .companyName(od.getTOrder() != null ? od.getTOrder().getCompanyName() : null)
                .partnerCode(od.getTOrder() != null ? od.getTOrder().getPartnerCode() : null)
                .partnerNo(od.getTOrder() != null ? od.getTOrder().getPartnerNo() : null)
                .orderDetailStatus(od.getOrderDetailStatus())
                .orderDateTime(od.getTOrder() != null ? od.getTOrder().getOrderDateTime() : null)
                .goodsCode(od.getGoodsCode())
                .goodsName(od.getGoodsName())
                .goodsPrice(od.getGoodsPrice())
                .orderNum(od.getOrderNum())
                .cancelNum(od.getCancelNum())
                .returnNum(od.getReturnNum())
                .unitContainNum(od.getUnitContainNum())
                .unitNum(od.getUnitNum())
                .subtotal(subtotal)
                .slipNo(od.getTDelivery() != null ? od.getTDelivery().getSlipNo() : null)
                .slipDate(od.getTDelivery() != null ? od.getTDelivery().getSlipDate() : null)
                .deliveryNo(od.getDeliveryNo())
                .build();
    }
}
