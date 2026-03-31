package jp.co.oda32.domain.model.master;

import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;

import jakarta.persistence.*;
import java.sql.Timestamp;

/**
 * 倉庫マスタEntity
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
@Table(name = "m_warehouse")
@CompanyEntity
public class MWarehouse extends AbstractCompanyEntity implements ICompanyEntity {
    @Id
    @Column(name = "warehouse_no")
    @SequenceGenerator(name = "m_warehouse_warehouse_no_seq_gen", sequenceName = "m_warehouse_warehouse_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_warehouse_warehouse_no_seq_gen")
    private Integer warehouseNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "warehouse_name")
    private String warehouseName;
    @Column(name = "zip_code")
    private String zipCode;
    @Column(name = "tel_no")
    private String telNo;
    @Column(name = "fax_no")
    private String faxNo;

    @Column(name = "address1")
    private String address1;
    @Column(name = "address2")
    private String address2;
    @Column(name = "address3")
    private String address3;
    @Column(name = "address4")
    private String address4;


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
}
