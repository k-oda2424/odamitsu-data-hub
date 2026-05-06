package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MF OAuth2 token エンドポイントのレスポンス。
 *
 * <p>SF-02: {@code accessToken} / {@code refreshToken} は機密値のため
 * {@link #toString()} で {@code ***} に置換する (record の自動生成 toString 上書き)。
 */
public record MfTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("refresh_token") String refreshToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn,
        @JsonProperty("scope") String scope
) {
    @Override
    public String toString() {
        return "MfTokenResponse[accessToken=***, refreshToken=***, tokenType=" + tokenType
                + ", expiresIn=" + expiresIn + ", scope=" + scope + "]";
    }
}
