package jp.co.oda32.domain.service.finance.mf;

/**
 * MF tenant 強制バインド失敗時に throw される例外 (G1-M3)。
 * <p>P1-01 で導入した tenant binding は、refresh 経路で検証する設計だった。
 * しかし、P1-01 導入前から認可済みの client (mf_tenant_id IS NULL) は、
 * access_token が fresh な間は tenant 検証されず業務 API を実行できる穴があった。
 *
 * <p>G1-M3 修正で {@link MfOauthService#getValidAccessToken()} は
 * 未バインド client に対して強制的に {@code /v2/tenant} を呼び出して binding するが、
 * その fetch が失敗 (HTTP error / 認証拒否 / レスポンス不正) した場合に本例外を throw する。
 *
 * <p>本例外と {@link MfTenantMismatchException} の使い分け:
 * <ul>
 *   <li>{@link MfTenantMismatchException}: 既存バインドと観測 tenant が <strong>異なる</strong> 場合 (= 別会社 MF 誤認可)</li>
 *   <li>{@link MfTenantBindingFailedException}: 初回バインド試行で tenant fetch 自体が <strong>失敗</strong> した場合</li>
 * </ul>
 *
 * <p>Controller では 503 (SERVICE_UNAVAILABLE) で返却する想定 ({@link jp.co.oda32.api.finance.FinanceExceptionHandler})。
 * MF 側が一時的に応答しない / scope 不足等が想定要因のため、業務 API は短時間で再試行可能。
 *
 * @since 2026-05-06 (G1-M3)
 */
public class MfTenantBindingFailedException extends RuntimeException {

    public MfTenantBindingFailedException(String message) {
        super(message);
    }

    public MfTenantBindingFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
