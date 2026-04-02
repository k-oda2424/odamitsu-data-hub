package jp.co.oda32.dto.finance;

import jp.co.oda32.domain.model.finance.MPartnerGroup;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PartnerGroupResponse {
    private Integer partnerGroupId;
    private String groupName;
    private Integer shopNo;
    private List<String> partnerCodes;

    public static PartnerGroupResponse from(MPartnerGroup group) {
        return PartnerGroupResponse.builder()
                .partnerGroupId(group.getPartnerGroupId())
                .groupName(group.getGroupName())
                .shopNo(group.getShopNo())
                .partnerCodes(group.getPartnerCodes())
                .build();
    }
}
