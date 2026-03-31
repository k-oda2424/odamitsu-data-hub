package jp.co.oda32.domain.service.stock;

import jp.co.oda32.domain.model.stock.TShopAppropriateStock;
import jp.co.oda32.domain.repository.stock.TShopAppropriateStockRepository;
import jp.co.oda32.domain.service.CustomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在庫Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Service
public class TShopAppropriateStockService extends CustomService {
    private final TShopAppropriateStockRepository tStockRepository;

    @Autowired
    public TShopAppropriateStockService(TShopAppropriateStockRepository tStockRepository) {
        this.tStockRepository = tStockRepository;
    }

    public TShopAppropriateStock getByPK(Integer goodsNo, Integer shopNo) {
        return this.tStockRepository.getByGoodsNoAndShopNo(goodsNo, shopNo);
    }

    @Transactional
    public void truncateTShopAppropriateStock() {
        this.tStockRepository.truncateTShopAppropriateStock();
    }

    /**
     * ショップ適正在庫を登録します。
     *
     * @param tShopAppropriateStock ショップ適正在庫
     * @return 登録したショップ適正在庫Entity
     */
    public TShopAppropriateStock insert(TShopAppropriateStock tShopAppropriateStock) throws Exception {
        return this.insert(this.tStockRepository, tShopAppropriateStock);
    }

    /**
     * ショップ適正在庫を更新します。
     *
     * @param tShopAppropriateStock ショップ適正在庫
     * @return 更新したショップ適正在庫Entity
     * @throws Exception システム例外
     */
    public TShopAppropriateStock update(TShopAppropriateStock tShopAppropriateStock) throws Exception {
        return this.update(this.tStockRepository, tShopAppropriateStock);
    }

}
