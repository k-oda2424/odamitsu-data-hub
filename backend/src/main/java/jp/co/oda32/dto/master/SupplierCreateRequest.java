package jp.co.oda32.dto.master;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SupplierCreateRequest {
    @NotBlank(message = "仕入先コードは必須です")
    private String supplierCode;

    @NotBlank(message = "仕入先名は必須です")
    private String supplierName;

    private String supplierNameDisplay;

    @NotNull(message = "店舗Noは必須です")
    private Integer shopNo;

    private Integer standardLeadTime;
}
