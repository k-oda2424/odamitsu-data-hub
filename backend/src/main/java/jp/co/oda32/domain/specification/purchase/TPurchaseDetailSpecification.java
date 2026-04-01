package jp.co.oda32.domain.specification.purchase;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.purchase.TPurchaseDetail;
import jp.co.oda32.domain.specification.CommonSpecification;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * 仕入明細テーブル検索条件
 *
 * @author k_oda
 * @since 2019/06/02
 */
public class TPurchaseDetailSpecification extends CommonSpecification<TPurchaseDetail> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<TPurchaseDetail> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 仕入番号の検索条件
     *
     * @param purchaseNo 仕入番号
     * @return 仕入番号の検索条件
     */
    public Specification<TPurchaseDetail> purchaseNoContains(Integer purchaseNo) {
        return purchaseNo == null ? null : (root, query, cb) -> cb.equal(root.get("purchaseNo"), purchaseNo);
    }

    /**
     * 仕入明細番号の検索条件
     *
     * @param purchaseDetailNo 仕入番号
     * @return 仕入明細番号の検索条件
     */
    public Specification<TPurchaseDetail> purchaseDetailNoContains(Integer purchaseDetailNo) {
        return purchaseDetailNo == null ? null : (root, query, cb) -> cb.equal(root.get("purchaseDetailNo"), purchaseDetailNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TPurchaseDetail> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }
    /**
     * 倉庫番号の検索条件
     *
     * @param warehouseNo 倉庫番号
     * @return 倉庫番号の検索条件
     */
    public Specification<TPurchaseDetail> warehouseNoContains(Integer warehouseNo) {
        return warehouseNo == null ? null : (root, query, cb) -> cb.equal(root.get("warehouseNo"), warehouseNo);
    }
    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<TPurchaseDetail> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品コードの検索条件
     *
     * @param goodsCode 商品コード
     * @return 商品コードの検索条件
     */
    public Specification<TPurchaseDetail> goodsCodeContains(String goodsCode) {
        return likeSuffixNormalized("goodsCode", goodsCode);
    }
    /**
     * 仕入日の検索条件
     *
     * @param purchaseDateFrom 仕入日FROM
     * @param purchaseDateTo   仕入日TO
     * @return 仕入日FROMの検索条件
     */
    public Specification<TPurchaseDetail> purchaseDateContains(LocalDate purchaseDateFrom, LocalDate purchaseDateTo) {
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
     * 在庫処理フラグの検索条件
     *
     * @param stockProcessFlg 在庫処理フラグ
     * @return 在庫処理フラグの検索条件
     */
    public Specification<TPurchaseDetail> stockProcessFlgContains(Flag stockProcessFlg) {
        return stockProcessFlg == null ? null : (root, query, cb) -> cb.or(cb.equal(root.get("stockProcessFlg"), stockProcessFlg.getValue()), cb.isNull(root.get("stockProcessFlg")));
    }

    public Specification<TPurchaseDetail> sendOrderNoIsNull() {
        return (root, query, cb) -> cb.isNull(root.get("sendOrderNo"));
    }

    /**
     * 仕入先番号の検索条件
     *
     * @param supplierNo メーカー番号
     * @return メーカー番号の検索条件
     */
    public Specification<TPurchaseDetail> supplierNoContains(Integer supplierNo) {
        return supplierNo == null ? null : (root, query, cb) -> cb.equal(root.get("tPurchase").get("supplierNo"), supplierNo);
    }
}
