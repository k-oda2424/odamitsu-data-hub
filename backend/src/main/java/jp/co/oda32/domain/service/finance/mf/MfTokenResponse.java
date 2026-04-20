package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MF OAuth2 token エンドポイントのレスポンス。
 */
public record MfTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("scope") String scope
) {}
