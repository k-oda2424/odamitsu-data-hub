package jp.co.oda32.dto.goods;

import jp.co.oda32.domain.model.order.IOrderDetailEntity;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class OrderHistoryResponse {
    private LocalDateTime orderDateTime;
    private String goodsCode;
    private String goodsName;
    private BigDecimal goodsPrice;
    private BigDecimal goodsNum;

    public static OrderHistoryResponse from(IOrderDetailEntity entity) {
        return OrderHistoryResponse.builder()
                .orderDateTime(entity.getOrderDateTime())
                .goodsCode(entity.getGoodsCode())
                .goodsName(entity.getGoodsName())
                .goodsPrice(entity.getGoodsPrice())
                .goodsNum(entity.getGoodsNum())
                .build();
    }
}
