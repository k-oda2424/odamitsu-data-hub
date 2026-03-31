package jp.co.oda32.constant;

/**
 * 注文方法
 *
 * @author k_oda
 * @since 2018/11/28
 */
public enum OrderRoute {
    WEB("web"),
    TEL("tel"),
    FAX("fax"),
    MAIL("mail"),
    B_CART("b_cart"),
    OTHER("other");

    private String value;

    OrderRoute(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OrderRoute purse(String key) {
        for (OrderRoute orderRoute : values()) {
            if (orderRoute.getValue().equals(key)) {
                return orderRoute;
            }
        }
        return null;
    }
}
