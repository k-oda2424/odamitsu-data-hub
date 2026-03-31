package jp.co.oda32.domain.specification.estimate;

import jp.co.oda32.domain.model.estimate.TEstimateDetail;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

/**
 * 見積明細テーブル検索条件
 *
 * @author k_oda
 * @since 2022/10/24
 */
public class TEstimateDetailSpecification extends CommonSpecification<TEstimateDetail> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<TEstimateDetail> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 見積番号の検索条件
     *
     * @param estimateNo 見積番号
     * @return 見積番号の検索条件
     */
    public Specification<TEstimateDetail> estimateNoContains(Integer estimateNo) {
        return estimateNo == null ? null : (root, query, cb) -> cb.equal(root.get("estimateNo"), estimateNo);
    }

    /**
     * 見積番号リストのin句の条件を返します
     *
     * @param estimateNoList 見積番号リスト
     * @return 見積番号リストのin句の条件
     */
    public Specification<TEstimateDetail> estimateNoListContains(List<Integer> estimateNoList) {
        return CollectionUtil.isEmpty(estimateNoList) ? null : (root, query, cb) -> root.get("estimateNo").in(estimateNoList);
    }

    /**
     * 見積明細番号の検索条件
     *
     * @param estimateDetailNo 見積番号
     * @return 見積明細番号の検索条件
     */
    public Specification<TEstimateDetail> estimateDetailNoContains(Integer estimateDetailNo) {
        return estimateDetailNo == null ? null : (root, query, cb) -> cb.equal(root.get("estimateDetailNo"), estimateDetailNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TEstimateDetail> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

    /**
     * 会社番号リストのin句の条件を返します
     *
     * @param companyNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<TEstimateDetail> companyNoListContains(List<Integer> companyNoList) {
        return CollectionUtil.isEmpty(companyNoList) ? null : (root, query, cb) -> root.get("companyNo").in(companyNoList);
    }

    /**
     * 見積明細ステータスの検索条件
     *
     * @param estimateDetailStatus 見積明細ステータス
     * @return 見積明細ステータスの検索条件
     */
    public Specification<TEstimateDetail> estimateDetailStatusContains(String estimateDetailStatus) {
        return StringUtil.isEmpty(estimateDetailStatus) ? null : (root, query, cb) -> cb.equal(root.get("estimateDetailStatus"), estimateDetailStatus);
    }

    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<TEstimateDetail> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品番号リストのin句の条件を返します
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<TEstimateDetail> goodsNoListContains(List<Integer> goodsNoList) {
        return CollectionUtil.isEmpty(goodsNoList) ? null : (root, query, cb) -> root.get("goodsNo").in(goodsNoList);
    }

    /**
     * 見積日の検索条件
     *
     * @param estimateDateFrom 見積日FROM
     * @param estimateDateTo   見積日TO
     * @return 見積日FROMの検索条件
     */
    public Specification<TEstimateDetail> estimateDateContains(LocalDate estimateDateFrom, LocalDate estimateDateTo) {
        if (estimateDateFrom == null && estimateDateTo == null) {
            return null;
        }
        if (estimateDateTo == null) {
            // estimateDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("tEstimate").get("estimateDate"), estimateDateFrom);
        }
        if (estimateDateFrom == null) {
            // estimateDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("tEstimate").get("estimateDate"), estimateDateTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("tEstimate").get("estimateDate"), estimateDateFrom, estimateDateTo);
    }
}
