package jp.co.oda32.constant;

/**
 * 出荷ステータス
 *
 * @author k_oda
 * @since 2018/11/28
 */
public enum DeliveryStatus {
    // 00：伝票未入力、10：出荷待ち、20：納品済、90：全キャンセル、99：全返品
    NOT_INPUT("00"),
    WAIT_SHIPPING("10"),
    DELIVERED("20"),
    CANCEL("90"),
    RETURN("99");

    private String value;

    DeliveryStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DeliveryStatus purse(String key) {
        for (DeliveryStatus deliveryStatus : values()) {
            if (deliveryStatus.getValue().equals(key)) {
                return deliveryStatus;
            }
        }
        return null;
    }
}
