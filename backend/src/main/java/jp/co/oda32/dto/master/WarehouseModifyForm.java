package jp.co.oda32.dto.master;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 倉庫更新DTO
 *
 * @author k_oda
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarehouseModifyForm {
    private Integer warehouseNo;
    private String warehouseName;
    private String action;
}
