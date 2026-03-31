package jp.co.oda32.domain.model.master;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.*;
import org.hibernate.Hibernate;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import jakarta.persistence.*;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * 会社マスタEntity
 *
 * @author k_oda
 * @since 2017/05/01
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "m_company")
@ShopEntity
public class MCompany implements IEntity {

    @Id
    @Column(name = "company_no")
    @SequenceGenerator(name = "m_company_company_no_seq_gen", sequenceName = "m_company_company_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_company_company_no_seq_gen")
    private Integer companyNo;

    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "company_name")
    private String companyName;
    @Column(name = "abbreviated_company_name")
    private String abbreviatedCompanyName;
    @Column(name = "company_type")
    private String companyType;
    @Column(name = "tax_pattern")
    private String taxPattern;
    @Column(name = "mat_api_key")
    private String matApiKey;

    @Column(name = "add_date_time")
    private Timestamp addDateTime;
    @Column(name = "add_user_no")
    private Integer addUserNo;
    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;
    @Column(name = "modify_user_no")
    private Integer modifyUserNo;
    @Column(name = "del_flg")
    private String delFlg;

    @OneToOne
    @JoinColumn(name = "shop_no", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    @ToString.Exclude
    private MShop shop;

    @JsonIgnore
    @OneToOne
    @JoinColumn(name = "partner_no", insertable = false, updatable = false)
    @NotFound(action = NotFoundAction.IGNORE)
    @ToString.Exclude
    private MPartner partner;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        MCompany mCompany = (MCompany) o;
        return companyNo != null && Objects.equals(companyNo, mCompany.companyNo);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
