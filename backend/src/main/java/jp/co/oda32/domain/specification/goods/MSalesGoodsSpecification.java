package jp.co.oda32.domain.specification.goods;

import jp.co.oda32.domain.model.goods.MSalesGoods;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * 販売商品テーブル検索条件
 *
 * @author k_oda
 * @since 2018/07/20
 */
public class MSalesGoodsSpecification extends CommonSpecification<MSalesGoods> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<MSalesGoods> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<MSalesGoods> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品名の検索条件
     *
     * @param goodsName 商品名
     * @return 商品名の検索条件
     */
    private Specification<MSalesGoods> goodsNameContains(String goodsName) {
        return StringUtil.isEmpty(goodsName) ? null : (root, query, cb) -> cb.like(root.get("goodsName"), "%" + goodsName + "%");
    }

    /**
     * 商品コードの検索条件
     *
     * @param goodsCode 商品コード
     * @return 商品コードの検索条件
     */
    public Specification<MSalesGoods> goodsCodeContains(String goodsCode) {
        return StringUtil.isEmpty(goodsCode) ? null : (root, query, cb) -> cb.like(root.get("goodsCode"), "%" + goodsCode);
    }

    /**
     * 商品名の複数検索条件(or接続)
     *
     * @param goodsName 商品名(半角スペースか全角スペース区切りでsplit)
     * @return 商品名の複数検索条件
     */
    public Specification<MSalesGoods> goodsNamesContains(String goodsName) {
        final Specification<MSalesGoods> noSpecification = Specification.where(null);
        List<String> goodsNames = splitQuery(goodsName);
        return goodsNames.stream()
                .map(this::goodsNameContains)
                .reduce(noSpecification, Specification::or);
    }

    /**
     * キーワードの検索条件
     *
     * @param keyword 商品名
     * @return キーワードの検索条件
     */
    private Specification<MSalesGoods> keywordContains(String keyword) {
        return StringUtil.isEmpty(keyword) ? null : (root, query, cb) -> cb.like(root.get("keyword"), "%" + keyword + "%");
    }

    /**
     * キーワードの複数検索条件(or接続)
     *
     * @param keyword キーワード(半角スペースか全角スペース区切りでsplit)
     * @return キーワードの複数検索条件
     */
    public Specification<MSalesGoods> keywordsContains(String keyword) {
        final Specification<MSalesGoods> noSpecification = Specification.where(null);
        List<String> keywords = splitQuery(keyword);
        return keywords.stream()
                .map(this::keywordContains)
                .reduce(noSpecification, Specification::and);
    }

    /**
     * 仕入先番号の検索条件
     *
     * @param supplierNo メーカー番号
     * @return メーカー番号の検索条件
     */
    public Specification<MSalesGoods> supplierNoContains(Integer supplierNo) {
        return supplierNo == null ? null : (root, query, cb) -> cb.equal(root.get("supplierNo"), supplierNo);
    }

    /**
     * 商品番号リストのin句の条件を返します
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<MSalesGoods> goodsNoListContains(List<Integer> goodsNoList) {
        return CollectionUtil.isEmpty(goodsNoList) ? null : (root, query, cb) -> root.get("goodsNo").in(goodsNoList);
    }

    /**
     * 商品コードリストのin句の条件を返します
     *
     * @param goodsCodeList 商品番号リスト
     * @return 商品コードリストのin句の条件
     */
    public Specification<MSalesGoods> goodsCodeListContains(List<String> goodsCodeList) {
        return CollectionUtil.isEmpty(goodsCodeList) ? null : (root, query, cb) -> root.get("goodsCode").in(goodsCodeList);
    }
}
