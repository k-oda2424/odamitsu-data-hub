package jp.co.oda32.dto.purchase;

import jp.co.oda32.domain.model.purchase.MPurchasePrice;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PurchasePriceResponse {
    private Integer purchasePriceNo;
    private Integer goodsNo;
    private String goodsCode;
    private String goodsName;
    private String makerName;
    private Integer supplierNo;
    private String supplierName;
    private String supplierCode;
    private Integer shopNo;
    private Integer partnerNo;
    private String partnerName;
    private Integer destinationNo;
    private String destinationName;
    private BigDecimal goodsPrice;
    private BigDecimal includeTaxGoodsPrice;
    private BigDecimal taxRate;
    private int taxCategory;
    private LocalDate lastPurchaseDate;

    public static PurchasePriceResponse from(MPurchasePrice pp) {
        var builder = PurchasePriceResponse.builder()
                .purchasePriceNo(pp.getPurchasePriceNo())
                .goodsNo(pp.getGoodsNo())
                .supplierNo(pp.getSupplierNo())
                .shopNo(pp.getShopNo())
                .partnerNo(pp.getPartnerNo())
                .destinationNo(pp.getDestinationNo())
                .goodsPrice(pp.getGoodsPrice())
                .includeTaxGoodsPrice(pp.getIncludeTaxGoodsPrice())
                .taxRate(pp.getTaxRate())
                .taxCategory(pp.getTaxCategory())
                .lastPurchaseDate(pp.getLastPurchaseDate());

        if (pp.getMGoods() != null) {
            builder.goodsName(pp.getMGoods().getGoodsName());
            if (pp.getMGoods().getMaker() != null) {
                builder.makerName(pp.getMGoods().getMaker().getMakerName());
            }
        }
        if (pp.getWSalesGoods() != null) {
            builder.goodsCode(pp.getWSalesGoods().getGoodsCode());
        }
        if (pp.getMSupplier() != null) {
            builder.supplierName(pp.getMSupplier().getSupplierName());
            builder.supplierCode(pp.getMSupplier().getSupplierCode());
        }
        if (pp.getMPartner() != null) {
            builder.partnerName(pp.getMPartner().getPartnerName());
        }
        if (pp.getMDeliveryDestination() != null) {
            builder.destinationName(pp.getMDeliveryDestination().getDestinationName());
        }
        return builder.build();
    }
}
