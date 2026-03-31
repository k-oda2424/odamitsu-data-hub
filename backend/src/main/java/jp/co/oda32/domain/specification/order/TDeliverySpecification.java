package jp.co.oda32.domain.specification.order;

import jp.co.oda32.domain.model.order.TDelivery;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

/**
 * 出荷テーブル検索条件
 *
 * @author k_oda
 * @since 2018/11/23
 */
public class TDeliverySpecification extends CommonSpecification<TDelivery> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<TDelivery> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 出荷番号リストのin句の条件を返します
     *
     * @param deliveryNoList 出荷番号リスト
     * @return 出荷番号リストのin句の条件
     */
    public Specification<TDelivery> deliveryNoListContains(List<Integer> deliveryNoList) {
        return CollectionUtil.isEmpty(deliveryNoList) ? null : (root, query, cb) -> root.get("deliveryNo").in(deliveryNoList);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TDelivery> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

    /**
     * 会社名の検索条件
     *
     * @param companyName 会社名
     * @return 会社名の検索条件
     */
    public Specification<TDelivery> companyNameContains(String companyName) {
        return StringUtil.isEmpty(companyName) ? null : (root, query, cb) -> cb.like(root.get("companyName"), "%" + companyName + "%");
    }

    /**
     * 出荷ステータスの検索条件
     *
     * @param orderStatus 出荷ステータス
     * @return 出荷ステータスの検索条件
     */
    public Specification<TDelivery> orderStatusContains(String orderStatus) {
        return StringUtil.isEmpty(orderStatus) ? null : (root, query, cb) -> cb.equal(root.get("orderStatus"), orderStatus);
    }

    /**
     * 出荷日時の検索条件
     *
     * @param orderDateFrom 出荷日時FROM
     * @param orderDateTo   出荷日時TO
     * @return 出荷日時FROMの検索条件
     */
    public Specification<TDelivery> deliveryDateContains(LocalDate orderDateFrom, LocalDate orderDateTo) {
        if (orderDateFrom == null && orderDateTo == null) {
            return null;
        }
        if (orderDateTo == null) {
            // orderDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("deliveryDate"), orderDateFrom);
        }
        if (orderDateFrom == null) {
            // orderDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("deliveryDate"), orderDateTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("deliveryDate"), orderDateFrom, orderDateTo);
    }

    /**
     * 伝票日付の検索条件
     *
     * @param slipDate 伝票日付
     * @return 伝票日付の検索条件
     */
    public Specification<TDelivery> slipDateContains(String slipDate) {
        return StringUtil.isEmpty(slipDate) ? null : (root, query, cb) -> cb.equal(root.get("slipDate"), slipDate);
    }

    /**
     * 伝票番号リストのin句の条件を返します
     *
     * @param slipNoList 伝票番号リスト
     * @return 伝票番号リストのin句の条件
     */
    public Specification<TDelivery> slipNoListContains(List<String> slipNoList) {
        return CollectionUtil.isEmpty(slipNoList) ? null : (root, query, cb) -> root.get("slipNo").in(slipNoList);
    }
}
