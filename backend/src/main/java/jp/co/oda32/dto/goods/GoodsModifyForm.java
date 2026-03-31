package jp.co.oda32.dto.goods;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品修正DTO
 *
 * @author k_oda
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsModifyForm {
    private Integer goodsNo;
    @NotBlank
    private String goodsName;
    @NotNull
    private Integer makerNo;
    private String janCode;
    private BigDecimal caseContainNum;
    private String keyword;
    private String action;
    private String specification;
    private boolean applyReducedTaxRateFlg;
}
