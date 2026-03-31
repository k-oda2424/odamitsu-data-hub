package jp.co.oda32.domain.specification.master;

import jp.co.oda32.domain.model.master.MSmartMat;
import jp.co.oda32.domain.specification.CommonSpecification;
import org.springframework.data.jpa.domain.Specification;

/**
 * スマートマット管理マスタ検索条件
 *
 * @author k_oda
 * @since 2020/01/09
 */
public class MSmartMatSpecification extends CommonSpecification<MSmartMat> {
    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<MSmartMat> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

}
