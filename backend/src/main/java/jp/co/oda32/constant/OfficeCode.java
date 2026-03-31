package jp.co.oda32.constant;

/**
 * 営業所コードの定義クラス
 *
 * @author k_oda
 * @since 2021/08/05
 */
public enum OfficeCode {
    DAINI("00000002"),
    CLEAN_LABO("00000001"),
    DAIICHI("00000003"),
    INNER_PURCHASE("00000004"),
    INNER_ORDER("00000005"),
    BCART_ORDER("00000005");

    private String value;

    OfficeCode(String value) {
        this.value = value;
    }

    public static OfficeCode purse(String key) {
        for (OfficeCode companyType : values()) {
            if (companyType.getValue().equals(key)) {
                return companyType;
            }
        }
        return null;
    }

    public String getValue() {
        return value;
    }
}
