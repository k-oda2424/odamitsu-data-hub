package jp.co.oda32.domain.service.goods;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.ISalesGoods;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.repository.goods.WSalesGoodsRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.goods.WSalesGoodsSpecification;
import jp.co.oda32.util.GoodsUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 販売商品ワークEntity操作用サービスクラス
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Service
public class WSalesGoodsService extends CustomService {
    private final WSalesGoodsRepository salesGoodsRepository;
    private WSalesGoodsSpecification goodsSpecification = new WSalesGoodsSpecification();

    @Autowired
    public WSalesGoodsService(WSalesGoodsRepository salesGoodsRepository) {
        this.salesGoodsRepository = salesGoodsRepository;
    }

    public List<WSalesGoods> findAll() { return salesGoodsRepository.findAll(); }
    public List<WSalesGoods> findByGoodsNoList(List<Integer> goodsNoList) { return this.salesGoodsRepository.findAll(Specification.where(this.goodsSpecification.goodsNoListContains(goodsNoList))); }
    public List<WSalesGoods> findByShopNo(Integer shopNo) { return this.salesGoodsRepository.findAll(Specification.where(this.goodsSpecification.shopNoContains(shopNo))); }
    public WSalesGoods getByShopNoAndGoodsCode(Integer shopNo, String goodsCode) { return salesGoodsRepository.getByShopNoAndGoodsCode(shopNo, goodsCode); }
    public List<WSalesGoods> findByShopNoAndGoodsCode(Integer shopNo, List<String> goodsCodeList) { return this.salesGoodsRepository.findAll(Specification.where(this.goodsSpecification.shopNoContains(shopNo)).and(this.goodsSpecification.goodsCodeListContains(goodsCodeList))); }
    public WSalesGoods getByPK(Integer shopNo, Integer goodsNo) { return this.salesGoodsRepository.getByShopNoAndGoodsNo(shopNo, goodsNo); }
    public List<WSalesGoods> findByGoodsName(String goodsName) { return salesGoodsRepository.findByGoodsName(goodsName); }

    public List<WSalesGoods> find(Integer shopNo, Integer goodsNo, String goodsName, String notLikeGoodsName, String goodsCode, String keyword, Integer supplierNo, Flag delFlg) {
        return this.salesGoodsRepository.findAll(Specification
                .where(this.goodsSpecification.shopNoContains(shopNo))
                .and(this.goodsSpecification.goodsNoContains(goodsNo))
                .and(this.goodsSpecification.goodsNamesContains(goodsName))
                .and(this.goodsSpecification.goodsNamesNotContains(notLikeGoodsName))
                .and(this.goodsSpecification.goodsCodeContains(goodsCode))
                .and(this.goodsSpecification.keywordsContains(keyword))
                .and(this.goodsSpecification.supplierNoContains(supplierNo))
                .and(this.goodsSpecification.delFlgContains(delFlg)));
    }

    /**
     * 複数 supplier_no（IN条件）で検索。比較見積等の仕入先グループ展開検索用。
     */
    public List<WSalesGoods> findBySupplierNoList(Integer shopNo, String goodsName, String goodsCode, java.util.Collection<Integer> supplierNoList, Flag delFlg) {
        return this.salesGoodsRepository.findAll(Specification
                .where(this.goodsSpecification.shopNoContains(shopNo))
                .and(this.goodsSpecification.goodsNamesContains(goodsName))
                .and(this.goodsSpecification.goodsCodeContains(goodsCode))
                .and(this.goodsSpecification.supplierNoListContains(supplierNoList))
                .and(this.goodsSpecification.delFlgContains(delFlg)));
    }

    public WSalesGoods update(ISalesGoods iSalesGoods) throws Exception {
        WSalesGoods wSalesGoods = this.getByPK(iSalesGoods.getShopNo(), iSalesGoods.getGoodsNo());
        BeanUtils.copyProperties(iSalesGoods, wSalesGoods);
        String goodsName = GoodsUtil.removePriceFromName(wSalesGoods.getGoodsName());
        wSalesGoods.setGoodsName(goodsName);
        return this.update(this.salesGoodsRepository, wSalesGoods);
    }

    public WSalesGoods insert(ISalesGoods iSalesGoods) throws Exception {
        WSalesGoods wSalesGoods = new WSalesGoods();
        BeanUtils.copyProperties(iSalesGoods, wSalesGoods);
        String goodsName = GoodsUtil.removePriceFromName(wSalesGoods.getGoodsName());
        wSalesGoods.setGoodsName(goodsName);
        return this.insert(this.salesGoodsRepository, wSalesGoods);
    }
}
