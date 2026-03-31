package jp.co.oda32.domain.model.master;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import jakarta.persistence.*;
import java.sql.Timestamp;

/**
 * ショップマスタEntity
 *
 * @author k_oda
 * @since 2018/04/11
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_shop")
@ShopEntity
@SQLRestriction("shop_no <> 0")
public class MShop implements IEntity {
    @Id
    @Column(name = "shop_no")
    @SequenceGenerator(name = "m_shop_shop_no_seq_gen", sequenceName = "m_shop_shop_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_shop_shop_no_seq_gen")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "shop_name")
    private String shopName;
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
