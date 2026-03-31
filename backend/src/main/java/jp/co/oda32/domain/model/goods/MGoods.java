package jp.co.oda32.domain.model.goods;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.model.master.MMaker;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 商品マスタEntity
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_goods")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
public class MGoods implements IEntity {
    @Id
    @Column(name = "goods_no")
    @SequenceGenerator(name = "m_goods_goods_no_seq_gen", sequenceName = "m_goods_goods_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "m_goods_goods_no_seq_gen")
    private Integer goodsNo;
    @Column(name = "goods_name")
    private String goodsName;
    @Column(name = "jan_code")
    private String janCode;
    @Column(name = "maker_no")
    private Integer makerNo;
    @Column(name = "del_flg")
    private String delFlg;
    @Column(name = "keyword")
    private String keyword;
    @Column(name = "tax_category")
    private Integer taxCategory;
    @Column(name = "tax_category_name")
    private String taxCategoryName;
    @Column(name = "specification")
    private String specification;
    @Column(name = "discontinued_flg")
    private String discontinuedFlg;
    @Column(name = "case_contain_num")
    private BigDecimal caseContainNum;
    @Column(name = "is_apply_reduced_tax_rate")
    private boolean isApplyReducedTaxRate;
    @Column(name = "smile_goods_name")
    private String smileGoodsName;

    @Column(name = "add_date_time")
    private Timestamp addDateTime;
    @Column(name = "add_user_no")
    private Integer addUserNo;
    @Column(name = "modify_date_time")
    private Timestamp modifyDateTime;
    @Column(name = "modify_user_no")
    private Integer modifyUserNo;

    @OneToOne
    @JoinColumn(name = "maker_no", insertable = false, updatable = false)
    private MMaker maker;

    @Override
    public Integer getShopNo() {
        return null;
    }
}
