package jp.co.oda32.dto.purchase;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseListResponse {
    private List<PurchaseHeaderResponse> rows;
    private Summary summary;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Summary {
        private int totalRows;
        private BigDecimal totalAmountExcTax;
        private BigDecimal totalTaxAmount;
        private BigDecimal totalAmountIncTax;
        private List<TaxRateBreakdown> byTaxRate;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TaxRateBreakdown {
        private BigDecimal taxRate;
        private int rows;                     // 明細件数
        private BigDecimal amountExcTax;
        private BigDecimal taxAmount;
        private BigDecimal amountIncTax;
    }
}
