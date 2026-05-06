package jp.co.oda32.exception;

/**
 * Finance 業務例外: ユーザーに伝えるべき業務メッセージを保持する例外。
 *
 * <p>HTTP 400 Bad Request にマップされ、message はそのまま client に返る。
 * 例: 「対象取引月にデータがありません」「請求金額が 0 円です」「未登録の支払先です」等
 *
 * <p>機微情報を含むメッセージには使わないこと (= {@link FinanceInternalException} を使う)。
 *
 * <p>関連:
 * <ul>
 *   <li>T5 (本クラス): 例外ハンドリング統一</li>
 *   <li>{@link FinanceInternalException}: 内部エラー (機微情報含む可能性、汎用化される)</li>
 *   <li>{@code FinanceExceptionHandler}: HTTP マッピング</li>
 * </ul>
 *
 * @since 2026-05-04 (T5)
 */
public class FinanceBusinessException extends RuntimeException {

    private final String errorCode;

    public FinanceBusinessException(String message) {
        super(message);
        this.errorCode = null;
    }

    public FinanceBusinessException(String message, String errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public FinanceBusinessException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
