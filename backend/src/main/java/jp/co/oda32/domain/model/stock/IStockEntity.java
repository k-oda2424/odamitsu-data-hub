package jp.co.oda32.domain.model.stock;

import jp.co.oda32.domain.model.IEntity;

import java.math.BigDecimal;

/**
 * 会社番号を持つEntityクラスのインターフェースクラス
 *
 * @author k_oda
 * @since 2019/07/18
 */
public interface IStockEntity extends IEntity {
    Integer getGoodsNo();

    Integer getWarehouseNo();

    Integer getUnit1No();

    BigDecimal getUnit1StockNum();

    Integer getUnit2No();

    void setUnit1StockNum(BigDecimal unit1StockNum);

    Integer getUnit3No();

    BigDecimal getUnit2StockNum();

    void setUnit1No(Integer unit1No);

    void setUnit2No(Integer unit2No);

    void setUnit3No(Integer unit3No);

    void setUnit2StockNum(BigDecimal unit2StockNum);

    BigDecimal getUnit3StockNum();

    void setUnit3StockNum(BigDecimal unit3StockNum);
}
