package jp.co.oda32.dto.bcart;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jp.co.oda32.constant.BcartShipmentStatus;

import java.util.List;

public record BCartShippingBulkStatusRequest(
        @NotEmpty
        @Size(max = 1000, message = "一括更新は 1000 件以内に絞ってください")
        @JsonProperty("bCartLogisticsIds")
        List<Long> bCartLogisticsIds,

        @NotNull
        BcartShipmentStatus shipmentStatus
) {
}
