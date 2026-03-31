package jp.co.oda32.domain.specification.bcart;

import jp.co.oda32.domain.model.bcart.BCartOrder;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

/**
 * 見積明細テーブル検索条件
 *
 * @author k_oda
 * @since 2023/03/21
 */
public class BCartOrderSpecification {
    /**
     * ショップ番号の検索条件
     *
     * @param status ステータス
     * @return statusの検索条件
     */
    public Specification<BCartOrder> statusContains(String status) {
        return StringUtil.isEmpty(status) ? null : (root, query, cb) -> cb.equal(root.get("estimateDetailStatus"), status);
    }

}
