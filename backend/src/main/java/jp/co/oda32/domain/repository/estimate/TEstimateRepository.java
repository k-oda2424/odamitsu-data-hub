package jp.co.oda32.domain.repository.estimate;

import jp.co.oda32.domain.model.estimate.TEstimate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * 見積(t_estimate)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2022/10/24
 */
public interface TEstimateRepository extends JpaRepository<TEstimate, Integer>, JpaSpecificationExecutor<TEstimate> {
    @Query(value = "with price_change_plan as (select ppc.estimate_no" +
            "                           from m_partner_goods_price_change_plan ppc" +
            "                           where parent_change_plan_no is not null" +
            "                             and exists(select 'X'" +
            "                                        from m_partner_goods_price_change_plan parent_ppc" +
            "                                        where ppc.parent_change_plan_no = parent_ppc.partner_goods_price_change_plan_no" +
            "                                          and parent_ppc.estimate_created))" +
            "select e.*" +
            "from t_estimate e" +
            "         join price_change_plan pcp on e.estimate_no = pcp.estimate_no" +
            " where e.estimate_status in ('00', '20');", nativeQuery = true)
    List<TEstimate> findChildrenEstimate();
}
