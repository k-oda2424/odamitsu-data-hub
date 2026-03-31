package jp.co.oda32.constant;

import lombok.Getter;

/**
 * 消費税分類の列挙型クラス
 *
 * @author k_oda
 * @since 2024/09/11
 */
@Getter
public enum TaxCategory {
    NORMAL(0, "通常税率"),
    REDUCED(1, "軽減税率"),
    EXEMPT(2, "非課税");

    private final int code;
    private final String description;

    TaxCategory(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static TaxCategory purse(int code) {
        for (TaxCategory category : values()) {
            if (category.getCode() == code) {
                return category;
            }
        }
        throw new IllegalArgumentException("Invalid tax category code: " + code);
    }
}
