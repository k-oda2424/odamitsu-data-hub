package jp.co.oda32.domain.model.order;

import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.embeddable.TOrderDetailPK;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 注文明細Entity
 *
 * @author k_oda
 * @since 2018/11/20
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_order_detail")
@IdClass(TOrderDetailPK.class)
@CompanyEntity
public class TOrderDetail extends AbstractCompanyEntity implements ICompanyEntity, IOrderDetailEntity {
    @Id
    @Column(name = "order_no")
    private Integer orderNo;
    @Id
    @Column(name = "order_detail_no")
    private Integer orderDetailNo;
    // 注文を受けたショップ
    @Column(name = "shop_no")
    private Integer shopNo;
    // 注文した会社番号
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "order_detail_status")
    private String orderDetailStatus;
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "goods_code")
    private String goodsCode;
    @Column(name = "unit_no")
    private Integer unitNo;
    @Column(name = "unit_num")
    private Integer unitNum;
    @Column(name = "unit_contain_num")
    private Integer unitContainNum;
    @Column(name = "unit_name")
    private String unitName;
    @Column(name = "order_num")
    private BigDecimal orderNum;
    @Column(name = "cancel_num")
    private BigDecimal cancelNum;
    @Column(name = "return_num")
    private BigDecimal returnNum;

    @Column(name = "goods_price")
    private BigDecimal goodsPrice;
    @Column(name = "goods_name")
    private String goodsName;
    @Column(name = "tax_type")
    private String taxType;
    @Column(name = "tax_rate")
    private BigDecimal taxRate;
    @Column(name = "delivery_no")
    private Integer deliveryNo;
    @Column(name = "delivery_detail_no")
    private Integer deliveryDetailNo;
    @Column(name = "note")
    private String note;
    @Column(name = "purchase_price")
    private BigDecimal purchasePrice;
    @Column(name = "markup_ratio")
    private BigDecimal markupRatio;
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
    @JoinColumn(name = "order_no", insertable = false, updatable = false)
    protected TOrder tOrder;
    @OneToOne
    @JoinColumn(name = "delivery_no", insertable = false, updatable = false)
    protected TDelivery tDelivery;

    public BigDecimal getCancelNum() {
        return this.cancelNum != null ? this.cancelNum : BigDecimal.ZERO;
    }

    public BigDecimal getReturnNum() {
        return this.returnNum != null ? this.returnNum : BigDecimal.ZERO;
    }

    @Override
    public BigDecimal getGoodsNum() {
        return this.orderNum;
    }

    @Override
    public void setGoodsNum(BigDecimal goodsNum) {
        setOrderNum(goodsNum);
    }

    @Override
    public LocalDateTime getOrderDateTime() {
        return this.tOrder.getOrderDateTime();
    }

    public LocalDate getOrderDate() {
        return this.getOrderDateTime().toLocalDate();
    }

    public BigDecimal getTotalAmount() {
        return getGoodsPrice().multiply(getOrderNum().subtract(getCancelNum()).subtract(getReturnNum()));
    }
}
