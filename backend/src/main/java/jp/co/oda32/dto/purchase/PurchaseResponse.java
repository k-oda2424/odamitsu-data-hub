package jp.co.oda32.dto.purchase;

import jp.co.oda32.domain.model.purchase.TPurchase;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PurchaseResponse {
    private Integer purchaseNo;
    private Integer shopNo;
    private Integer supplierNo;
    private BigDecimal purchaseAmount;
    private BigDecimal includeTaxAmount;
    private BigDecimal taxAmount;
    private LocalDate purchaseDate;

    public static PurchaseResponse from(TPurchase p) {
        return PurchaseResponse.builder()
                .purchaseNo(p.getPurchaseNo())
                .shopNo(p.getShopNo())
                .supplierNo(p.getSupplierNo())
                .purchaseAmount(p.getPurchaseAmount())
                .includeTaxAmount(p.getIncludeTaxAmount())
                .taxAmount(p.getTaxAmount())
                .purchaseDate(p.getPurchaseDate())
                .build();
    }
}
