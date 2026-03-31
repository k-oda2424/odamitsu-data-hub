package jp.co.oda32.constant;

/**
 * Smile連携「消費税分類」定義
 *
 * @author k_oda
 * @since 2023/04/11
 */
public enum ConsumptionTaxType {
    REGULAR(0, "通常"),
    REDUCED(1, "軽減");

    private final int code;
    private final String description;

    ConsumptionTaxType(int code, String description) {
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
