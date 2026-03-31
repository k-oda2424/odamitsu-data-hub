package jp.co.oda32.constant;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 発注明細ステータス
 *
 * @author k_oda
 * @since 2019/07/24
 */
public enum SendOrderDetailStatus {
    SEND_ORDER("00", "発注済"),
    ARRIVAL_TO_PROMISE("10", "納期回答"),
    ARRIVED("20", "入荷済"),
    PURCHASED("30", "仕入入力済"),
    CANCEL("99", "キャンセル");

    private String code;
    private String value;

    SendOrderDetailStatus(String code, String value) {
        this.code = code;
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static SendOrderDetailStatus purse(String code) {
        for (SendOrderDetailStatus orderDetailStatus : values()) {
            if (orderDetailStatus.getCode().equals(code)) {
                return orderDetailStatus;
            }
        }
        return null;
    }

    public static Map<String, String> toMap() {
        return Arrays.stream(SendOrderDetailStatus.class.getEnumConstants())
                .collect(Collectors.toMap(SendOrderDetailStatus::getCode, SendOrderDetailStatus::getValue, (a, b) -> b, TreeMap::new));
    }

    public String getCode() {
        return this.code;
    }
}
