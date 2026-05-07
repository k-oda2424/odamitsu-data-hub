package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * MF tenant API ({@code GET /v2/tenant}) のレスポンス最小フィールド。
 * <p>
 * P1-01 / Cluster F DD-F-04 で導入: 別会社 MF 誤接続検知のために
 * tenant 識別子と名称を保持する。MF API は他にもフィールドを返すが、
 * binding 用途には識別子と名称のみ参照する ({@link JsonIgnoreProperties} で無視)。
 * <p>
 * 実 API レスポンスは {@code {"tenant_code":"...","tenant_name":"..."}} 形式
 * (例: {@code {"tenant_code":"6947-6398","tenant_name":"小田光　株式会社"}})。
 * record アクセサ名 (id/name) は呼び出し側互換性維持のためそのまま、
 * JSON マッピングのみ実フィールドに合わせる。
 *
 * @since 2026-05-04 (P1-01)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfTenantResponse(
        @JsonProperty("tenant_code") String id,
        @JsonProperty("tenant_name") String name
) {}
