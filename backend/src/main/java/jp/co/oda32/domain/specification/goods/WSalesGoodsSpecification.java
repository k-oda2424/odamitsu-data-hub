package jp.co.oda32.domain.specification.goods;

import jp.co.oda32.domain.model.goods.WSalesGoods;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * 販売商品ワークテーブル検索条件
 *
 * @author k_oda
 * @since 2018/07/20
 */
public class WSalesGoodsSpecification extends CommonSpecification<WSalesGoods> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<WSalesGoods> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<WSalesGoods> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品名の検索条件
     *
     * @param goodsName 商品名
     * @return 商品名の検索条件
     */
    private Specification<WSalesGoods> goodsNameContains(String goodsName) {
        return likeNormalized("goodsName", goodsName);
    }

    /**
     * 商品名の複数検索条件(or接続)
     *
     * @param goodsName 商品名(半角スペースか全角スペース区切りでsplit)
     * @return 商品名の複数検索条件
     */
    public Specification<WSalesGoods> goodsNamesContains(String goodsName) {
        final Specification<WSalesGoods> noSpecification = Specification.where(null);
        List<String> goodsNames = splitQuery(goodsName);
        return goodsNames.stream()
                .map(this::goodsNameContains)
                .reduce(noSpecification, Specification::or);
    }

    /**
     * 商品名に含まない(not like)検索条件
     *
     * @param goodsName 含めたくない商品名
     * @return 含めたくない商品名の検索条件
     */
    private Specification<WSalesGoods> goodsNameNotContains(String goodsName) {
        return StringUtil.isEmpty(goodsName) ? null : (root, query, cb) -> cb.notLike(root.get("goodsName"), "%" + goodsName + "%");
    }

    /**
     * 含めたくない商品名の複数検索条件(or接続)
     *
     * @param goodsName 含めたくない商品名(半角スペースか全角スペース区切りでsplit)
     * @return 含めたくない商品名の複数検索条件
     */
    public Specification<WSalesGoods> goodsNamesNotContains(String goodsName) {
        final Specification<WSalesGoods> noSpecification = Specification.where(null);
        List<String> goodsNames = splitQuery(goodsName);
        return goodsNames.stream()
                .map(this::goodsNameNotContains)
                .reduce(noSpecification, Specification::and);
    }

    /**
     * 商品コードの検索条件
     *
     * @param goodsCode 商品コード
     * @return 商品コードの検索条件
     */
    public Specification<WSalesGoods> goodsCodeContains(String goodsCode) {
        return likeSuffixNormalized("goodsCode", goodsCode);
    }

    /**
     * 仕入先番号の検索条件
     *
     * @param supplierNo メーカー番号
     * @return メーカー番号の検索条件
     */
    public Specification<WSalesGoods> supplierNoContains(Integer supplierNo) {
        return supplierNo == null ? null : (root, query, cb) -> cb.equal(root.get("supplierNo"), supplierNo);
    }

    /**
     * 仕入先番号のIN条件（複数仕入先での検索）
     */
    public Specification<WSalesGoods> supplierNoListContains(java.util.Collection<Integer> supplierNoList) {
        return (supplierNoList == null || supplierNoList.isEmpty()) ? null
                : (root, query, cb) -> root.get("supplierNo").in(supplierNoList);
    }

    /**
     * キーワードの検索条件
     *
     * @param keyword 商品名
     * @return キーワードの検索条件
     */
    private Specification<WSalesGoods> keywordContains(String keyword) {
        return likeNormalized("keyword", keyword);
    }

    /**
     * キーワードの複数検索条件(or接続)
     *
     * @param keyword キーワード(半角スペースか全角スペース区切りでsplit)
     * @return キーワードの複数検索条件
     */
    public Specification<WSalesGoods> keywordsContains(String keyword) {
        final Specification<WSalesGoods> noSpecification = Specification.where(null);
        List<String> keywords = splitQuery(keyword);
        return keywords.stream()
                .map(this::keywordContains)
                .reduce(noSpecification, Specification::and);
    }

    /**
     * 商品番号リストのin句の条件を返します
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<WSalesGoods> goodsNoListContains(List<Integer> goodsNoList) {
        return CollectionUtil.isEmpty(goodsNoList) ? null : (root, query, cb) -> root.get("goodsNo").in(goodsNoList);
    }

    /**
     * 商品コードリストのin句の条件を返します
     *
     * @param goodsCodeList 商品番号リスト
     * @return 商品コードリストのin句の条件
     */
    public Specification<WSalesGoods> goodsCodeListContains(List<String> goodsCodeList) {
        return CollectionUtil.isEmpty(goodsCodeList) ? null : (root, query, cb) -> root.get("goodsCode").in(goodsCodeList);
    }
}
