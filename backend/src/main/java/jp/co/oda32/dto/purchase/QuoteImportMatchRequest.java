package jp.co.oda32.dto.purchase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuoteImportMatchRequest {
    @NotBlank(message = "商品コードは必須です")
    private String goodsCode;
    @NotNull(message = "商品番号は必須です")
    private Integer goodsNo;
}
