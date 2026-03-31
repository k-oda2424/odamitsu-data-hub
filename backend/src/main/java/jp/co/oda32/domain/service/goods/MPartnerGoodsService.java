package jp.co.oda32.domain.service.goods;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.embeddable.MPartnerGoodsPK;
import jp.co.oda32.domain.model.goods.MPartnerGoods;
import jp.co.oda32.domain.repository.goods.MPartnerGoodsRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.goods.MPartnerGoodsSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 得意先商品Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Service
public class MPartnerGoodsService extends CustomService {
    private final MPartnerGoodsRepository mPartnerGoodsRepository;
    private final MPartnerGoodsSpecification mPartnerGoodsSpecification = new MPartnerGoodsSpecification();

    @Autowired
    public MPartnerGoodsService(MPartnerGoodsRepository salesGoodsRepository) {
        this.mPartnerGoodsRepository = salesGoodsRepository;
    }

    public List<MPartnerGoods> findByGoodsName(String goodsName) {
        return this.mPartnerGoodsRepository.findByGoodsName(goodsName);
    }

    public MPartnerGoods getByPK(MPartnerGoodsPK partnerGoodsPK) {
        return this.mPartnerGoodsRepository.getByPartnerNoAndGoodsNoAndDestinationNo(partnerGoodsPK.getPartnerNo(), partnerGoodsPK.getGoodsNo(), partnerGoodsPK.getDestinationNo());
    }

    public List<MPartnerGoods> find(Integer shopNo, Integer companyNo, String partnerCode, Integer goodsNo, String goodsName, String goodsCode, String keyword, Integer destinationNo, Flag delFlg) {
        return this.mPartnerGoodsRepository.findAll(Specification
                .where(this.mPartnerGoodsSpecification.shopNoContains(shopNo))
                .and(this.mPartnerGoodsSpecification.companyNoContains(companyNo))
                .and(this.mPartnerGoodsSpecification.partnerCodeContains(partnerCode))
                .and(this.mPartnerGoodsSpecification.goodsNoContains(goodsNo))
                .and(this.mPartnerGoodsSpecification.goodsNamesContains(goodsName))
                .and(this.mPartnerGoodsSpecification.goodsCodeContains(goodsCode))
                .and(this.mPartnerGoodsSpecification.keywordsContains(keyword))
                .and(this.mPartnerGoodsSpecification.destinationNoContains(destinationNo))
                .and(this.mPartnerGoodsSpecification.delFlgContains(delFlg)));
    }

    public List<MPartnerGoods> find(Integer shopNo, Integer partnerNo, String goodsCode, Integer destinationNo, Flag delFlg) {
        if (partnerNo != null && partnerNo == 0) { partnerNo = null; }
        if (destinationNo != null && destinationNo == 0) { destinationNo = null; }
        return this.mPartnerGoodsRepository.findAll(Specification
                .where(this.mPartnerGoodsSpecification.shopNoContains(shopNo))
                .and(this.mPartnerGoodsSpecification.partnerNoContains(partnerNo))
                .and(this.mPartnerGoodsSpecification.goodsCodeContains(goodsCode))
                .and(this.mPartnerGoodsSpecification.destinationNoContains(destinationNo))
                .and(this.mPartnerGoodsSpecification.delFlgContains(delFlg)));
    }

    public List<MPartnerGoods> find(Integer shopNo, List<Integer> partnerNoList, String goodsCode, Integer destinationNo, Flag delFlg) {
        if (destinationNo != null && destinationNo == 0) { destinationNo = null; }
        return this.mPartnerGoodsRepository.findAll(Specification
                .where(this.mPartnerGoodsSpecification.shopNoContains(shopNo))
                .and(this.mPartnerGoodsSpecification.partnerNoListContains(partnerNoList))
                .and(this.mPartnerGoodsSpecification.goodsCodeContains(goodsCode))
                .and(this.mPartnerGoodsSpecification.destinationNoContains(destinationNo))
                .and(this.mPartnerGoodsSpecification.delFlgContains(delFlg)));
    }

    public MPartnerGoods update(MPartnerGoods mPartnerGoods) throws Exception {
        return this.update(this.mPartnerGoodsRepository, mPartnerGoods);
    }

    public MPartnerGoods insert(MPartnerGoods mPartnerGoods) throws Exception {
        return this.insert(this.mPartnerGoodsRepository, mPartnerGoods);
    }

    @Transactional
    public int updateAllClearOrderNumPerYear() {
        return this.mPartnerGoodsRepository.updateAllClearOrderNumPerYear();
    }
}
