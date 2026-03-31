package jp.co.oda32.domain.specification.purchase;

import jp.co.oda32.domain.model.purchase.MPurchasePriceLog;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * 仕入価格マスタ履歴テーブル検索条件
 *
 * @author k_oda
 * @since 2020/01/21
 */
public class MPurchasePriceLogSpecification extends CommonSpecification<MPurchasePriceLog> {
    /**
     * 仕入先番号の検索条件
     *
     * @param supplierNo メーカー番号
     * @return メーカー番号の検索条件
     */
    public Specification<MPurchasePriceLog> supplierNoContains(Integer supplierNo) {
        return supplierNo == null ? null : (root, query, cb) -> cb.equal(root.get("supplierNo"), supplierNo);
    }

    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<MPurchasePriceLog> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<MPurchasePriceLog> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品番号リストのin句の条件を返します
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<MPurchasePriceLog> goodsNoListContains(List<Integer> goodsNoList) {
        return CollectionUtil.isEmpty(goodsNoList) ? null : (root, query, cb) -> root.get("goodsNo").in(goodsNoList);
    }

}
