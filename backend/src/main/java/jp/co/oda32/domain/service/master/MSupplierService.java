package jp.co.oda32.domain.service.master;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.repository.master.MSupplierRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.master.MSupplierSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 仕入先Entity操作用サービスクラス
 *
 * @author k_oda
 * @since 2018/07/25
 */
@Service
public class MSupplierService extends CustomService {
    private final MSupplierRepository mSupplierRepository;
    private MSupplierSpecification supplierSpecification = new MSupplierSpecification();

    @Autowired
    public MSupplierService(MSupplierRepository supplierRepository) {
        this.mSupplierRepository = supplierRepository;
    }

    public List<MSupplier> findAll() {
        return mSupplierRepository.findAll();
    }

    public List<MSupplier> find(Integer supplierNo, String supplierName, String supplierCode, Integer paymentSupplierNo, Flag delFlg) {
        return this.mSupplierRepository.findAll(Specification
                .where(this.supplierSpecification.supplierNoContains(supplierNo))
                .and(this.supplierSpecification.supplierNameContains(supplierName))
                .and(this.supplierSpecification.supplierCodeContains(supplierCode))
                .and(this.supplierSpecification.paymentSupplierNoContains(paymentSupplierNo))
                .and(this.supplierSpecification.paymentSupplierNoIsNotNull())
                .and(this.supplierSpecification.delFlgContains(delFlg)));
    }

    public MSupplier getBySupplierNo(Integer supplierNo) {
        return mSupplierRepository.findById(supplierNo).orElse(null);
    }

    public List<MSupplier> findByShopNo(Integer shopNo) {
        return this.mSupplierRepository.findByShopNo(shopNo);
    }

    /**
     * ユニークキーである仕入先コードで検索し、仕入先Entityを返します。
     *
     * @param shopNo       ショップ番号
     * @param supplierCode 仕入先コード
     * @return 仕入先Entity
     */
    public MSupplier getByUniqueKey(Integer shopNo, String supplierCode) {
        return this.mSupplierRepository.getByShopNoAndSupplierCode(shopNo, supplierCode);
    }

    public List<MSupplier> findBySupplierCodeList(Integer shopNo, List<String> supplierCodeList) {
        return this.mSupplierRepository.findAll(Specification
                .where(this.supplierSpecification.shopNoContains(shopNo))
                .and(this.supplierSpecification.supplierCodeListContains(supplierCodeList))
                .and(this.supplierSpecification.delFlgContains(Flag.NO)));
    }
    /**
     * 仕入先を登録します。
     *
     * @param supplier 仕入先Entity
     * @return 登録した仕入先Entity
     */
    public MSupplier insert(MSupplier supplier) throws Exception {
        return this.insert(this.mSupplierRepository, supplier);
    }

    /**
     * 仕入先を更新します。
     *
     * @param supplier 仕入先Entity
     * @return 更新した仕入先Entity
     * @throws Exception システム例外
     */
    public MSupplier update(MSupplier supplier) throws Exception {
        return this.update(this.mSupplierRepository, supplier);
    }

    /**
     * 削除フラグを立てます
     *
     * @param supplier 更新対象
     * @throws Exception システム例外
     */
    public void delete(MSupplier supplier) throws Exception {
        MSupplier mSupplier = this.mSupplierRepository.findById(supplier.getSupplierNo()).orElseThrow();
        mSupplier.setDelFlg(Flag.YES.getValue());
        this.update(this.mSupplierRepository, mSupplier);
    }
}
