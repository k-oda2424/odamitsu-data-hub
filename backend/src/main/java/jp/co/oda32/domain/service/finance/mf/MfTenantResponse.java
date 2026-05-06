package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MF tenant API ({@code GET /v2/tenant}) のレスポンス最小フィールド。
 * <p>
 * P1-01 / Cluster F DD-F-04 で導入: 別会社 MF 誤接続検知のために
 * tenant id / 名 を保持する。MF API は他にもフィールドを返すが、
 * binding 用途には id / name のみ参照する ({@link JsonIgnoreProperties} で無視)。
 *
 * @since 2026-05-04 (P1-01)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfTenantResponse(
        @JsonProperty("id") String id,
        @JsonProperty("name") String name
) {}
