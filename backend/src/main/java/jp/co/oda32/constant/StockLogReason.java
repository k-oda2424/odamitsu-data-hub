package jp.co.oda32.constant;

/**
 * 在庫履歴理由
 *
 * @author k_oda
 * @since 2019/04/30
 */
public enum StockLogReason {
    // NEW:新規登録,ARRIVED:仕入前入荷,PURCHASE:仕入,ALLOCATE:引当,SHIPMENT:出荷,MOVE_IN:移動入庫,MOVE_OUT:移動出庫,RETURN:返品,USE:使用,DISPOSE:処分(破棄),ADJUST:在庫調整,INVENTORY:棚卸
    NEW("new"),
    ARRIVED("arrived"),
    PURCHASE("purchase"),
    ALLOCATE("allocate"),
    SHIPMENT("shipment"),
    MOVE_IN("move_in"),
    MOVE_OUT("move_out"),
    RETURN("return"),
    USE("use"),
    DISPOSE("dispose"),
    ADJUST("adjust"),
    INVENTORY("inventory");

    private String value;

    StockLogReason(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static StockLogReason purse(String key) {
        for (StockLogReason returnStatus : values()) {
            if (returnStatus.getValue().equals(key)) {
                return returnStatus;
            }
        }
        return null;
    }
}
