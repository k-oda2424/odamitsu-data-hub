package jp.co.oda32.dto.stock;

import jp.co.oda32.domain.model.stock.TStock;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class StockResponse {
    private Integer goodsNo;
    private Integer warehouseNo;
    private String goodsName;
    private String warehouseName;
    private Integer companyNo;
    private Integer shopNo;
    private Integer unit1No;
    private BigDecimal unit1StockNum;
    private Integer unit2No;
    private BigDecimal unit2StockNum;
    private Integer unit3No;
    private BigDecimal unit3StockNum;
    private Integer leadTime;
    private BigDecimal enoughStock;

    public static StockResponse from(TStock stock) {
        return StockResponse.builder()
                .goodsNo(stock.getGoodsNo())
                .warehouseNo(stock.getWarehouseNo())
                .goodsName(stock.getMGoods() != null ? stock.getMGoods().getGoodsName() : null)
                .warehouseName(stock.getMWarehouse() != null ? stock.getMWarehouse().getWarehouseName() : null)
                .companyNo(stock.getCompanyNo())
                .shopNo(stock.getShopNo())
                .unit1No(stock.getUnit1No())
                .unit1StockNum(stock.getUnit1StockNum())
                .unit2No(stock.getUnit2No())
                .unit2StockNum(stock.getUnit2StockNum())
                .unit3No(stock.getUnit3No())
                .unit3StockNum(stock.getUnit3StockNum())
                .leadTime(stock.getLeadTime())
                .enoughStock(stock.getEnoughStock())
                .build();
    }
}
