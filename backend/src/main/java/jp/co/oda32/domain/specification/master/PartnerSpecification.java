package jp.co.oda32.domain.specification.master;

import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

/**
 * 得意先検索条件
 *
 * @author k_oda
 * @since 2018/11/23
 */
public class PartnerSpecification extends CommonSpecification<MPartner> {
    /**
     * 得意先番号の検索条件
     *
     * @param partnerNo 得意先番号
     * @return 得意先番号の検索条件
     */
    public Specification<MPartner> partnerNoContains(Integer partnerNo) {
        return partnerNo == null ? null : (root, query, cb) -> cb.equal(root.get("partnerNo"), partnerNo);
    }

    /**
     * 得意先番号リストのin句の条件を返します
     *
     * @param partnerNoList 得意先番号リスト
     * @return 得意先番号リストのin句の条件
     */
    public Specification<MPartner> partnerNoListContains(List<Integer> partnerNoList) {
        return CollectionUtil.isEmpty(partnerNoList) ? null : (root, query, cb) -> root.get("partnerNo").in(partnerNoList);
    }

    /**
     * 親得意先有りの検索条件
     *
     * @return 親得意先有りの検索条件
     */
    public Specification<MPartner> hasParentPartnerContains() {
        return (root, query, cb) -> cb.isNotNull(root.get("parentPartnerNo"));
    }

    /**
     * 親得意先番号の検索条件
     *
     * @param parentPartnerNo 得意先番号
     * @return 得意先番号の検索条件
     */
    public Specification<MPartner> parentPartnerNoContains(Integer parentPartnerNo) {
        return parentPartnerNo == null ? null : (root, query, cb) -> cb.equal(root.get("parentPartnerNo"), parentPartnerNo);
    }

    /**
     * 得意先名の検索条件
     *
     * @param partnerName 得意先名
     * @return 得意先名の検索条件
     */
    public Specification<MPartner> partnerNameContains(String partnerName) {
        return StringUtil.isEmpty(partnerName) ? null : (root, query, cb) -> cb.like(root.get("partnerName"), "%" + partnerName + "%");
    }

    /**
     * 得意先コードの検索条件
     *
     * @param partnerCode 得意先コード
     * @return 得意先コードの検索条件
     */
    public Specification<MPartner> partnerCodeContains(String partnerCode) {
        return StringUtil.isEmpty(partnerCode) ? null : (root, query, cb) -> cb.equal(root.get("partnerCode"), partnerCode);
    }

    /**
     * 得意先コードリストのin句の条件を返します
     *
     * @param partnerCodeList 得意先コードリスト
     * @return 得意先コードリストのin句の条件
     */
    public Specification<MPartner> partnerCodeListContains(List<String> partnerCodeList) {
        return CollectionUtil.isEmpty(partnerCodeList) ? null : (root, query, cb) -> root.get("partnerCode").in(partnerCodeList);
    }

    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<MPartner> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }
    /**
     * 注文日時の検索条件
     *
     * @param orderDateFrom 注文日時FROM
     * @param orderDateTo   注文日時TO
     * @return 注文日時FROMの検索条件
     */
    public Specification<MPartner> lastOrderDateContains(LocalDate orderDateFrom, LocalDate orderDateTo) {
        if (orderDateFrom == null && orderDateTo == null) {
            return null;
        }
        if (orderDateTo == null) {
            // orderDateFromだけ入力有
            return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("lastOrderDate"), orderDateFrom);
        }
        if (orderDateFrom == null) {
            // orderDateToだけ入力有
            return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("lastOrderDate"), orderDateTo);
        }
        // 両方入力有
        return (root, query, cb) -> cb.between(root.get("lastOrderDate"), orderDateFrom, orderDateTo);
    }
}
