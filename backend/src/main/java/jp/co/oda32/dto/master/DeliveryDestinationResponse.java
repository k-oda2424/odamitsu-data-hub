package jp.co.oda32.dto.master;

import jp.co.oda32.domain.model.order.MDeliveryDestination;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DeliveryDestinationResponse {
    private Integer destinationNo;
    private String destinationName;
    private String destinationCode;
    private Integer partnerNo;

    public static DeliveryDestinationResponse from(MDeliveryDestination dest) {
        return DeliveryDestinationResponse.builder()
                .destinationNo(dest.getDestinationNo())
                .destinationName(dest.getDestinationName())
                .destinationCode(dest.getDestinationCode())
                .partnerNo(dest.getPartnerNo())
                .build();
    }
}
