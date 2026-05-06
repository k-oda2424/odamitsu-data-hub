package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.databind.JsonNode;
import jp.co.oda32.domain.model.finance.MMfOauthClient;
import jp.co.oda32.util.SensitiveLogMasker;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * SF-14: MF API への診断用 HTTP クライアント (dev/test プロファイル限定)。
 * <p>
 * 旧 {@code MfApiClient.getRaw} を本クラスに移し、prod ビルドでは Bean が存在しないようにする。
 * 通常運用の {@link MfApiClient} と分離することで「本番でも生 JSON を返せる経路」を物理的に消す。
 *
 * @since 2026-05-04 (SF-14)
 */
@Component
@Profile({"dev", "test"})
@Log4j2
public class MfDebugApiClient {

    private final RestClient restClient;

    public MfDebugApiClient(@Qualifier("mfRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    /**
     * 指定パスの MF API を生 JSON のまま取得する（診断用）。
     * 401 は {@link MfReAuthRequiredException} に変換。
     */
    public JsonNode getRaw(MMfOauthClient client, String accessToken, String path) {
        String url = client.getApiBaseUrl() + path;
        try {
            return restClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            String body = e.getResponseBodyAsString();
            log.warn("MF {} 取得失敗 (debug): status={}, body={}",
                    path, status.value(), SensitiveLogMasker.mask(body));
            if (status.value() == 401) {
                throw new MfReAuthRequiredException("再認証が必要です。詳細は管理者ログを参照してください", e);
            }
            throw e;
        }
    }
}
