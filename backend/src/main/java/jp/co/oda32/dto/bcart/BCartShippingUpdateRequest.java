package jp.co.oda32.dto.bcart;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jp.co.oda32.constant.BcartShipmentStatus;

import java.time.LocalDate;

public record BCartShippingUpdateRequest(
        @NotNull
        @JsonProperty("bCartLogisticsId")
        Long bCartLogisticsId,

        @Size(max = 255)
        String deliveryCode,

        /** YYYY-MM-DD */
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
        LocalDate shipmentDate,

        @Size(max = 65535)
        String memo,

        /** UIで編集された場合の新しい値。adminMessageDirty=false のときは無視される。 */
        @Size(max = 65535)
        String adminMessage,

        /** true のときのみ adminMessage を上書きする。false/null 時は既存値を保持。 */
        boolean adminMessageDirty,

        @NotNull
        BcartShipmentStatus shipmentStatus
) {
}
