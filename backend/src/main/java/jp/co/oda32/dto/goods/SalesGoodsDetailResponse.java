package jp.co.oda32.dto.goods;

import jp.co.oda32.domain.model.goods.ISalesGoods;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Builder
public class SalesGoodsDetailResponse {
    private Integer shopNo;
    private Integer goodsNo;
    private String goodsCode;
    private String goodsSkuCode;
    private String goodsName;
    private Integer categoryNo;
    private BigDecimal referencePrice;
    private BigDecimal purchasePrice;
    private BigDecimal goodsPrice;
    private Integer supplierNo;
    private String supplierName;
    private String catchphrase;
    private String goodsIntroduction;
    private String goodsDescription1;
    private String goodsDescription2;
    private String directShippingFlg;
    private Integer leadTime;
    private String keyword;
    private String delFlg;
    private boolean isWork;
    private String janCode;
    private String makerName;
    private String specification;

    public static SalesGoodsDetailResponse from(MSalesGoods sg) {
        MGoods mGoods = sg.getMGoods();
        return SalesGoodsDetailResponse.builder()
                .shopNo(sg.getShopNo())
                .goodsNo(sg.getGoodsNo())
                .goodsCode(sg.getGoodsCode())
                .goodsSkuCode(sg.getGoodsSkuCode())
                .goodsName(sg.getGoodsName())
                .categoryNo(sg.getCategoryNo())
                .referencePrice(sg.getReferencePrice())
                .purchasePrice(sg.getPurchasePrice())
                .goodsPrice(sg.getGoodsPrice())
                .supplierNo(sg.getSupplierNo())
                .supplierName(sg.getMSupplier() != null ? sg.getMSupplier().getSupplierName() : null)
                .catchphrase(sg.getCatchphrase())
                .goodsIntroduction(sg.getGoodsIntroduction())
                .goodsDescription1(sg.getGoodsDescription1())
                .goodsDescription2(sg.getGoodsDescription2())
                .directShippingFlg(sg.getDirectShippingFlg())
                .leadTime(sg.getLeadTime())
                .keyword(sg.getKeyword())
                .delFlg(sg.getDelFlg())
                .isWork(false)
                .janCode(mGoods != null ? mGoods.getJanCode() : null)
                .makerName(mGoods != null && mGoods.getMaker() != null ? mGoods.getMaker().getMakerName() : null)
                .specification(mGoods != null ? mGoods.getSpecification() : null)
                .build();
    }

    public static SalesGoodsDetailResponse from(WSalesGoods sg) {
        MGoods mGoods = sg.getMGoods();
        return SalesGoodsDetailResponse.builder()
                .shopNo(sg.getShopNo())
                .goodsNo(sg.getGoodsNo())
                .goodsCode(sg.getGoodsCode())
                .goodsSkuCode(sg.getGoodsSkuCode())
                .goodsName(sg.getGoodsName())
                .categoryNo(sg.getCategoryNo())
                .referencePrice(sg.getReferencePrice())
                .purchasePrice(sg.getPurchasePrice())
                .goodsPrice(sg.getGoodsPrice())
                .supplierNo(sg.getSupplierNo())
                .supplierName(sg.getMSupplier() != null ? sg.getMSupplier().getSupplierName() : null)
                .catchphrase(sg.getCatchphrase())
                .goodsIntroduction(sg.getGoodsIntroduction())
                .goodsDescription1(sg.getGoodsDescription1())
                .goodsDescription2(sg.getGoodsDescription2())
                .directShippingFlg(sg.getDirectShippingFlg())
                .leadTime(sg.getLeadTime())
                .keyword(sg.getKeyword())
                .delFlg(sg.getDelFlg())
                .isWork(true)
                .janCode(mGoods != null ? mGoods.getJanCode() : null)
                .makerName(mGoods != null && mGoods.getMaker() != null ? mGoods.getMaker().getMakerName() : null)
                .specification(mGoods != null ? mGoods.getSpecification() : null)
                .build();
    }
}
