package jp.co.oda32.domain.model.query;

import jp.co.oda32.domain.model.embeddable.WSalesGoodsPK;
import jp.co.oda32.domain.validation.ShopEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import java.math.BigDecimal;

/**
 * 販売商品系テーブルカスタマイズ用Entityクラス
 *
 * @author k_oda
 * @since 2019/07/29
 */
@Data
@Builder
@Entity
@AllArgsConstructor
@NoArgsConstructor
@ShopEntity
@IdClass(WSalesGoodsPK.class)
public class CustomSalesGoods {
    @Id
    private Integer shopNo;
    @Id
    private Integer goodsNo;
    private String goodsCode;
    private String goodsSkuCode;
    private String goodsName;
    private Integer categoryNo;
    private BigDecimal referencePrice;
    private BigDecimal purchasePrice;
    private BigDecimal goodsPrice;
    private Integer supplierNo;
    private String supplierName;
    private String catchphrase;
    private String goodsIntroduction;
    private String goodsDescription1;
    private String goodsDescription2;
    private String delFlg;
    private String keyword;
    private String directShippingFlg;
    private Integer leadTime;
    // ケース入数
    private BigDecimal caseContainNum;
}
