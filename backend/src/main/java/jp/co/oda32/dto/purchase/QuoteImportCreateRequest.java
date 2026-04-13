package jp.co.oda32.dto.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class QuoteImportCreateRequest {
    @NotNull(message = "店舗番号は必須です")
    private Integer shopNo;
    private String supplierName;
    private String fileName;
    private LocalDate quoteDate;
    private LocalDate effectiveDate;
    private String changeReason;
    private String priceType;

    @NotEmpty(message = "明細は1件以上必要です")
    @Valid
    private List<Detail> details;

    @Data
    public static class Detail {
        private Integer rowNo;
        private String janCode;
        @NotBlank(message = "商品名は必須です")
        private String quoteGoodsName;
        private String quoteGoodsCode;
        private String specification;
        private Integer quantityPerCase;
        private BigDecimal oldPrice;
        private BigDecimal newPrice;
        private BigDecimal oldBoxPrice;
        private BigDecimal newBoxPrice;
    }
}
