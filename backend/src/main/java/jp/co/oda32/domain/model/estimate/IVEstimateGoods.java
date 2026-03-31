package jp.co.oda32.domain.model.estimate;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 見積商品情報収集Viewのインターフェース
 *
 * @author k_oda
 * @since 2022/12/21
 */
public interface IVEstimateGoods {
    Integer getGoodsNo();

    String getGoodsCode();

    String getGoodsName();

    BigDecimal getPurchasePrice();

    LocalDate getChangePlanDate();

    BigDecimal getCaseContainNum();

    BigDecimal getChangeContainNum();

    BigDecimal getBeforePrice();

    BigDecimal getAfterPrice();
}
