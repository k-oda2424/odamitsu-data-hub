package jp.co.oda32.domain.specification.purchase;

import jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * 仕入価格変更予定テーブル検索条件
 *
 * @author k_oda
 * @since 2022/10/13
 */
public class MPurchasePriceChangePlanSpecification extends CommonSpecification<MPurchasePriceChangePlan> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo ショップ番号
     * @return ショップ番号の検索条件
     */
    public Specification<MPurchasePriceChangePlan> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 仕入先コードの検索条件
     *
     * @param supplierCode 仕入先コード
     * @return 仕入先コードの検索条件
     */
    public Specification<MPurchasePriceChangePlan> supplierCodeContains(String supplierCode) {
        return StringUtil.isEmpty(supplierCode) ? null : (root, query, cb) -> cb.equal(root.get("supplierCode"), supplierCode);
    }

    /**
     * 商品コードの検索条件
     *
     * @param goodsCode 商品コード
     * @return 商品コードの検索条件
     */
    public Specification<MPurchasePriceChangePlan> goodsCodeContains(String goodsCode) {
        return likeSuffixNormalized("goodsCode", goodsCode);
    }

    /**
     * JANコードの検索条件
     *
     * @param janCode JANコード
     * @return JANコードの検索条件
     */
    public Specification<MPurchasePriceChangePlan> janCodeContains(String janCode) {
        return likeSuffixNormalized("janCode", janCode);
    }

    /**
     * 得意先番号の検索条件
     *
     * @param partnerNo 得意先番号
     * @return 得意先番号の検索条件
     */
    public Specification<MPurchasePriceChangePlan> partnerNoContains(Integer partnerNo) {
        return partnerNo == null ? null : (root, query, cb) -> cb.equal(root.get("partnerNo"), partnerNo);
    }

    /**
     * 届け先番号の検索条件
     *
     * @param destinationNo 届け先番号
     * @return 届け先番号の検索条件
     */
    public Specification<MPurchasePriceChangePlan> destinationNoContains(Integer destinationNo) {
        return destinationNo == null ? null : (root, query, cb) -> cb.equal(root.get("destinationNo"), destinationNo);
    }

    /**
     * 仕入価格変更理由の検索条件
     *
     * @param changeReason 仕入価格変更理由
     * @return 仕入価格変更理由の検索条件
     */
    public Specification<MPurchasePriceChangePlan> changeReasonContains(String changeReason) {
        return StringUtil.isEmpty(changeReason) ? null : (root, query, cb) -> cb.equal(root.get("changeReason"), changeReason);
    }

    /**
     * 仕入価格変更予定日の検索条件
     *
     * @param changePlanDateFrom 仕入価格変更予定日FROM
     * @param changePlanDateTo   仕入価格変更予定日TO
     * @return 仕入価格変更予定日の検索条件
     */
    public Specification<MPurchasePriceChangePlan> changePlanDateRangeContains(LocalDate changePlanDateFrom, LocalDate changePlanDateTo) {
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
     * 仕入価格変更日の検索条件（完全一致条件）
     *
     * @param changePlanDate 仕入価格変更日
     * @return 価格変更日の検索条件
     */
    public Specification<MPurchasePriceChangePlan> changePlanDateContains(LocalDate changePlanDate) {
        if (changePlanDate == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("changePlanDate"), changePlanDate);
    }

    /**
     * 得意先価格変更予定作成フラグの検索条件
     *
     * @param partnerPriceChangePlanCreated 得意先価格変更予定作成フラグ
     * @return 得意先価格変更予定作成フラグの検索条件
     */
    public Specification<MPurchasePriceChangePlan> partnerPriceChangePlanCreatedContains(boolean partnerPriceChangePlanCreated) {
        return (root, query, cb) -> cb.or(cb.equal(root.get("partnerPriceChangePlanCreated"), partnerPriceChangePlanCreated), cb.isNull(root.get("partnerPriceChangePlanCreated")));
    }

    /**
     * 商品名の検索条件（部分一致）
     */
    public Specification<MPurchasePriceChangePlan> goodsNameContains(String goodsName) {
        return likeNormalized("goodsName", goodsName);
    }
}
