package jp.co.oda32.domain.service.finance.mf;

/**
 * MF API 呼び出しで 403 が返った場合に投げる例外。
 * 認可時の scope に必要な権限が含まれていないことを示す。
 * UI 側は再認可 (scope 追加後の保存 → 再認証) を促す。
 * <p>
 * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §5.2
 *
 * @since 2026-04-22 (Phase B)
 */
public class MfScopeInsufficientException extends RuntimeException {

    private final String requiredScope;

    public MfScopeInsufficientException(String requiredScope, String message, Throwable cause) {
        super(message, cause);
        this.requiredScope = requiredScope;
    }

    public String getRequiredScope() {
        return requiredScope;
    }
}
