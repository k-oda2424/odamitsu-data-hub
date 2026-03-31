package jp.co.oda32.domain.service.estimate;

import jp.co.oda32.domain.model.estimate.VEstimateGoods;
import jp.co.oda32.domain.repository.estimate.VEstimateGoodsRepository;
import jp.co.oda32.domain.specification.estimate.VEstimateGoodsSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 見積商品情報Viewのサービスクラス
 *
 * @author k_oda
 * @since 2022/10/28
 */
@Service
@RequiredArgsConstructor
public class VEstimateGoodsService {
    final VEstimateGoodsRepository vEstimateGoodsRepository;
    VEstimateGoodsSpecification vEstimateGoodsSpecification = new VEstimateGoodsSpecification();

    public List<VEstimateGoods> findGoods(Integer shopNo, List<Integer> goodsNoList) {
        return this.vEstimateGoodsRepository.findAll(Specification
                .where(this.vEstimateGoodsSpecification.shopNoContains(shopNo))
                .and(this.vEstimateGoodsSpecification.goodsNoListContains(goodsNoList))
        );
    }

    public List<VEstimateGoods> find(Integer shopNo, Integer goodsNo, String goodsCode) {
        return this.vEstimateGoodsRepository.findAll(Specification
                .where(this.vEstimateGoodsSpecification.shopNoContains(shopNo))
                .and(this.vEstimateGoodsSpecification.goodsNoContains(goodsNo))
                .and(this.vEstimateGoodsSpecification.goodsCodeContains(goodsCode))
        );
    }
}
