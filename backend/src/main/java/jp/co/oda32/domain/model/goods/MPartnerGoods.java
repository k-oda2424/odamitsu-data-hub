package jp.co.oda32.domain.model.goods;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.model.embeddable.MPartnerGoodsPK;
import jp.co.oda32.domain.model.master.MCompany;
import jp.co.oda32.domain.model.master.MShop;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * 得意先商品テーブルのEntityクラス
 *
 * @author k_oda
 * @since 2018/11/23
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "m_partner_goods")
@IdClass(MPartnerGoodsPK.class)
@ShopEntity
public class MPartnerGoods implements IEntity {
    @Id
    @Column(name = "partner_no")
    private Integer partnerNo;
    @Id
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Id
    @Column(name = "destination_no")
    private Integer destinationNo;
    @Column(name = "company_no")
    private Integer companyNo;
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "goods_price")
    private BigDecimal goodsPrice;
    @Column(name = "order_num_per_year")
    private BigDecimal orderNumPerYear;
    @Column(name = "goods_name")
    private String goodsName;
    @Column(name = "goods_code")
    private String goodsCode;
    @Column(name = "keyword")
    private String keyword;
    @Column(name = "last_sales_date")
    private LocalDate lastSalesDate;
    @Column(name = "last_price_update_date")
    private LocalDate lastPriceUpdateDate;
    @Column(name = "reflected_estimate_no")
    private Integer reflectedEstimateNo;
    @Column(name = "reflected_estimate_detail_no")
    private Integer reflectedEstimateDetailNo;

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
    @JoinColumn(name = "goods_no", insertable = false, updatable = false)
    private MGoods mGoods;

    @OneToOne
    @JoinColumn(name = "shop_no", insertable = false, updatable = false)
    private MShop mShop;

    @OneToOne
    @JoinColumn(name = "company_no", insertable = false, updatable = false)
    private MCompany mCompany;
}
