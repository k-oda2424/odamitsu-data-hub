package jp.co.oda32.dto.master;

import jp.co.oda32.domain.model.master.MMaker;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MakerResponse {
    private Integer makerNo;
    private String makerName;

    public static MakerResponse from(MMaker m) {
        return MakerResponse.builder()
                .makerNo(m.getMakerNo())
                .makerName(m.getMakerName())
                .build();
    }
}
