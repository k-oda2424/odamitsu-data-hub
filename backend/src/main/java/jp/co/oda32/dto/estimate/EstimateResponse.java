package jp.co.oda32.dto.estimate;

import jp.co.oda32.domain.model.estimate.TEstimate;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class EstimateResponse {
    private Integer estimateNo;
    private Integer shopNo;
    private Integer partnerNo;
    private String partnerCode;
    private String partnerName;
    private Integer destinationNo;
    private String destinationName;
    private LocalDate estimateDate;
    private LocalDate priceChangeDate;
    private String estimateStatus;
    private String note;
    private String requirement;
    private String recipientName;
    private String proposalMessage;
    private Boolean isIncludeTaxDisplay;
    private List<EstimateDetailResponse> details;

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

        String destinationName = null;
        if (e.getMDeliveryDestination() != null) {
            destinationName = e.getMDeliveryDestination().getDestinationName();
        }

        return EstimateResponse.builder()
                .estimateNo(e.getEstimateNo())
                .shopNo(e.getShopNo())
                .partnerNo(e.getPartnerNo())
                .partnerCode(partnerCode)
                .partnerName(partnerName)
                .destinationNo(e.getDestinationNo())
                .destinationName(destinationName)
                .estimateDate(e.getEstimateDate())
                .priceChangeDate(e.getPriceChangeDate())
                .estimateStatus(e.getEstimateStatus())
                .note(e.getNote())
                .requirement(e.getRequirement())
                .recipientName(e.getRecipientName())
                .proposalMessage(e.getProposalMessage())
                .build();
    }

    public static EstimateResponse fromWithDetails(TEstimate e) {
        EstimateResponse resp = from(e);
        resp.setIsIncludeTaxDisplay(e.isIncludeTaxDisplay());
        List<EstimateDetailResponse> details = e.getTEstimateDetailList().stream()
                .sorted(java.util.Comparator.comparingInt((jp.co.oda32.domain.model.estimate.TEstimateDetail d) -> d.getDisplayOrder())
                        .thenComparing(d -> d.getGoodsCode() != null ? d.getGoodsCode() : ""))
                .map(EstimateDetailResponse::from)
                .toList();
        resp.setDetails(details);
        return resp;
    }
}
