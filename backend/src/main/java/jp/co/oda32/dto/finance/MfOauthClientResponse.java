package jp.co.oda32.dto.finance;

import jp.co.oda32.domain.model.finance.MMfOauthClient;
import lombok.Builder;
import lombok.Data;

/**
 * MF OAuth クライアント設定のレスポンス。Client Secret は返却しない（マスク）。
 */
@Data
@Builder
public class MfOauthClientResponse {
    private Integer id;
    private String clientId;
    private boolean clientSecretConfigured; // Secret が登録済みか（値は返さない）
    private String redirectUri;
    private String scope;
    private String authorizeUrl;
    private String tokenUrl;
    private String apiBaseUrl;

    public static MfOauthClientResponse from(MMfOauthClient c) {
        return MfOauthClientResponse.builder()
                .id(c.getId())
                .clientId(c.getClientId())
                .clientSecretConfigured(c.getClientSecretEnc() != null && !c.getClientSecretEnc().isBlank())
                .redirectUri(c.getRedirectUri())
                .scope(c.getScope())
                .authorizeUrl(c.getAuthorizeUrl())
                .tokenUrl(c.getTokenUrl())
                .apiBaseUrl(c.getApiBaseUrl())
                .build();
    }
}
