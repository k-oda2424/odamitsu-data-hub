package jp.co.oda32.domain.model.stock;

import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.embeddable.TStockPK;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.MGoodsUnit;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MWarehouse;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;
import org.hibernate.annotations.NotFound;
import org.hibernate.annotations.NotFoundAction;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 在庫Entity
 *
 * @author k_oda
 * @since 2017/05/11
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_stock")
@IdClass(TStockPK.class)
@CompanyEntity
public class TStock extends AbstractCompanyEntity implements ICompanyEntity, IStockEntity {
    @Id
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Id
    @Column(name = "warehouse_no")
    private Integer warehouseNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "unit1_no")
    private Integer unit1No;
    @Column(name = "unit1_stock_num")
    private BigDecimal unit1StockNum;
    @Column(name = "unit2_no")
    private Integer unit2No;
    @Column(name = "unit2_stock_num")
    private BigDecimal unit2StockNum;
    @Column(name = "unit3_no")
    private Integer unit3No;
    @Column(name = "unit3_stock_num")
    private BigDecimal unit3StockNum;
    @Column(name = "lead_time")
    private Integer leadTime;

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

    @OneToOne
    @JoinColumn(name = "goods_no", insertable = false, updatable = false)
    private MGoods mGoods;

    @OneToOne
    @JoinColumn(name = "warehouse_no", insertable = false, updatable = false)
    private MWarehouse mWarehouse;

    @OneToOne
    @JoinColumn(name = "unit1_no", insertable = false, updatable = false)
    private MGoodsUnit goodsUnit1;
    @OneToOne
    @JoinColumn(name = "unit2_no", insertable = false, updatable = false)
    private MGoodsUnit goodsUnit2;
    @OneToOne
    @JoinColumn(name = "unit3_no", insertable = false, updatable = false)
    private MGoodsUnit goodsUnit3;

    @ManyToOne(fetch = FetchType.LAZY)
    @NotFound(action = NotFoundAction.IGNORE)
    @JoinColumns({
            @JoinColumn(name = "goods_no", referencedColumnName = "goods_no", insertable = false, updatable = false),
            @JoinColumn(name = "shop_no", referencedColumnName = "shop_no", insertable = false, updatable = false)})
    private TShopAppropriateStock tShopAppropriateStock;

    private BigDecimal getPieceNum() {
        BigDecimal unit1Piece = BigDecimal.ZERO;
        if (this.unit1StockNum != null && this.goodsUnit1 != null && this.goodsUnit1.getContainNum() != null) {
            unit1Piece = this.unit1StockNum.multiply(this.goodsUnit1.getContainNum());
        }

        BigDecimal unit2Piece = BigDecimal.ZERO;
        if (this.unit2StockNum != null && this.goodsUnit2 != null && this.goodsUnit2.getContainNum() != null) {
            unit2Piece = this.unit2StockNum.multiply(this.goodsUnit2.getContainNum());
        }
        BigDecimal unit3Piece = BigDecimal.ZERO;
        if (this.unit3StockNum != null && this.goodsUnit3 != null && this.goodsUnit3.getContainNum() != null) {
            unit3Piece = this.unit3StockNum.multiply(this.goodsUnit3.getContainNum());
        }
        return unit1Piece.add(unit2Piece).add(unit3Piece);
    }

    /**
     * 在庫一覧で使用している
     *
     * @return 在庫過不足
     */
    public BigDecimal getEnoughStock() {
        if (this.tShopAppropriateStock == null || this.tShopAppropriateStock.getAppropriateStock() == null) {
            return null;
        }
        return getPieceNum().subtract(this.tShopAppropriateStock.getAppropriateStock());
    }
}
