package jp.co.oda32.domain.specification.master;

import jp.co.oda32.domain.model.master.MTaxRate;
import jp.co.oda32.domain.specification.CommonSpecification;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

/**
 * 消費税率マスタテーブル検索条件
 *
 * @author k_oda
 * @since 2020/01/21
 */
public class MTaxRateSpecification extends CommonSpecification<MTaxRate> {
    /**
     * 期間の検索条件(今日>=期間FROM)
     *
     * @return 期間の検索条件
     */
    public Specification<MTaxRate> periodFromContains() {
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("periodFrom"), LocalDate.now());
    }

    /**
     * 期間の検索条件(今日<=期間To)
     *
     * @return 期間の検索条件
     */
    public Specification<MTaxRate> periodToContains() {
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("periodTo"), LocalDate.now());
    }
}
