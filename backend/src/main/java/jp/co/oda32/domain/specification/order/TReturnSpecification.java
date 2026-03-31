package jp.co.oda32.domain.specification.order;

import jp.co.oda32.domain.model.order.TReturn;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * 返品テーブル検索条件
 *
 * @author k_oda
 * @since 2018/11/29
 */
public class TReturnSpecification extends CommonSpecification<TReturn> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<TReturn> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 注文番号の検索条件
     *
     * @param orderNo 注文番号
     * @return 注文番号の検索条件
     */
    public Specification<TReturn> orderNoContains(Integer orderNo) {
        return orderNo == null ? null : (root, query, cb) -> cb.equal(root.get("orderNo"), orderNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TReturn> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

    /**
     * 返品ステータスの検索条件
     *
     * @param returnStatus 返品ステータス
     * @return 返品ステータスの検索条件
     */
    public Specification<TReturn> returnStatusContains(String returnStatus) {
        return StringUtil.isEmpty(returnStatus) ? null : (root, query, cb) -> cb.equal(root.get("returnStatus"), returnStatus);
    }

    /**
     * 返品日時の検索条件
     *
     * @param returnDateFrom 返品日時FROM
     * @param returnDateTo   返品日時TO
     * @return 返品日時FROMの検索条件
     */
    public Specification<TReturn> returnDateTimeContains(LocalDateTime returnDateFrom, LocalDateTime returnDateTo) {
        if (returnDateFrom == null && returnDateTo == null) {
            return null;
        }
        if (returnDateTo == null) {
            // returnDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("returnDateTime"), java.sql.Timestamp.valueOf(returnDateFrom));
        }
        if (returnDateFrom == null) {
            // returnDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("returnDateTime"), java.sql.Timestamp.valueOf(returnDateTo));
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("returnDateTime"), java.sql.Timestamp.valueOf(returnDateFrom), java.sql.Timestamp.valueOf(returnDateTo));
    }

    /**
     * 返品伝票日付の検索条件
     *
     * @param returnSlipDate 伝票日付
     * @return 伝票日付の検索条件
     */
    public Specification<TReturn> returnSlipDateContains(String returnSlipDate) {
        return StringUtil.isEmpty(returnSlipDate) ? null : (root, query, cb) -> cb.equal(root.get("returnSlipDate"), returnSlipDate);
    }


}
