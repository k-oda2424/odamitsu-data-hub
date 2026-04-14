package jp.co.oda32.constant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * BCart出荷ステータス
 *
 * @author k_oda
 * @since 2023/04/18
 */
public enum BcartShipmentStatus {
    NOT_SHIPPED("未発送"),
    SHIPPING_INSTRUCTED("発送指示"),
    SHIPPED("発送済"),
    EXCLUDED("対象外");

    private final String displayName;

    BcartShipmentStatus(String displayName) {
        this.displayName = displayName;
    }

    public static BcartShipmentStatus parse(String key) {
        for (BcartShipmentStatus bcartShipmentStatus : values()) {
            if (bcartShipmentStatus.getDisplayName().equals(key)) {
                return bcartShipmentStatus;
            }
        }
        return null;
    }

    @JsonCreator
    public static BcartShipmentStatus fromJson(String value) {
        if (value == null) return null;
        BcartShipmentStatus byDisplay = parse(value);
        if (byDisplay != null) return byDisplay;
        try {
            return BcartShipmentStatus.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown BcartShipmentStatus: " + value);
        }
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }
}
