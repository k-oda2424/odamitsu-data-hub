package jp.co.oda32.domain.model.purchase;

import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MSupplier;
import jp.co.oda32.domain.model.master.MWarehouse;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.List;

/**
 * 仕入Entity
 *
 * @author k_oda
 * @since 2019/06/02
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_purchase")
@CompanyEntity
public class TPurchase extends AbstractCompanyEntity implements ICompanyEntity {

    @Id
    @Column(name = "purchase_no")
    @SequenceGenerator(name = "t_purchase_purchase_no_seq_gen", sequenceName = "t_purchase_purchase_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "t_purchase_purchase_no_seq_gen")
    private Integer purchaseNo;
    @Column(name = "ext_purchase_no")// smile処理連番
    private Long extPurchaseNo;
    @Column(name = "purchase_code")
    private String purchaseCode;
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;
    @Column(name = "supplier_no")
    private Integer supplierNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "warehouse_no")
    private Integer warehouseNo;
    @Column(name = "send_order_no")
    private Integer sendOrderNo;
    @Column(name = "purchase_amount")
    private BigDecimal purchaseAmount;
    @Column(name = "include_tax_amount")
    private BigDecimal includeTaxAmount;
    @Column(name = "tax_amount")
    private BigDecimal taxAmount;
    @Column(name = "tax_type")
    private String taxType;
    @Column(name = "tax_timing")
    private String taxTiming;
    @Column(name = "tax_rate")
    private BigDecimal taxRate;
    @Column(name = "department_no")
    private Integer departmentNo;
    @Column(name = "note")
    private String note;

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
    @JoinColumn(name = "supplier_no", insertable = false, updatable = false)
    private MSupplier mSupplier;
    @OneToOne
    @JoinColumn(name = "warehouse_no", insertable = false, updatable = false)
    private MWarehouse mWarehouse;
    @OneToMany
    @JoinColumn(name = "purchase_no", insertable = false, updatable = false)
    private List<TPurchaseDetail> purchaseDetailList;
}
