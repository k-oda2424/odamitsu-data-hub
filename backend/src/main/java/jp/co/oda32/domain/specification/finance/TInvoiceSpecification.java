package jp.co.oda32.domain.specification.finance;

import jp.co.oda32.domain.model.finance.TInvoice;
import jp.co.oda32.domain.specification.CommonSpecification;
import org.springframework.data.jpa.domain.Specification;

/**
 * 請求テーブル検索条件
 *
 * @since 2025/05/21
 */
public class TInvoiceSpecification extends CommonSpecification<TInvoice> {
    /**
     * 締月の検索条件
     * yyyy/MMの形式で検索できるようにする
     *
     * @param closingDate 締月（yyyy/MM形式）
     * @return 締月の検索条件
     */
    public Specification<TInvoice> closingDateContains(String closingDate) {
        return likePrefixNormalized("closingDate", closingDate);
    }

    /**
     * ショップ番号の検索条件
     *
     * @param shopNo ショップ番号
     * @return ショップ番号の検索条件
     */
    public Specification<TInvoice> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * 得意先コードの検索条件（前方一致）
     *
     * @param partnerCode 得意先コード
     * @return 得意先コードの検索条件
     */
    public Specification<TInvoice> partnerCodeContains(String partnerCode) {
        return likePrefixNormalized("partnerCode", partnerCode);
    }

    /**
     * 顧客名の検索条件
     *
     * @param partnerName 顧客名
     * @return 顧客名の検索条件
     */
    public Specification<TInvoice> partnerNameContains(String partnerName) {
        return likeNormalized("partnerName", partnerName);
    }

    /**
     * 請求IDの検索条件
     *
     * @param invoiceId 請求ID
     * @return 請求IDの検索条件
     */
    public Specification<TInvoice> invoiceIdContains(Integer invoiceId) {
        return invoiceId == null ? null : (root, query, cb) -> cb.equal(root.get("invoiceId"), invoiceId);
    }

    /**
     * 前回請求残高の範囲検索条件
     *
     * @param minBalance 最小残高
     * @param maxBalance 最大残高
     * @return 前回請求残高の範囲検索条件
     */
    public Specification<TInvoice> previousBalanceContains(Double minBalance, Double maxBalance) {
        if (minBalance == null && maxBalance == null) {
            return null;
        }
        if (maxBalance == null) {
            // minBalanceだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("previousBalance"), minBalance);
        }
        if (minBalance == null) {
            // maxBalanceだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("previousBalance"), maxBalance);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("previousBalance"), minBalance, maxBalance);
    }

    /**
     * 今回請求額の範囲検索条件
     *
     * @param minAmount 最小金額
     * @param maxAmount 最大金額
     * @return 今回請求額の範囲検索条件
     */
    public Specification<TInvoice> currentBillingAmountContains(Double minAmount, Double maxAmount) {
        if (minAmount == null && maxAmount == null) {
            return null;
        }
        if (maxAmount == null) {
            // minAmountだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("currentBillingAmount"), minAmount);
        }
        if (minAmount == null) {
            // maxAmountだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("currentBillingAmount"), maxAmount);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("currentBillingAmount"), minAmount, maxAmount);
    }
}
