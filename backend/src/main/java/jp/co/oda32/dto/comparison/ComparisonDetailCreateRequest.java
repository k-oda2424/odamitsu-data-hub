package jp.co.oda32.dto.comparison;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ComparisonDetailCreateRequest {
    private Integer goodsNo;
    private String goodsCode;
    @NotBlank
    private String goodsName;
    private String specification;
    private BigDecimal purchasePrice;
    private BigDecimal proposedPrice;
    private BigDecimal containNum;
    private String detailNote;
    private int displayOrder;
    private Integer supplierNo;
}
