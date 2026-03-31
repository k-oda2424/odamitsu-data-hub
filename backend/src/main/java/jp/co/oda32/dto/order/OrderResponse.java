package jp.co.oda32.dto.order;

import jp.co.oda32.domain.model.order.TOrder;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderResponse {
    private Integer orderNo;
    private Integer shopNo;
    private Integer companyNo;
    private String companyName;
    private String orderStatus;
    private LocalDateTime orderDateTime;
    private BigDecimal totalPrice;
    private BigDecimal taxTotalPrice;

    public static OrderResponse from(TOrder o) {
        return OrderResponse.builder()
                .orderNo(o.getOrderNo())
                .shopNo(o.getShopNo())
                .companyNo(o.getCompanyNo())
                .companyName(o.getCompanyName())
                .orderStatus(o.getOrderStatus())
                .orderDateTime(o.getOrderDateTime())
                .totalPrice(o.getTotalPrice())
                .taxTotalPrice(o.getTaxTotalPrice())
                .build();
    }
}
