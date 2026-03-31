package jp.co.oda32.dto.bcart;

import jp.co.oda32.domain.model.bcart.BCartOrder;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BCartShippingResponse {
    private Long orderId;
    private String compName;
    private String status;
    private String shippingNumber;

    public static BCartShippingResponse from(BCartOrder o) {
        return BCartShippingResponse.builder()
                .orderId(o.getId())
                .compName(o.getCustomerCompName())
                .status(o.getStatus())
                .build();
    }
}
