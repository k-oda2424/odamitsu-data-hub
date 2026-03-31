package jp.co.oda32.constant;

/**
 * Smile連携用「課税区分」定義
 *
 * @author k_oda
 * @since 2023/04/10
 */
public enum SmileTaxType {
    TAX_EXCLUDED(0, "税抜"),
    CONSUMPTION_TAX(1, "消費税");

    private final int code;
    private final String description;

    SmileTaxType(int code, String description) {
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
