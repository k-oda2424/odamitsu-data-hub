package jp.co.oda32.dto.purchase;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QuoteImportSupplierMatchRequest {
    @NotBlank(message = "仕入先コードは必須です")
    private String supplierCode;
    @NotNull(message = "仕入先番号は必須です")
    private Integer supplierNo;
}
