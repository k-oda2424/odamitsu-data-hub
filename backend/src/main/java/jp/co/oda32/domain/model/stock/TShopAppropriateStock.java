package jp.co.oda32.domain.model.stock;

import jp.co.oda32.domain.model.IEntity;
import jp.co.oda32.domain.model.embeddable.TShopAppropriateStockPK;
import jp.co.oda32.domain.validation.CompanyEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * ショップ適正在庫Entity
 *
 * @author k_oda
 * @since 2019/05/24
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "t_shop_appropriate_stock")
@IdClass(TShopAppropriateStockPK.class)
@CompanyEntity
public class TShopAppropriateStock implements IEntity {
    @Id
    @Column(name = "goods_no")
    private Integer goodsNo;
    @Id
    @Column(name = "shop_no")
    private Integer shopNo;
    @Column(name = "appropriate_stock")
    private BigDecimal appropriateStock;
    @Column(name = "safety_stock")
    private BigDecimal safetyStock;
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
