package jp.co.oda32.dto.goods;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PartnerGoodsUpdateRequest {
    @NotBlank(message = "商品名は必須です")
    private String goodsName;
    private String keyword;
    @Positive(message = "売価は正の値を入力してください")
    private BigDecimal goodsPrice;
}
