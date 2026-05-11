package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.MPartnerGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MPartnerGroupRepository extends JpaRepository<MPartnerGroup, Integer> {

    /**
     * partnerGroupId 単体で active group を取得（partnerCodes を JOIN FETCH）。
     * 受注一覧グループ検索で Service 層から LazyInitializationException なく partnerCodes を取得するため使用。
     */
    @Query("SELECT DISTINCT g FROM MPartnerGroup g "
            + "LEFT JOIN FETCH g.partnerCodes "
            + "WHERE g.partnerGroupId = :id AND g.delFlg = '0'")
    Optional<MPartnerGroup> findActiveByIdFetchMembers(@Param("id") Integer id);


    /**
     * ショップ単位で active group を取得 (LAZY コレクションを 1 クエリで JOIN FETCH)。
     * SF-19: N+1 解消 / SF-20: del_flg='0' 絞り込み
     */
    @Query("SELECT DISTINCT g FROM MPartnerGroup g "
            + "LEFT JOIN FETCH g.partnerCodes "
            + "WHERE g.shopNo = :shopNo AND g.delFlg = '0' "
            + "ORDER BY g.groupName ASC")
    List<MPartnerGroup> findActiveByShopNoFetchMembers(@Param("shopNo") Integer shopNo);

    /**
     * 全 active group (admin 用) — partnerCodes を JOIN FETCH。
     */
    @Query("SELECT DISTINCT g FROM MPartnerGroup g "
            + "LEFT JOIN FETCH g.partnerCodes "
            + "WHERE g.delFlg = '0' "
            + "ORDER BY g.shopNo ASC, g.groupName ASC")
    List<MPartnerGroup> findAllActiveFetchMembers();
}
