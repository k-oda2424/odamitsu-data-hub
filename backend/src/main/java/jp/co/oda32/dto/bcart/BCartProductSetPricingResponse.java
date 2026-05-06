package jp.co.oda32.dto.bcart;

import com.fasterxml.jackson.annotation.JsonProperty;
import jp.co.oda32.domain.model.bcart.BCartProductSets;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 価格・配送サイズ更新後の B-CART 商品セットレスポンス DTO。
 * Entity を直接公開しないために用意。
 */
@Data
@Builder
public class BCartProductSetPricingResponse {
    private Long id;
    private Long productId;
    private String productNo;
    private String name;
    private BigDecimal unitPrice;
    private BigDecimal shippingSize;
    @JsonProperty("bCartPriceReflected")
    private boolean bCartPriceReflected;
    private Date updatedAt;

    public static BCartProductSetPricingResponse from(BCartProductSets s) {
        return BCartProductSetPricingResponse.builder()
                .id(s.getId())
                .productId(s.getProductId())
                .productNo(s.getProductNo())
                .name(s.getName())
                .unitPrice(s.getUnitPrice())
                .shippingSize(s.getShippingSize())
                .bCartPriceReflected(s.isBCartPriceReflected())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}
