package jp.co.oda32.domain.model.estimate;

import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.embeddable.TComparisonDetailPK;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;
import org.hibernate.Hibernate;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Objects;

@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "t_comparison_detail")
@IdClass(TComparisonDetailPK.class)
@CompanyEntity
public class TComparisonDetail extends AbstractCompanyEntity implements ICompanyEntity {
    @Id
    @Column(name = "comparison_no")
    private Integer comparisonNo;

    @Id
    @Column(name = "group_no")
    private Integer groupNo;

    @Id
    @Column(name = "detail_no")
    private Integer detailNo;

    @Column(name = "goods_no")
    private Integer goodsNo;

    @Column(name = "goods_code")
    private String goodsCode;

    @Column(name = "goods_name")
    private String goodsName;

    @Column(name = "specification")
    private String specification;

    @Column(name = "purchase_price")
    private BigDecimal purchasePrice;

    @Column(name = "proposed_price")
    private BigDecimal proposedPrice;

    @Column(name = "contain_num")
    private BigDecimal containNum;

    @Column(name = "profit_rate")
    private BigDecimal profitRate;

    @Column(name = "detail_note")
    private String detailNote;

    @Column(name = "display_order")
    private int displayOrder;

    @Column(name = "supplier_no")
    private Integer supplierNo;

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

    @OneToOne
    @JoinColumn(name = "company_no", insertable = false, updatable = false)
    private MCompany company;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TComparisonDetail that = (TComparisonDetail) o;
        return comparisonNo != null && Objects.equals(comparisonNo, that.comparisonNo)
                && groupNo != null && Objects.equals(groupNo, that.groupNo)
                && detailNo != null && Objects.equals(detailNo, that.detailNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(comparisonNo, groupNo, detailNo);
    }
}
