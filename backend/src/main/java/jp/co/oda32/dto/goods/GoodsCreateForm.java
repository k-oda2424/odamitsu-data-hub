package jp.co.oda32.dto.goods;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品登録DTO
 *
 * @author k_oda
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GoodsCreateForm {
    private Integer goodsNo;
    @NotBlank
    private String goodsName;
    @NotNull
    private Integer makerNo;
    @NotBlank
    @Size(min = 8, max = 13)
    private String janCode;
    private BigDecimal caseContainNum;
    private boolean applyReducedTaxRateFlg;
    private String keyword;
    private String specification;
}
