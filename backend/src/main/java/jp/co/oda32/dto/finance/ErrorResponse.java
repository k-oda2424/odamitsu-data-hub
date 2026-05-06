package jp.co.oda32.dto.finance;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.OffsetDateTime;

/**
 * Finance 系 API の標準エラーレスポンス。
 * <ul>
 *   <li>{@code message} : エンドユーザー向けメッセージ（既存応答と同じフィールド名で互換）</li>
 *   <li>{@code code}    : クライアントが分岐に使う安定識別子（例: MF_REAUTH_REQUIRED）</li>
 *   <li>{@code timestamp} : サーバー時刻（OffsetDateTime, ISO-8601）</li>
 *   <li>{@code requiredScope} : MF scope 不足時の必要 scope (旧 endpoint 互換、Optional/null 可)</li>
 * </ul>
 *
 * @since 2026-05-04 (SF-25)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(String message, String code, OffsetDateTime timestamp, String requiredScope) {

    public static ErrorResponse of(String message, String code) {
        return new ErrorResponse(message, code, OffsetDateTime.now(), null);
    }

    public static ErrorResponse withScope(String message, String code, String requiredScope) {
        return new ErrorResponse(message, code, OffsetDateTime.now(), requiredScope);
    }
}
