package jp.co.oda32.domain.specification.order;

import jp.co.oda32.domain.model.order.TReturnDetail;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;

/**
 * 返品明細テーブル検索条件
 *
 * @author k_oda
 * @since 2018/11/29
 */
public class TReturnDetailSpecification extends CommonSpecification<TReturnDetail> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<TReturnDetail> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 注文番号の検索条件
     *
     * @param orderNo 注文番号
     * @return 注文番号の検索条件
     */
    public Specification<TReturnDetail> orderNoContains(Integer orderNo) {
        return orderNo == null ? null : (root, query, cb) -> cb.equal(root.get("orderNo"), orderNo);
    }

    /**
     * 注文明細番号の検索条件
     *
     * @param orderDetailNo 注文番号
     * @return 注文明細番号の検索条件
     */
    public Specification<TReturnDetail> orderDetailNoContains(Integer orderDetailNo) {
        return orderDetailNo == null ? null : (root, query, cb) -> cb.equal(root.get("orderDetailNo"), orderDetailNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TReturnDetail> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

    /**
     * 返品明細ステータスの検索条件
     *
     * @param returnDetailStatus 返品明細ステータス
     * @return 返品明細ステータスの検索条件
     */
    public Specification<TReturnDetail> returnDetailStatusContains(String returnDetailStatus) {
        return StringUtil.isEmpty(returnDetailStatus) ? null : (root, query, cb) -> cb.equal(root.get("returnDetailStatus"), returnDetailStatus);
    }

    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<TReturnDetail> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 返品日の検索条件
     *
     * @param returnDateTimeFrom 返品日FROM
     * @param returnDateTimeTo   返品日TO
     * @return 返品日FROMの検索条件
     */
    public Specification<TReturnDetail> returnDateTimeContains(LocalDateTime returnDateTimeFrom, LocalDateTime returnDateTimeTo) {
        if (returnDateTimeFrom == null && returnDateTimeTo == null) {
            return null;
        }
        if (returnDateTimeTo == null) {
            // returnDateTimeFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("tReturn").get("returnDateTime"), java.sql.Timestamp.valueOf(returnDateTimeFrom));
        }
        if (returnDateTimeFrom == null) {
            // returnDateTimeToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("tReturn").get("returnDateTime"), java.sql.Timestamp.valueOf(returnDateTimeTo));
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("tReturn").get("returnDateTime"), java.sql.Timestamp.valueOf(returnDateTimeFrom), java.sql.Timestamp.valueOf(returnDateTimeTo));
    }

    /**
     * 返品伝票日付の検索条件
     * yyyyMMddの文字列
     *
     * @param returnSlipDateFrom 返品伝票日付FROM
     * @param returnSlipDateTo   返品伝票日付TO
     * @return 返品伝票日付の検索条件
     */
    public Specification<TReturnDetail> returnSlipDateContains(String returnSlipDateFrom, String returnSlipDateTo) {
        if (StringUtil.isEmpty(returnSlipDateFrom) && StringUtil.isEmpty(returnSlipDateTo)) {
            return null;
        }
        if (StringUtil.isEmpty(returnSlipDateTo)) {
            // returnSlipDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("tReturn").get("returnSlipDate"), returnSlipDateFrom);
        }
        if (StringUtil.isEmpty(returnSlipDateFrom)) {
            // returnSlipDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("tReturn").get("returnSlipDate"), returnSlipDateTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("tReturn").get("returnSlipDate"), returnSlipDateFrom, returnSlipDateTo);
    }
}
