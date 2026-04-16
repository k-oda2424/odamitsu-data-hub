package jp.co.oda32.constant;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * B-CART API アクセストークンを保持する Spring Bean。
 *
 * <p>トークンは {@code bcart.api.access-token} または環境変数 {@code BCART_ACCESS_TOKEN}
 * から注入される。ハードコードは行わない（git 履歴に残存している旧トークンは速やかに
 * 失効させ、新トークンを環境変数経由で投入すること）。
 *
 * <p>既存タスクレット群が {@code getInstance()} 経由でアクセスしているため、
 * Spring 起動時に {@code @PostConstruct} で static 参照を焼き込んで後方互換を維持する。
 *
 * @author k_oda
 * @since 2023/03/17
 */
@Component
public class BCartApiConfig {

    private static BCartApiConfig instance;

    private final String accessToken;

    public BCartApiConfig(@Value("${bcart.api.access-token:}") String accessToken) {
        this.accessToken = accessToken;
    }

    @PostConstruct
    void init() {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException(
                    "B-CART アクセストークンが未設定です。環境変数 BCART_ACCESS_TOKEN "
                            + "または application.yml の bcart.api.access-token に設定してください");
        }
        instance = this;
    }

    /**
     * Spring 管理外のレガシー呼び出し互換。Spring 起動後に初期化される。
     * 新規コードでは {@link BCartApiConfig} を DI で注入すること。
     */
    public static BCartApiConfig getInstance() {
        if (instance == null) {
            throw new IllegalStateException(
                    "BCartApiConfig が未初期化です（Spring コンテキスト起動前にアクセスされました）");
        }
        return instance;
    }

    public String getAccessToken() {
        return accessToken;
    }
}
