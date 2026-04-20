package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * MF OAuth クライアント設定の登録/更新リクエスト。
 * clientSecret は新規登録時は必須、更新時は空で送れば既存値を維持。
 */
@Data
public class MfOauthClientRequest {

    @NotBlank
    private String clientId;

    /** 新規時は必須、更新時は null or 空で既存値を維持。 */
    private String clientSecret;

    @NotBlank
    private String redirectUri;

    @NotBlank
    private String scope;

    @NotBlank
    private String authorizeUrl;

    @NotBlank
    private String tokenUrl;

    @NotBlank
    private String apiBaseUrl;
}
