package jp.co.oda32.domain.model.order;

import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.embeddable.TDeliveryDetailPK;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 出荷明細Entity
 *
 * @author k_oda
 * @since 2018/12/13
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_delivery_detail")
@IdClass(TDeliveryDetailPK.class)
@CompanyEntity
public class TDeliveryDetail extends AbstractCompanyEntity implements ICompanyEntity, IOrderDetailEntity {
    @Id
    @Column(name = "delivery_no")
    private Integer deliveryNo;
    @Id
    @Column(name = "delivery_detail_no")
    private Integer deliveryDetailNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "slip_no")
    private String slipNo;
    @Column(name = "order_no")
    private Integer orderNo;
    @Column(name = "order_detail_no")
    private Integer orderDetailNo;
    @Column(name = "delivery_detail_status")
    private String deliveryDetailStatus;
    @Column(name = "warehouse_no")
    private Integer warehouseNo;
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "goods_code")
    private String goodsCode;
    @Column(name = "goods_price")
    private BigDecimal goodsPrice;
    @Column(name = "tax_type")
    private String taxType;
    @Column(name = "tax_price")
    private BigDecimal taxPrice;
    @Column(name = "unit_no")
    private Integer unitNo;
    @Column(name = "unit_num")
    private Integer unitNum;
    @Column(name = "unit_contain_num")
    private Integer unitContainNum;
    @Column(name = "unit_name")
    private String unitName;
    @Column(name = "delivery_num")
    private BigDecimal deliveryNum;
    @Column(name = "return_num")
    private Integer returnNum;
    @Column(name = "mat_api_flg")
    private String matApiFlg;
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
    @JoinColumn(name = "delivery_no", insertable = false, updatable = false)
    protected TDelivery tDelivery;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns({
            @JoinColumn(name = "order_no", referencedColumnName = "order_no", insertable = false, updatable = false),
            @JoinColumn(name = "order_detail_no", referencedColumnName = "order_detail_no", insertable = false, updatable = false)})
    private TOrderDetail tOrderDetail;

    public Integer getReturnNum() {
        return this.returnNum != null ? this.returnNum : 0;
    }

    @Override
    public String getGoodsName() {
        // 今のところ使用想定なし 使用する場合商品名カラムを追加する
        return null;
    }

    @Override
    public BigDecimal getGoodsNum() {
        return this.deliveryNum;
    }

    @Override
    public void setGoodsNum(BigDecimal goodsNum) {
        setDeliveryNum(goodsNum);
    }

    @Override
    public LocalDateTime getOrderDateTime() {
        return this.tDelivery.getDeliveryDate().atStartOfDay();
    }
}
