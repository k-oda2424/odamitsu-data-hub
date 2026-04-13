package jp.co.oda32.dto.estimate;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class CompareGoodsResponse {
    private Integer goodsNo;
    private String goodsCode;
    private String goodsName;
    private String specification;
    private String janCode;
    private String makerName;
    private String supplierName;
    private Integer supplierNo;
    private BigDecimal purchasePrice;
    private BigDecimal nowGoodsPrice;
    private BigDecimal containNum;
    private BigDecimal changeContainNum;
    private String pricePlanInfo;
    private BigDecimal planAfterPrice;
}
