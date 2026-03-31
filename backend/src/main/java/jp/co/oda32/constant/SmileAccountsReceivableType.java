package jp.co.oda32.constant;

/**
 * Smile連携用「売掛区分」定義
 *
 * @author k_oda
 * @since 2023/04/10
 */
public enum SmileAccountsReceivableType {
    NORMAL(0, "売掛");

    private final int code;
    private final String description;

    SmileAccountsReceivableType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
