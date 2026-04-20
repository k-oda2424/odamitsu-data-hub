package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.TMfOauthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Optional;

@Repository
public interface TMfOauthTokenRepository extends JpaRepository<TMfOauthToken, Long> {

    /** 指定クライアントの有効トークン（del_flg='0'）。通常 1 件。 */
    Optional<TMfOauthToken> findFirstByClientIdAndDelFlgOrderByIdDesc(Integer clientId, String delFlg);

    /** 新規トークン保存前に旧レコードを一括論理削除する。 */
    @Modifying
    @Query("UPDATE TMfOauthToken t SET t.delFlg='1', t.modifyDateTime=:now, t.modifyUserNo=:userNo " +
            "WHERE t.clientId = :clientId AND t.delFlg='0'")
    int softDeleteActiveTokens(@Param("clientId") Integer clientId,
                               @Param("now") Timestamp now,
                               @Param("userNo") Integer userNo);
}
