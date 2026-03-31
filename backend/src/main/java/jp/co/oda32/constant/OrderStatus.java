package jp.co.oda32.constant;

/**
 * 注文ステータス
 *
 * @author k_oda
 * @since 2018/11/28
 */
public enum OrderStatus {
    // 00：注文受付、10：出荷待ち、20：納品済、90：全キャンセル、99：全返品
    RECEIPT("00"),
    WAIT_SHIPPING("10"),
    DELIVERED("20"),
    CANCEL("90"),
    RETURN("99");

    private String value;

    OrderStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OrderStatus purse(String key) {
        for (OrderStatus orderStatus : values()) {
            if (orderStatus.getValue().equals(key)) {
                return orderStatus;
            }
        }
        return null;
    }
}
