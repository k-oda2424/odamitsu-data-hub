package jp.co.oda32.dto.bcart;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record BCartShippingInputResponse(
        @JsonProperty("bCartLogisticsId") Long bCartLogisticsId,
        String partnerCode,
        String partnerName,
        String deliveryCompName,
        String deliveryCode,
        String shipmentDate,
        String memo,
        String adminMessage,
        String shipmentStatus,
        List<String> goodsInfo,
        /** SMILE 連携後の処理連番 (psn_updated=true のレコードのみ) */
        List<Long> smileSerialNoList,
        @JsonProperty("bCartCsvExported") boolean bCartCsvExported
) {
}
