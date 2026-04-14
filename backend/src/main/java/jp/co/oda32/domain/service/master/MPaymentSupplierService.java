package jp.co.oda32.domain.service.master;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.master.MPaymentSupplier;
import jp.co.oda32.domain.repository.purchase.MPaymentSupplierRepository;
import jp.co.oda32.domain.service.CustomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;

/**
 * 支払先マスタEntity操作用サービスクラス
 *
 * @author k_oda
 * @since 2019/06/21
 */
@Service
public class MPaymentSupplierService extends CustomService {
    private final MPaymentSupplierRepository mPaymentSupplierRepository;

    @Autowired
    public MPaymentSupplierService(MPaymentSupplierRepository supplierRepository) {
        this.mPaymentSupplierRepository = supplierRepository;
    }

    public MPaymentSupplier getByPaymentSupplierNo(Integer paymentSupplierNo) {
        return mPaymentSupplierRepository.findById(paymentSupplierNo).orElse(null);
    }

    public List<MPaymentSupplier> findAllByPaymentSupplierNos(java.util.Collection<Integer> paymentSupplierNos) {
        if (paymentSupplierNos == null || paymentSupplierNos.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return mPaymentSupplierRepository.findAllById(paymentSupplierNos);
    }

    /**
     * 指定店舗の支払先一覧を返します（削除フラグ無視せず active のみ）。
     * DB側でフィルタ・ソートを実施。
     */
    public List<MPaymentSupplier> findByShopNo(Integer shopNo) {
        Specification<MPaymentSupplier> spec = (root, query, cb) -> {
            List<Predicate> preds = new ArrayList<>();
            if (shopNo != null) {
                preds.add(cb.equal(root.get("shopNo"), shopNo));
            }
            preds.add(cb.equal(root.get("delFlg"), Flag.NO.getValue()));
            return cb.and(preds.toArray(new Predicate[0]));
        };
        return this.mPaymentSupplierRepository.findAll(spec, Sort.by(Sort.Direction.ASC, "paymentSupplierCode"));
    }

    /**
     * ユニークキーである支払先コードで検索し、支払先Entityを返します。
     * 削除フラグがたっていないもののみ
     *
     * @param shopNo              ショップ番号
     * @param paymentSupplierCode 支払先コード
     * @return 支払先Entity
     */
    public MPaymentSupplier getByPaymentSupplierCode(Integer shopNo, String paymentSupplierCode) {
        return this.mPaymentSupplierRepository.findByShopNoAndPaymentSupplierCode(shopNo, paymentSupplierCode)
                .stream()
                .filter(paymentSupplier -> paymentSupplier.getDelFlg().equals(Flag.NO.getValue()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 支払先を登録します。
     *
     * @param mPaymentSupplier 支払先Entity
     * @return 登録した支払先Entity
     */
    public MPaymentSupplier insert(MPaymentSupplier mPaymentSupplier) throws Exception {
        return this.insert(this.mPaymentSupplierRepository, mPaymentSupplier);
    }

    /**
     * 支払先を更新します。
     *
     * @param mPaymentSupplier 支払先Entity
     * @return 更新した支払先Entity
     * @throws Exception システム例外
     */
    public MPaymentSupplier update(MPaymentSupplier mPaymentSupplier) throws Exception {
        return this.update(this.mPaymentSupplierRepository, mPaymentSupplier);
    }

    /**
     * 削除フラグを立てます
     *
     * @param supplier 更新対象
     * @throws Exception システム例外
     */
    public void delete(MPaymentSupplier supplier) throws Exception {
        MPaymentSupplier mPaymentSupplier = this.mPaymentSupplierRepository.findById(supplier.getPaymentSupplierNo()).orElseThrow();
        mPaymentSupplier.setDelFlg(Flag.YES.getValue());
        this.update(this.mPaymentSupplierRepository, mPaymentSupplier);
    }
}
