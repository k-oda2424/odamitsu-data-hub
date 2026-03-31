package jp.co.oda32.domain.model.order;

import jp.co.oda32.domain.model.IEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.sql.Timestamp;

/**
 * 届け先マスタEntity
 *
 * @author k_oda
 * @since 2018/04/11
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_delivery_destination")
public class MDeliveryDestination implements IEntity {
    @Id
    @Column(name = "destination_no")
    @SequenceGenerator(name = "m_delivery_destination_destination_no_seq_gen", sequenceName = "m_delivery_destination_destination_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_delivery_destination_destination_no_seq_gen")
    private Integer destinationNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "partner_no")
    private Integer partnerNo;
    @Column(name = "destination_code")
    private String destinationCode;
    @Column(name = "destination_name")
    private String destinationName;
    @Column(name = "address1")
    private String address1;
    @Column(name = "address2")
    private String address2;
    @Column(name = "address3")
    private String address3;
    @Column(name = "tel_number")
    private String telNumber;
    @Column(name = "fax_number")
    private String faxNumber;
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
