package jp.co.oda32.dto.purchase;

import jp.co.oda32.domain.model.purchase.TSendOrder;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class SendOrderResponse {
    private Integer sendOrderNo;
    private Integer shopNo;
    private Integer supplierNo;
    private String supplierName;
    private LocalDateTime sendOrderDateTime;

    public static SendOrderResponse from(TSendOrder so) {
        return SendOrderResponse.builder()
                .sendOrderNo(so.getSendOrderNo())
                .shopNo(so.getShopNo())
                .supplierNo(so.getSupplierNo())
                .sendOrderDateTime(so.getSendOrderDateTime())
                .build();
    }
}
