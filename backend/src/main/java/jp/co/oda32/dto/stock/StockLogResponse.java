package jp.co.oda32.dto.stock;

import jp.co.oda32.domain.model.stock.TStockLog;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class StockLogResponse {
    private Integer stockLogNo;
    private Integer goodsNo;
    private String goodsName;
    private Integer warehouseNo;
    private BigDecimal unit1StockNum;
    private BigDecimal unit2StockNum;
    private BigDecimal unit3StockNum;
    private String reason;
    private LocalDateTime moveTime;

    public static StockLogResponse from(TStockLog log) {
        return StockLogResponse.builder()
                .stockLogNo(log.getStockLogNo())
                .goodsNo(log.getGoodsNo())
                .goodsName(log.getMGoods() != null ? log.getMGoods().getGoodsName() : null)
                .warehouseNo(log.getWarehouseNo())
                .unit1StockNum(log.getUnit1StockNum())
                .unit2StockNum(log.getUnit2StockNum())
                .unit3StockNum(log.getUnit3StockNum())
                .reason(log.getReason())
                .moveTime(log.getMoveTime())
                .build();
    }
}
