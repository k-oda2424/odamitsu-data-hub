package jp.co.oda32.util;

import jakarta.annotation.PostConstruct;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/**
 * AES-256 (GCM) ベースの対称暗号化ユーティリティ。
 * <p>
 * {@code app.crypto.key} (パスフレーズ) と {@code app.crypto.salt} (hex 文字列) を
 * application.yml or 環境変数で設定する。主に MF OAuth Client Secret / access_token /
 * refresh_token の DB 保存時に利用する。
 *
 * <ul>
 *   <li>鍵が未設定 or 長さ不足なら起動時に fail fast（ログに鍵は出さない）</li>
 *   <li>Spring Security の {@link Encryptors#delux} を使用（PBKDF2 + AES/GCM）</li>
 *   <li>encrypt/decrypt の出力は <b>Hex エンコード文字列</b>。内部実装は
 *       {@code HexEncodingTextEncryptor(stronger(...))} で、出力は IV(16B) + CipherText + GMAC tag を
 *       連結した hex。Base64 ではない。</li>
 *   <li>salt は <b>hex 文字列必須</b> ({@code PBKDF2} 用)。Base64 や非 hex 文字を渡すと
 *       実行時に {@code IllegalArgumentException: Detected a Non-hex character} で起動失敗する。</li>
 * </ul>
 *
 * @since 2026/04/20
 */
@Component
@Log4j2
public class CryptoUtil {

    private final String password;
    private final String salt;
    private TextEncryptor encryptor;

    public CryptoUtil(@Value("${app.crypto.key:}") String password,
                      @Value("${app.crypto.salt:}") String salt) {
        this.password = password;
        this.salt = salt;
    }

    @PostConstruct
    void init() {
        if (password == null || password.isBlank() || password.length() < 16) {
            throw new IllegalStateException(
                    "app.crypto.key が未設定または短すぎます（16 文字以上必須）。環境変数 APP_CRYPTO_KEY を設定してください。");
        }
        if (salt == null || salt.isBlank()) {
            throw new IllegalStateException(
                    "app.crypto.salt が未設定です。16 byte 以上の hex 文字列（例: 0123456789abcdef）を環境変数 APP_CRYPTO_SALT で設定してください。");
        }
        // hex 以外が混ざっていると delux() 内部で PBKDF2 salt デコード時に例外になる。
        // 起動時に fail-fast してメッセージを明確にする (B-W1)。
        if (!HEX_ONLY.matcher(salt).matches()) {
            throw new IllegalStateException(
                    "app.crypto.salt は hex 文字列 (0-9a-f) のみ受け付けます。現在値は hex 以外を含みます。");
        }
        if (salt.length() < 16) {
            throw new IllegalStateException(
                    "app.crypto.salt は 16 文字以上 (= 8 byte) の hex 必須。推奨 32 文字 (16 byte)。");
        }
        // Encryptors.delux: PBKDF2 + AES/GCM/NoPadding + 16byte IV + Hex エンコード出力
        this.encryptor = Encryptors.delux(password, salt);
        log.info("CryptoUtil 初期化完了（AES-256 GCM, Hex 出力）");
    }

    /** 平文を暗号化して Hex 文字列を返す (IV+CipherText+GMAC tag 連結)。null は null を返す。 */
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
