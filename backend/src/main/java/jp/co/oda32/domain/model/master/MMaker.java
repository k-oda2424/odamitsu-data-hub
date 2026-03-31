package jp.co.oda32.domain.model.master;

import jp.co.oda32.domain.model.IEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.sql.Timestamp;

/**
 * メーカーマスタEntity
 *
 * @author k_oda
 * @since 2018/04/11
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_maker")
public class MMaker implements IEntity {
    @Id
    @Column(name = "maker_no")
    @SequenceGenerator(name = "m_maker_maker_no_seq_gen", sequenceName = "m_maker_maker_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_maker_maker_no_seq_gen")
    private Integer makerNo;
    @Column(name = "maker_code")
    private String makerCode;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "maker_name")
    private String makerName;
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

    @Override
    public Integer getShopNo() {
        return shopNo;
    }
}
