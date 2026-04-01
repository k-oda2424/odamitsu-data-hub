package jp.co.oda32.dto.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class QuoteImportCreateNewRequest {

    @Valid
    @NotNull
    private GoodsInfo goods;

    @Valid
    @NotNull
    private SalesGoodsInfo salesGoods;

    @Data
    public static class GoodsInfo {
        @NotBlank(message = "商品名は必須です")
        private String goodsName;
        private String janCode;
        private Integer makerNo;
        private String specification;
        private BigDecimal caseContainNum;
        private boolean applyReducedTaxRate;
    }

    @Data
    public static class SalesGoodsInfo {
        @NotBlank(message = "商品コードは必須です")
        private String goodsCode;
        private String goodsName;
        @NotNull(message = "標準仕入単価は必須です")
        private BigDecimal purchasePrice;
        @NotNull(message = "標準売単価は必須です")
        private BigDecimal goodsPrice;
    }
}
