package jp.co.oda32.dto.comparison;

import jp.co.oda32.domain.model.estimate.TComparisonDetail;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ComparisonDetailResponse {
    private Integer detailNo;
    private Integer goodsNo;
    private String goodsCode;
    private String goodsName;
    private String specification;
    private BigDecimal purchasePrice;
    private BigDecimal proposedPrice;
    private BigDecimal containNum;
    private BigDecimal profitRate;
    private String detailNote;
    private int displayOrder;
    private Integer supplierNo;

    public static ComparisonDetailResponse from(TComparisonDetail d) {
        return ComparisonDetailResponse.builder()
                .detailNo(d.getDetailNo())
                .goodsNo(d.getGoodsNo())
                .goodsCode(d.getGoodsCode())
                .goodsName(d.getGoodsName())
                .specification(d.getSpecification())
                .purchasePrice(d.getPurchasePrice())
                .proposedPrice(d.getProposedPrice())
                .containNum(d.getContainNum())
                .profitRate(d.getProfitRate())
                .detailNote(d.getDetailNote())
                .displayOrder(d.getDisplayOrder())
                .supplierNo(d.getSupplierNo())
                .build();
    }
}
