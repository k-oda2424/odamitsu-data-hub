package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.master.MCompany;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 会社マスタ(m_company)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/11/23
 */
public interface CompanyRepository extends JpaRepository<MCompany, Integer>, JpaSpecificationExecutor<MCompany> {

    MCompany getByPartnerNo(@Param("partnerNo") Integer partnerNo);

    MCompany getByShopNoAndCompanyType(@Param("shopNo") Integer shopNo, @Param("companyType") String companyType);

    List<MCompany> findByShopNoAndCompanyTypeAndDelFlg(@Param("shopNo") Integer shopNo, @Param("companyType") String companyType, @Param("delFlg") String delFlg);
}
