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
 * 得意先カテゴリマスタEntity
 *
 * @author k_oda
 * @since 2021/01/21
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_partner_category")
@ShopEntity
public class MPartnerCategory implements IEntity {
    @Id
    @Column(name = "partner_category_no")
    @SequenceGenerator(name = "m_partner_category_partner_category_no_seq_gen", sequenceName = "m_partner_category_partner_category_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_partner_category_partner_category_no_seq_gen")
    private Integer partnerCategoryNo;
    @Column(name = "partner_category_name")
    private String partnerCategoryName;
    @Column(name = "partner_category_code")
    private String partnerCategoryCode;

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
    @Column(name = "shop_no")
    private Integer shopNo;
}
