package jp.co.oda32.constant;

/**
 * 商品単位の定義クラス
 *
 * @author k_oda
 * @since 2018/07/25
 */
public enum UnitType {
    CASE("case"),
    PIECE("piece");

    private String value;

    UnitType(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static UnitType purse(String key) {
        for (UnitType unit : values()) {
            if (unit.getValue().equals(key)) {
                return unit;
            }
        }
        return null;
    }
}
