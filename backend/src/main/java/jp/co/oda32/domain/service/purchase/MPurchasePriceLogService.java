package jp.co.oda32.domain.service.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.MPurchasePriceLog;
import jp.co.oda32.domain.repository.purchase.MPurchasePriceLogRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.purchase.MPurchasePriceLogSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 仕入価格履歴マスタ履歴Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2020/01/21
 */
@Service
public class MPurchasePriceLogService extends CustomService {
    private final MPurchasePriceLogRepository purchasePriceLogRepository;
    private MPurchasePriceLogSpecification mPurchasePriceLogSpecification = new MPurchasePriceLogSpecification();

    @Autowired
    public MPurchasePriceLogService(MPurchasePriceLogRepository mPurchasePriceLogRepository) {
        this.purchasePriceLogRepository = mPurchasePriceLogRepository;
    }

    public List<MPurchasePriceLog> findAll() {
        return purchasePriceLogRepository.findAll();
    }

    public List<MPurchasePriceLog> findByShopNo(Integer shopNo) {
        return this.purchasePriceLogRepository.findAll(Specification
                .where(this.mPurchasePriceLogSpecification.shopNoContains(shopNo)));
    }


    public List<MPurchasePriceLog> findByGoodsNoList(List<Integer> goodsNoList) {
        return this.purchasePriceLogRepository.findAll(Specification
                .where(this.mPurchasePriceLogSpecification.goodsNoListContains(goodsNoList)));
    }

    public List<MPurchasePriceLog> find(Integer shopNo, Integer goodsNo, String goodsCode, Integer supplierNo, Flag delFlg) {
        return this.purchasePriceLogRepository.findAll(Specification
                .where(this.mPurchasePriceLogSpecification.shopNoContains(shopNo))
                .and(this.mPurchasePriceLogSpecification.goodsNoContains(goodsNo))
                .and(this.mPurchasePriceLogSpecification.supplierNoContains(supplierNo))
                .and(this.mPurchasePriceLogSpecification.delFlgContains(delFlg)));
    }

    /**
     * 仕入価格履歴を登録,更新します。
     *
     * @param purchasePriceLog 仕入価格履歴Entity
     * @return 登録した仕入価格履歴Entity
     */
    public MPurchasePriceLog save(MPurchasePriceLog purchasePriceLog) throws Exception {
        return this.insert(this.purchasePriceLogRepository, purchasePriceLog);
    }
}
