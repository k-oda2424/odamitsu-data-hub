package jp.co.oda32.constant;

public enum OfficeShopNo {
    ADMIN(0),
    DAINI(2),
    CLEAN_LABO(3),
    DAIICHI(1),
    INNER_PURCHASE(1),
    INNER_ORDER(1),
    B_CART_ORDER(1);

    private final int value;

    OfficeShopNo(int value) {
        this.value = value;
    }

    public static OfficeShopNo purse(int key) {
        for (OfficeShopNo shopNo : values()) {
            if (shopNo.getValue() == key) {
                return shopNo;
            }
        }
        return null;
    }

    public int getValue() {
        return value;
    }
}
