package jp.co.oda32.domain.model.master;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jp.co.oda32.constant.PaymentType;
import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.*;
import org.hibernate.Hibernate;

import jakarta.persistence.*;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Objects;

/**
 * 得意先マスタEntity
 *
 * @author k_oda
 * @since 2018/04/11
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "m_partner")
@ShopEntity
public class MPartner implements IEntity {
    @Id
    @Column(name = "partner_no")
    @SequenceGenerator(name = "m_partner_partner_no_seq_gen", sequenceName = "m_partner_partner_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_partner_partner_no_seq_gen")
    private Integer partnerNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "partner_name")
    private String partnerName;
    @Column(name = "abbreviated_partner_name")
    private String abbreviatedPartnerName;
    @Column(name = "partner_code")
    private String partnerCode;
    @Column(name = "last_order_date")
    private LocalDate lastOrderDate;
    @Column(name = "cutoff_date")
    private Integer cutoffDate;
    @Column(name = "note")
    private String note;
    @Column(name = "partner_category_code")
    private String partnerCategoryCode;
    @Column(name = "parent_partner_no")
    private Integer parentPartnerNo;
    @Column(name = "is_include_tax_display")
    private boolean isIncludeTaxDisplay;
    @Column(name = "invoice_partner_code")
    private String invoicePartnerCode;
    @Column(name = "del_flg")
    private String delFlg;
    @Column(name = "add_date_time")
    private Timestamp addDateTime;
    @Column(name = "add_user_no")
    private Integer addUserNo;
    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;
    @Column(name = "modify_user_no")
    private Integer modifyUserNo;

    @JsonIgnore
    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "company_no", insertable = false, updatable = false)
    @ToString.Exclude
    private MCompany mCompany;

    public MCompany getCompany() {
        return this.mCompany;
    }

    /**
     * 支払タイプを取得します
     *
     * @return 支払タイプ
     */
    public PaymentType getPaymentType() {
        return PaymentType.fromCutoffCode(this.cutoffDate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        MPartner mPartner = (MPartner) o;
        return partnerNo != null && Objects.equals(partnerNo, mPartner.partnerNo);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
