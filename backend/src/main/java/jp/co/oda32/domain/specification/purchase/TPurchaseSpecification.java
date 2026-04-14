package jp.co.oda32.domain.specification.purchase;

import jp.co.oda32.domain.model.purchase.TPurchase;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

/**
 * 仕入テーブル検索条件
 *
 * @author k_oda
 * @since 2019/06/02
 */
public class TPurchaseSpecification extends CommonSpecification<TPurchase> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<TPurchase> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 仕入番号の検索条件
     *
     * @param purchaseNo 仕入番号
     * @return 仕入番号の検索条件
     */
    public Specification<TPurchase> purchaseNoContains(Integer purchaseNo) {
        return purchaseNo == null ? null : (root, query, cb) -> cb.equal(root.get("purchaseNo"), purchaseNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TPurchase> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

    /**
     * 仕入ステータスの検索条件
     *
     * @param purchaseStatus 仕入ステータス
     * @return 仕入ステータスの検索条件
     */
    public Specification<TPurchase> purchaseStatusContains(String purchaseStatus) {
        return StringUtil.isEmpty(purchaseStatus) ? null : (root, query, cb) -> cb.equal(root.get("purchaseStatus"), purchaseStatus);
    }

    /**
     * 仕入日時の検索条件
     *
     * @param purchaseDateFrom 仕入日時FROM
     * @param purchaseDateTo   仕入日時TO
     * @return 仕入日時FROMの検索条件
     */
    public Specification<TPurchase> purchaseDateContains(LocalDate purchaseDateFrom, LocalDate purchaseDateTo) {
        if (purchaseDateFrom == null && purchaseDateTo == null) {
            return null;
        }
        if (purchaseDateTo == null) {
            // purchaseDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("purchaseDate"), purchaseDateFrom);
        }
        if (purchaseDateFrom == null) {
            // purchaseDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("purchaseDate"), purchaseDateTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("purchaseDate"), purchaseDateFrom, purchaseDateTo);
    }

    /**
     * 処理連番リストのin句の条件を返します
     *
     * @param extPurchaseNoList 処理連番のリスト
     * @return 処理連番リストのin句の条件
     */
    public Specification<TPurchase> extPurchaseNoListContains(List<Long> extPurchaseNoList) {
        return CollectionUtil.isEmpty(extPurchaseNoList) ? null : (root, query, cb) -> root.get("extPurchaseNo").in(extPurchaseNoList);
    }

    /**
     * 仕入番号リストのin句の条件を返します
     *
     * @param purchaseNoList 仕入番号リスト
     * @return 仕入番号リストのin句の条件
     */
    public Specification<TPurchase> purchaseNoListContains(List<Integer> purchaseNoList) {
        return CollectionUtil.isEmpty(purchaseNoList) ? null : (root, query, cb) -> root.get("purchaseNo").in(purchaseNoList);
    }

    public Specification<TPurchase> supplierNoListContains(List<Integer> supplierNoList) {
        return CollectionUtil.isEmpty(supplierNoList) ? null : (root, query, cb) -> root.get("supplierNo").in(supplierNoList);
    }

    public Specification<TPurchase> supplierNoEquals(Integer supplierNo) {
        return supplierNo == null ? null : (root, query, cb) -> cb.equal(root.get("supplierNo"), supplierNo);
    }

    public Specification<TPurchase> purchaseNoEquals(Integer purchaseNo) {
        return purchaseNo == null ? null : (root, query, cb) -> cb.equal(root.get("purchaseNo"), purchaseNo);
    }
}
