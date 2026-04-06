package jp.co.oda32.dto.estimate;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class EstimateGoodsSearchResponse {
    private Integer goodsNo;
    private String goodsCode;
    private String goodsName;
    private String specification;
    private BigDecimal purchasePrice;
    private BigDecimal containNum;
    private BigDecimal changeContainNum;
    private BigDecimal nowGoodsPrice;
    private String pricePlanInfo;
    private String janCode;
    private String source;
    private Integer purchasePriceChangePlanNo;
}
