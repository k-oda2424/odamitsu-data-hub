package jp.co.oda32.constant;

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

    public static BcartShipmentStatus purse(String key) {
        for (BcartShipmentStatus bcartShipmentStatus : values()) {
            if (bcartShipmentStatus.getDisplayName().equals(key)) {
                return bcartShipmentStatus;
            }
        }
        return null;
    }

    public String getDisplayName() {
        return displayName;
    }
}
