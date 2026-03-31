package jp.co.oda32.dto.estimate;

import jp.co.oda32.domain.model.estimate.TEstimate;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

@Data
@Builder
public class EstimateResponse {
    private Integer estimateNo;
    private Integer shopNo;
    private Integer partnerNo;
    private String partnerCode;
    private String partnerName;
    private Integer destinationNo;
    private LocalDate estimateDate;
    private LocalDate priceChangeDate;
    private String estimateStatus;
    private String note;

    public static EstimateResponse from(TEstimate e) {
        String partnerCode = null;
        String partnerName = null;
        if (e.getMPartner() != null) {
            partnerCode = e.getMPartner().getPartnerCode();
            partnerName = e.getMPartner().getPartnerName();
            if (partnerName == null && e.getCompany() != null) {
                partnerName = e.getCompany().getCompanyName();
            }
        } else if (e.getCompany() != null) {
            partnerName = e.getCompany().getCompanyName();
        }

        return EstimateResponse.builder()
                .estimateNo(e.getEstimateNo())
                .shopNo(e.getShopNo())
                .partnerNo(e.getPartnerNo())
                .partnerCode(partnerCode)
                .partnerName(partnerName)
                .destinationNo(e.getDestinationNo())
                .estimateDate(e.getEstimateDate())
                .priceChangeDate(e.getPriceChangeDate())
                .estimateStatus(e.getEstimateStatus())
                .note(e.getNote())
                .build();
    }
}
