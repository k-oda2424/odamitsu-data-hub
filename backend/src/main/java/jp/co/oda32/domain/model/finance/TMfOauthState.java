package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * MF OAuth2 認可フローの CSRF 防止 state + PKCE code_verifier を DB 永続化するエンティティ。
 * <p>
 * 旧実装は ConcurrentHashMap でインメモリだったが、マルチ JVM / 再起動で state が消失する
 * 問題があったため DB バックに移行。TTL 10 分で sweep される。
 *
 * @since 2026-04-21 (B-3 / B-4)
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "t_mf_oauth_state")
public class TMfOauthState {

    @Id
    @Column(name = "state")
    private String state;

    @Column(name = "user_no")
    private Integer userNo;

    /** PKCE S256 の verifier。code_challenge は SHA-256(verifier) を base64url-encode したもの。 */
    @Column(name = "code_verifier", nullable = false)
    private String codeVerifier;

    @Column(name = "expires_at", nullable = false)
    private Timestamp expiresAt;

    @Column(name = "add_date_time", nullable = false)
    private Timestamp addDateTime;
}
