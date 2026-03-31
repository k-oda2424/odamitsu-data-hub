package jp.co.oda32.constant;

/**
 * Smile連携用「伝票消費税計算区分」定義
 *
 * @author k_oda
 * @since 2023/04/12
 */
public enum SmileSlipConsumptionTaxCalculationType {
    AUTO(0, "自動"),
    NON(1, "なし");

    private final int code;
    private final String description;

    SmileSlipConsumptionTaxCalculationType(int code, String description) {
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
