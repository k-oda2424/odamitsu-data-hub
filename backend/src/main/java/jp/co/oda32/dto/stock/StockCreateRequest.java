package jp.co.oda32.dto.stock;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class StockCreateRequest {
    @NotNull
    private Integer goodsNo;
    @NotNull
    private Integer warehouseNo;
    @NotNull
    private Integer companyNo;
    private Integer shopNo;
    private Integer unit1No;
    private BigDecimal unit1StockNum;
    private Integer unit2No;
    private BigDecimal unit2StockNum;
    private Integer unit3No;
    private BigDecimal unit3StockNum;
    private Integer leadTime;
}
