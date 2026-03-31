package jp.co.oda32.dto.goods;

import jp.co.oda32.domain.model.goods.MPartnerGoods;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
public class PartnerGoodsDetailResponse {
    private Integer partnerNo;
    private Integer goodsNo;
    private Integer destinationNo;
    private Integer shopNo;
    private Integer companyNo;
    private String shopName;
    private String companyName;
    private String goodsCode;
    private String goodsName;
    private BigDecimal goodsPrice;
    private String keyword;
    private Integer reflectedEstimateNo;
    private LocalDate lastSalesDate;
    private LocalDate lastPriceUpdateDate;
    private List<OrderHistoryResponse> orderHistory;

    public static PartnerGoodsDetailResponse from(MPartnerGoods pg, List<OrderHistoryResponse> orderHistory) {
        return PartnerGoodsDetailResponse.builder()
                .partnerNo(pg.getPartnerNo())
                .goodsNo(pg.getGoodsNo())
                .destinationNo(pg.getDestinationNo())
                .shopNo(pg.getShopNo())
                .companyNo(pg.getCompanyNo())
                .shopName(pg.getMShop() != null ? pg.getMShop().getShopName() : null)
                .companyName(pg.getMCompany() != null ? pg.getMCompany().getCompanyName() : null)
                .goodsCode(pg.getGoodsCode())
                .goodsName(pg.getGoodsName())
                .goodsPrice(pg.getGoodsPrice())
                .keyword(pg.getKeyword())
                .reflectedEstimateNo(pg.getReflectedEstimateNo())
                .lastSalesDate(pg.getLastSalesDate())
                .lastPriceUpdateDate(pg.getLastPriceUpdateDate())
                .orderHistory(orderHistory)
                .build();
    }
}
