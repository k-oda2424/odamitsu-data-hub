package jp.co.oda32.domain.model.master;

import jp.co.oda32.constant.CompanyType;
import jp.co.oda32.constant.OfficeShopNo;
import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.sql.Timestamp;

/**
 * ログインユーザマスタEntity
 *
 * @author k_oda
 * @since 2017/05/01
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_login_user")
@ShopEntity
public class MLoginUser implements IEntity {

    @Id
    @Column(name = "login_user_no")
    @SequenceGenerator(name = "m_login_user_login_user_no_seq_gen", sequenceName = "m_login_user_login_user_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_login_user_login_user_no_seq_gen")
    private Integer loginUserNo;

    @Column(name = "user_name")
    private String userName;
    @Column(name = "password")
    private String password;
    @Column(name = "login_id")
    private String loginId;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "company_type")
    private String companyType;
    @Column(name = "add_date_time")
    private Timestamp addDateTime;
    @Column(name = "add_user_no")
    private Integer addUserNo;
    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;
    @Column(name = "modify_user_no")
    private Integer modifyUserNo;
    @Column(name = "del_flg")
    private String delFlg;

    @OneToOne
    @JoinColumn(name = "company_no", insertable = false, updatable = false)
    private MCompany company;

    @Override
    public Integer getShopNo() {
        CompanyType ut = CompanyType.purse(companyType);
        if (ut == null) {
            // 該当なし
            return -1;
        }
        switch (ut) {
            case ADMIN:
                // shop_no:0に固定する
                return OfficeShopNo.ADMIN.getValue();
            case SHOP:
                return this.company.getShopNo();
            case PARTNER:
                // パートナーの場合は所属ショップ番号を返す
                return this.company.getPartner().getShopNo();
        }
        // 該当なし
        return -1;
    }
}
