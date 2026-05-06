package jp.co.oda32.domain.service.finance.mf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * MF API 呼び出し用の共通 {@link RestClient} Bean 定義。
 * <p>
 * connect/read timeout を明示設定して、ハングや MF 側の遅延で
 * すべての ledger / reconcile / health check 操作が永久ブロックされる
 * DoS を回避する (SF-01)。
 * <p>
 * {@link jp.co.oda32.batch.bcart.config.BCartHttpClientConfig} を参考。
 *
 * @since 2026-05-04 (SF-01)
 */
@Configuration
public class MfHttpClientConfig {

    /** MF API への TCP 接続確立タイムアウト。 */
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    /** MF API レスポンス受信タイムアウト (大きめのページ取得を許容)。 */
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(30);

    @Bean(name = "mfRestClient")
    public RestClient mfRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) CONNECT_TIMEOUT.toMillis());
        factory.setReadTimeout((int) READ_TIMEOUT.toMillis());
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
