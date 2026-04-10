package jp.co.oda32.domain.service.master;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.repository.master.MSupplierRepository;
import jp.co.oda32.domain.repository.purchase.MPaymentSupplierRepository;
import jp.co.oda32.domain.service.CustomService;
import jp.co.oda32.domain.specification.master.MSupplierSpecification;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
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
    private final MPaymentSupplierRepository mPaymentSupplierRepository;
    private MSupplierSpecification supplierSpecification = new MSupplierSpecification();

    @Autowired
    public MSupplierService(MSupplierRepository supplierRepository, MPaymentSupplierRepository paymentSupplierRepository) {
        this.mSupplierRepository = supplierRepository;
        this.mPaymentSupplierRepository = paymentSupplierRepository;
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

    /**
     * supplierNo リストに含まれる仕入先Entityを一括取得します。
     */
    public List<MSupplier> findBySupplierNoList(List<Integer> supplierNoList) {
        if (supplierNoList == null || supplierNoList.isEmpty()) {
            return List.of();
        }
        return this.mSupplierRepository.findAllById(supplierNoList);
    }

    /**
     * payment_supplier_no で紐づく全 m_supplier を取得します（同グループの全仕入先）。
     * 親レコード（payment_supplier_no IS NULL かつ supplier_code = m_payment_supplier.payment_supplier_code）も含みます。
     */
    public List<MSupplier> findByPaymentSupplierNo(Integer shopNo, Integer paymentSupplierNo) {
        if (paymentSupplierNo == null) {
            return List.of();
        }
        // m_payment_supplier から payment_supplier_code を取得（親レコード照合用）
        MPaymentSupplier ps = mPaymentSupplierRepository.findById(paymentSupplierNo).orElse(null);
        final String paymentSupplierCode = ps != null ? ps.getPaymentSupplierCode() : null;

        return this.mSupplierRepository.findAll((root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (shopNo != null) {
                preds.add(cb.equal(root.get("shopNo"), shopNo));
            }
            preds.add(cb.equal(root.get("delFlg"), Flag.NO.getValue()));

            // 子（payment_supplier_no = ?） OR 親（payment_supplier_no IS NULL かつ supplier_code 一致）
            Predicate childMatch = cb.equal(root.get("paymentSupplierNo"), paymentSupplierNo);
            Predicate combined;
            if (paymentSupplierCode != null && !paymentSupplierCode.isBlank()) {
                Predicate parentMatch = cb.and(
                        cb.isNull(root.get("paymentSupplierNo")),
                        cb.equal(root.get("supplierCode"), paymentSupplierCode));
                combined = cb.or(childMatch, parentMatch);
            } else {
                combined = childMatch;
            }
            preds.add(combined);
            return cb.and(preds.toArray(new Predicate[0]));
        });
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
