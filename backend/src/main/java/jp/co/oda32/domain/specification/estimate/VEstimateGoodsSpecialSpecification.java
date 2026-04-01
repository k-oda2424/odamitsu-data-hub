package jp.co.oda32.domain.specification.estimate;

import jp.co.oda32.domain.model.estimate.VEstimateGoodsSpecial;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * 見積商品情報Viewの検索条件
 *
 * @author k_oda
 * @since 2022/12/21
 */
public class VEstimateGoodsSpecialSpecification extends CommonSpecification<VEstimateGoodsSpecial> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo ショップ番号
     * @return ショップ番号の検索条件
     */
    public Specification<VEstimateGoodsSpecial> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<VEstimateGoodsSpecial> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 得意先番号の検索条件
     *
     * @param partnerNo 得意先番号
     * @return 得意先番号の検索条件
     */
    public Specification<VEstimateGoodsSpecial> partnerNoContains(Integer partnerNo) {
        return partnerNo == null ? null : (root, query, cb) -> cb.equal(root.get("partnerNo"), partnerNo);
    }

    /**
     * 届け先番号の検索条件
     *
     * @param destinationNo 届け先番号
     * @return 届け先番号の検索条件
     */
    public Specification<VEstimateGoodsSpecial> destinationNoContains(Integer destinationNo) {
        return destinationNo == null ? null : (root, query, cb) -> cb.equal(root.get("destinationNo"), destinationNo);
    }

    /**
     * 商品番号リストのin句の条件を返します
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<VEstimateGoodsSpecial> goodsNoListContains(List<Integer> goodsNoList) {
        return CollectionUtil.isEmpty(goodsNoList) ? null : (root, query, cb) -> root.get("goodsNo").in(goodsNoList);
    }

    /**
     * 商品コードの検索条件
     *
     * @param goodsCode 商品コード
     * @return 商品コードの検索条件
     */
    public Specification<VEstimateGoodsSpecial> goodsCodeContains(String goodsCode) {
        return likeSuffixNormalized("goodsCode", goodsCode);
    }
}
