package jp.co.oda32.domain.model.purchase;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.model.master.MWarehouse;
import jp.co.oda32.domain.validation.CompanyEntity;
import jp.co.oda32.util.DateTimeUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_send_order")
@CompanyEntity
public class TSendOrder implements IEntity {

    @Id
    @Column(name = "send_order_no")
    @SequenceGenerator(name = "t_send_order_send_order_no_seq_gen", sequenceName = "t_send_order_send_order_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "t_send_order_send_order_no_seq_gen")
    private Integer sendOrderNo;
    @Column(name = "send_order_date_time")
    private LocalDateTime sendOrderDateTime;
    @Column(name = "desired_delivery_date")
    private LocalDate desiredDeliveryDate;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;

    @Column(name = "supplier_no")
    private Integer supplierNo;
    @Column(name = "send_order_status")
    private String sendOrderStatus;
    @Column(name = "warehouse_no")
    private Integer warehouseNo;

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
    @JoinColumn(name = "send_order_no", insertable = false, updatable = false)
    private List<TSendOrderDetail> tSendOrderDetailList;

    @OneToOne
    @JoinColumn(name = "supplier_no", insertable = false, updatable = false)
    private MSupplier mSupplier;

    @OneToOne
    @JoinColumn(name = "warehouse_no", insertable = false, updatable = false)
    private MWarehouse mWarehouse;

    public String getSendOrderDisplayDate() {
        return DateTimeUtil.displayDate(this.sendOrderDateTime);
    }
}
