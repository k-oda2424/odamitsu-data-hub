package jp.co.oda32.domain.specification.master;

import jp.co.oda32.domain.model.master.MShop;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.CollectionUtil;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

/**
 * ショップ明細テーブル検索条件
 *
 * @author k_oda
 * @since 2019/07/17
 */
public class MShopSpecification extends CommonSpecification<MShop> {
    /**
     * ショップ番号の検索条件
     *
     * @param shopNo 商品番号
     * @return ショップ番号の検索条件
     */
    public Specification<MShop> shopNoContains(Integer shopNo) {
        return shopNo == null ? null : (root, query, cb) -> cb.equal(root.get("shopNo"), shopNo);
    }

    /**
     * ショップ番号リストのin句の条件を返します
     *
     * @param shopNoList ショップ番号リスト
     * @return ショップ番号リストのin句の条件
     */
    public Specification<MShop> shopNoListContains(List<Integer> shopNoList) {
        return CollectionUtil.isEmpty(shopNoList) ? null : (root, query, cb) -> root.get("shopNo").in(shopNoList);
    }

    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<MShop> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

}
