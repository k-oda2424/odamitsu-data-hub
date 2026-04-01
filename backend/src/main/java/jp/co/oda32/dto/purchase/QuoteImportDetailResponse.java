package jp.co.oda32.dto.purchase;

import jp.co.oda32.domain.model.purchase.WQuoteImportDetail;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class QuoteImportDetailResponse {
    private Integer quoteImportDetailId;
    private Integer rowNo;
    private String janCode;
    private String quoteGoodsName;
    private String quoteGoodsCode;
    private String specification;
    private Integer quantityPerCase;
    private BigDecimal oldPrice;
    private BigDecimal newPrice;
    private BigDecimal oldBoxPrice;
    private BigDecimal newBoxPrice;

    public static QuoteImportDetailResponse from(WQuoteImportDetail d) {
        return QuoteImportDetailResponse.builder()
                .quoteImportDetailId(d.getQuoteImportDetailId())
                .rowNo(d.getRowNo())
                .janCode(d.getJanCode())
                .quoteGoodsName(d.getQuoteGoodsName())
                .quoteGoodsCode(d.getQuoteGoodsCode())
                .specification(d.getSpecification())
                .quantityPerCase(d.getQuantityPerCase())
                .oldPrice(d.getOldPrice())
                .newPrice(d.getNewPrice())
                .oldBoxPrice(d.getOldBoxPrice())
                .newBoxPrice(d.getNewBoxPrice())
                .build();
    }
}
