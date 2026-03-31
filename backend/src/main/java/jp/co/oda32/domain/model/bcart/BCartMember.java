package jp.co.oda32.domain.model.bcart;

/**
 * B-Cartの会員情報
 * ext_idにsmileの得意先コードを設定
 *
 * @author k_oda
 * @since 2023/06/09
 */

import com.google.gson.annotations.SerializedName;
import lombok.*;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;

@Getter
@Setter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(name = "b_cart_member")
public class BCartMember {

    @Id
    private Long id;

    @SerializedName("ext_id")
    @Column(name = "ext_id")
    private String extId;

    @SerializedName("agent_id")
    @Column(name = "agent_id")
    private String agentId;

    @SerializedName("agent_rate")
    @Column(name = "agent_rate")
    private Double agentRate;

    @SerializedName("parent_id")
    @Column(name = "parent_id")
    private Long parentId;

    @SerializedName("destination_code")
    @Column(name = "destination_code")
    private String destinationCode;

    @SerializedName("comp_name")
    @Column(name = "comp_name")
    private String compName;

    @SerializedName("comp_name_kana")
    @Column(name = "comp_name_kana")
    private String compNameKana;

    @SerializedName("ceo_last_name")
    @Column(name = "ceo_last_name")
    private String ceoLastName;

    @SerializedName("ceo_first_name")
    @Column(name = "ceo_first_name")
    private String ceoFirstName;

    @SerializedName("ceo_last_name_kana")
    @Column(name = "ceo_last_name_kana")
    private String ceoLastNameKana;

    @SerializedName("ceo_first_name_kana")
    @Column(name = "ceo_first_name_kana")
    private String ceoFirstNameKana;

    @SerializedName("department")
    @Column(name = "department")
    private String department;
    @SerializedName("tanto_last_name")
    @Column(name = "tanto_last_name")
    private String tantoLastName;

    @SerializedName("tanto_first_name")
    @Column(name = "tanto_first_name")
    private String tantoFirstName;

    @SerializedName("tanto_last_name_kana")
    @Column(name = "tanto_last_name_kana")
    private String tantoLastNameKana;

    @SerializedName("tanto_first_name_kana")
    @Column(name = "tanto_first_name_kana")
    private String tantoFirstNameKana;

    @SerializedName("zip")
    @Column(name = "zip")
    private String zip;

    @SerializedName("pref")
    @Column(name = "pref")
    private String pref;

    @SerializedName("address1")
    @Column(name = "address1")
    private String address1;

    @SerializedName("address2")
    @Column(name = "address2")
    private String address2;

    @SerializedName("address3")
    @Column(name = "address3")
    private String address3;

    @SerializedName("email")
    @Column(name = "email")
    private String email;

    @SerializedName("email_cc")
    @Column(name = "email_cc")
    private String emailCc;

    @SerializedName("tel")
    @Column(name = "tel")
    private String tel;

    @SerializedName("mobile_phone")
    @Column(name = "mobile_phone")
    private String mobilePhone;

    @SerializedName("fax")
    @Column(name = "fax")
    private String fax;

    @SerializedName("url")
    @Column(name = "url")
    private String url;

    @SerializedName("foundation")
    @Column(name = "foundation")
    private String foundation;

    @SerializedName("sales")
    @Column(name = "sales")
    private Integer sales;

    @SerializedName("job")
    @Column(name = "job")
    private String job;

    @SerializedName("memo")
    @Column(name = "memo")
    private String memo;

    @SerializedName("payment")
    @Column(name = "payment")
    private String payment;

    @SerializedName("special_shipping_cost")
    @Column(name = "special_shipping_cost")
    private String specialShippingCost;

    @SerializedName("paid")
    @Column(name = "paid")
    private String paid;

    @SerializedName("mm_flag")
    @Column(name = "mm_flag")
    private Integer mmFlag;

    @SerializedName("point")
    @Column(name = "point")
    private Integer point;

    @SerializedName("price_group_id")
    @Column(name = "price_group_id")
    private Long priceGroupId;

    @SerializedName("view_group_id")
    @Column(name = "view_group_id")
    private Long viewGroupId;

    @SerializedName("salesman_id")
    @Column(name = "salesman_id")
    private String salesmanId;

    @SerializedName("af_id")
    @Column(name = "af_id")
    private String afId;

    @SerializedName("credit_limit")
    @Column(name = "credit_limit")
    private Integer creditLimit;

    @SerializedName("cutoff_date")
    @Column(name = "cutoff_date")
    private String cutoffDate;

    @SerializedName("payment_month")
    @Column(name = "payment_month")
    private String paymentMonth;

    @SerializedName("payment_date")
    @Column(name = "payment_date")
    private String paymentDate;

    @SerializedName("default_other_shipping_id")
    @Column(name = "default_other_shipping_id")
    private Long defaultOtherShippingId;

    @SerializedName("default_payment")
    @Column(name = "default_payment")
    private String defaultPayment;

    @SerializedName("hidden_price")
    @Column(name = "hidden_price")
    private Integer hiddenPrice;

    @SerializedName("status")
    @Column(name = "status")
    private String status;

    @SerializedName("created_at")
    @Column(name = "created_at")
    private LocalDate createdAt;

    @SerializedName("updated_at")
    @Column(name = "updated_at")
    private LocalDate updatedAt;

    @Column(name = "smile_partner_master_linked")
    private boolean smilePartnerMasterLinked;

    @Column(name = "need_smile_order_file_goods_code")
    private boolean needSmileOrderFileGoodsCode;
}
