package jp.co.oda32.domain.model.goods;

import jp.co.oda32.domain.model.IEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 商品単位マスタEntity
 *
 * @author k_oda
 * @since 2018/07/20
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_goods_unit")
public class MGoodsUnit implements IEntity {
    @Id
    @Column(name = "unit_no")
    @SequenceGenerator(name = "m_goods_unit_unit_no_seq_gen", sequenceName = "m_goods_unit_unit_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_goods_unit_unit_no_seq_gen")
    private Integer unitNo;
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "unit")
    private String unit;
    @Column(name = "contain_num")
    private BigDecimal containNum;
    @Column(name = "parent_unit_no")
    private Integer parentUnitNo;
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
    @Override
    public Integer getShopNo() {
        return null;
    }
}
