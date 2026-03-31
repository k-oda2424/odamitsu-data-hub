
package jp.co.oda32.batch.estimate;

import jp.co.oda32.domain.model.goods.IPartnerGoodsPriceChangePlan;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * 親得意先商品価格変更予定拡張Entity
 *
 * @author k_oda
 * @since 2022/12/29
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CustomParentGoodsPriceChange implements IPartnerGoodsPriceChangePlan {
    Integer cParentPartnerNo;
    String cParentPartnerCode;
    Integer partnerGoodsPriceChangePlanNo;
    private Integer shopNo;
    private Integer companyNo;
    private Integer partnerNo;
    private String partnerCode;
    private Integer goodsNo;
    private String goodsCode;
    private String janCode;
    private BigDecimal beforePrice;
    private BigDecimal afterPrice;
    private String goodsName;
    private BigDecimal changeContainNum;
    private Integer destinationNo;
    private LocalDate changePlanDate;
    private String changeReason;
    private BigDecimal beforePurchasePrice;
    private BigDecimal afterPurchasePrice;
    private boolean estimateCreated;
    private Integer estimateNo;
    private Integer estimateDetailNo;
    private boolean partnerPriceReflect;
    private Integer parentChangePlanNo;
    private boolean deficitFlg;
    private String note;
    private String delFlg;
    private Timestamp addDateTime;
    private Integer addUserNo;
    private Timestamp modifyDateTime;
    private Integer modifyUserNo;
}
