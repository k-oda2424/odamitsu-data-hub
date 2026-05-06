package jp.co.oda32.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * MF OAuth クライアント / トークン専用 AES-256 (GCM) 暗号化ユーティリティ (P1-05 案 C.3)。
 * <p>
 * 既存 {@link CryptoUtil} (社内 password 等) とは <b>別の鍵 / salt</b> を使う。
 * これにより、リポジトリに commit 済みの dev fallback (CryptoUtil 用) が万一 prod で
 * 流入しても、MF OAuth client_secret / access_token / refresh_token は復号できないようにする。
 *
 * <p>対象カラム:
 * <ul>
 *   <li>{@code m_mf_oauth_client.client_secret_enc}</li>
 *   <li>{@code t_mf_oauth_token.access_token_enc}</li>
 *   <li>{@code t_mf_oauth_token.refresh_token_enc}</li>
 * </ul>
 *
 * <p>{@code app.crypto.oauth-key} / {@code app.crypto.oauth-salt} は <b>dev / prod 共通で env 必須</b>。
 * 未設定なら起動時に fail-fast する。鍵生成 / セットアップ手順は
 * {@code claudedocs/runbook-mf-oauth-keys.md} を参照。
 *
 * @since 2026/05/04
 */
@Component
@Log4j2
public class OauthCryptoUtil {

    private final String password;
    private final String salt;
    private TextEncryptor encryptor;

    public OauthCryptoUtil(@Value("${app.crypto.oauth-key:}") String password,
                           @Value("${app.crypto.oauth-salt:}") String salt) {
        this.password = password;
        this.salt = salt;
    }

    @PostConstruct
    void init() {
        if (password == null || password.isBlank() || password.length() < 16) {
            throw new IllegalStateException(
                    "app.crypto.oauth-key が未設定または短すぎます（16 文字以上必須）。"
                            + "環境変数 APP_CRYPTO_OAUTH_KEY を設定してください。"
                            + "詳細は claudedocs/runbook-mf-oauth-keys.md (Step 1-2) を参照。");
        }
        if (salt == null || salt.isBlank()) {
            throw new IllegalStateException(
                    "app.crypto.oauth-salt が未設定です。16 byte 以上の hex 文字列"
                            + "（例: 0123456789abcdef0123456789abcdef）を環境変数 APP_CRYPTO_OAUTH_SALT で設定してください。"
                            + "詳細は claudedocs/runbook-mf-oauth-keys.md (Step 1-2) を参照。");
        }
        if (!HEX_ONLY.matcher(salt).matches()) {
            throw new IllegalStateException(
                    "app.crypto.oauth-salt は hex 文字列 (0-9a-f) のみ受け付けます。現在値は hex 以外を含みます。");
        }
        if (salt.length() < 16) {
            throw new IllegalStateException(
                    "app.crypto.oauth-salt は 16 文字以上 (= 8 byte) の hex 必須。推奨 32 文字 (16 byte)。");
        }
        // CryptoUtil と同じ AES-256/GCM + PBKDF2 + Hex 出力。鍵 / salt のみ別。
        this.encryptor = Encryptors.delux(password, salt);
        log.info("OauthCryptoUtil 初期化完了（MF OAuth 専用 AES-256 GCM, Hex 出力）");
    }

    /** 平文を暗号化して Hex 文字列を返す。null は null を返す。 */
    public String encrypt(String plain) {
        if (plain == null) return null;
        return encryptor.encrypt(plain);
    }

    /** Hex 暗号文を復号して平文を返す。null は null を返す。 */
    public String decrypt(String encrypted) {
        if (encrypted == null) return null;
        return encryptor.decrypt(encrypted);
    }

    private static final java.util.regex.Pattern HEX_ONLY = java.util.regex.Pattern.compile("^[0-9a-fA-F]+$");
}
