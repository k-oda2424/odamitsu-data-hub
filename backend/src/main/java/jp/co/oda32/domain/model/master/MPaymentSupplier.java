package jp.co.oda32.domain.model.master;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 支払先マスタEntity
 *
 * @author k_oda
 * @since 2019/06/05
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_payment_supplier")
@ShopEntity
public class MPaymentSupplier implements IEntity {
    @Id
    @Column(name = "payment_supplier_no")
    @SequenceGenerator(name = "m_payment_supplier_payment_supplier_no_seq_gen", sequenceName = "m_payment_supplier_payment_supplier_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_payment_supplier_payment_supplier_no_seq_gen")
    private Integer paymentSupplierNo;
    @Column(name = "payment_supplier_code")
    private String paymentSupplierCode;
    @Column(name = "payment_supplier_name")
    private String paymentSupplierName;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "tax_timing_code")
    private BigDecimal taxTimingCode;
    @Column(name = "tax_timing")
    private String taxTiming;
    @Column(name = "cutoff_date")
    private Integer cutoffDate;

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
}
