package jp.co.oda32.dto.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class PurchasePriceChangePlanBulkRequest {
    @NotNull(message = "店舗番号は必須です")
    private Integer shopNo;
    @NotBlank(message = "仕入先コードは必須です")
    private String supplierCode;
    @NotNull(message = "変更予定日は必須です")
    private LocalDate changePlanDate;
    @NotBlank(message = "変更理由は必須です")
    @Pattern(regexp = "^(PU|PD|ES)$", message = "変更理由はPU/PD/ESのいずれかです")
    private String changeReason;
    private Integer partnerNo;
    private Integer destinationNo;

    @NotEmpty(message = "明細は1件以上必要です")
    @Valid
    private List<Detail> details;

    @Data
    public static class Detail {
        @NotBlank(message = "商品コードは必須です")
        private String goodsCode;
        private String goodsName;
        private BigDecimal beforePrice;
        @NotNull(message = "変更後価格は必須です")
        private BigDecimal afterPrice;
        private BigDecimal changeContainNum;
    }
}
