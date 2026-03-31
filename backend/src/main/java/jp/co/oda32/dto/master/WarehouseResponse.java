package jp.co.oda32.dto.master;

import jp.co.oda32.domain.model.master.MWarehouse;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WarehouseResponse {
    private Integer warehouseNo;
    private String warehouseName;
    private Integer companyNo;

    public static WarehouseResponse from(MWarehouse w) {
        return WarehouseResponse.builder()
                .warehouseNo(w.getWarehouseNo())
                .warehouseName(w.getWarehouseName())
                .companyNo(w.getCompanyNo())
                .build();
    }
}
