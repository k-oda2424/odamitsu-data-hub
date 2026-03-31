package jp.co.oda32.domain.model.order;

import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

/**
 * 出荷Entity
 *
 * @author k_oda
 * @since 2018/11/21
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_delivery")
@CompanyEntity
public class TDelivery extends AbstractCompanyEntity implements ICompanyEntity {
    @Id
    @Column(name = "delivery_no")
    @SequenceGenerator(name = "t_delivery_delivery_no_seq_gen", sequenceName = "t_delivery_delivery_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "t_delivery_delivery_no_seq_gen")
    private Integer deliveryNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "partner_code")
    private String partnerCode;
    @Column(name = "slip_no")
    private String slipNo;
    @Column(name = "slip_date")
    private LocalDate slipDate;
    @Column(name = "delivery_status")
    private String deliveryStatus;
    @Column(name = "destination_no")
    private Integer destinationNo;
    @Column(name = "destination_name")
    private String destinationName;
    @Column(name = "delivery_plan_date")
    private LocalDate deliveryPlanDate;
    @Column(name = "delivery_date")
    private LocalDate deliveryDate;
    @Column(name = "direct_shipping_flg")
    private String directShippingFlg;
    @Column(name = "total_price")
    private BigDecimal totalPrice;
    @Column(name = "tax_total_price")
    private BigDecimal taxTotalPrice;
    @Column(name = "tracking_number")
    private String trackingNumber;
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
    @OneToMany
    @JoinColumn(name = "delivery_no", insertable = false, updatable = false)
    private List<TDeliveryDetail> deliveryDetailList;

}
