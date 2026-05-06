package jp.co.oda32.exception;

/**
 * Finance 内部例外: 機微情報を含む可能性のある内部状態異常。
 *
 * <p>HTTP 422 Unprocessable Entity にマップされ、message は <strong>client には汎用化されて返る</strong>
 * (情報漏洩防止)。詳細メッセージはサーバーログのみ記録。
 *
 * <p>例:
 * <ul>
 *   <li>「DB に予期しない null が含まれています: supplier_no=123」(= supplier 内部 ID 露出防止)</li>
 *   <li>「集計バッチの内部状態が不整合: ...」(= バッチ実装詳細露出防止)</li>
 *   <li>「MF API レスポンスのパース失敗: {raw body}」(= API キー含む可能性)</li>
 * </ul>
 *
 * <p>業務メッセージをユーザーに伝えたい場合は {@link FinanceBusinessException} を使うこと。
 *
 * @since 2026-05-04 (T5)
 */
public class FinanceInternalException extends RuntimeException {

    public FinanceInternalException(String message) {
        super(message);
    }

    public FinanceInternalException(String message, Throwable cause) {
        super(message, cause);
    }
}
