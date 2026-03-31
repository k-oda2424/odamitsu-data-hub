package jp.co.oda32.domain.model.estimate;

import jp.co.oda32.dto.estimate.IEstimateDetail;
import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.embeddable.TEstimateDetailPK;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;
import org.hibernate.Hibernate;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.Objects;

/**
 * 見積明細Entity
 *
 * @author k_oda
 * @since 2022/10/24
 */
@Getter
@Setter
@ToString
@RequiredArgsConstructor
@Builder
@Entity
@AllArgsConstructor
@Table(name = "t_estimate_detail")
@IdClass(TEstimateDetailPK.class)
@CompanyEntity
public class TEstimateDetail extends AbstractCompanyEntity implements ICompanyEntity, IEstimateDetail {
    @OneToOne
    @JoinColumn(name = "estimate_no", insertable = false, updatable = false)
    protected TEstimate tEstimate;
    @Id
    @Column(name = "estimate_no")
    private Integer estimateNo;
    @Id
    @Column(name = "estimate_detail_no")
    private Integer estimateDetailNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "goods_code")
    private String goodsCode;
    @Column(name = "goods_price")
    private BigDecimal goodsPrice;
    // 入数
    @Column(name = "contain_num")
    private BigDecimal containNum;
    // 変更入数(変更がない場合null)
    @Column(name = "change_contain_num")
    private BigDecimal changeContainNum;
    // 見積ケース数
    @Column(name = "estimate_case_num")
    private BigDecimal estimateCaseNum;
    // 見積数量
    @Column(name = "estimate_num")
    private BigDecimal estimateNum;
    @Column(name = "goods_name")
    private String goodsName;
    // 仕様
    @Column(name = "specification")
    private String specification;
    @Column(name = "detail_note")
    private String detailNote;

    @Column(name = "profit_rate")
    private BigDecimal profitRate;
    @Column(name = "purchase_price")
    private BigDecimal purchasePrice;
    @Column(name = "display_order")
    private int displayOrder;
    @Column(name = "partner_goods_reflect")
    private boolean partnerGoodsReflect;
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
    @OneToOne
    @JoinColumn(name = "goods_no", insertable = false, updatable = false)
    private MGoods mGoods;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || Hibernate.getClass(this) != Hibernate.getClass(o)) return false;
        TEstimateDetail that = (TEstimateDetail) o;
        return estimateNo != null && Objects.equals(estimateNo, that.estimateNo)
                && estimateDetailNo != null && Objects.equals(estimateDetailNo, that.estimateDetailNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(estimateNo, estimateDetailNo);
    }
}
