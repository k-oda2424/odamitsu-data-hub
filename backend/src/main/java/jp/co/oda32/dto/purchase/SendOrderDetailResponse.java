package jp.co.oda32.dto.purchase;

import jp.co.oda32.domain.model.purchase.TSendOrderDetail;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class SendOrderDetailResponse {
    private Integer sendOrderNo;
    private Integer sendOrderDetailNo;
    private Integer shopNo;
    private Integer supplierNo;
    private String supplierName;
    private Integer warehouseNo;
    private String warehouseName;
    private LocalDateTime sendOrderDateTime;
    private LocalDate desiredDeliveryDate;
    private Integer goodsNo;
    private String goodsCode;
    private String goodsName;
    private BigDecimal goodsPrice;
    private Integer sendOrderNum;
    private BigDecimal sendOrderCaseNum;
    private Integer containNum;
    private BigDecimal subtotal;
    private LocalDate arrivePlanDate;
    private LocalDate arrivedDate;
    private BigDecimal arrivedNum;
    private String sendOrderDetailStatus;

    public static SendOrderDetailResponse from(TSendOrderDetail d) {
        BigDecimal subtotal = d.getGoodsPrice() != null && d.getSendOrderNum() != null
                ? d.getGoodsPrice().multiply(new BigDecimal(d.getSendOrderNum()))
                : BigDecimal.ZERO;
        return SendOrderDetailResponse.builder()
                .sendOrderNo(d.getSendOrderNo())
                .sendOrderDetailNo(d.getSendOrderDetailNo())
                .shopNo(d.getShopNo())
                .supplierNo(d.getTSendOrder() != null ? d.getTSendOrder().getSupplierNo() : null)
                .supplierName(d.getTSendOrder() != null && d.getTSendOrder().getMSupplier() != null
                        ? d.getTSendOrder().getMSupplier().getSupplierName() : null)
                .warehouseNo(d.getWarehouseNo())
                .warehouseName(d.getTSendOrder() != null && d.getTSendOrder().getMWarehouse() != null
                        ? d.getTSendOrder().getMWarehouse().getWarehouseName() : null)
                .sendOrderDateTime(d.getTSendOrder() != null ? d.getTSendOrder().getSendOrderDateTime() : null)
                .desiredDeliveryDate(d.getTSendOrder() != null ? d.getTSendOrder().getDesiredDeliveryDate() : null)
                .goodsNo(d.getGoodsNo())
                .goodsCode(d.getGoodsCode())
                .goodsName(d.getGoodsName())
                .goodsPrice(d.getGoodsPrice())
                .sendOrderNum(d.getSendOrderNum())
                .sendOrderCaseNum(d.getSendOrderCaseNum())
                .containNum(d.getContainNum())
                .subtotal(subtotal)
                .arrivePlanDate(d.getArrivePlanDate())
                .arrivedDate(d.getArrivedDate())
                .arrivedNum(d.getArrivedNum())
                .sendOrderDetailStatus(d.getSendOrderDetailStatus())
                .build();
    }
}
