package jp.co.oda32.domain.model.finance;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * マネーフォワードクラウド会計 API OAuth2 クライアント設定。
 * 通常 1 レコードのみ運用（del_flg=0 が有効）。
 */
@Data
@Builder
@Entity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "m_mf_oauth_client")
public class MMfOauthClient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "client_id", nullable = false)
    private String clientId;

    /** AES-256 暗号化済み Client Secret。CryptoUtil で復号する。 */
    @Column(name = "client_secret_enc", nullable = false)
    private String clientSecretEnc;

    @Column(name = "redirect_uri", nullable = false)
    private String redirectUri;

    @Column(name = "scope", nullable = false)
    private String scope;

    @Column(name = "authorize_url", nullable = false)
    private String authorizeUrl;

    @Column(name = "token_url", nullable = false)
    private String tokenUrl;

    @Column(name = "api_base_url", nullable = false)
    private String apiBaseUrl;

    @Column(name = "del_flg", nullable = false)
    @Builder.Default
    private String delFlg = "0";

    @Column(name = "add_date_time", nullable = false)
    private Timestamp addDateTime;

    @Column(name = "add_user_no")
    private Integer addUserNo;

    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;

    @Column(name = "modify_user_no")
    private Integer modifyUserNo;

    /**
     * JPA ライフサイクルで監査フィールドを自動補完 (B-W3)。
     * サービス層で明示 set した値があればそちらを優先する。
     */
    @PrePersist
    void prePersist() {
        if (addDateTime == null) addDateTime = Timestamp.from(java.time.Instant.now());
    }

    @PreUpdate
    void preUpdate() {
        modifyDateTime = Timestamp.from(java.time.Instant.now());
    }
}
