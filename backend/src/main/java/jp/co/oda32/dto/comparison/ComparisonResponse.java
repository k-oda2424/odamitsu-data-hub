package jp.co.oda32.dto.comparison;

import jp.co.oda32.domain.model.estimate.TEstimateComparison;
import jp.co.oda32.domain.model.estimate.TComparisonGroup;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class ComparisonResponse {
    private Integer comparisonNo;
    private Integer shopNo;
    private Integer partnerNo;
    private String partnerName;
    private Integer destinationNo;
    private String destinationName;
    private String comparisonDate;
    private String comparisonStatus;
    private Integer sourceEstimateNo;
    private String title;
    private String note;
    private int groupCount;
    private List<ComparisonGroupResponse> groups;

    public static ComparisonResponse from(TEstimateComparison e) {
        return ComparisonResponse.builder()
                .comparisonNo(e.getComparisonNo())
                .shopNo(e.getShopNo())
                .partnerNo(e.getPartnerNo())
                .partnerName(e.getMPartner() != null ? e.getMPartner().getPartnerName() : null)
                .destinationNo(e.getDestinationNo())
                .destinationName(e.getMDeliveryDestination() != null ? e.getMDeliveryDestination().getDestinationName() : null)
                .comparisonDate(e.getComparisonDate() != null ? e.getComparisonDate().toString() : null)
                .comparisonStatus(e.getComparisonStatus())
                .sourceEstimateNo(e.getSourceEstimateNo())
                .title(e.getTitle())
                .note(e.getNote())
                .groupCount(e.getComparisonGroupList() != null ? e.getComparisonGroupList().size() : 0)
                .groups(List.of())
                .build();
    }

    public static ComparisonResponse fromWithDetails(TEstimateComparison e) {
        ComparisonResponse resp = from(e);
        List<TComparisonGroup> groups = e.getComparisonGroupList();
        resp.setGroups(groups != null
                ? groups.stream().map(ComparisonGroupResponse::from).collect(Collectors.toList())
                : List.of());
        return resp;
    }
}
