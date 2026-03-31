package jp.co.oda32.dto.goods;

import jp.co.oda32.domain.model.goods.MGoods;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class GoodsResponse {
    private Integer goodsNo;
    private String goodsName;
    private String janCode;
    private Integer makerNo;
    private String makerName;
    private String keyword;
    private Integer taxCategory;
    private String specification;
    private String discontinuedFlg;
    private BigDecimal caseContainNum;
    private boolean applyReducedTaxRate;
    private String delFlg;

    public static GoodsResponse from(MGoods goods) {
        return GoodsResponse.builder()
                .goodsNo(goods.getGoodsNo())
                .goodsName(goods.getGoodsName())
                .janCode(goods.getJanCode())
                .makerNo(goods.getMakerNo())
                .makerName(goods.getMaker() != null ? goods.getMaker().getMakerName() : null)
                .keyword(goods.getKeyword())
                .taxCategory(goods.getTaxCategory())
                .specification(goods.getSpecification())
                .discontinuedFlg(goods.getDiscontinuedFlg())
                .caseContainNum(goods.getCaseContainNum())
                .applyReducedTaxRate(goods.isApplyReducedTaxRate())
                .delFlg(goods.getDelFlg())
                .build();
    }
}
