package jp.co.oda32.domain.repository.finance;

import jp.co.oda32.domain.model.finance.TMfOauthState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;

@Repository
public interface TMfOauthStateRepository extends JpaRepository<TMfOauthState, String> {

    /** 期限切れの state を sweep する (TTL 超過行の物理削除)。 */
    @Modifying
    @Query("DELETE FROM TMfOauthState s WHERE s.expiresAt < :now")
    int deleteExpired(@Param("now") Timestamp now);

    /**
     * SF-06: state を atomic に取り出して削除する (DELETE ... RETURNING)。
     * <p>
     * read → delete を 2 つの SQL に分けると、TOCTOU race で同じ state が
     * 2 回消費可能になるため、単一 SQL で消費を直列化する。
     * <p>
     * 戻り値は {@code [[user_no(Integer or null), code_verifier(String), expires_at(Timestamp)]]}
     * の List。state は PK 相当なので最大 1 行。
     * <p>
     * Hibernate 6 + {@code @Modifying} + multi-column {@code RETURNING} の組み合わせで
     * {@code Optional<Object[]>} を返すと結果が二重 array にラップされる ClassCastException
     * バグがあるため、{@code List<Object[]>} で受けて呼び出し側で先頭を取り出す。
     */
    @Modifying
    @Query(value = "DELETE FROM t_mf_oauth_state WHERE state = :state "
            + "RETURNING user_no, code_verifier, expires_at", nativeQuery = true)
    List<Object[]> deleteAndReturnByState(@Param("state") String state);
}
