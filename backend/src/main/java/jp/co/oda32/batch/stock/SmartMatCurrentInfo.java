package jp.co.oda32.batch.stock;

/**
 * スマートマットのcurrent_info APIのフォーマット
 *
 * @author k_oda
 * @since 2019/12/11
 */
public class SmartMatCurrentInfo {
    SmartMatGoodsInfo Jan;
    private String id;
    private String customerid;
    private String jan;
    private String full;
    private String unit_weight;
    private String container_weight;
    private String set_full_date;
    private String remaining;
    private String remaining_number;
    private String set_remaining_date;
    private String current_measure_data;
    private String set_current_measure_date;
    private String ave_measure_data;
    private String is_connected_wifi;
    private String wifi_connected_date;
    private String can_create_pre_order;
    private String schedule_create_pre_order;
    private String latest_pre_order_id;
    private String pre_order_trigger_remaining;
    private String pre_order_trigger_remaining_number;
    private String battery_percent;
    private String location;
    private String version;
    private String output_type;
}
