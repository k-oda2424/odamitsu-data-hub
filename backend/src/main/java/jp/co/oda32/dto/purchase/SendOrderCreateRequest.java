package jp.co.oda32.dto.purchase;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class SendOrderCreateRequest {
    @NotNull(message = "店舗は必須です")
    private Integer shopNo;
    @NotNull(message = "倉庫は必須です")
    private Integer warehouseNo;
    @NotNull(message = "仕入先は必須です")
    private Integer supplierNo;
    @NotNull(message = "発注日時は必須です")
    private LocalDateTime sendOrderDateTime;
    private LocalDate desiredDeliveryDate;
    @Valid
    private List<SendOrderDetailCreateRequest> details;

    @Data
    public static class SendOrderDetailCreateRequest {
        @NotNull(message = "商品番号は必須です")
        private Integer goodsNo;
        private String goodsCode;
        private String goodsName;
        private java.math.BigDecimal goodsPrice;
        @NotNull(message = "発注数量は必須です")
        private Integer sendOrderNum;
        private Integer containNum;
    }
}
