package jp.co.oda32.domain.specification.stock;

import jp.co.oda32.domain.model.stock.TStock;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * 在庫検索条件
 *
 * @author k_oda
 * @since 2019/04/23
 */
public class TStockSpecification extends CommonSpecification<TStock> {
    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<TStock> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品番号リストのin句の条件を返します
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<TStock> goodsNoListContains(List<Integer> goodsNoList) {
        return CollectionUtil.isEmpty(goodsNoList) ? null : (root, query, cb) -> root.get("goodsNo").in(goodsNoList);
    }

    /**
     * 商品名の検索条件
     *
     * @param goodsName 商品名
     * @return 商品名の検索条件
     */
    public Specification<TStock> goodsNameContains(String goodsName) {
        return StringUtil.isEmpty(goodsName) ? null : (root, query, cb) -> cb.like(root.get("mGoods").get("goodsName"), "%" + goodsName + "%");
    }

    /**
     * 倉庫番号の検索条件
     *
     * @param warehouseNo 倉庫番号
     * @return 倉庫番号の検索条件
     */
    public Specification<TStock> warehouseNoContains(Integer warehouseNo) {
        return warehouseNo == null ? null : (root, query, cb) -> cb.equal(root.get("warehouseNo"), warehouseNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TStock> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }


}
