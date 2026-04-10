package jp.co.oda32.dto.master;

import jp.co.oda32.domain.model.master.MPaymentSupplier;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentSupplierResponse {
    private Integer paymentSupplierNo;
    private String paymentSupplierCode;
    private String paymentSupplierName;
    private Integer shopNo;

    public static PaymentSupplierResponse from(MPaymentSupplier p) {
        return PaymentSupplierResponse.builder()
                .paymentSupplierNo(p.getPaymentSupplierNo())
                .paymentSupplierCode(p.getPaymentSupplierCode())
                .paymentSupplierName(p.getPaymentSupplierName())
                .shopNo(p.getShopNo())
                .build();
    }
}
