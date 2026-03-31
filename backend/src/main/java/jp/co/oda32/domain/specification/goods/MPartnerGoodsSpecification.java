package jp.co.oda32.domain.specification.goods;

import jp.co.oda32.domain.model.goods.MPartnerGoods;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * 取引先商品テーブル検索条件
 *
 * @author k_oda
 * @since 2019/01/17
 */
public class MPartnerGoodsSpecification extends CommonSpecification<MPartnerGoods> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo ショップ番号
     * @return ショップ番号の検索条件
     */
    public Specification<MPartnerGoods> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<MPartnerGoods> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

    /**
     * 得意先コードの検索条件
     *
     * @param partnerCode 得意先コード
     * @return 得意先コードの検索条件
     */
    public Specification<MPartnerGoods> partnerCodeContains(String partnerCode) {
        return StringUtil.isEmpty(partnerCode) ? null : (root, query, cb) -> cb.like(root.get("mCompany").get("partner").get("partnerCode"), "%" + partnerCode);
    }

    /**
     * 得意先番号の検索条件
     *
     * @param partnerNo 得意先番号
     * @return 得意先番号の検索条件
     */
    public Specification<MPartnerGoods> partnerNoContains(Integer partnerNo) {
        return partnerNo == null ? null : (root, query, cb) -> cb.equal(root.get("mCompany").get("partner").get("partnerNo"), partnerNo);
    }

    /**
     * 得意先番号リストのin句の条件を返します
     *
     * @param partnerNoList 得意先番号リスト
     * @return 得意先番号リストのin句の条件
     */
    public Specification<MPartnerGoods> partnerNoListContains(List<Integer> partnerNoList) {
        return CollectionUtil.isEmpty(partnerNoList) ? null : (root, query, cb) -> root.get("partnerNo").in(partnerNoList);
    }
    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<MPartnerGoods> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品名の検索条件
     *
     * @param goodsName 商品名
     * @return 商品名の検索条件
     */
    private Specification<MPartnerGoods> goodsNameContains(String goodsName) {
        return StringUtil.isEmpty(goodsName) ? null : (root, query, cb) -> cb.like(root.get("goodsName"), "%" + goodsName + "%");
    }

    /**
     * 商品コードの検索条件
     *
     * @param goodsCode 商品コード
     * @return 商品コードの検索条件
     */
    public Specification<MPartnerGoods> goodsCodeContains(String goodsCode) {
        return StringUtil.isEmpty(goodsCode) ? null : (root, query, cb) -> cb.like(root.get("goodsCode"), "%" + goodsCode);
    }

    /**
     * 商品名の複数検索条件(or接続)
     *
     * @param goodsName 商品名(半角スペースか全角スペース区切りでsplit)
     * @return 商品名の複数検索条件
     */
    public Specification<MPartnerGoods> goodsNamesContains(String goodsName) {
        final Specification<MPartnerGoods> noSpecification = Specification.where(null);
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
    private Specification<MPartnerGoods> keywordContains(String keyword) {
        return StringUtil.isEmpty(keyword) ? null : (root, query, cb) -> cb.like(root.get("keyword"), "%" + keyword + "%");
    }

    /**
     * キーワードの複数検索条件(or接続)
     *
     * @param keyword キーワード(半角スペースか全角スペース区切りでsplit)
     * @return キーワードの複数検索条件
     */
    public Specification<MPartnerGoods> keywordsContains(String keyword) {
        final Specification<MPartnerGoods> noSpecification = Specification.where(null);
        List<String> keywords = splitQuery(keyword);
        return keywords.stream()
                .map(this::keywordContains)
                .reduce(noSpecification, Specification::and);
    }

    /**
     * 届け先番号の検索条件
     *
     * @param destinationNo 届け先番号
     * @return 届け先番号の検索条件
     */
    public Specification<MPartnerGoods> destinationNoContains(Integer destinationNo) {
        return destinationNo == null ? null : (root, query, cb) -> cb.equal(root.get("destinationNo"), destinationNo);
    }
}
