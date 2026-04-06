package jp.co.oda32.dto.estimate;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class EstimateDetailCreateRequest {
    private Integer goodsNo;
    @NotNull(message = "商品コードは必須です")
    private String goodsCode;
    private String goodsName;
    private String specification;
    @NotNull(message = "見積単価は必須です")
    private BigDecimal goodsPrice;
    private BigDecimal purchasePrice;
    private BigDecimal containNum;
    private BigDecimal changeContainNum;
    private BigDecimal profitRate;
    private String detailNote;
    private int displayOrder;
}
