package jp.co.oda32.domain.model.purchase;


import jp.co.oda32.domain.model.AbstractCompanyEntity;
import jp.co.oda32.domain.model.ICompanyEntity;
import jp.co.oda32.domain.model.embeddable.TPurchaseDetailPK;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.validation.CompanyEntity;
import jp.co.oda32.util.BigDecimalUtil;
import lombok.*;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * 仕入明細Entity
 *
 * @author k_oda
 * @since 2018/11/20
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_purchase_detail")
@IdClass(TPurchaseDetailPK.class)
@CompanyEntity
public class TPurchaseDetail extends AbstractCompanyEntity implements ICompanyEntity {
    @Id
    @Column(name = "purchase_no")
    private Integer purchaseNo;
    @Id
    @Column(name = "purchase_detail_no")
    private Integer purchaseDetailNo;
    @Column(name = "ext_purchase_no")// smile処理連番
    private Long extPurchaseNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Column(name = "goods_code")
    private String goodsCode;
    @Column(name = "goods_name")
    private String goodsName;
    @Column(name = "goods_price")
    private BigDecimal goodsPrice;
    @Column(name = "include_tax_goods_price")
    private BigDecimal includeTaxGoodsPrice;
    @Column(name = "goods_num")
    private BigDecimal goodsNum;
    @Column(name = "purchase_date")
    private LocalDate purchaseDate;
    @Column(name = "warehouse_no")
    private Integer warehouseNo;
    @Column(name = "tax_type") // 税込金額か税抜金額か
    private String taxType;
    @Column(name = "tax_category") // 課税種別 0：通常税率 1：軽減税率 2：非課税
    private Integer taxCategory;
    @Column(name = "tax_rate")
    private BigDecimal taxRate;
    @Column(name = "tax_price")
    private BigDecimal taxPrice;
    @Column(name = "subtotal")
    private BigDecimal subtotal;
    @Column(name = "include_tax_subtotal")
    private BigDecimal includeTaxSubtotal;
    @Column(name = "difficult_price")
    private BigDecimal difficultPrice;
    @Column(name = "note")
    private String note;
    @Column(name = "stock_process_flg")
    private String stockProcessFlg;
    @Column(name = "send_order_no")
    private Integer sendOrderNo;
    @Column(name = "send_order_detail_no")
    private Integer sendOrderDetailNo;
    @Column(name = "contain_num")
    private BigDecimal containNum;
    @Column(name = "purchase_price_reflect")
    private String purchasePriceReflect;

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
    @JoinColumn(name = "purchase_no", insertable = false, updatable = false)
    private TPurchase tPurchase;

    public BigDecimal getPurchaseCaseNum() {
        if (BigDecimalUtil.isZero(BigDecimalUtil.convertNullToZero(containNum))) {
            return null;
        }
        return goodsNum.divide(containNum, RoundingMode.DOWN);
    }
}
