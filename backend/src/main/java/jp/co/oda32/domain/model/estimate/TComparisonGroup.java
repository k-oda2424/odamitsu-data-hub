package jp.co.oda32.domain.model.estimate;

import jp.co.oda32.constant.Flag;
import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.embeddable.TComparisonGroupPK;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;
import org.hibernate.Hibernate;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
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
@Table(name = "t_comparison_group")
@IdClass(TComparisonGroupPK.class)
@CompanyEntity
public class TComparisonGroup extends AbstractCompanyEntity implements ICompanyEntity {
    @Id
    @Column(name = "comparison_no")
    private Integer comparisonNo;

    @Id
    @Column(name = "group_no")
    private Integer groupNo;

    @Column(name = "base_goods_no")
    private Integer baseGoodsNo;

    @Column(name = "base_goods_code")
    private String baseGoodsCode;

    @Column(name = "base_goods_name")
    private String baseGoodsName;

    @Column(name = "base_specification")
    private String baseSpecification;

    @Column(name = "base_purchase_price")
    private BigDecimal basePurchasePrice;

    @Column(name = "base_goods_price")
    private BigDecimal baseGoodsPrice;

    @Column(name = "base_contain_num")
    private BigDecimal baseContainNum;

    @Column(name = "display_order")
    private int displayOrder;

    @Column(name = "group_note")
    private String groupNote;

    @Column(name = "shop_no")
    private Integer shopNo;

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
    @JoinColumns({
            @JoinColumn(name = "comparison_no", referencedColumnName = "comparison_no", insertable = false, updatable = false),
            @JoinColumn(name = "group_no", referencedColumnName = "group_no", insertable = false, updatable = false)
    })
    @ToString.Exclude
    private List<TComparisonDetail> comparisonDetailList;

    @OneToOne
    @JoinColumn(name = "company_no", insertable = false, updatable = false)
    private MCompany company;

    public List<TComparisonDetail> getComparisonDetailList() {
        if (this.comparisonDetailList == null) return List.of();
        return this.comparisonDetailList.stream()
                .filter(d -> Flag.NO.getValue().equals(d.getDelFlg()))
                .collect(Collectors.toList());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TComparisonGroup that = (TComparisonGroup) o;
        return comparisonNo != null && Objects.equals(comparisonNo, that.comparisonNo)
                && groupNo != null && Objects.equals(groupNo, that.groupNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(comparisonNo, groupNo);
    }
}
