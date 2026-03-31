package jp.co.oda32.domain.service.estimate;

import jp.co.oda32.domain.model.estimate.VEstimateGoodsSpecial;
import jp.co.oda32.domain.repository.estimate.VEstimateGoodsSpecialRepository;
import jp.co.oda32.domain.specification.estimate.VEstimateGoodsSpecialSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 特値見積商品情報Viewのサービスクラス
 *
 * @author k_oda
 * @since 2022/12/21
 */
@Service
@RequiredArgsConstructor
public class VEstimateGoodsSpecialService {
    final VEstimateGoodsSpecialRepository vEstimateGoodsSpecialRepository;
    VEstimateGoodsSpecialSpecification vEstimateGoodsSpecialSpecification = new VEstimateGoodsSpecialSpecification();

    public List<VEstimateGoodsSpecial> findGoods(Integer shopNo, List<Integer> goodsNoList, Integer partnerNo, Integer destinationNo) {
        return this.vEstimateGoodsSpecialRepository.findAll(Specification
                .where(this.vEstimateGoodsSpecialSpecification.shopNoContains(shopNo))
                .and(this.vEstimateGoodsSpecialSpecification.goodsNoListContains(goodsNoList))
                .and(this.vEstimateGoodsSpecialSpecification.partnerNoContains(partnerNo))
                .and(this.vEstimateGoodsSpecialSpecification.destinationNoContains(destinationNo))
        );
    }

    public List<VEstimateGoodsSpecial> find(Integer shopNo, Integer goodsNo, String goodsCode, Integer partnerNo, Integer destinationNo) {
        return this.vEstimateGoodsSpecialRepository.findAll(Specification
                .where(this.vEstimateGoodsSpecialSpecification.shopNoContains(shopNo))
                .and(this.vEstimateGoodsSpecialSpecification.goodsNoContains(goodsNo))
                .and(this.vEstimateGoodsSpecialSpecification.goodsCodeContains(goodsCode))
                .and(this.vEstimateGoodsSpecialSpecification.partnerNoContains(partnerNo))
                .and(this.vEstimateGoodsSpecialSpecification.destinationNoContains(destinationNo))
        );
    }
}
