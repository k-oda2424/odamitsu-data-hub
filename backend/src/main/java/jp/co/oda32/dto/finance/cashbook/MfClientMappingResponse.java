package jp.co.oda32.dto.finance.cashbook;

import jp.co.oda32.domain.model.finance.MMfClientMapping;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MfClientMappingResponse {
    private Integer id;
    private String alias;
    private String mfClientName;

    public static MfClientMappingResponse from(MMfClientMapping e) {
        return MfClientMappingResponse.builder()
                .id(e.getId())
                .alias(e.getAlias())
                .mfClientName(e.getMfClientName())
                .build();
    }
}
