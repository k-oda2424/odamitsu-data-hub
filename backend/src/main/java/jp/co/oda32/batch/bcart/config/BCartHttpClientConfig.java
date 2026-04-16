package jp.co.oda32.batch.bcart.config;

import okhttp3.OkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * B-Cart API 呼び出し用の共通 {@link OkHttpClient} Bean 定義。
 * <p>singleton にすることで接続プールを共有し、毎回 new するコスト（接続プール破棄）を回避する。
 * timeout を明示設定して、ハングや不明瞭な停止を防ぐ。
 */
@Configuration
public class BCartHttpClientConfig {

    @Bean("bCartHttpClient")
    public OkHttpClient bCartHttpClient() {
        return new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(120))
                .writeTimeout(Duration.ofSeconds(60))
                .build();
    }
}
