package jp.co.oda32.domain.repository.master;

/**
 * WSmilePartnerのリポジトリクラス
 *
 * @author k_oda
 * @since 2024/06/12
 */

import jp.co.oda32.domain.model.embeddable.WSmilePartnerPK;
import jp.co.oda32.domain.model.master.WSmilePartner;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.transaction.Transactional;

@Repository
public interface WSmilePartnerRepository extends JpaRepository<WSmilePartner, WSmilePartnerPK> {
    @Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE w_smile_partner", nativeQuery = true)
    void truncateTable();

//    @Query("SELECT wsp FROM WSmilePartner wsp " +
//            "JOIN MPartner mp ON mp.shopNo = wsp.shopNo AND mp.partnerCode = wsp.得意先コード " +
//            "WHERE COALESCE(mp.partnerName, '') <> COALESCE(wsp.得意先名1, '') || COALESCE(wsp.得意先名2, '') " +
//            "OR COALESCE(mp.abbreviatedPartnerName, '') <> COALESCE(wsp.得意先名略称, '')")
//    List<WSmilePartner> findRecordsForUpdate();
}
