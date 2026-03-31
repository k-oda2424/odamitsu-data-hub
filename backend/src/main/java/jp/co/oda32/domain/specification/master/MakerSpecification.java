package jp.co.oda32.domain.specification.master;

import jp.co.oda32.domain.model.master.MMaker;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

/**
 * メーカー検索条件
 *
 * @author k_oda
 * @since 2018/04/16
 */
public class MakerSpecification extends CommonSpecification<MMaker> {
    /**
     * メーカー番号の検索条件
     *
     * @param makerNo メーカー番号
     * @return メーカー番号の検索条件
     */
    public Specification<MMaker> makerNoContains(Integer makerNo) {
        return makerNo == null ? null : (root, query, cb) -> cb.equal(root.get("makerNo"), makerNo);
    }

    /**
     * メーカー名の検索条件
     *
     * @param makerName メーカー名
     * @return メーカー名の検索条件
     */
    public Specification<MMaker> makerNameContains(String makerName) {
        return StringUtil.isEmpty(makerName) ? null : (root, query, cb) -> cb.like(root.get("makerName"), "%" + makerName + "%");
    }

}
