package jp.co.oda32.dto.comparison;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ComparisonGroupCreateRequest {
    private Integer baseGoodsNo;
    private String baseGoodsCode;
    @NotBlank
    private String baseGoodsName;
    private String baseSpecification;
    private BigDecimal basePurchasePrice;
    private BigDecimal baseGoodsPrice;
    private BigDecimal baseContainNum;
    private int displayOrder;
    private String groupNote;
    @Valid
    private List<ComparisonDetailCreateRequest> details;
}
