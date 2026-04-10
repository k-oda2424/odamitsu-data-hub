package jp.co.oda32.dto.master;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class WarehouseCreateRequest {
    @NotBlank(message = "倉庫名は必須です")
    private String warehouseName;

    @NotNull(message = "会社Noは必須です")
    private Integer companyNo;
}
