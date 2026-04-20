package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * マネーフォワードクラウド会計 API への低レベル HTTP クライアント。
 * <p>
 * OAuth2 の token 交換・refresh のみ担当。Journal / accounts 取得は別 Service から
 * {@link #restClient()} を経由して呼ぶ。再認可が必要なエラーは
 * {@link MfReAuthRequiredException} に変換する。
 *
 * @since 2026/04/20
 */
@Component
@Log4j2
@RequiredArgsConstructor
public class MfApiClient {

    private final RestClient.Builder restClientBuilder;

    /**
     * 認可 URL を組み立てる（ブラウザをリダイレクトするための URL）。
     * PKCE S256 を使う: {@code code_challenge} と {@code code_challenge_method=S256} を付与する (B-4)。
     */
    public String buildAuthorizeUrl(MMfOauthClient client, String state, String codeChallenge) {
        StringBuilder sb = new StringBuilder(client.getAuthorizeUrl());
        sb.append(client.getAuthorizeUrl().contains("?") ? "&" : "?");
        sb.append("response_type=code");
        sb.append("&client_id=").append(urlEncode(client.getClientId()));
        sb.append("&redirect_uri=").append(urlEncode(client.getRedirectUri()));
        sb.append("&scope=").append(urlEncode(client.getScope()));
        sb.append("&state=").append(urlEncode(state));
        if (codeChallenge != null && !codeChallenge.isEmpty()) {
            sb.append("&code_challenge=").append(urlEncode(codeChallenge));
            sb.append("&code_challenge_method=S256");
        }
        return sb.toString();
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
        return postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret));
    }

    /** refresh_token で新しい access_token を取得する（CLIENT_SECRET_BASIC）。 */
    public MfTokenResponse refreshToken(MMfOauthClient client, String clientSecret, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return postToken(client.getTokenUrl(), form, basicAuthHeader(client.getClientId(), clientSecret));
    }

    /**
     * 認可されたリクエスト用の RestClient を返す（呼び出し側で Authorization ヘッダを付ける）。
     * MfJournalService など高レベル Service が利用する想定。
     */
    public RestClient restClient() {
        return restClientBuilder.build();
    }

    /**
     * 指定パスの MF API を生 JSON のまま取得する（診断用）。
     */
    public JsonNode getRaw(MMfOauthClient client, String accessToken, String path) {
        String url = client.getApiBaseUrl() + path;
        try {
            JsonNode node = restClientBuilder.build().get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JsonNode.class);
            return node;
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF {} 取得失敗: status={}, body={}", path, status.value(), body);
            if (status.value() == 401) {
                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
            }
            throw e;
        }
    }

    /**
     * GET /api/v3/accounts で勘定科目一覧を取得。
     * @param client 有効な MMfOauthClient
     * @param accessToken Bearer token
     * @return 勘定科目リスト（subAccounts を含む）
     */
    public MfAccountsResponse listAccounts(MMfOauthClient client, String accessToken) {
        String url = client.getApiBaseUrl() + "/api/v3/accounts";
        try {
            MfAccountsResponse res = restClientBuilder.build().get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(MfAccountsResponse.class);
            return res != null ? res : new MfAccountsResponse(null);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF /accounts 取得失敗: status={}, body={}", status.value(), body);
            if (status.value() == 401) {
                throw new MfReAuthRequiredException("MF API 認証失敗。再認証してください: " + body, e);
            }
            throw e;
        }
    }

    private MfTokenResponse postToken(String tokenUrl, MultiValueMap<String, String> form, String basicAuth) {
        try {
            MfTokenResponse res = restClientBuilder.build().post()
                    .uri(tokenUrl)
                    .header(HttpHeaders.AUTHORIZATION, basicAuth)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(MfTokenResponse.class);
            if (res == null || res.accessToken() == null || res.refreshToken() == null) {
                throw new IllegalStateException("MF token エンドポイントから不正なレスポンス");
            }
            return res;
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF token エンドポイント失敗: status={}, body={}", status.value(), body);
            if (status.value() == 400 || status.value() == 401) {
                throw new MfReAuthRequiredException(
                        "MF トークン交換に失敗しました（再認証が必要）: " + body, e);
            }
            throw e;
        }
    }

    private static String basicAuthHeader(String clientId, String clientSecret) {
        String raw = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private static String urlEncode(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
