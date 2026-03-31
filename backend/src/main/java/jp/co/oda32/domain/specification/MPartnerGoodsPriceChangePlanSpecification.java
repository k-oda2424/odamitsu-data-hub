package jp.co.oda32.domain.specification;

import jp.co.oda32.domain.model.goods.MPartnerGoodsPriceChangePlan;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

/**
 * 得意先商品価格変更予定テーブル検索条件
 *
 * @author k_oda
 * @since 2022/10/13
 */
public class MPartnerGoodsPriceChangePlanSpecification extends CommonSpecification<MPartnerGoodsPriceChangePlan> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 得意先番号の検索条件
     *
     * @param partnerNo 得意先番号
     * @return 得意先番号の検索条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> partnerNoContains(Integer partnerNo) {
        return partnerNo == null ? null : (root, query, cb) -> cb.equal(root.get("partnerNo"), partnerNo);
    }

    /**
     * 得意先番号リストのin句の条件を返します
     *
     * @param partnerNoList 得意先番号リスト
     * @return 得意先番号リストのin句の条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> partnerNoListContains(List<Integer> partnerNoList) {
        return CollectionUtil.isEmpty(partnerNoList) ? null : (root, query, cb) -> root.get("partnerNo").in(partnerNoList);
    }

    /**
     * 届け先番号の検索条件
     *
     * @param destinationNo 届け先番号
     * @return 届け先番号の検索条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> destinationNoContains(Integer destinationNo) {
        return destinationNo == null ? null : (root, query, cb) -> cb.equal(root.get("destinationNo"), destinationNo);
    }

    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品番号リストのin句の条件を返します
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> goodsNoListContains(List<Integer> goodsNoList) {
        return CollectionUtil.isEmpty(goodsNoList) ? null : (root, query, cb) -> root.get("goodsNo").in(goodsNoList);
    }

    /**
     * 商品コードの検索条件
     *
     * @param goodsCode 商品コード
     * @return 商品コードの検索条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> goodsCodeContains(String goodsCode) {
        return StringUtil.isEmpty(goodsCode) ? null : (root, query, cb) -> cb.like(root.get("goodsCode"), "%" + goodsCode);
    }

    /**
     * JANコードの検索条件
     *
     * @param janCode JANコード
     * @return JANコードの検索条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> janCodeContains(String janCode) {
        return StringUtil.isEmpty(janCode) ? null : (root, query, cb) -> cb.like(root.get("janCode"), "%" + janCode);
    }

    /**
     * 価格変更予定日の検索条件
     *
     * @param changePlanDateFrom 仕入価格変更予定日FROM
     * @param changePlanDateTo   仕入価格変更予定日TO
     * @return 仕入価格変更予定日の検索条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> changePlanDateRangeContains(LocalDate changePlanDateFrom, LocalDate changePlanDateTo) {
        if (changePlanDateFrom == null && changePlanDateTo == null) {
            return null;
        }
        if (changePlanDateTo == null) {
            // changePlanDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("changePlanDate"), changePlanDateFrom);
        }
        if (changePlanDateFrom == null) {
            // changePlanDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("changePlanDate"), changePlanDateTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("changePlanDate"), changePlanDateFrom, changePlanDateTo);
    }

    /**
     * 価格変更予定日の検索条件（完全一致条件）
     *
     * @param changePlanDate 価格変更予定日
     * @return 価格変更予定日の検索条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> changePlanDateContains(LocalDate changePlanDate) {
        if (changePlanDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("changePlanDate"), changePlanDate);
    }

    /**
     * 見積作成フラグの検索条件
     *
     * @param estimateCreated 見積作成フラグ
     * @return 見積作成フラグの検索条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> estimateCreatedContains(boolean estimateCreated) {
        return (root, query, cb) -> cb.or(cb.equal(root.get("estimateCreated"), estimateCreated), cb.isNull(root.get("estimateCreated")));
    }

    /**
     * 得意先親価格変更予定番号がnullの検索条件
     *
     * @return 得意先親価格変更予定番号がnullの検索条件
     */
    public Specification<MPartnerGoodsPriceChangePlan> parentChangePlanNoIsNull() {
        return (root, query, cb) -> cb.isNull(root.get("parentChangePlanNo"));
    }
}
