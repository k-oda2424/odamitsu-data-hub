package jp.co.oda32.domain.model.goods;

import jp.co.oda32.domain.model.IEntity;

import java.math.BigDecimal;

/**
 * 販売商品系テーブルの共通インターフェースクラス
 *
 * @author k_oda
 * @since 2018/07/20
 */
public interface ISalesGoods extends IEntity {
    boolean getIsWork();

    MGoods getMGoods();

    Integer getSupplierNo();

    BigDecimal getPurchasePrice();

    BigDecimal getGoodsPrice();

    String getGoodsCode();

    String getGoodsName();

    Integer getGoodsNo();

    void setPurchasePrice(BigDecimal purchasePrice);

    void setGoodsName(String goodsName);

}
