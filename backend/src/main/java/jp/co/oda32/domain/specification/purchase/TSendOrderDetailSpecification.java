package jp.co.oda32.domain.specification.purchase;

import jp.co.oda32.domain.model.purchase.TSendOrderDetail;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 仕入明細テーブル検索条件
 *
 * @author k_oda
 * @since 2019/06/02
 */
public class TSendOrderDetailSpecification extends CommonSpecification<TSendOrderDetail> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<TSendOrderDetail> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 仕入先番号の検索条件
     *
     * @param supplierNo メーカー番号
     * @return メーカー番号の検索条件
     */
    public Specification<TSendOrderDetail> supplierNoContains(Integer supplierNo) {
        return supplierNo == null ? null : (root, query, cb) -> cb.equal(root.get("tSendOrder").get("supplierNo"), supplierNo);
    }

    /**
     * 倉庫番号の検索条件
     *
     * @param warehouseNo 倉庫番号
     * @return 倉庫番号の検索条件
     */
    public Specification<TSendOrderDetail> warehouseNoContains(Integer warehouseNo) {
        return warehouseNo == null ? null : (root, query, cb) -> cb.equal(root.get("warehouseNo"), warehouseNo);
    }

    /**
     * 発注明細ステータスの検索条件
     *
     * @param sendOrderDetailStatus 仕入明細ステータス
     * @return 仕入明細ステータスの検索条件
     */
    public Specification<TSendOrderDetail> sendOrderDetailStatusContains(String sendOrderDetailStatus) {
        return StringUtil.isEmpty(sendOrderDetailStatus) ? null : (root, query, cb) -> cb.equal(root.get("sendOrderDetailStatus"), sendOrderDetailStatus);
    }

    /**
     * 複数の発注明細ステータスの検索条件
     *
     * @param sendOrderDetailStatusList 仕入明細ステータス
     * @return 仕入明細ステータスの検索条件
     */
    public Specification<TSendOrderDetail> sendOrderDetailStatusListContains(List<String> sendOrderDetailStatusList) {
        return CollectionUtil.isEmpty(sendOrderDetailStatusList) ? null : (root, query, cb) -> root.get("sendOrderDetailStatus").in(sendOrderDetailStatusList);
    }

    /**
     * 発注日の検索条件
     *
     * @param sendOrderDateFrom 発注日FROM
     * @param sendOrderDateTo   発注日TO
     * @return 発注日FROMの検索条件
     */
    public Specification<TSendOrderDetail> sendOrderDateContains(LocalDateTime sendOrderDateFrom, LocalDateTime sendOrderDateTo) {
        if (sendOrderDateFrom == null && sendOrderDateTo == null) {
            return null;
        }
        if (sendOrderDateTo == null) {
            // sendOrderDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("tSendOrder").get("sendOrderDateTime"), sendOrderDateFrom);
        }
        if (sendOrderDateFrom == null) {
            // sendOrderDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("tSendOrder").get("sendOrderDateTime"), sendOrderDateTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("tSendOrder").get("sendOrderDateTime"), sendOrderDateFrom, sendOrderDateTo);
    }

}
