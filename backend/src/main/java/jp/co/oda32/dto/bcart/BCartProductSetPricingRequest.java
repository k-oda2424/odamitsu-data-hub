package jp.co.oda32.dto.bcart;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import lombok.Data;

import java.math.BigDecimal;

/**
 * B-CART商品セットの価格・配送サイズ更新リクエスト
 *
 * 両フィールドとも optional。null は未変更扱い。
 */
@Data
public class BCartProductSetPricingRequest {
    @DecimalMin(value = "0", inclusive = true, message = "unitPrice must be >= 0")
    private BigDecimal unitPrice;

    @DecimalMin(value = "0", inclusive = true, message = "shippingSize must be >= 0")
    @DecimalMax(value = "1", inclusive = true, message = "shippingSize must be <= 1")
    private BigDecimal shippingSize;
}
