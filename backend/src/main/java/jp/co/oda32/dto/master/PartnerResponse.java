package jp.co.oda32.dto.master;

import jp.co.oda32.domain.model.master.MPartner;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PartnerResponse {
    private Integer partnerNo;
    private String partnerName;
    private String partnerCode;
    private Integer shopNo;
    private Integer companyNo;

    public static PartnerResponse from(MPartner partner) {
        return PartnerResponse.builder()
                .partnerNo(partner.getPartnerNo())
                .partnerName(partner.getPartnerName())
                .partnerCode(partner.getPartnerCode())
                .shopNo(partner.getShopNo())
                .companyNo(partner.getCompanyNo())
                .build();
    }
}
