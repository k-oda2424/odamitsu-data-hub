package jp.co.oda32.dto.estimate;

import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EstimateDetailResponse {
    private Integer estimateDetailNo;
    private Integer goodsNo;
    private String goodsCode;
    private String goodsName;
    private String specification;
    private BigDecimal goodsPrice;
    private BigDecimal containNum;
    private BigDecimal changeContainNum;
    private BigDecimal estimateCaseNum;
    private BigDecimal estimateNum;
    private BigDecimal purchasePrice;
    private BigDecimal profitRate;
    private String detailNote;
    private int displayOrder;
    private String pricePlanInfo;

    public static EstimateDetailResponse from(TEstimateDetail d) {
        return EstimateDetailResponse.builder()
                .estimateDetailNo(d.getEstimateDetailNo())
                .goodsNo(d.getGoodsNo())
                .goodsCode(d.getGoodsCode())
                .goodsName(d.getGoodsName())
                .specification(d.getSpecification())
                .goodsPrice(d.getGoodsPrice())
                .containNum(d.getChangeContainNum() != null ? d.getChangeContainNum() : d.getContainNum())
                .changeContainNum(d.getChangeContainNum())
                .estimateCaseNum(d.getEstimateCaseNum())
                .estimateNum(d.getEstimateNum())
                .purchasePrice(d.getPurchasePrice())
                .profitRate(d.getProfitRate())
                .detailNote(d.getDetailNote())
                .displayOrder(d.getDisplayOrder())
                .pricePlanInfo(null)
                .build();
    }
}
