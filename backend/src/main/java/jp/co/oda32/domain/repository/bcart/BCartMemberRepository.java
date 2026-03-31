package jp.co.oda32.domain.repository.bcart;

/**
 * @author k_oda
 * @since 2023/06/09
 */

import jp.co.oda32.domain.model.bcart.BCartMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BCartMemberRepository extends JpaRepository<BCartMember, Long> {

    List<BCartMember> findBySmilePartnerMasterLinkedFalse();

    // b_cart_memberテーブル内でext_idがNULLでなく、かつm_partnerテーブルのpartner_codeには存在しないレコードを探すカスタムクエリ
    // ext_idがpartner_codeで終わる（後方一致）レコードを検索
    @Query(value = "SELECT * FROM b_cart_member bcm WHERE bcm.ext_id IS NOT NULL AND NOT EXISTS (SELECT 1 FROM m_partner mp WHERE bcm.ext_id LIKE '%' || mp.partner_code)", nativeQuery = true)
    List<BCartMember> fetchNonPartneredMembers();
}
