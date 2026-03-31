package jp.co.oda32.domain.service.stock;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.stock.TStockLog;
import jp.co.oda32.domain.repository.stock.TStockLogRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.stock.TStockLogSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 在庫Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Service
public class TStockLogService extends CustomService {
    private final TStockLogRepository tStockLogRepository;
    private TStockLogSpecification tStockLogSpecification = new TStockLogSpecification();

    @Autowired
    public TStockLogService(TStockLogRepository tStockLogRepository) {
        this.tStockLogRepository = tStockLogRepository;
    }


    public List<TStockLog> find(Integer goodsNo, Integer companyNo, Integer warehouseNo, Flag delFlg) {
        return this.tStockLogRepository.findAll(Specification
                .where(this.tStockLogSpecification.goodsNoContains(goodsNo))
                .and(this.tStockLogSpecification.companyNoContains(companyNo))
                .and(this.tStockLogSpecification.warehouseNoContains(warehouseNo))
                .and(this.tStockLogSpecification.delFlgContains(delFlg)));
    }

    public List<TStockLog> findByMoveTime(LocalDateTime moveTimeFrom, LocalDateTime moveTimeTo, Flag delFlg) {
        return this.tStockLogRepository.findAll(Specification
                .where(this.tStockLogSpecification.moveTimeContains(moveTimeFrom, moveTimeTo))
                .and(this.tStockLogSpecification.delFlgContains(delFlg)));
    }

    public TStockLog getByUniqKey(int goodsNo, int warehouseNo, LocalDateTime moveTime, Integer deliveryNo, Integer purchaseNo) {
        return this.tStockLogRepository.getByGoodsNoAndWarehouseNoAndMoveTimeAndDeliveryNoAndPurchaseNo(goodsNo, warehouseNo, moveTime, deliveryNo, purchaseNo);
    }

    /**
     * 在庫履歴を登録します。
     *
     * @param tStockLog 在庫履歴
     * @return 登録した在庫履歴Entity
     */
    public TStockLog insert(TStockLog tStockLog) throws Exception {
        return this.insert(this.tStockLogRepository, tStockLog);
    }

    /**
     * 在庫履歴を更新します。
     *
     * @param tStockLog 在庫履歴
     * @return 更新した在庫履歴Entity
     * @throws Exception システム例外
     */
    public TStockLog update(TStockLog tStockLog) throws Exception {
        return this.update(this.tStockLogRepository, tStockLog);
    }

    /**
     * 在庫履歴が存在する場合、更新
     * 存在しない場合、登録します。
     *
     * @param tStockLog 保存する在庫履歴
     * @return 保存した在庫履歴
     */
    public TStockLog save(TStockLog tStockLog) throws Exception {
        TStockLog stockLog = this.getByUniqKey(tStockLog.getGoodsNo(), tStockLog.getWarehouseNo(), tStockLog.getMoveTime(), tStockLog.getDeliveryNo(), tStockLog.getPurchaseNo());
        if (stockLog == null) {
            return this.insert(tStockLog);
        }
        tStockLog.setStockLogNo(stockLog.getStockLogNo());
        return this.update(tStockLog);
    }

    /**
     * 削除フラグを立てます
     *
     * @param tStockLog 更新対象
     * @throws Exception システム例外
     */
    public void delete(TStockLog tStockLog) throws Exception {
        TStockLog updateCompany = this.tStockLogRepository.findById(tStockLog.getCompanyNo()).orElseThrow();
        updateCompany.setDelFlg(Flag.YES.getValue());
        this.update(this.tStockLogRepository, updateCompany);
    }

    public void deleteForInventory(LocalDateTime inventoryTime) {
        this.tStockLogRepository.deleteForInventory(inventoryTime);
    }
}
