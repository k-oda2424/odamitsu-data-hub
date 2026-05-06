package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.exception.FinanceBusinessException;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * MF OAuth エンドポイント URL のホスト allowlist (G1-M5)。
 * <p>admin が {@code authorizeUrl} / {@code tokenUrl} / {@code apiBaseUrl} を自由入力できる従来仕様では、
 * 攻撃者制御の URL に client_secret を Basic auth で送信する経路 (credential exfiltration) が
 * 残っていた。本クラスで MF 公式ホスト + dev 用 localhost のみ許可する。
 *
 * <h3>許可ホスト (production)</h3>
 * <ul>
 *   <li>{@code api.biz.moneyforward.com} (authorize / token)</li>
 *   <li>{@code api-accounting.moneyforward.com} (api base)</li>
 * </ul>
 *
 * <h3>dev profile 追加許可</h3>
 * <ul>
 *   <li>{@code localhost} / {@code 127.0.0.1} — テスト用 mock OAuth サーバー想定。
 *       prod では絶対に許可しない (profile=dev/test のみ)。</li>
 * </ul>
 *
 * <p>scheme は https 必須。dev profile かつ localhost ホストのみ http を許可する
 * (mock OAuth サーバーは TLS なしで起動するケースが多いため)。
 *
 * @since 2026-05-06 (G1-M5)
 */
@Component
@Log4j2
public class MfOauthHostAllowlist {

    /** MF_HOST_NOT_ALLOWED エラーコード (FinanceExceptionHandler で 400 にマップされる)。 */
    public static final String ERROR_CODE = "MF_HOST_NOT_ALLOWED";

    /** prod でも常に許可される MF 公式ホスト。 */
    private static final Set<String> PRODUCTION_HOSTS = Set.of(
            "api.biz.moneyforward.com",
            "api-accounting.moneyforward.com"
    );

    /** dev profile でのみ追加許可されるホスト。 */
    private static final Set<String> DEV_HOSTS = Set.of(
            "localhost",
            "127.0.0.1"
    );

    private final boolean devProfile;

    public MfOauthHostAllowlist(@Value("${spring.profiles.active:}") String activeProfiles) {
        this.devProfile = activeProfiles != null
                && (activeProfiles.contains("dev") || activeProfiles.contains("test"));
        log.info("MF OAuth allowlist 初期化: prodHosts={}, devHostsEnabled={} (activeProfiles={})",
                PRODUCTION_HOSTS, this.devProfile, activeProfiles);
    }

    /**
     * URL のホストが allowlist に含まれるか検証する。
     *
     * @param url       検証対象 URL (authorizeUrl / tokenUrl / apiBaseUrl)
     * @param fieldName エラーメッセージに含めるフィールド名
     * @throws FinanceBusinessException 違反時 ({@link #ERROR_CODE})
     */
    public void validate(String url, String fieldName) {
        if (url == null || url.isBlank()) {
            throw new FinanceBusinessException(fieldName + " は必須です。", ERROR_CODE);
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new FinanceBusinessException(
                    fieldName + " は有効な URL ではありません: " + url, ERROR_CODE);
        }
        String scheme = uri.getScheme();
        String host = uri.getHost();
        if (scheme == null || host == null) {
            throw new FinanceBusinessException(
                    fieldName + " の scheme/host が解釈できません: " + url, ERROR_CODE);
        }
        // scheme: https 必須。dev profile かつ localhost に限り http も許可。
        if (!"https".equalsIgnoreCase(scheme)) {
            boolean isDevLocalhost = devProfile
                    && DEV_HOSTS.contains(host)
                    && "http".equalsIgnoreCase(scheme);
            if (!isDevLocalhost) {
                throw new FinanceBusinessException(
                        fieldName + " は https のみ許可されます: " + url, ERROR_CODE);
            }
        }
        // host: allowlist に含まれるか
        if (PRODUCTION_HOSTS.contains(host)) {
            return;
        }
        if (devProfile && DEV_HOSTS.contains(host)) {
            return;
        }
        throw new FinanceBusinessException(
                fieldName + " のホストは許可されていません: " + host
                        + " (allowlist: " + allowlistDescription() + ")",
                ERROR_CODE);
    }

    private String allowlistDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(", ", PRODUCTION_HOSTS));
        if (devProfile) {
            sb.append(" + dev: ").append(String.join(", ", DEV_HOSTS));
        }
        return sb.toString();
    }
}
