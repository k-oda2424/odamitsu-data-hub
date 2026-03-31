package jp.co.oda32.constant;

/**
 * Smile連携用「請求区分」定義
 *
 * @author k_oda
 * @since 2023/04/10
 */
public enum SmileBillingType {
    NORMAL(0, "今回");

    private final int code;
    private final String description;

    SmileBillingType(int code, String description) {
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
