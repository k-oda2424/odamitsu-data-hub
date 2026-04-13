package jp.co.oda32.domain.model.estimate;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MPartner;
import jp.co.oda32.domain.model.order.MDeliveryDestination;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;
import org.hibernate.Hibernate;

import jakarta.persistence.*;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "t_estimate_comparison")
@CompanyEntity
public class TEstimateComparison extends AbstractCompanyEntity implements ICompanyEntity {
    @Id
    @Column(name = "comparison_no")
    @SequenceGenerator(name = "t_estimate_comparison_comparison_no_seq_gen", sequenceName = "t_estimate_comparison_comparison_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "t_estimate_comparison_comparison_no_seq_gen")
    private Integer comparisonNo;

    @Column(name = "shop_no")
    private Integer shopNo;

    @Column(name = "partner_no")
    private Integer partnerNo;

    @Column(name = "destination_no")
    private Integer destinationNo;

    @Column(name = "comparison_date")
    private LocalDate comparisonDate;

    @Column(name = "comparison_status")
    private String comparisonStatus;

    @Column(name = "source_estimate_no")
    private Integer sourceEstimateNo;

    @Column(name = "title")
    private String title;

    @Column(name = "note")
    private String note;

    @Column(name = "company_no")
    private Integer companyNo;

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

    @OneToMany
    @JoinColumn(name = "comparison_no", insertable = false, updatable = false)
    @ToString.Exclude
    private List<TComparisonGroup> comparisonGroupList;

    @OneToOne
    @JoinColumn(name = "company_no", insertable = false, updatable = false)
    private MCompany company;

    @OneToOne
    @JoinColumn(name = "partner_no", insertable = false, updatable = false)
    private MPartner mPartner;

    @OneToOne
    @JoinColumn(name = "destination_no", insertable = false, updatable = false)
    private MDeliveryDestination mDeliveryDestination;

    public List<TComparisonGroup> getComparisonGroupList() {
        if (this.comparisonGroupList == null) return List.of();
        return this.comparisonGroupList.stream()
                .filter(g -> Flag.NO.getValue().equals(g.getDelFlg()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TEstimateComparison that = (TEstimateComparison) o;
        return comparisonNo != null && Objects.equals(comparisonNo, that.comparisonNo);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
