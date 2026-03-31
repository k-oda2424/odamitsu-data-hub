package jp.co.oda32.dto.goods;

import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
public class GoodsDetailResponse {
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
    private List<SalesGoodsDetailResponse> salesGoodsList;

    public static GoodsDetailResponse from(MGoods goods, List<MSalesGoods> masterList, List<WSalesGoods> workList) {
        List<SalesGoodsDetailResponse> salesGoods = new ArrayList<>();
        if (workList != null) {
            salesGoods.addAll(workList.stream().map(SalesGoodsDetailResponse::from).collect(Collectors.toList()));
        }
        if (masterList != null) {
            salesGoods.addAll(masterList.stream().map(SalesGoodsDetailResponse::from).collect(Collectors.toList()));
        }
        return GoodsDetailResponse.builder()
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
                .salesGoodsList(salesGoods)
                .build();
    }
}
