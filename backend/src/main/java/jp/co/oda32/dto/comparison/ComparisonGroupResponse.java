package jp.co.oda32.dto.comparison;

import jp.co.oda32.domain.model.estimate.TComparisonGroup;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class ComparisonGroupResponse {
    private Integer groupNo;
    private Integer baseGoodsNo;
    private String baseGoodsCode;
    private String baseGoodsName;
    private String baseSpecification;
    private BigDecimal basePurchasePrice;
    private BigDecimal baseGoodsPrice;
    private BigDecimal baseContainNum;
    private int displayOrder;
    private String groupNote;
    private List<ComparisonDetailResponse> details;

    public static ComparisonGroupResponse from(TComparisonGroup g) {
        return ComparisonGroupResponse.builder()
                .groupNo(g.getGroupNo())
                .baseGoodsNo(g.getBaseGoodsNo())
                .baseGoodsCode(g.getBaseGoodsCode())
                .baseGoodsName(g.getBaseGoodsName())
                .baseSpecification(g.getBaseSpecification())
                .basePurchasePrice(g.getBasePurchasePrice())
                .baseGoodsPrice(g.getBaseGoodsPrice())
                .baseContainNum(g.getBaseContainNum())
                .displayOrder(g.getDisplayOrder())
                .groupNote(g.getGroupNote())
                .details(g.getComparisonDetailList().stream()
                        .map(ComparisonDetailResponse::from)
                        .collect(Collectors.toList()))
                .build();
    }
}
