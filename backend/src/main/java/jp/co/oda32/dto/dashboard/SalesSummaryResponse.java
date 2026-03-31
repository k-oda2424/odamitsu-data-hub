package jp.co.oda32.dto.dashboard;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class SalesSummaryResponse {
    private Integer shopNo;
    private String month;
    private BigDecimal totalSales;
}
