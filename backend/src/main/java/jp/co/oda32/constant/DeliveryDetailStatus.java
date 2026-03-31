package jp.co.oda32.constant;

import java.util.Arrays;
import java.util.List;

/**
 * 出荷明細ステータス
 *
 * @author k_oda
 * @since 2018/12/15
 */
public enum DeliveryDetailStatus {
    // 00：伝票未入力、10：出荷待ち、20：納品済、90：全キャンセル、99：全返品
    NOT_INPUT("00"),
    WAIT_SHIPPING("10"),
    DELIVERED("20"),
    CANCEL("90"),
    RETURN("99");

    private String value;

    DeliveryDetailStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static DeliveryDetailStatus purse(String key) {
        for (DeliveryDetailStatus deliveryDetailStatus : values()) {
            if (deliveryDetailStatus.getValue().equals(key)) {
                return deliveryDetailStatus;
            }
        }
        return null;
    }

    public static List<DeliveryDetailStatus> getNotCancelStatus() {
        return Arrays.asList(WAIT_SHIPPING, DELIVERED);
    }
}
