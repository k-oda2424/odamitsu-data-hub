package jp.co.oda32.dto.purchase;

import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.model.purchase.TPurchase;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PurchaseHeaderResponse {
    private Integer purchaseNo;
    private String purchaseCode;
    private LocalDate purchaseDate;
    private Integer shopNo;
    private Integer supplierNo;
    private String supplierCode;
    private String supplierName;
    private BigDecimal purchaseAmount;
    private BigDecimal includeTaxAmount;
    private BigDecimal taxAmount;
    private BigDecimal taxRate;
    private String note;

    public static PurchaseHeaderResponse from(TPurchase p, MSupplier supplier) {
        return PurchaseHeaderResponse.builder()
                .purchaseNo(p.getPurchaseNo())
                .purchaseCode(p.getPurchaseCode())
                .purchaseDate(p.getPurchaseDate())
                .shopNo(p.getShopNo())
                .supplierNo(p.getSupplierNo())
                .supplierCode(supplier != null ? supplier.getSupplierCode() : null)
                .supplierName(supplier != null ? supplier.getSupplierName() : null)
                .purchaseAmount(p.getPurchaseAmount())
                .includeTaxAmount(p.getIncludeTaxAmount())
                .taxAmount(p.getTaxAmount())
                .taxRate(p.getTaxRate())
                .note(p.getNote())
                .build();
    }
}
