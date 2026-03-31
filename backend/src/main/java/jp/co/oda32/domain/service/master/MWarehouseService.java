package jp.co.oda32.domain.service.master;

import jp.co.oda32.dto.master.WarehouseCreateForm;
import jp.co.oda32.dto.master.WarehouseModifyForm;
import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MWarehouse;
import jp.co.oda32.domain.repository.master.MWarehouseRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.master.WarehouseSpecification;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 倉庫Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Service
public class MWarehouseService extends CustomService {
    private final MWarehouseRepository warehouseRepository;
    private WarehouseSpecification warehouseSpecification = new WarehouseSpecification();

    @Autowired
    public MWarehouseService(MWarehouseRepository warehouseRepository) {
        this.warehouseRepository = warehouseRepository;
    }

    /**
     * delフラグを考慮した全検索
     *
     * @return 倉庫Entityのリスト
     */
    public List<MWarehouse> findAll() {
        return super.defaultFindAll(this.warehouseRepository);
    }

    /**
     * 倉庫番号で検索し、倉庫Entityを返します。
     * 倉庫番号「0」は初期値なので空とみなす
     *
     * @param warehouseNo 倉庫番号
     * @return 倉庫Entity
     */
    public MWarehouse getByWarehouseNo(Integer warehouseNo) {
        Optional<MWarehouse> mWarehouseOptional = warehouseRepository.findById(warehouseNo);
        return mWarehouseOptional.orElse(null);
    }

    /**
     * 倉庫名で検索し、リストを返します。
     *
     * @param warehouseName 倉庫名
     * @return 倉庫マスタEntityリスト
     */
    public List<MWarehouse> findByWarehouseName(String warehouseName) {
        return this.warehouseRepository.findByWarehouseName(warehouseName);
    }

    /**
     * 検索条件を指定して検索します。
     *
     * @param warehouseNo   倉庫番号
     * @param warehouseName 倉庫名
     * @param companyNo 会社番号
     * @param delFlg        削除フラグ
     * @return 倉庫Entityリスト
     */
    public List<MWarehouse> find(Integer warehouseNo, String warehouseName, Integer companyNo, Flag delFlg) {
        return this.warehouseRepository.findAll(Specification
                .where(this.warehouseSpecification.warehouseNoContains(warehouseNo))
                .and(this.warehouseSpecification.warehouseNameContains(warehouseName))
                .and(this.warehouseSpecification.companyNoContains(companyNo))
                .and(this.warehouseSpecification.delFlgContains(delFlg)));
    }

    /**
     * 倉庫を登録します。
     *
     * @param warehouseCreateForm 倉庫登録フォーム
     * @return 登録した倉庫Entity
     */
    public MWarehouse insert(WarehouseCreateForm warehouseCreateForm) throws Exception {
        MWarehouse saveWarehouse = new MWarehouse();
        BeanUtils.copyProperties(warehouseCreateForm, saveWarehouse);
        return this.insert(this.warehouseRepository, saveWarehouse);
    }

    /**
     * 倉庫を更新します。
     *
     * @param warehouseModifyForm 倉庫更新フォーム
     * @return 更新した倉庫Entity
     * @throws Exception システム例外
     */
    public MWarehouse update(WarehouseModifyForm warehouseModifyForm) throws Exception {
        MWarehouse updateWarehouse = this.warehouseRepository.findById(warehouseModifyForm.getWarehouseNo()).orElseThrow();
        // 更新対象カラムの設定
        updateWarehouse.setWarehouseName(warehouseModifyForm.getWarehouseName());
        return this.update(this.warehouseRepository, updateWarehouse);
    }

    /**
     * 削除フラグを立てます
     *
     * @param warehouseModifyForm 更新対象
     * @throws Exception システム例外
     */
    public void delete(WarehouseModifyForm warehouseModifyForm) throws Exception {
        MWarehouse updateWarehouse = this.warehouseRepository.findById(warehouseModifyForm.getWarehouseNo()).orElseThrow();
        updateWarehouse.setDelFlg(Flag.YES.getValue());
        this.update(this.warehouseRepository, updateWarehouse);
    }

    /**
     * 倉庫を登録します。
     *
     * @param warehouse 倉庫登録フォーム
     * @return 登録した倉庫Entity
     */
    public MWarehouse insert(MWarehouse warehouse) throws Exception {
        return this.insert(this.warehouseRepository, warehouse);
    }

    /**
     * 倉庫を更新します。
     *
     * @param warehouse 倉庫更新フォーム
     * @return 更新した倉庫Entity
     * @throws Exception システム例外
     */
    public MWarehouse update(MWarehouse warehouse) throws Exception {
        return this.update(this.warehouseRepository, warehouse);
    }

    /**
     * 削除フラグを立てます
     *
     * @param warehouse 更新対象
     * @throws Exception システム例外
     */
    public void delete(MWarehouse warehouse) throws Exception {
        MWarehouse updateWarehouse = this.warehouseRepository.findById(warehouse.getWarehouseNo()).orElseThrow();
        updateWarehouse.setDelFlg(Flag.YES.getValue());
        this.update(this.warehouseRepository, updateWarehouse);
    }
}
