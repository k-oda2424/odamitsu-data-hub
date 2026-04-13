package jp.co.oda32.dto.purchase;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class PurchasePriceCreateRequest {
    @NotNull(message = "店舗は必須です")
    private Integer shopNo;
    @NotNull(message = "商品は必須です")
    private Integer goodsNo;
    @NotNull(message = "仕入先は必須です")
    private Integer supplierNo;
    private Integer partnerNo;
    private Integer destinationNo;
    @NotNull(message = "仕入単価は必須です")
    @Positive(message = "仕入単価は正の数を入力してください")
    private BigDecimal goodsPrice;
    private BigDecimal taxRate;
    private boolean includeTaxFlg;
    private LocalDate periodFrom;
    private LocalDate periodTo;
    private String note;
}
