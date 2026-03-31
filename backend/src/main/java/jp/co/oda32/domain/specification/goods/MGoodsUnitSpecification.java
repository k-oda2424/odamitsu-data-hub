package jp.co.oda32.domain.specification.goods;

import jp.co.oda32.domain.model.goods.MGoodsUnit;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * 商品単位マスタ検索条件
 *
 * @author k_oda
 * @since 2019/05/29
 */
public class MGoodsUnitSpecification extends CommonSpecification<MGoodsUnit> {

    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<MGoodsUnit> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品番号リストのin句の条件を返します
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<MGoodsUnit> goodsNoListContains(List<Integer> goodsNoList) {
        return CollectionUtil.isEmpty(goodsNoList) ? null : (root, query, cb) -> root.get("goodsNo").in(goodsNoList);
    }

    /**
     * 商品単位番号リストのin句の条件を返します
     *
     * @param unitNoList 商品単位番号リスト
     * @return 商品単位番号リストのin句の条件
     */
    public Specification<MGoodsUnit> unitNoListContains(List<Integer> unitNoList) {
        return CollectionUtil.isEmpty(unitNoList) ? null : (root, query, cb) -> root.get("unitNo").in(unitNoList);
    }
}
