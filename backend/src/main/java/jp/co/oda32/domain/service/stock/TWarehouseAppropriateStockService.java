package jp.co.oda32.domain.service.stock;

import jp.co.oda32.domain.model.stock.TWarehouseAppropriateStock;
import jp.co.oda32.domain.repository.stock.TWarehouseAppropriateStockRepository;
import jp.co.oda32.domain.service.CustomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 在庫Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Service
public class TWarehouseAppropriateStockService extends CustomService {
    private final TWarehouseAppropriateStockRepository tStockRepository;

    @Autowired
    public TWarehouseAppropriateStockService(TWarehouseAppropriateStockRepository tStockRepository) {
        this.tStockRepository = tStockRepository;
    }

    public TWarehouseAppropriateStock getByPK(Integer goodsNo, Integer warehouseNo) {
        return this.tStockRepository.getByGoodsNoAndWarehouseNo(goodsNo, warehouseNo);
    }

    /**
     * 倉庫適正在庫を登録します。
     *
     * @param tShopAppropriateStock 倉庫適正在庫
     * @return 登録した倉庫適正在庫Entity
     */
    public TWarehouseAppropriateStock insert(TWarehouseAppropriateStock tShopAppropriateStock) throws Exception {
        return this.insert(this.tStockRepository, tShopAppropriateStock);
    }

    /**
     * 倉庫適正在庫を更新します。
     *
     * @param tShopAppropriateStock 倉庫適正在庫
     * @return 更新した倉庫適正在庫Entity
     * @throws Exception システム例外
     */
    public TWarehouseAppropriateStock update(TWarehouseAppropriateStock tShopAppropriateStock) throws Exception {
        return this.update(this.tStockRepository, tShopAppropriateStock);
    }

}
