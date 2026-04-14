package jp.co.oda32.dto.purchase;

import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class PurchaseDetailResponse {
    private Integer purchaseNo;
    private Integer purchaseDetailNo;
    private LocalDate purchaseDate;
    private Integer goodsNo;
    private String goodsCode;
    private String goodsName;
    private BigDecimal goodsPrice;
    private BigDecimal goodsNum;
    private BigDecimal subtotal;
    private BigDecimal includeTaxSubtotal;
    private BigDecimal taxRate;
    private BigDecimal taxPrice;
    private String taxType;
    private String note;

    public static PurchaseDetailResponse from(TPurchaseDetail d) {
        return PurchaseDetailResponse.builder()
                .purchaseNo(d.getPurchaseNo())
                .purchaseDetailNo(d.getPurchaseDetailNo())
                .purchaseDate(d.getPurchaseDate())
                .goodsNo(d.getGoodsNo())
                .goodsCode(d.getGoodsCode())
                .goodsName(d.getGoodsName())
                .goodsPrice(d.getGoodsPrice())
                .goodsNum(d.getGoodsNum())
                .subtotal(d.getSubtotal())
                .includeTaxSubtotal(d.getIncludeTaxSubtotal())
                .taxRate(d.getTaxRate())
                .taxPrice(d.getTaxPrice())
                .taxType(d.getTaxType())
                .note(d.getNote())
                .build();
    }
}
