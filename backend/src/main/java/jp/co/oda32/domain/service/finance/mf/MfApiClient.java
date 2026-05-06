package jp.co.oda32.domain.service.finance.mf;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.util.SensitiveLogMasker;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * マネーフォワードクラウド会計 API への低レベル HTTP クライアント。
 * <p>
 * OAuth2 の token 交換・refresh のみ担当。Journal / accounts 取得は別 Service から
 * {@link #restClient()} を経由して呼ぶ。再認可が必要なエラーは
 * {@link MfReAuthRequiredException} に変換する。
 *
 * <p>SF-01: timeout 設定済み {@link RestClient} (mfRestClient) を field injection で利用。
 * <p>SF-04: error body のロギングは {@link SensitiveLogMasker#mask(String)} 経由でマスキング。
 * <p>SF-07: basic auth header は RFC 6749 §2.3.1 準拠で URL encode してから base64。
 * <p>SF-08: 429/5xx は {@link #executeWithRetry(Supplier, String)} 経由で指数バックオフ。
 * <p>SF-10: {@link #urlEncode(String)} は null を fail-fast。
 * <p>SF-11: URL 組み立ては {@link UriComponentsBuilder} を使用。
 * <p>SF-21: postToken は access_token のみ必須 (refresh_token null fallback は呼び出し側)。
 *
 * @since 2026/04/20
 */
@Component
@Log4j2
public class MfApiClient {

    /** 429/5xx 時のリトライ間隔 (指数バックオフ): 1s → 2s → 4s。 */
    private static final long[] RETRY_BACKOFFS_MS = {1000L, 2000L, 4000L};

    private final RestClient restClient;

    public MfApiClient(@Qualifier("mfRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 認可 URL を組み立てる（ブラウザをリダイレクトするための URL）。
     * PKCE S256 を使う: {@code code_challenge} と {@code code_challenge_method=S256} を付与する (B-4)。
     */
    public String buildAuthorizeUrl(MMfOauthClient client, String state, String codeChallenge) {
        UriComponentsBuilder b = UriComponentsBuilder.fromHttpUrl(client.getAuthorizeUrl())
                .queryParam("response_type", "code")
                .queryParam("client_id", client.getClientId())
                .queryParam("redirect_uri", client.getRedirectUri())
                .queryParam("scope", client.getScope())
                .queryParam("state", state);
        if (codeChallenge != null && !codeChallenge.isEmpty()) {
            b.queryParam("code_challenge", codeChallenge)
                    .queryParam("code_challenge_method", "S256");
        }
        return b.build().encode(StandardCharsets.UTF_8).toUriString();
    }

    /**
     * authorization code を access/refresh token に交換する（CLIENT_SECRET_BASIC + PKCE code_verifier）。
     */
    public MfTokenResponse exchangeCodeForToken(
            MMfOauthClient client, String clientSecret, String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", client.getRedirectUri());
        if (codeVerifier != null && !codeVerifier.isEmpty()) {
            form.add("code_verifier", codeVerifier);
        }
        return executeWithRetry(
                () -> postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret)),
                "exchangeCodeForToken");
    }

    /** refresh_token で新しい access_token を取得する（CLIENT_SECRET_BASIC）。 */
    public MfTokenResponse refreshToken(MMfOauthClient client, String clientSecret, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return executeWithRetry(
                () -> postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret)),
                "refreshToken");
    }

    /**
     * 認可されたリクエスト用の RestClient を返す（呼び出し側で Authorization ヘッダを付ける）。
     * MfJournalService など高レベル Service が利用する想定。
     */
    public RestClient restClient() {
        return restClient;
    }

    /**
     * GET /api/v3/journals で指定期間の仕訳一覧を取得。
     * @param startDate 開始日 (yyyy-MM-dd)
     * @param endDate   終了日 (yyyy-MM-dd)
     */
    public MfJournalsResponse listJournals(MMfOauthClient client, String accessToken,
                                            String startDate, String endDate, int page, int perPage) {
        String url = UriComponentsBuilder.fromHttpUrl(client.getApiBaseUrl() + "/api/v3/journals")
                .queryParam("start_date", startDate)
                .queryParam("end_date", endDate)
                .queryParam("page", page)
                .queryParam("per_page", perPage)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        return executeWithRetry(() -> {
            try {
                MfJournalsResponse res = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(MfJournalsResponse.class);
                return res != null ? res : new MfJournalsResponse(null);
            } catch (HttpClientErrorException e) {
                HttpStatusCode status = e.getStatusCode();
                String body = e.getResponseBodyAsString();
                log.warn("MF /journals 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
                if (status.value() == 401) {
                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
                }
                if (status.value() == 403) {
                    throw new MfScopeInsufficientException("mfc/accounting/journal.read",
                            "MF scope 不足です (mfc/accounting/journal.read 必要)。クライアント設定更新 + 再認証してください", e);
                }
                throw e;
            }
        }, "listJournals");
    }

    /** GET /api/v3/taxes で税区分一覧を取得。 */
    public MfTaxesResponse listTaxes(MMfOauthClient client, String accessToken) {
        String url = client.getApiBaseUrl() + "/api/v3/taxes";
        return executeWithRetry(() -> {
            try {
                MfTaxesResponse res = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(MfTaxesResponse.class);
                return res != null ? res : new MfTaxesResponse(null);
            } catch (HttpClientErrorException e) {
                HttpStatusCode status = e.getStatusCode();
                String body = e.getResponseBodyAsString();
                log.warn("MF /taxes 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
                if (status.value() == 401) {
                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
                }
                throw e;
            }
        }, "listTaxes");
    }

    /**
     * GET /api/v3/reports/trial_balance_bs で貸借対照表試算表を取得。
     * <p>
     * Phase 0 実測 (設計書 §1) で確定した仕様:
     * <ul>
     *   <li>query は {@code end_date=YYYY-MM-DD} のみ。period / from / date 等は unsupported</li>
     *   <li>closing_balance が end_date 時点の累積残を表す</li>
     *   <li>sub_account 粒度は含まれない (account leaf のみ)</li>
     * </ul>
     * scope {@code mfc/accounting/report.read} が必要。不足時は 403 →
     * {@link MfScopeInsufficientException} に変換。
     *
     * @param endDate 月末日 (yyyy-MM-dd)、締め日 20日想定
     */
    public MfTrialBalanceBsResponse getTrialBalanceBs(MMfOauthClient client, String accessToken,
                                                       String endDate) {
        String url = UriComponentsBuilder.fromHttpUrl(client.getApiBaseUrl() + "/api/v3/reports/trial_balance_bs")
                .queryParam("end_date", endDate)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUriString();
        return executeWithRetry(() -> {
            try {
                MfTrialBalanceBsResponse res = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(MfTrialBalanceBsResponse.class);
                return res != null ? res : new MfTrialBalanceBsResponse(null, endDate, null, null);
            } catch (HttpClientErrorException e) {
                HttpStatusCode status = e.getStatusCode();
                String body = e.getResponseBodyAsString();
                log.warn("MF /trial_balance_bs 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
                if (status.value() == 401) {
                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
                }
                if (status.value() == 403) {
                    throw new MfScopeInsufficientException("mfc/accounting/report.read",
                            "MF scope 不足です (mfc/accounting/report.read)。クライアント設定に scope を追加 → 再認証してください", e);
                }
                throw e;
            }
        }, "getTrialBalanceBs");
    }

    /**
     * GET /v2/tenant で MF クラウド会計の tenant (= 連携先事業者) 情報を取得。
     * <p>
     * P1-01 / DD-F-04: 別会社 MF への誤接続を検知するため、callback 直後と
     * refresh 後に呼び出して {@code mf_tenant_id} の一致確認に使う。scope
     * {@code mfc/admin/tenant.read} が必要。不足時は 403 →
     * {@link MfScopeInsufficientException} に変換する。
     * <p>
     * 401 (token 失効) は {@link MfReAuthRequiredException} に変換 (他 endpoint と同様)。
     */
    public MfTenantResponse getTenant(MMfOauthClient client, String accessToken) {
        String url = client.getApiBaseUrl() + "/v2/tenant";
        return executeWithRetry(() -> {
            try {
                MfTenantResponse res = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(MfTenantResponse.class);
                if (res == null || res.id() == null || res.id().isBlank()) {
                    throw new IllegalStateException("MF /v2/tenant が tenant id を返却しませんでした");
                }
                return res;
            } catch (HttpClientErrorException e) {
                HttpStatusCode status = e.getStatusCode();
                String body = e.getResponseBodyAsString();
                log.warn("MF /v2/tenant 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
                if (status.value() == 401) {
                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
                }
                if (status.value() == 403) {
                    throw new MfScopeInsufficientException("mfc/admin/tenant.read",
                            "MF scope 不足です (mfc/admin/tenant.read 必要)。クライアント設定の scope を更新 → 再認証してください", e);
                }
                throw e;
            }
        }, "getTenant");
    }

    /**
     * GET /api/v3/accounts で勘定科目一覧を取得。
     * @param client 有効な MMfOauthClient
     * @param accessToken Bearer token
     * @return 勘定科目リスト（subAccounts を含む）
     */
    public MfAccountsResponse listAccounts(MMfOauthClient client, String accessToken) {
        String url = client.getApiBaseUrl() + "/api/v3/accounts";
        return executeWithRetry(() -> {
            try {
                MfAccountsResponse res = restClient.get()
                        .uri(url)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(MfAccountsResponse.class);
                return res != null ? res : new MfAccountsResponse(null);
            } catch (HttpClientErrorException e) {
                HttpStatusCode status = e.getStatusCode();
                String body = e.getResponseBodyAsString();
                log.warn("MF /accounts 取得失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
                if (status.value() == 401) {
                    throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
                }
                throw e;
            }
        }, "listAccounts");
    }

    private MfTokenResponse postToken(String tokenUrl, MultiValueMap<String, String> form, String basicAuth) {
        try {
            MfTokenResponse res = restClient.post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MfTokenResponse.class);
            // SF-21: refresh_token は MF が返さないこともある (refresh_token_rotation 設定次第)。
            // access_token のみ必須。refresh_token null の場合は呼び出し側で既存値を流用する。
            if (res == null || res.accessToken() == null) {
                throw new IllegalStateException("MF token エンドポイントから不正なレスポンス (access_token なし)");
            }
            return res;
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF token エンドポイント失敗: status={}, body={}", status.value(), SensitiveLogMasker.mask(body));
            if (status.value() == 400 || status.value() == 401) {
                throw new MfReAuthRequiredException(
                        "再認証が必要です。詳細は管理者ログを参照してください", e);
            }
            throw e;
        }
    }

    /**
     * SF-07: RFC 6749 §2.3.1 準拠の HTTP Basic 認証ヘッダを生成。
     * client_id / client_secret に予約文字 (例: {@code :}) や非 ASCII が含まれる場合に備えて、
     * base64 する前に application/x-www-form-urlencoded で URL encode する。
     */
    private static String basicAuthHeader(String clientId, String clientSecret) {
        Objects.requireNonNull(clientId, "clientId が null");
        Objects.requireNonNull(clientSecret, "clientSecret が null");
        String encId = URLEncoder.encode(clientId, StandardCharsets.UTF_8);
        String encSecret = URLEncoder.encode(clientSecret, StandardCharsets.UTF_8);
        String raw = encId + ":" + encSecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** SF-10: null in は fail-fast。これまで暗黙に空文字に変換していたバグの再発防止。 */
    private static String urlEncode(String s) {
        Objects.requireNonNull(s, "url-encoded 引数が null");
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /**
     * SF-08: MF API 呼び出しの共通リトライラッパ。
     * <ul>
     *   <li>429 (Too Many Requests) と 5xx を retryable とみなす</li>
     *   <li>1s → 2s → 4s の指数バックオフ (最大 3 回)</li>
     *   <li>{@link MfReAuthRequiredException}/{@link MfScopeInsufficientException} は即時 throw</li>
     * </ul>
     */
    private <T> T executeWithRetry(Supplier<T> op, String operation) {
        int attempt = 0;
        while (true) {
            try {
                return op.get();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && attempt < RETRY_BACKOFFS_MS.length) {
                    long wait = RETRY_BACKOFFS_MS[attempt];
                    log.warn("[mf-api] {} 429, {}ms sleep して retry ({}回目)", operation, wait, attempt + 1);
                    sleepQuietly(wait);
                    attempt++;
                    continue;
                }
                throw e;
            } catch (HttpServerErrorException e) {
                if (attempt < RETRY_BACKOFFS_MS.length) {
                    long wait = RETRY_BACKOFFS_MS[attempt];
                    log.warn("[mf-api] {} {} server error, {}ms sleep して retry ({}回目)",
                            operation, e.getStatusCode().value(), wait, attempt + 1);
                    sleepQuietly(wait);
                    attempt++;
                    continue;
                }
                throw e;
            }
        }
    }

    private static void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
