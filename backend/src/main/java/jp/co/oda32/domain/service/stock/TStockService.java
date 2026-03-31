package jp.co.oda32.domain.service.stock;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.stock.TStock;
import jp.co.oda32.domain.repository.stock.TStockRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.stock.TStockSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 在庫Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Service
public class TStockService extends CustomService {
    private final TStockRepository tStockRepository;
    private TStockSpecification tStockSpecification = new TStockSpecification();

    @Autowired
    public TStockService(TStockRepository tStockRepository) {
        this.tStockRepository = tStockRepository;
    }

    public TStock getByPK(Integer goodsNo, Integer warehouseNo) {
        return this.tStockRepository.getByGoodsNoAndWarehouseNo(goodsNo, warehouseNo);
    }

    public List<TStock> find(Integer goodsNo, Integer companyNo, Integer warehouseNo, String goodsName, Flag delFlg) {
        return this.tStockRepository.findAll(Specification
                .where(this.tStockSpecification.goodsNoContains(goodsNo))
                .and(this.tStockSpecification.companyNoContains(companyNo))
                .and(this.tStockSpecification.warehouseNoContains(warehouseNo))
                .and(this.tStockSpecification.goodsNameContains(goodsName))
                .and(this.tStockSpecification.delFlgContains(delFlg)));
    }

    public List<TStock> find(List<Integer> goodsNoList, Integer companyNo, Integer warehouseNo, Flag delFlg) {
        return this.tStockRepository.findAll(Specification
                .where(this.tStockSpecification.goodsNoListContains(goodsNoList))
                .and(this.tStockSpecification.companyNoContains(companyNo))
                .and(this.tStockSpecification.warehouseNoContains(warehouseNo))
                .and(this.tStockSpecification.delFlgContains(delFlg)));
    }

    /**
     * 在庫を登録します。
     *
     * @param tStock 在庫登録フォーム
     * @return 登録した在庫Entity
     */
    public TStock insert(TStock tStock) throws Exception {
        return this.insert(this.tStockRepository, tStock);
    }

    /**
     * 在庫を更新します。
     *
     * @param tStock 在庫更新フォーム
     * @return 更新した在庫Entity
     * @throws Exception システム例外
     */
    public TStock update(TStock tStock) throws Exception {
        return this.update(this.tStockRepository, tStock);
    }

    /**
     * 削除フラグを立てます
     *
     * @param tStock 更新対象
     * @throws Exception システム例外
     */
    public void delete(TStock tStock) throws Exception {
        TStock updateCompany = this.tStockRepository.findById(tStock.getCompanyNo()).orElseThrow();
        updateCompany.setDelFlg(Flag.YES.getValue());
        this.update(this.tStockRepository, updateCompany);
    }
}
