package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.TMfOauthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;

@Repository
public interface TMfOauthStateRepository extends JpaRepository<TMfOauthState, String> {

    /** 期限切れの state を sweep する (TTL 超過行の物理削除)。 */
    @Modifying
    @Query("DELETE FROM TMfOauthState s WHERE s.expiresAt < :now")
    int deleteExpired(@Param("now") Timestamp now);
}
