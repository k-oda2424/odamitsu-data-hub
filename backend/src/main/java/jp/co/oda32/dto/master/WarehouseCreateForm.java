package jp.co.oda32.dto.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 倉庫登録DTO
 *
 * @author k_oda
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseCreateForm {
    private Integer warehouseNo;
    private String warehouseName;
    private Integer companyNo;
}
