package jp.co.oda32.domain.model.bcart.productSets;

import com.google.gson.annotations.SerializedName;
import lombok.*;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@AllArgsConstructor
public class Customs {
    @SerializedName("field_id")
    private Integer fieldId;

    @SerializedName("value")
    private String value;
}
