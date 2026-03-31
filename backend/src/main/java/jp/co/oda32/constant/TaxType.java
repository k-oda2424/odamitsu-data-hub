package jp.co.oda32.constant;

import lombok.Getter;

/**
 * 課税種類の定義クラス
 *
 * @author k_oda
 * @since 2018/11/28
 */
@Getter
public enum TaxType {
    TAX_EXCLUDE("0"),
    TAXABLE_INCLUDE("1"),
    TAX_FREE("2");

    private String value;

    TaxType(String value) {
        this.value = value;
    }

    public static TaxType purse(String key) {
        for (TaxType companyType : values()) {
            if (companyType.getValue().equals(key)) {
                return companyType;
            }
        }
        return null;
    }
}
