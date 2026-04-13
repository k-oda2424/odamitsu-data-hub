package jp.co.oda32.domain.specification.goods;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import jakarta.persistence.criteria.JoinType;
import java.util.List;

/**
 * 商品検索条件
 *
 * @author k_oda
 * @since 2017/08/23
 */
public class GoodsSpecification extends CommonSpecification<MGoods> {
    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<MGoods> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品番号リストの検索条件（IN句）
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品番号リストの検索条件
     */
    public Specification<MGoods> goodsNoListContains(List<Integer> goodsNoList) {
        return (goodsNoList == null || goodsNoList.isEmpty()) ? null
                : (root, query, cb) -> root.get("goodsNo").in(goodsNoList);
    }

    /**
     * 商品名の検索条件
     *
     * @param goodsName 商品名
     * @return 商品名の検索条件
     */
    public Specification<MGoods> goodsNameContains(String goodsName) {
        return likeNormalized("goodsName", goodsName);
    }

    /**
     * メーカー番号の検索条件
     *
     * @param makerNo メーカー番号
     * @return メーカー番号の検索条件
     */
    public Specification<MGoods> makerNoContains(Integer makerNo) {
        return makerNo == null ? null : (root, query, cb) -> cb.equal(root.get("makerNo"), makerNo);
    }

    /**
     * キーワードの検索条件
     *
     * @param keyword 商品名
     * @return キーワードの検索条件
     */
    public Specification<MGoods> keywordContains(String keyword) {
        return likeNormalized("keyword", keyword);
    }

    /**
     * JANコードの検索条件
     *
     * @param janCode JANコード
     * @return JANコードの検索条件
     */
    public Specification<MGoods> janCodeContains(String janCode) {
        return StringUtil.isEmpty(janCode) ? null : (root, query, cb) -> cb.equal(root.get("janCode"), janCode);
    }

    /**
     * 削除フラグの検索条件
     *
     * @param discontinuedFlg フラグ
     * @return 削除フラグの検索条件
     */
    public Specification<MGoods> discontinuedFlgContains(Flag discontinuedFlg) {
        return discontinuedFlg == null ? null : (root, query, cb) -> cb.equal(root.get("delFlg"), discontinuedFlg.getValue());
    }


    /**
     * 販売商品WORKをleft joinし、販売商品WORK.商品番号がnullの検索条件を返します。
     *
     * @return 販売商品WORK.商品番号がnullの検索条件
     */
    public Specification<MGoods> leftJoinWSalesGoods() {
        return (Specification<MGoods>) (root, criteriaQuery, criteriaBuilder)
                -> root.join("wSalesGoods", JoinType.LEFT).get("goodsNo").isNull();
    }
}
