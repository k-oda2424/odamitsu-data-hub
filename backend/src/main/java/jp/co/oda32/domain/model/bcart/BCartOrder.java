package jp.co.oda32.domain.model.bcart;

import com.google.gson.annotations.SerializedName;
import io.hypersistence.utils.hibernate.type.array.ListArrayType;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import lombok.*;
import org.hibernate.annotations.Type;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

@Getter
@Setter
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "b_cart_order")
public class BCartOrder {
    @Id
    @Column(name = "id")
    private Long id; // 受注ID (整数, 最大11桁)
    @Column(name = "code")
    private Long code; // 受注番号 (整数, 最大255桁)
    @Column(name = "customer_id")
    @SerializedName("customer_id")
    private Long customerId; // 注文者 会員ID (整数, 最大11桁)
    @Column(name = "customer_ext_id")
    @SerializedName("customer_ext_id")
    private String customerExtId; // 注文者 貴社独自会員ID (文字列, 最大255桁)
    @Column(name = "customer_parent_id")
    @SerializedName("customer_parent_id")
    private String customerParentId; // 注文者 親代理店ID (文字列, 最大255桁)
    @Column(name = "customer_salesman_id")
    @SerializedName("customer_salesman_id")
    private String customerSalesmanId; // 注文者 営業担当者ID (文字列, 最大255桁, カンマ区切り)
    @Column(name = "customer_comp_name")
    @SerializedName("customer_comp_name")
    private String customerCompName; // 注文者 会社名 (文字列, 最大128桁)
    @Column(name = "customer_department")
    @SerializedName("customer_department")
    private String customerDepartment; // 注文者 部署名 (文字列, 最大255桁)
    @Column(name = "customer_name")
    @SerializedName("customer_name")
    private String customerName; // 注文者名 (文字列, 最大128桁)
    @Column(name = "customer_tel")
    @SerializedName("customer_tel")
    private String customerTel; // 注文者 電話番号 (文字列, 最大255桁)
    @Column(name = "customer_mobile_phone")
    @SerializedName("customer_mobile_phone")
    private String customerMobilePhone; // 注文者 携帯番号 (文字列, 最大255桁)
    @Column(name = "customer_email")
    @SerializedName("customer_email")
    private String customerEmail; // 注文者 メールアドレス (文字列, 最大255桁)
    @Column(name = "customer_price_group_id")
    @SerializedName("customer_price_group_id")
    private String customerPriceGroupId; // 注文者 価格グループ名 (文字列, 最大255桁, 通常会員の場合null, 以外は会員価格グループ名)
    @Column(name = "customer_zip")
    @SerializedName("customer_zip")
    private String customerZip; // 注文者 郵便番号 (文字列, 最大32桁)
    @Column(name = "customer_pref")
    @SerializedName("customer_pref")
    private String customerPref; // 注文者 都道府県 (文字列, 最大32桁)
    @Column(name = "customer_address1")
    @SerializedName("customer_address1")
    private String customerAddress1; // 注文者 市町区村 (文字列, 最大255桁)
    @Column(name = "customer_address2")
    @SerializedName("customer_address2")
    private String customerAddress2; // 注文者 町域・番地 (文字列, 最大255桁)
    @Column(name = "customer_address3")
    @SerializedName("customer_address3")
    private String customerAddress3; // 注文者 ビル・建物名 (文字列, 最大255桁)
    @Type(value = ListArrayType.class, parameters = {
            @org.hibernate.annotations.Parameter(name = ListArrayType.SQL_ARRAY_TYPE, value = "text")
    })
    @Column(name = "customer_customs", columnDefinition = "text[]")
    @SerializedName("customer_customs")
    private List<Object> customerCustoms; // 注文者 カスタム項目 (配列)
    @Column(name = "payment")
    @SerializedName("payment")
    private String payment; // 決済方法 (文字列, 最大255桁)
    @Column(name = "payment_at")
    @SerializedName("payment_at")
    private LocalDate paymentAt; // 決済確定日 (日付, 10桁, Y-m-d)
    @Column(name = "total_price", precision = 25, scale = 2)
    @SerializedName("total_price")
    private BigDecimal totalPrice; // 商品合計金額 (数値, 最大255桁)
    @Column(name = "tax", length = 8)
    @SerializedName("tax")
    private BigDecimal tax; // 消費税 (整数, 最大8桁)
    @Column(name = "tax_rate", precision = 25, scale = 2)
    @SerializedName("tax_rate")
    private BigDecimal taxRate; // 税率 (数値, 最大255桁)
    @Column(name = "COD_cost", length = 8)
    @SerializedName("COD_cost")
    private BigDecimal CODCost; // 決済手数料 (整数, 最大8桁)
    @Column(name = "shipping_cost", length = 8)
    @SerializedName("shipping_cost")
    private BigDecimal shippingCost; // 送料 (整数, 最大8桁)
    @Column(name = "final_price", length = 11)
    @SerializedName("final_price")
    private BigDecimal finalPrice; // 受注総額 (整数, 最大11桁)
    @Column(name = "use_point")
    @SerializedName("use_point")
    private BigDecimal usePoint; // ポイント利用 (整数, 最大255桁)
    @Column(name = "get_point")
    @SerializedName("get_point")
    private BigDecimal getPoint; // ポイント取得 (整数, 最大255桁)
    @Type(JsonBinaryType.class)
    @Column(name = "order_totals", columnDefinition = "jsonb")
    @SerializedName("order_totals")
    private List<OrderTotal> orderTotals;// 税率ごとの合計金額 (配列, taxRate=税率, total=税抜合計金額, tax=消費税, totalInclTax=税込合計金額)
    @Column(name = "customer_message", length = 65535)
    @SerializedName("customer_message")
    private String customerMessage; // お客様からの連絡事項 (文字列, 最大65,535桁)
    @Column(name = "admin_message", length = 65535)
    @SerializedName("admin_message")
    private String adminMessage; // お客様への連絡事項 (文字列, 最大65,535桁)
    @Column(name = "memo", length = 65535)
    @SerializedName("memo")
    private String memo; // 管理用メモ (文字列, 最大65,535桁)
    @Type(value = ListArrayType.class, parameters = {
            @org.hibernate.annotations.Parameter(name = ListArrayType.SQL_ARRAY_TYPE, value = "text")
    })
    @Column(name = "customs", columnDefinition = "text[]")
    @SerializedName("customs")
    private List<String> customs; // 受注カスタム項目 (配列)
    @Column(name = "enquete1")
    @SerializedName("enquete1")
    private String enquete1; // アンケート1 (文字列, 最大255桁)
    @Column(name = "enquete2")
    @SerializedName("enquete2")
    private String enquete2; // アンケート2 (文字列, 最大255桁)
    @Column(name = "enquete3")
    @SerializedName("enquete3")
    private String enquete3; // アンケート3 (文字列, 最大255桁)
    @Column(name = "enquete4")
    @SerializedName("enquete4")
    private String enquete4; // アンケート4 (文字列, 最大255桁)
    @Column(name = "enquete5")
    @SerializedName("enquete5")
    private String enquete5; // アンケート5 (文字列, 最大255桁)
    @Column(name = "ordered_at")
    @SerializedName("ordered_at")
    private String orderedAt; // 受注日 (日付, 最大19桁, Y-m-d H:i:s)
    @Column(name = "affiliate_id")
    @SerializedName("affiliate_id")
    private String affiliateId; // 参照元ID (文字列, 最大255桁)
    @Column(name = "estimate_id")
    @SerializedName("estimate_id")
    private Long estimateId; // 見積番号 (整数, 最大255桁)
    @Column(name = "status")
    @SerializedName("status")
    private String status; // 対応状況 (文字列, 最大255桁)
    // cascade は MERGE のみ (BCartLogistics と同方針)
    @OneToMany(mappedBy = "bCartOrder", cascade = CascadeType.MERGE, fetch = FetchType.LAZY)
    @SerializedName("order_products")
    private List<BCartOrderProduct> orderProductList;// 受注商品情報
    @Transient
    @SerializedName("logistics")
    private List<BCartLogistics> bCartLogisticsList;// 出荷情報

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class OrderTotal {
        @SerializedName("tax_rate")
        private BigDecimal taxRate; // 税率 (数値, 最大255桁)
        @SerializedName("total")
        private BigDecimal total; // 税抜合計金額 (数値, 最大255桁)
        @SerializedName("tax")
        private BigDecimal tax; // 消費税 (整数, 最大8桁)
        @SerializedName("total_incl_tax")
        private BigDecimal totalInclTax; // 税込合計金額 (数値, 最大255桁)

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OrderTotal that = (OrderTotal) o;
            return Objects.equals(taxRate, that.taxRate) &&
                    Objects.equals(total, that.total) &&
                    Objects.equals(tax, that.tax) &&
                    Objects.equals(totalInclTax, that.totalInclTax);
        }

        @Override
        public int hashCode() {
            return Objects.hash(taxRate, total, tax, totalInclTax);
        }
    }
}
