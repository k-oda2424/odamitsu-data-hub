package jp.co.oda32.dto.purchase;

import jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PurchasePriceChangePlanResponse {
    private Integer purchasePriceChangePlanNo;
    private Integer shopNo;
    private String goodsCode;
    private String goodsName;
    private String janCode;
    private String supplierCode;
    private String supplierName;
    private BigDecimal beforePrice;
    private BigDecimal afterPrice;
    private LocalDate changePlanDate;
    private String changeReason;
    private BigDecimal changeContainNum;
    private Integer partnerNo;
    private Integer destinationNo;
    private boolean partnerPriceChangePlanCreated;
    private boolean purchasePriceReflect;

    public static PurchasePriceChangePlanResponse from(MPurchasePriceChangePlan plan) {
        var builder = PurchasePriceChangePlanResponse.builder()
                .purchasePriceChangePlanNo(plan.getPurchasePriceChangePlanNo())
                .shopNo(plan.getShopNo())
                .goodsCode(plan.getGoodsCode())
                .goodsName(plan.getGoodsName())
                .janCode(plan.getJanCode())
                .supplierCode(plan.getSupplierCode())
                .beforePrice(plan.getBeforePrice())
                .afterPrice(plan.getAfterPrice())
                .changePlanDate(plan.getChangePlanDate())
                .changeReason(plan.getChangeReason())
                .changeContainNum(plan.getChangeContainNum())
                .partnerNo(plan.getPartnerNo())
                .destinationNo(plan.getDestinationNo())
                .partnerPriceChangePlanCreated(plan.isPartnerPriceChangePlanCreated())
                .purchasePriceReflect(plan.isPurchasePriceReflect());

        if (plan.getMSupplier() != null) {
            builder.supplierName(plan.getMSupplier().getSupplierName());
        }
        return builder.build();
    }
}
