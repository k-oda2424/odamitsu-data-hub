package jp.co.oda32.dto.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 適正在庫計算用DTO
 *
 * @author k_oda
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppropriateStockEntity {
    private LocalDate moveDate;
    private Integer goodsNo;
    private BigDecimal goodsNum;
    private int leadTime;
    private Integer shopNo;
    private Integer warehouseNo;
}
