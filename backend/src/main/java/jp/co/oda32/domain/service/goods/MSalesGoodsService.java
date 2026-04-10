package jp.co.oda32.domain.service.goods;

import jp.co.oda32.dto.goods.GoodsModifyForm;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.ISalesGoods;
import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.repository.goods.MSalesGoodsRepository;
import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.repository.goods.WSalesGoodsRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.goods.MSalesGoodsSpecification;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 販売商品Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Service
public class MSalesGoodsService extends CustomService {
    private final MSalesGoodsRepository salesGoodsRepository;
    private final WSalesGoodsRepository wSalesGoodsRepository;
    private MSalesGoodsSpecification goodsSpecification = new MSalesGoodsSpecification();

    @Autowired
    public MSalesGoodsService(MSalesGoodsRepository salesGoodsRepository, WSalesGoodsRepository wSalesGoodsRepository) {
        this.salesGoodsRepository = salesGoodsRepository;
        this.wSalesGoodsRepository = wSalesGoodsRepository;
    }

    public List<MSalesGoods> findAll() { return salesGoodsRepository.findAll(); }
    public List<MSalesGoods> findByShopNo(Integer shopNo) { return this.salesGoodsRepository.findAll(Specification.where(this.goodsSpecification.shopNoContains(shopNo))); }
    public MSalesGoods getByShopNoAndGoodsCode(Integer shopNo, String goodsCode) { return salesGoodsRepository.getByShopNoAndGoodsCode(shopNo, goodsCode); }
    public List<MSalesGoods> findByShopNoAndGoodsCode(Integer shopNo, List<String> goodsCodeList) { return this.salesGoodsRepository.findAll(Specification.where(this.goodsSpecification.shopNoContains(shopNo)).and(this.goodsSpecification.goodsCodeListContains(goodsCodeList))); }
    public MSalesGoods getByPK(Integer shopNo, Integer goodsNo) { return this.salesGoodsRepository.getByShopNoAndGoodsNo(shopNo, goodsNo); }
    public List<MSalesGoods> findByGoodsNoList(List<Integer> goodsNoList) { return this.salesGoodsRepository.findAll(Specification.where(this.goodsSpecification.goodsNoListContains(goodsNoList))); }
    public List<MSalesGoods> findByGoodsName(String goodsName) { return salesGoodsRepository.findByGoodsName(goodsName); }

    public List<MSalesGoods> find(Integer shopNo, Integer goodsNo, String goodsName, String goodsCode, String keyword, Integer supplierNo, Flag delFlg) {
        return this.salesGoodsRepository.findAll(Specification
                .where(this.goodsSpecification.shopNoContains(shopNo))
                .and(this.goodsSpecification.goodsNoContains(goodsNo))
                .and(this.goodsSpecification.goodsNamesContains(goodsName))
                .and(this.goodsSpecification.goodsCodeContains(goodsCode))
                .and(this.goodsSpecification.keywordsContains(keyword))
                .and(this.goodsSpecification.supplierNoContains(supplierNo))
                .and(this.goodsSpecification.delFlgContains(delFlg)));
    }

    /**
     * 複数 supplier_no（IN条件）で検索。比較見積等の仕入先グループ展開検索用。
     */
    public List<MSalesGoods> findBySupplierNoList(Integer shopNo, String goodsName, String goodsCode, java.util.Collection<Integer> supplierNoList, Flag delFlg) {
        return this.salesGoodsRepository.findAll(Specification
                .where(this.goodsSpecification.shopNoContains(shopNo))
                .and(this.goodsSpecification.goodsNamesContains(goodsName))
                .and(this.goodsSpecification.goodsCodeContains(goodsCode))
                .and(this.goodsSpecification.supplierNoListContains(supplierNoList))
                .and(this.goodsSpecification.delFlgContains(delFlg)));
    }

    public MSalesGoods update(ISalesGoods iSalesGoods) throws Exception {
        MSalesGoods mSalesGoods = this.getByPK(iSalesGoods.getShopNo(), iSalesGoods.getGoodsNo());
        BeanUtils.copyProperties(iSalesGoods, mSalesGoods);
        return this.update(this.salesGoodsRepository, mSalesGoods);
    }

    public MSalesGoods insert(ISalesGoods iSalesGoods) throws Exception {
        MSalesGoods mSalesGoods = new MSalesGoods();
        BeanUtils.copyProperties(iSalesGoods, mSalesGoods);
        mSalesGoods.setModifyDateTime(null);
        mSalesGoods.setModifyUserNo(null);
        return this.insert(this.salesGoodsRepository, mSalesGoods);
    }

    public MSalesGoods save(ISalesGoods iSalesGoods) throws Exception {
        MSalesGoods salesGoods = this.getByPK(iSalesGoods.getShopNo(), iSalesGoods.getGoodsNo());
        if (salesGoods == null) { return this.insert(iSalesGoods); }
        return this.update(iSalesGoods);
    }

    public void delete(GoodsModifyForm goodsModifyForm) throws Exception {
        MSalesGoods updateMaker = this.salesGoodsRepository.findById(goodsModifyForm.getGoodsNo()).orElseThrow();
        updateMaker.setDelFlg(Flag.YES.getValue());
        this.update(this.salesGoodsRepository, updateMaker);
    }

    @Transactional
    public MSalesGoods reflectFromWork(Integer shopNo, Integer goodsNo) throws Exception {
        WSalesGoods work = wSalesGoodsRepository.getByShopNoAndGoodsNo(shopNo, goodsNo);
        if (work == null) {
            return null;
        }
        MSalesGoods master = new MSalesGoods();
        BeanUtils.copyProperties(work, master, "mGoods", "mShop", "mSupplier", "addDateTime", "addUserNo", "modifyDateTime", "modifyUserNo");
        MSalesGoods saved = this.save(master);
        work.setDelFlg(Flag.YES.getValue());
        this.update(this.wSalesGoodsRepository, work);
        return saved;
    }
}
