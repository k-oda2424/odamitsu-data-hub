package jp.co.oda32.dto.goods;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class GoodsCreateRequest {
    @NotBlank
    private String goodsName;
    private String janCode;
    private Integer makerNo;
    private String keyword;
    private Integer taxCategory;
    private String specification;
    private BigDecimal caseContainNum;
    private boolean applyReducedTaxRate;
}
