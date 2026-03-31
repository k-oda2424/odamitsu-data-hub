package jp.co.oda32.constant;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 注文明細ステータス
 *
 * @author k_oda
 * @since 2018/11/28
 */
public enum OrderDetailStatus {
    RECEIPT("00", "注文受付"),
    BACK_ORDERED("01", "入荷待ち"),
    ALLOCATION("10", "在庫引当"),
    DELIVERED("20", "納品済"),
    CANCEL("90", "キャンセル"),
    RETURN("99", "返品");

    private String code;
    private String value;

    OrderDetailStatus(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public String getCode() {
        return this.code;
    }

    public String getValue() {
        return value;
    }

    public static OrderDetailStatus purse(String key) {
        for (OrderDetailStatus orderDetailStatus : values()) {
            if (orderDetailStatus.getCode().equals(key)) {
                return orderDetailStatus;
            }
        }
        return null;
    }

    public static Map<String, String> toMap() {
        return Arrays.stream(OrderDetailStatus.class.getEnumConstants())
                .collect(Collectors.toMap(OrderDetailStatus::getCode, OrderDetailStatus::getValue, (a, b) -> b, TreeMap::new));
    }
}
