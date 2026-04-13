package jp.co.oda32.dto.purchase;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class SupplierQuoteHistoryResponse {
    private Integer quoteImportDetailId;
    private LocalDate quoteDate;
    private LocalDate effectiveDate;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private BigDecimal oldBoxPrice;
    private BigDecimal newBoxPrice;
    private String fileName;
    private String changeReason;
    private String supplierName;
    private boolean latest;
}
