package jp.co.oda32.domain.model.master;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.sql.Timestamp;

/**
 * 仕入先マスタEntity
 *
 * @author k_oda
 * @since 2018/07/18
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_supplier")
@ShopEntity
public class MSupplier implements IEntity {
    @Id
    @Column(name = "supplier_no")
    @SequenceGenerator(name = "m_supplier_supplier_no_seq_gen", sequenceName = "m_supplier_supplier_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_supplier_supplier_no_seq_gen")
    private Integer supplierNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "supplier_code")
    private String supplierCode;
    @Column(name = "supplier_name")
    private String supplierName;
    @Column(name = "supplier_name_display")
    private String supplierNameDisplay;
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
    @Column(name = "standard_lead_time")
    private Integer standardLeadTime;
    @Column(name = "payment_supplier_no")
    private Integer paymentSupplierNo;

    @OneToOne
    @JoinColumn(name = "payment_supplier_no", insertable = false, updatable = false)
    private MPaymentSupplier paymentSupplier;
}
