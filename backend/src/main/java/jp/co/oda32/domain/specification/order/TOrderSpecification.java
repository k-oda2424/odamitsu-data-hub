package jp.co.oda32.domain.specification.order;

import jp.co.oda32.domain.model.order.TOrder;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 注文テーブル検索条件
 *
 * @author k_oda
 * @since 2018/11/23
 */
public class TOrderSpecification extends CommonSpecification<TOrder> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<TOrder> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 注文番号の検索条件
     *
     * @param orderNo 注文番号
     * @return 注文番号の検索条件
     */
    public Specification<TOrder> orderNoContains(Integer orderNo) {
        return orderNo == null ? null : (root, query, cb) -> cb.equal(root.get("orderNo"), orderNo);
    }

    /**
     * 注文番号リストのin句の条件を返します
     *
     * @param orderNoList 注文番号リスト
     * @return 注文番号リストのin句の条件
     */
    public Specification<TOrder> orderNoListContains(List<Integer> orderNoList) {
        return CollectionUtil.isEmpty(orderNoList) ? null : (root, query, cb) -> root.get("orderNo").in(orderNoList);
    }


    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TOrder> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

    /**
     * 会社名の検索条件
     *
     * @param companyName 会社名
     * @return 会社名の検索条件
     */
    public Specification<TOrder> companyNameContains(String companyName) {
        return likeNormalized("companyName", companyName);
    }

    /**
     * 注文ステータスの検索条件
     *
     * @param orderStatus 注文ステータス
     * @return 注文ステータスの検索条件
     */
    public Specification<TOrder> orderStatusContains(String orderStatus) {
        return StringUtil.isEmpty(orderStatus) ? null : (root, query, cb) -> cb.equal(root.get("orderStatus"), orderStatus);
    }

    /**
     * 注文日時の検索条件
     *
     * @param orderDateFrom 注文日時FROM
     * @param orderDateTo   注文日時TO
     * @return 注文日時FROMの検索条件
     */
    public Specification<TOrder> orderDateTimeContains(LocalDateTime orderDateFrom, LocalDateTime orderDateTo) {
        if (orderDateFrom == null && orderDateTo == null) {
            return null;
        }
        if (orderDateTo == null) {
            // orderDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("orderDateTime"), java.sql.Timestamp.valueOf(orderDateFrom));
        }
        if (orderDateFrom == null) {
            // orderDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("orderDateTime"), java.sql.Timestamp.valueOf(orderDateTo));
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("orderDateTime"), java.sql.Timestamp.valueOf(orderDateFrom), java.sql.Timestamp.valueOf(orderDateTo));
    }

    /**
     * 伝票日付の検索条件
     *
     * @param slipDate 伝票日付
     * @return 伝票日付の検索条件
     */
    public Specification<TOrder> slipDateContains(String slipDate) {
        return StringUtil.isEmpty(slipDate) ? null : (root, query, cb) -> cb.equal(root.get("slipDate"), slipDate);
    }

    /**
     * 注文方法の検索条件
     *
     * @param orderRoute 注文方法
     * @return 注文方法の検索条件
     */
    public Specification<TOrder> orderRouteContains(String orderRoute) {
        return StringUtil.isEmpty(orderRoute) ? null : (root, query, cb) -> cb.equal(root.get("orderRoute"), orderRoute);
    }

}
