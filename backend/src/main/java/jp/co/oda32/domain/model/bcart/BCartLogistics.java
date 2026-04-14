package jp.co.oda32.domain.model.bcart;

import com.google.gson.annotations.SerializedName;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import jakarta.persistence.*;
import java.util.List;

/**
 * B-Cartの受注商品に紐づく出荷情報
 *
 * @author k_oda
 * @since 2023/03/21
 */
@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "b_cart_logistics")
public class BCartLogistics {
    @Id
    @Column(name = "id")
    @SerializedName("id")
    private Long id; // 出荷ID (整数, 最大11桁)

    @Column(name = "shipment_code")
    @SerializedName("shipment_code")
    private String shipmentCode; // 配送管理番号 (文字列, 最大255桁) smileの処理連番を入れておく

    @Column(name = "delivery_code")
    @SerializedName("delivery_code")
    private String deliveryCode; // 送り状番号 (文字列, 最大255桁)

    @Column(name = "destination_code")
    @SerializedName("destination_code")
    private String destinationCode; // 配送先コード (文字列, 最大255桁)

    @Column(name = "shipping_group_id")
    @SerializedName("shipping_group_id")
    private String shippingGroupId; // 配送グループID (文字列, 最大255桁)

    @Column(name = "comp_name")
    @SerializedName("comp_name")
    private String compName; // 配送先 会社名 (文字列, 最大255桁)

    @Column(name = "department")
    @SerializedName("department")
    private String department; // 配送先 部署名 (文字列, 最大255桁)

    @Column(name = "name")
    @SerializedName("name")
    private String name; // 配送先 担当者名 (文字列, 最大255桁)

    @Column(name = "zip")
    @SerializedName("zip")
    private String zip; // 配送先郵便番号 (文字列, 最大16桁)

    @Column(name = "pref")
    @SerializedName("pref")
    private String pref; // 配送先都道府県 (文字列, 最大32桁)

    @Column(name = "address1")
    @SerializedName("address1")
    private String address1; // 配送先 市町区村 (文字列, 最大255桁)
    @Column(name = "address2")
    @SerializedName("address2")
    private String address2; // 配送先 町域・番地 (文字列, 最大255桁)

    @Column(name = "address3")
    @SerializedName("address3")
    private String address3; // 配送先 ビル・建物名 (文字列, 最大255桁)

    @Column(name = "tel")
    @SerializedName("tel")
    private String tel; // 配送先電話番号 (文字列, 最大255桁)

    @Column(name = "due_date")
    @SerializedName("due_date")
    private String dueDate; // 配送希望日 (日付)
    @Column(name = "due_time")
    @SerializedName("due_time")
    private String dueTime; // 配送希望時間 (文字列, 最大32桁)

    @Column(name = "memo", columnDefinition = "TEXT")
    @SerializedName("memo")
    private String memo; // 発送メモ (文字列, 最大65535桁)

    @Column(name = "shipment_date")
    @SerializedName("shipment_date")
    private String shipmentDate; // 発送日 (日付)

    @Column(name = "arrival_date")
    @SerializedName("arrival_date")
    private String arrivalDate; // 納品日 (日付)

    @Column(name = "status")
    @SerializedName("status")
    private String status; // 発送状況 (文字列, 最大4桁, 未発送/発送指示/発送済)

    @Column(name = "is_updated")
    private boolean isUpdated;// レコードが更新されたかどうか、csv出力したらfalseにする

    @Column(name = "b_cart_csv_exported")
    private boolean bCartCsvExported;// B-CARTの出荷実績CSV出力したかどうか

    // cascade は MERGE のみ。BCartOrderProduct の FK (logistics_id/order_id) は
    // insertable=false/updatable=false で読み取り専用のため、PERSIST/REMOVE はそもそも機能しない。
    // 新規作成時は BCartOrderProductService 経由で明示的に保存する (BCartOrderRegisterTasklet 等)。
    @OneToMany(mappedBy = "bCartLogistics", cascade = CascadeType.MERGE, fetch = FetchType.EAGER)
    @BatchSize(size = 30)
    private List<BCartOrderProduct> bCartOrderProductList;

    /**
     * DBにカラムは持たない、コード上での便宜的なリレーション。
     */
    @Transient
    public BCartOrder getBCartOrder() {
        if (this.bCartOrderProductList == null || this.bCartOrderProductList.isEmpty()) {
            return null;
        }
        // 複数の商品が紐づいていても、同じ bCartOrder を指すはずなので
        // ここでは先頭のものを返す
        BCartOrderProduct firstProduct = this.bCartOrderProductList.get(0);
        return firstProduct.getBCartOrder();
    }
}
