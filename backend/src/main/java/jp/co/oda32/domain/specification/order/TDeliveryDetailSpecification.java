package jp.co.oda32.domain.specification.order;

import jp.co.oda32.domain.model.order.TDeliveryDetail;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

/**
 * 出荷明細テーブル検索条件
 *
 * @author k_oda
 * @since 2018/11/28
 */
public class TDeliveryDetailSpecification extends CommonSpecification<TDeliveryDetail> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<TDeliveryDetail> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 出荷番号の検索条件
     *
     * @param deliveryNo 出荷番号
     * @return 出荷番号の検索条件
     */
    public Specification<TDeliveryDetail> deliveryNoContains(Integer deliveryNo) {
        return deliveryNo == null ? null : (root, query, cb) -> cb.equal(root.get("deliveryNo"), deliveryNo);
    }

    /**
     * 出荷番号リストのin句の条件を返します
     *
     * @param deliveryNoList 出荷番号リスト
     * @return 出荷番号リストのin句の条件
     */
    public Specification<TDeliveryDetail> deliveryNoListContains(List<Integer> deliveryNoList) {
        return CollectionUtil.isEmpty(deliveryNoList) ? null : (root, query, cb) -> root.get("deliveryNo").in(deliveryNoList);
    }

    /**
     * 出荷明細番号の検索条件
     *
     * @param deliveryDetailNo 出荷番号
     * @return 出荷明細番号の検索条件
     */
    public Specification<TDeliveryDetail> deliveryDetailNoContains(Integer deliveryDetailNo) {
        return deliveryDetailNo == null ? null : (root, query, cb) -> cb.equal(root.get("deliveryDetailNo"), deliveryDetailNo);
    }

    /**
     * 注文番号の検索条件
     *
     * @param orderNo 注文番号
     * @return 注文番号の検索条件
     */
    public Specification<TDeliveryDetail> orderNoContains(Integer orderNo) {
        return orderNo == null ? null : (root, query, cb) -> cb.equal(root.get("orderNo"), orderNo);
    }

    /**
     * 注文明細番号の検索条件
     *
     * @param orderDetailNo 注文番号
     * @return 注文明細番号の検索条件
     */
    public Specification<TDeliveryDetail> orderDetailNoContains(Integer orderDetailNo) {
        return orderDetailNo == null ? null : (root, query, cb) -> cb.equal(root.get("orderDetailNo"), orderDetailNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TDeliveryDetail> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

    /**
     * 会社番号リストのin句の条件を返します
     *
     * @param companyNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<TDeliveryDetail> companyNoListContains(List<Integer> companyNoList) {
        return CollectionUtil.isEmpty(companyNoList) ? null : (root, query, cb) -> root.get("companyNo").in(companyNoList);
    }

    /**
     * 出荷明細ステータスの検索条件
     *
     * @param deliveryDetailStatus 出荷明細ステータス
     * @return 出荷明細ステータスの検索条件
     */
    public Specification<TDeliveryDetail> deliveryDetailStatusContains(String deliveryDetailStatus) {
        return StringUtil.isEmpty(deliveryDetailStatus) ? null : (root, query, cb) -> cb.equal(root.get("deliveryDetailStatus"), deliveryDetailStatus);
    }

    public Specification<TDeliveryDetail> orderDetailStatusContains(String orderDetailStatus) {
        return StringUtil.isEmpty(orderDetailStatus) ? null : (root, query, cb) -> cb.equal(root.get("tOrderDetail").get("orderDetailStatus"), orderDetailStatus);
    }

    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<TDeliveryDetail> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品番号リストのin句の条件を返します
     *
     * @param goodsNoList 商品番号リスト
     * @return 商品番号リストのin句の条件
     */
    public Specification<TDeliveryDetail> goodsNoListContains(List<Integer> goodsNoList) {
        return CollectionUtil.isEmpty(goodsNoList) ? null : (root, query, cb) -> root.get("goodsNo").in(goodsNoList);
    }

    /**
     * 出荷日の検索条件
     *
     * @param deliveryDateFrom 出荷日FROM
     * @param deliveryDateTo   出荷日TO
     * @return 出荷日FROMの検索条件
     */
    public Specification<TDeliveryDetail> deliveryDateContains(LocalDate deliveryDateFrom, LocalDate deliveryDateTo) {
        if (deliveryDateFrom == null && deliveryDateTo == null) {
            return null;
        }
        if (deliveryDateTo == null) {
            // deliveryDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("tDelivery").get("deliveryDate"), deliveryDateFrom);
        }
        if (deliveryDateFrom == null) {
            // deliveryDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("tDelivery").get("deliveryDate"), deliveryDateTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("tDelivery").get("deliveryDate"), deliveryDateFrom, deliveryDateTo);
    }

    /**
     * 伝票日付の検索条件
     * yyyyMMddの文字列
     *
     * @param slipDateFrom 伝票日付FROM
     * @param slipDateTo   伝票日付TO
     * @return 伝票日付の検索条件
     */
    public Specification<TDeliveryDetail> slipDateContains(LocalDate slipDateFrom, LocalDate slipDateTo) {
        if (slipDateFrom == null && slipDateTo == null) {
            return null;
        }
        if (slipDateTo == null) {
            // slipDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("tDelivery").get("slipDate"), slipDateFrom);
        }
        if (slipDateFrom == null) {
            // slipDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("tDelivery").get("slipDate"), slipDateTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("tDelivery").get("slipDate"), slipDateFrom, slipDateTo);
    }

    public Specification<TDeliveryDetail> matApiFlgContains(String matApiFlg) {
        return matApiFlg == null ? null : (root, query, cb) -> cb.or(cb.equal(root.get("matApiFlg"), matApiFlg), cb.isNull(root.get("matApiFlg")));
    }

    /**
     * 伝票番号リストのin句の条件を返します
     *
     * @param slipNo 伝票番号リスト
     * @return 伝票番号リストのin句の条件
     */
    public Specification<TDeliveryDetail> slipNoListContains(List<String> slipNo) {
        return CollectionUtil.isEmpty(slipNo) ? null : (root, query, cb) -> root.get("slipNo").in(slipNo);
    }
}
