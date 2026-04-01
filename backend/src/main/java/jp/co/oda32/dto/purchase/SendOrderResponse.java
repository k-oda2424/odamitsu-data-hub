package jp.co.oda32.dto.purchase;

import jp.co.oda32.domain.model.purchase.TSendOrder;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class SendOrderResponse {
    private Integer sendOrderNo;
    private Integer shopNo;
    private Integer supplierNo;
    private String supplierName;
    private Integer warehouseNo;
    private String warehouseName;
    private LocalDateTime sendOrderDateTime;
    private LocalDate desiredDeliveryDate;
    private String sendOrderStatus;
    private List<SendOrderDetailResponse> details;

    public static SendOrderResponse from(TSendOrder so) {
        List<SendOrderDetailResponse> details = null;
        if (so.getTSendOrderDetailList() != null) {
            details = so.getTSendOrderDetailList().stream()
                    .map(SendOrderDetailResponse::from)
                    .collect(Collectors.toList());
        }
        return SendOrderResponse.builder()
                .sendOrderNo(so.getSendOrderNo())
                .shopNo(so.getShopNo())
                .supplierNo(so.getSupplierNo())
                .supplierName(so.getMSupplier() != null ? so.getMSupplier().getSupplierName() : null)
                .warehouseNo(so.getWarehouseNo())
                .warehouseName(so.getMWarehouse() != null ? so.getMWarehouse().getWarehouseName() : null)
                .sendOrderDateTime(so.getSendOrderDateTime())
                .desiredDeliveryDate(so.getDesiredDeliveryDate())
                .sendOrderStatus(so.getSendOrderStatus())
                .details(details)
                .build();
    }
}
