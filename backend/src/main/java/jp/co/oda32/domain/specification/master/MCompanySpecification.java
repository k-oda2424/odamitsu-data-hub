package jp.co.oda32.domain.specification.master;

import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.specification.CommonSpecification;
import jp.co.oda32.util.StringUtil;
import org.springframework.data.jpa.domain.Specification;

/**
 * 会社マスタ検索条件
 *
 * @author k_oda
 * @since 2019/01/18
 */
public class MCompanySpecification extends CommonSpecification<MCompany> {
    /**
     * 会社番号の検索条件
     *
     * @param companyNo 会社番号
     * @return 会社番号の検索条件
     */
    public Specification<MCompany> companyNoContains(Integer companyNo) {
        return companyNo == null ? null : (root, query, cb) -> cb.equal(root.get("companyNo"), companyNo);
    }

    /**
     * 会社名の検索条件
     *
     * @param companyName 会社名
     * @return 会社名の検索条件
     */
    public Specification<MCompany> companyNameContains(String companyName) {
        return StringUtil.isEmpty(companyName) ? null : (root, query, cb) -> cb.like(root.get("companyName"), "%" + companyName + "%");
    }

    /**
     * 会社種類の検索条件
     *
     * @param companyType 商品名
     * @return キーワードの検索条件
     */
    public Specification<MCompany> companyTypeContains(String companyType) {
        return StringUtil.isEmpty(companyType) ? null : (root, query, cb) -> cb.equal(root.get("companyType"), companyType);
    }


}
