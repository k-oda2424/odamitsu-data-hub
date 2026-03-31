package jp.co.oda32.domain.model.order;

import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 注文Entity
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_order")
@CompanyEntity
public class TOrder extends AbstractCompanyEntity implements ICompanyEntity {
    @Id
    @Column(name = "order_no")
    @SequenceGenerator(name = "t_order_order_no_seq_gen", sequenceName = "t_order_order_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "t_order_order_no_seq_gen")
    private Integer orderNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "company_name")
    private String companyName;
    @Column(name = "order_status")
    private String orderStatus;
    @Column(name = "order_date_time")
    private LocalDateTime orderDateTime;
    @Column(name = "total_price")
    private BigDecimal totalPrice;
    @Column(name = "tax_total_price")
    private BigDecimal taxTotalPrice;
    @Column(name = "note")
    private String note;
    @Column(name = "order_route")
    private String orderRoute;
    @Column(name = "destination_no")
    private Integer destinationNo;
    @Column(name = "payment_method")
    private String paymentMethod;
    @Column(name = "b_cart_order_id")
    private Long bCartOrderId;
    @Column(name = "b_cart_order_code")
    private Long bCartOrderCode; // 受注番号 (整数, 最大255桁)
    @Column(name = "processing_serial_number") // smile処理連番
    private Long processingSerialNumber;

    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "partner_code")
    private String partnerCode;

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
    @JoinColumn(name = "order_no", insertable = false, updatable = false)
    private List<TOrderDetail> orderDetailList;
}
