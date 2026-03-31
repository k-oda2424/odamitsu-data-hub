package jp.co.oda32.domain.specification.master;

import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * 仕入先検索条件
 *
 * @author k_oda
 * @since 2018/11/23
 */
public class MSupplierSpecification extends CommonSpecification<MSupplier> {
    /**
     * 仕入先番号の検索条件
     *
     * @param supplierNo 仕入先番号
     * @return 仕入先番号の検索条件
     */
    public Specification<MSupplier> supplierNoContains(Integer supplierNo) {
        return supplierNo == null ? null : (root, query, cb) -> cb.equal(root.get("supplierNo"), supplierNo);
    }

    /**
     * 仕入先名の検索条件
     *
     * @param supplierName 仕入先名
     * @return 仕入先名の検索条件
     */
    public Specification<MSupplier> supplierNameContains(String supplierName) {
        return StringUtil.isEmpty(supplierName) ? null : (root, query, cb) -> cb.like(root.get("supplierName"), "%" + supplierName + "%");
    }

    /**
     * 仕入先コードの検索条件
     *
     * @param supplierCode 仕入先コード
     * @return 仕入先コードの検索条件
     */
    public Specification<MSupplier> supplierCodeContains(String supplierCode) {
        return StringUtil.isEmpty(supplierCode) ? null : (root, query, cb) -> cb.equal(root.get("supplierCode"), supplierCode);
    }

    /**
     * 仕入先コードリストのin句の条件を返します
     *
     * @param supplierCodeList 仕入先コードリスト
     * @return 仕入先コードリストのin句の条件
     */
    public Specification<MSupplier> supplierCodeListContains(List<String> supplierCodeList) {
        return CollectionUtil.isEmpty(supplierCodeList) ? null : (root, query, cb) -> root.get("supplierCode").in(supplierCodeList);
    }
    /**
     * 支払先番号の検索条件
     *
     * @param paymentSupplierNo 支払先番号
     * @return 支払先番号の検索条件
     */
    public Specification<MSupplier> paymentSupplierNoContains(Integer paymentSupplierNo) {
        return paymentSupplierNo == null ? null : (root, query, cb) -> cb.equal(root.get("paymentSupplierNo"), paymentSupplierNo);
    }

    /**
     * 支払先番号がNullではない→仕入先
     *
     * @return 仕入先の必須条件
     */
    public Specification<MSupplier> paymentSupplierNoIsNotNull() {
        return (root, query, cb) -> cb.isNotNull(root.get("paymentSupplierNo"));
    }

    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<MSupplier> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }
}
