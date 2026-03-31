package jp.co.oda32.dto.purchase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PurchasePriceChangePlanCreateRequest {
    @NotNull(message = "店舗番号は必須です")
    private Integer shopNo;
    @NotBlank(message = "商品コードは必須です")
    private String goodsCode;
    private String goodsName;
    private String janCode;
    @NotBlank(message = "仕入先コードは必須です")
    private String supplierCode;
    private BigDecimal beforePrice;
    @NotNull(message = "変更後価格は必須です")
    private BigDecimal afterPrice;
    @NotNull(message = "変更予定日は必須です")
    private LocalDate changePlanDate;
    @NotBlank(message = "変更理由は必須です")
    @Pattern(regexp = "^(PU|PD|ES)$", message = "変更理由はPU/PD/ESのいずれかです")
    private String changeReason;
    private BigDecimal changeContainNum;
    private Integer partnerNo;
    private Integer destinationNo;
}
