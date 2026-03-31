package jp.co.oda32.domain.specification.order;

import jp.co.oda32.domain.model.order.TOrderDetail;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 注文明細テーブル検索条件
 *
 * @author k_oda
 * @since 2018/11/28
 */
public class TOrderDetailSpecification extends CommonSpecification<TOrderDetail> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<TOrderDetail> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 注文番号の検索条件
     *
     * @param orderNo 注文番号
     * @return 注文番号の検索条件
     */
    public Specification<TOrderDetail> orderNoContains(Integer orderNo) {
        return orderNo == null ? null : (root, query, cb) -> cb.equal(root.get("orderNo"), orderNo);
    }

    /**
     * 注文番号リストのin句の条件を返します
     *
     * @param orderNoList 注文番号リスト
     * @return 注文番号リストのin句の条件
     */
    public Specification<TOrderDetail> orderNoListContains(List<Integer> orderNoList) {
        return CollectionUtil.isEmpty(orderNoList) ? null : (root, query, cb) -> root.get("orderNo").in(orderNoList);
    }

    /**
     * 注文明細番号の検索条件
     *
     * @param orderDetailNo 注文番号
     * @return 注文明細番号の検索条件
     */
    public Specification<TOrderDetail> orderDetailNoContains(Integer orderDetailNo) {
        return orderDetailNo == null ? null : (root, query, cb) -> cb.equal(root.get("orderDetailNo"), orderDetailNo);
    }

    /**
     * 伝票番号の検索条件
     *
     * @param slipNo 伝票
     * @return 伝票番号の検索条件
     */
    public Specification<TOrderDetail> slipNoContains(String slipNo) {
        return StringUtil.isEmpty(slipNo) ? null : (root, query, cb) -> cb.equal(root.get("tDelivery").get("slipNo"), slipNo);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<TOrderDetail> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

    /**
     * 注文明細ステータスの検索条件
     *
     * @param orderDetailStatus 注文明細ステータス
     * @return 注文明細ステータスの検索条件
     */
    public Specification<TOrderDetail> orderDetailStatusContains(String orderDetailStatus) {
        return StringUtil.isEmpty(orderDetailStatus) ? null : (root, query, cb) -> cb.equal(root.get("orderDetailStatus"), orderDetailStatus);
    }

    /**
     * 商品番号の検索条件
     *
     * @param goodsNo 商品番号
     * @return 商品番号の検索条件
     */
    public Specification<TOrderDetail> goodsNoContains(Integer goodsNo) {
        return goodsNo == null ? null : (root, query, cb) -> cb.equal(root.get("goodsNo"), goodsNo);
    }

    /**
     * 商品コードの検索条件
     *
     * @param goodsCode 商品コード
     * @return 商品コードの検索条件
     */
    public Specification<TOrderDetail> goodsCodeContains(String goodsCode) {
        return StringUtil.isEmpty(goodsCode) ? null : (root, query, cb) -> cb.like(root.get("goodsCode"), "%" + goodsCode);
    }

    /**
     * 商品名の検索条件
     *
     * @param goodsName 商品名
     * @return 商品名の検索条件
     */
    public Specification<TOrderDetail> goodsNameContains(String goodsName) {
        return StringUtil.isEmpty(goodsName) ? null : (root, query, cb) -> cb.like(root.get("goodsName"), "%" + goodsName + "%");
    }

    /**
     * 注文日の検索条件
     *
     * @param orderDateTimeFrom 注文日FROM
     * @param orderDateTimeTo   注文日TO
     * @return 注文日FROMの検索条件
     */
    public Specification<TOrderDetail> orderDateTimeContains(LocalDateTime orderDateTimeFrom, LocalDateTime orderDateTimeTo) {
        if (orderDateTimeFrom == null && orderDateTimeTo == null) {
            return null;
        }
        if (orderDateTimeTo == null) {
            // orderDateTimeFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("tOrder").get("orderDateTime"), orderDateTimeFrom);
        }
        if (orderDateTimeFrom == null) {
            // orderDateTimeToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("tOrder").get("orderDateTime"), orderDateTimeTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("tOrder").get("orderDateTime"), orderDateTimeFrom, orderDateTimeTo);
    }

    /**
     * 伝票日付の検索条件
     *
     * @param slipDateFrom 伝票日付FROM
     * @param slipDateTo   伝票日付TO
     * @return 伝票日付の検索条件
     */
    public Specification<TOrderDetail> slipDateContains(LocalDate slipDateFrom, LocalDate slipDateTo) {
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

    /**
     * 注文明細ステータスリストのin句の条件を返します
     *
     * @param orderDetailStatuses 注文明細ステータスリスト
     * @return 注文明細ステータスリストのin句の条件
     */
    public Specification<TOrderDetail> orderDetailStatusListContains(String... orderDetailStatuses) {
        if (orderDetailStatuses == null || Arrays.stream(orderDetailStatuses).allMatch(StringUtil::isEmpty)) {
            return null;
        }
        return (root, query, cb) -> root.get("orderDetailStatus").in((Object[]) orderDetailStatuses);
    }

    /**
     * 得意先番号リストのin句の条件を返します
     *
     * @param partnerNos 得意先番号リスト
     * @return 得意先番号リストのin句の条件
     */
    public Specification<TOrderDetail> partnerNoListContains(List<Integer> partnerNos) {
        return CollectionUtil.isEmpty(partnerNos) ? null : (root, query, cb) -> root.get("tOrder").get("partnerNo").in(partnerNos);
    }
}
