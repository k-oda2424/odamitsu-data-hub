package jp.co.oda32.domain.service.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.MPurchasePrice;
import jp.co.oda32.domain.model.purchase.MPurchasePriceLog;
import jp.co.oda32.domain.repository.purchase.MPurchasePriceRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.purchase.MPurchasePriceSpecification;
import jp.co.oda32.util.CollectionUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 仕入価格マスタEntity操作用サービスクラス
 *
 * @author k_oda
 * @since 2020/01/21
 */
@Service
public class MPurchasePriceService extends CustomService {
    private final MPurchasePriceRepository mPurchasePriceRepository;
    private final MPurchasePriceLogService mPurchasePriceLogService;
    private MPurchasePriceSpecification mPurchasePriceSpecification = new MPurchasePriceSpecification();

    @Autowired
    public MPurchasePriceService(MPurchasePriceRepository mPurchasePriceRepository, MPurchasePriceLogService mPurchasePriceLogService) {
        this.mPurchasePriceRepository = mPurchasePriceRepository;
        this.mPurchasePriceLogService = mPurchasePriceLogService;
    }

    public List<MPurchasePrice> findAll() {
        return mPurchasePriceRepository.findAll();
    }

    public List<MPurchasePrice> findByShopNo(Integer shopNo) {
        return this.mPurchasePriceRepository.findAll(Specification
                .where(this.mPurchasePriceSpecification.shopNoContains(shopNo)));
    }

    public MPurchasePrice getByPK(int purchasePriceNo) {
        return this.mPurchasePriceRepository.getByPurchasePriceNo(purchasePriceNo);
    }

    /**
     * ユニークキーで検索
     *
     * @param supplierNo    仕入先番号
     * @param shopNo        ショップ番号
     * @param goodsNo       商品番号
     * @param partnerNo     得意先番号
     * @param destinationNo 届け先番号
     * @return 仕入価格
     * @throws Exception 複数の行が返ってきたとき
     */
    public MPurchasePrice getByUK(Integer supplierNo, Integer shopNo, Integer goodsNo, Integer partnerNo, Integer destinationNo) throws Exception {
        List<MPurchasePrice> list = this.mPurchasePriceRepository.findAll(Specification
                .where(this.mPurchasePriceSpecification.shopNoContains(shopNo))
                .and(this.mPurchasePriceSpecification.goodsNoContains(goodsNo))
                .and(this.mPurchasePriceSpecification.supplierNoContains(supplierNo))
                .and(this.mPurchasePriceSpecification.partnerNoContains(partnerNo))
                .and(this.mPurchasePriceSpecification.destinationNoContains(destinationNo))
                .and(this.mPurchasePriceSpecification.delFlgContains(Flag.NO)));
        if (CollectionUtil.isEmpty(list)) {
            return null;
        }
        if (list.size() > 2) {
            throw new Exception(String.format("仕入価格検索:ユニークキー検索でユニークになりません。supplierNo:%d shopNo:%d, goodsNo:%d, partnerNo:%d, destinationNo:%d", supplierNo, shopNo, goodsNo, partnerNo, destinationNo));
        }
        return list.get(0);
    }

    public List<MPurchasePrice> findByGoodsNoList(List<Integer> goodsNoList) {
        return this.mPurchasePriceRepository.findAll(Specification
                .where(this.mPurchasePriceSpecification.goodsNoListContains(goodsNoList)));
    }

    public List<MPurchasePrice> find(Integer shopNo, Integer goodsNo, String goodsCode, String goodsName, String notLikeGoodsName, Integer supplierNo, Integer partnerNo, Flag delFlg) {
        return this.mPurchasePriceRepository.findAll(Specification
                .where(this.mPurchasePriceSpecification.shopNoContains(shopNo))
                .and(this.mPurchasePriceSpecification.goodsNoContains(goodsNo))
                .and(this.mPurchasePriceSpecification.goodsCodeContains(goodsCode))
                .and(this.mPurchasePriceSpecification.goodsNamesContains(goodsName))
                .and(this.mPurchasePriceSpecification.goodsNamesNotContains(notLikeGoodsName))
                .and(this.mPurchasePriceSpecification.supplierNoContains(supplierNo))
                .and(this.mPurchasePriceSpecification.partnerNoContains(partnerNo))
                .and(this.mPurchasePriceSpecification.delFlgContains(delFlg)));
    }

    /**
     * 仕入価格を登録,更新します。
     *
     * @param purchasePrice 仕入価格Entity
     * @return 登録した仕入価格Entity
     */
    public MPurchasePrice save(MPurchasePrice purchasePrice) throws Exception {
        // 仕入価格履歴にも登録,更新する
        MPurchasePrice mPurchasePrice = this.insert(this.mPurchasePriceRepository, purchasePrice);
        MPurchasePriceLog purchasePriceLog = new MPurchasePriceLog();
        BeanUtils.copyProperties(mPurchasePrice, purchasePriceLog);
        this.mPurchasePriceLogService.save(purchasePriceLog);
        return mPurchasePrice;
    }

    public List<MPurchasePrice> insert(List<MPurchasePrice> tPurchaseDetailList) throws Exception {
        List<MPurchasePrice> insertList = new ArrayList<>();
        for (MPurchasePrice mPurchasePrice : tPurchaseDetailList) {
            MPurchasePrice purchasePrice = this.save(mPurchasePrice);
            insertList.add(purchasePrice);
        }
        return insertList;
    }
}
