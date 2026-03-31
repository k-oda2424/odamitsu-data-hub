package jp.co.oda32.dto.goods;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class SalesGoodsUpdateRequest {
    @NotBlank
    private String goodsCode;
    private String goodsSkuCode;
    @NotBlank
    private String goodsName;
    private String keyword;
    private Integer categoryNo;
    @NotNull
    private Integer supplierNo;
    @NotNull
    private BigDecimal purchasePrice;
    @NotNull
    private BigDecimal goodsPrice;
    private BigDecimal referencePrice;
    private String catchphrase;
    private String goodsIntroduction;
    private String goodsDescription1;
    private String goodsDescription2;
    private String directShippingFlg;
    private Integer leadTime;
}
