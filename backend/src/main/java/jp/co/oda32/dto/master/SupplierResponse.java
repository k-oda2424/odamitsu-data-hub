package jp.co.oda32.dto.master;

import jp.co.oda32.domain.model.master.MSupplier;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SupplierResponse {
    private Integer supplierNo;
    private String supplierCode;
    private String supplierName;
    private Integer shopNo;

    public static SupplierResponse from(MSupplier s) {
        return SupplierResponse.builder()
                .supplierNo(s.getSupplierNo())
                .supplierCode(s.getSupplierCode())
                .supplierName(s.getSupplierName())
                .shopNo(s.getShopNo())
                .build();
    }
}
