package jp.co.oda32.domain.model.order;

import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.embeddable.TReturnDetailPK;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 注文明細Entity
 *
 * @author k_oda
 * @since 2018/11/20
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_return_detail")
@IdClass(TReturnDetailPK.class)
@CompanyEntity
public class TReturnDetail extends AbstractCompanyEntity implements ICompanyEntity, IOrderDetailEntity {
    @Id
    @Column(name = "return_no")
    private Integer returnNo;
    @Id
    @Column(name = "return_detail_no")
    private Integer returnDetailNo;
    @Column(name = "order_no")
    private Integer orderNo;
    @Column(name = "order_detail_no")
    private Integer orderDetailNo;
    @Column(name = "delivery_no")
    private Integer deliveryNo;
    @Column(name = "delivery_detail_no")
    private Integer deliveryDetailNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "return_detail_status")
    private String returnDetailStatus;
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "goods_code")
    private String goodsCode;
    @Column(name = "unit_no")
    private Integer unitNo;
    @Column(name = "unit_num")
    private Integer unitNum;
    @Column(name = "return_num")
    private BigDecimal returnNum;
    @Column(name = "goods_price")
    private BigDecimal goodsPrice;
    @Column(name = "goods_name")
    private String goodsName;
    @Column(name = "subtotal")
    private BigDecimal subtotal;
    @Column(name = "tax_type")
    private String taxType;
    @Column(name = "tax_price")
    private BigDecimal taxPrice;
    @Column(name = "note")
    private String note;
    @Column(name = "processing_serial_number")
    private Long processingSerialNumber;

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
    @JoinColumn(name = "return_no", insertable = false, updatable = false)
    protected TReturn tReturn;

    @Override
    public BigDecimal getGoodsNum() {
        return this.returnNum;
    }

    @Override
    public void setGoodsNum(BigDecimal goodsNum) {
        setReturnNum(goodsNum);
    }

    @Override
    public LocalDateTime getOrderDateTime() {
        return this.tReturn.getReturnDateTime();
    }
}
