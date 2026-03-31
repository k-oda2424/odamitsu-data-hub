package jp.co.oda32.domain.specification.purchase;

import jp.co.oda32.domain.model.purchase.MPurchasePrice;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * 仕入価格マスタテーブル検索条件
 *
 * @author k_oda
 * @since 2020/01/21
 */
public class MPurchasePriceSpecification extends CommonSpecification<MPurchasePrice> {
    /**
     * 仕入先番号の検索条件
     *
     * @param supplierNo 仕入先番号
     * @return 仕入先番号の検索条件
     */
    public Specification<MPurchasePrice> supplierNoContains(Integer supplierNo) {
        return supplierNo == null ? null : (root, query, cb) -> cb.equal(root.get("supplierNo"), supplierNo);
    }

    /**
     * 得意先番号の検索条件
     *
     * @param partnerNo 得意先番号
     * @return 得意先番号の検索条件
     */
    public Specification<MPurchasePrice> partnerNoContains(Integer partnerNo) {
        return (partnerNo == null || partnerNo == 0) ? null : (root, query, cb) -> cb.equal(root.get("partnerNo"), partnerNo);
    }

    /**
     * 届け先番号の検索条件
     *
     * @param destinationNo 届け先番号
     * @return 届け先番号の検索条件
     */
    public Specification<MPurchasePrice> destinationNoContains(Integer destinationNo) {
        return (destinationNo == null || destinationNo == 0) ? null : (root, query, cb) -> cb.equal(root.get("destinationNo"), destinationNo);
    }

    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<MPurchasePrice> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<MPurchasePrice> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品番号リストのin句の条件を返します
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<MPurchasePrice> goodsNoListContains(List<Integer> goodsNoList) {
        return CollectionUtil.isEmpty(goodsNoList) ? null : (root, query, cb) -> root.get("goodsNo").in(goodsNoList);
    }

    /**
     * 商品コードの検索条件
     *
     * @param goodsCode 商品コード
     * @return 商品コードの検索条件
     */
    public Specification<MPurchasePrice> goodsCodeContains(String goodsCode) {
        return StringUtil.isEmpty(goodsCode) ? null : (root, query, cb) -> cb.like(root.get("wSalesGoods").get("goodsCode"), "%" + goodsCode);
    }

    /**
     * 商品名の検索条件
     *
     * @param goodsName 商品名
     * @return 商品名の検索条件
     */
    public Specification<MPurchasePrice> goodsNameContains(String goodsName) {
        return StringUtil.isEmpty(goodsName) ? null : (root, query, cb) -> cb.like(root.get("wSalesGoods").get("goodsName"), "%" + goodsName + "%");
    }

    /**
     * 商品名の複数検索条件(or接続)
     *
     * @param goodsName 商品名(半角スペースか全角スペース区切りでsplit)
     * @return 商品名の複数検索条件
     */
    public Specification<MPurchasePrice> goodsNamesContains(String goodsName) {
        final Specification<MPurchasePrice> noSpecification = Specification.where(null);
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
    private Specification<MPurchasePrice> goodsNameNotContains(String goodsName) {
        return StringUtil.isEmpty(goodsName) ? null : (root, query, cb) -> cb.notLike(root.get("wSalesGoods").get("goodsName"), "%" + goodsName + "%");
    }

    /**
     * 含めたくない商品名の複数検索条件(or接続)
     *
     * @param goodsName 含めたくない商品名(半角スペースか全角スペース区切りでsplit)
     * @return 含めたくない商品名の複数検索条件
     */
    public Specification<MPurchasePrice> goodsNamesNotContains(String goodsName) {
        final Specification<MPurchasePrice> noSpecification = Specification.where(null);
        List<String> goodsNames = splitQuery(goodsName);
        return goodsNames.stream()
                .map(this::goodsNameNotContains)
                .reduce(noSpecification, Specification::and);
    }

}
