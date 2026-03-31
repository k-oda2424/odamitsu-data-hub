package jp.co.oda32.domain.model.purchase;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.model.embeddable.TSendOrderDetailPK;
import jp.co.oda32.domain.validation.CompanyEntity;
import jp.co.oda32.util.BigDecimalUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_send_order_detail")
@IdClass(TSendOrderDetailPK.class)
@CompanyEntity
public class TSendOrderDetail implements IEntity {

    @Id
    @Column(name = "send_order_no")
    private Integer sendOrderNo;
    @Id
    @Column(name = "send_order_detail_no")
    private Integer sendOrderDetailNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;

    @Column(name = "warehouse_no")
    private Integer warehouseNo;
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "goods_code")
    private String goodsCode;
    @Column(name = "goods_name")
    private String goodsName;
    @Column(name = "goods_price")
    private BigDecimal goodsPrice;
    @Column(name = "send_order_num")
    private Integer sendOrderNum;
    @Column(name = "send_order_case_num")
    private BigDecimal sendOrderCaseNum;
    @Column(name = "arrive_plan_date")
    private LocalDate arrivePlanDate;
    @Column(name = "arrived_date")
    private LocalDate arrivedDate;
    @Column(name = "arrived_num")
    private BigDecimal arrivedNum;
    @Column(name = "difference_num")
    private BigDecimal differenceNum;
    @Column(name = "send_order_detail_status")
    private String sendOrderDetailStatus;
    @Column(name = "contain_num")
    private Integer containNum;

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
    @JoinColumn(name = "send_order_no", insertable = false, updatable = false)
    protected TSendOrder tSendOrder;

    public String getSubtotalDisplay() {
        return String.format("%s円", BigDecimalUtil.decimalFormatMoney(this.goodsPrice.multiply(new BigDecimal(this.sendOrderNum))));
    }
}
