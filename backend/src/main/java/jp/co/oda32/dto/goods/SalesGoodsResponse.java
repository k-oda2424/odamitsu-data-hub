package jp.co.oda32.dto.goods;

import jp.co.oda32.domain.model.goods.ISalesGoods;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class SalesGoodsResponse {
    private Integer goodsNo;
    private String goodsName;
    private String goodsCode;
    private Integer supplierNo;
    private BigDecimal purchasePrice;
    private BigDecimal goodsPrice;
    private boolean isWork;

    public static SalesGoodsResponse from(ISalesGoods sg) {
        return SalesGoodsResponse.builder()
                .goodsNo(sg.getGoodsNo())
                .goodsName(sg.getGoodsName())
                .goodsCode(sg.getGoodsCode())
                .supplierNo(sg.getSupplierNo())
                .purchasePrice(sg.getPurchasePrice())
                .goodsPrice(sg.getGoodsPrice())
                .isWork(sg.getIsWork())
                .build();
    }
}
