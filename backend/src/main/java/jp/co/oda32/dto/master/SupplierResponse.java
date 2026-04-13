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
    private String supplierNameDisplay;
    private Integer shopNo;
    private Integer standardLeadTime;
    private Integer paymentSupplierNo;
    private String paymentSupplierName;

    public static SupplierResponse from(MSupplier s) {
        return SupplierResponse.builder()
                .supplierNo(s.getSupplierNo())
                .supplierCode(s.getSupplierCode())
                .supplierName(s.getSupplierName())
                .supplierNameDisplay(s.getSupplierNameDisplay())
                .shopNo(s.getShopNo())
                .standardLeadTime(s.getStandardLeadTime())
                .paymentSupplierNo(s.getPaymentSupplierNo())
                .paymentSupplierName(s.getPaymentSupplier() != null ? s.getPaymentSupplier().getPaymentSupplierName() : null)
                .build();
    }
}
