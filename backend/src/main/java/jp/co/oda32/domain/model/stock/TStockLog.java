package jp.co.oda32.domain.model.stock;

import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.goods.MGoods;
import jp.co.oda32.domain.model.goods.MGoodsUnit;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MWarehouse;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 在庫履歴Entity
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
@Table(name = "t_stock_log")
@CompanyEntity
public class TStockLog extends AbstractCompanyEntity implements ICompanyEntity, IStockEntity {
    @Id
    @Column(name = "stock_log_no")
    @SequenceGenerator(name = "t_stock_log_stock_log_no_seq_gen", sequenceName = "t_stock_log_stock_log_no_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.IDENTITY, generator = "t_stock_log_stock_log_no_seq_gen")
    private Integer stockLogNo;
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "warehouse_no")
    private Integer warehouseNo;
    @Column(name = "move_time")
    private LocalDateTime moveTime;
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
    @Column(name = "reason")
    private String reason;
    @Column(name = "delivery_no")
    private Integer deliveryNo;
    @Column(name = "destination_warehouse_no")
    private Integer destinationWarehouseNo;
    @Column(name = "purchase_no")
    private Integer purchaseNo;
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

}
