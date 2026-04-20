package jp.co.oda32.domain.service.finance.mf;

/**
 * MF の refresh_token が失効している等、ユーザーによる再認可が必要なケース。
 * Controller は 401 + 「再認証してください」メッセージを返す。
 */
public class MfReAuthRequiredException extends RuntimeException {
    public MfReAuthRequiredException(String message) {
        super(message);
    }

    public MfReAuthRequiredException(String message, Throwable cause) {
        super(message, cause);
    }
}
