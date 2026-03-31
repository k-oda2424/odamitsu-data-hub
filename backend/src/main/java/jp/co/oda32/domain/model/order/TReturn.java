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
import java.time.LocalDateTime;
import java.util.List;

/**
 * 返品Entity
 *
 * @author k_oda
 * @since 2018/11/29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_return")
@CompanyEntity
public class TReturn extends AbstractCompanyEntity implements ICompanyEntity {
    @Id
    @Column(name = "return_no")
    @SequenceGenerator(name = "t_return_return_no_seq_gen", sequenceName = "t_return_return_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "t_return_return_no_seq_gen")
    private Integer returnNo;
    @Column(name = "order_no")
    private Integer orderNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "return_status")
    private String returnStatus;
    @Column(name = "return_date_time")
    private LocalDateTime returnDateTime;
    @Column(name = "return_finish_date_time")
    private LocalDateTime returnFinishDateTime;
    @Column(name = "return_total_price")
    private BigDecimal returnTotalPrice;
    @Column(name = "return_tax_total_price")
    private BigDecimal returnTaxTotalPrice;
    @Column(name = "slip_no")
    private String slipNo;
    @Column(name = "return_slip_no")
    private String returnSlipNo;
    @Column(name = "note")
    private String note;
    @Column(name = "slip_date")
    private LocalDate slipDate;
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
    @JoinColumn(name = "return_no", insertable = false, updatable = false)
    private List<TReturnDetail> returnDetailList;
}
