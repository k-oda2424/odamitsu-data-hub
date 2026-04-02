package jp.co.oda32.dto.purchase;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class SupplierQuoteDataResponse {
    private String janCode;
    private String quoteGoodsName;
    private String specification;
    private Integer quantityPerCase;
    private BigDecimal currentPrice;
    private BigDecimal currentBoxPrice;
    private LocalDate effectiveDate;
    private String supplierName;
    private String supplierCode;
    private Integer quoteImportDetailId;
}
