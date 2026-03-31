package jp.co.oda32.constant;

/**
 * Smile連携用「取引属性」定義
 *
 * @author k_oda
 * @since 2023/04/10
 */
public enum SmileTransactionTypeAttribute {
    NORMAL(1, "売上");

    private final int code;
    private final String description;

    SmileTransactionTypeAttribute(int code, String description) {
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
