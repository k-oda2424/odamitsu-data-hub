package jp.co.oda32.domain.repository.master;

import jp.co.oda32.domain.model.master.MPartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 得意先マスタ(m_partner)のリポジトリインターフェース
 *
 * @author k_oda
 * @since 2018/11/23
 */
public interface PartnerRepository extends JpaRepository<MPartner, Integer>, JpaSpecificationExecutor<MPartner> {
    List<MPartner> findAll();

    List<MPartner> findByPartnerName(@Param("partnerName") String partnerName);

    MPartner getByShopNoAndPartnerCode(@Param("shopNo") Integer shopNo, @Param("partnerCode") String partnerCode);

    List<MPartner> findByParentPartnerNo(@Param("parentPartnerNo") int parentPartnerNo);

}
