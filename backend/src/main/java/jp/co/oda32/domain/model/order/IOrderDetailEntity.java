package jp.co.oda32.domain.model.order;

import jp.co.oda32.domain.model.IEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 注文明細情報を持つEntityクラスのインターフェースクラス
 *
 * @author k_oda
 * @since 2018/12/18
 */
public interface IOrderDetailEntity extends IEntity {
    // 商品コード
    String getGoodsCode();

    void setGoodsCode(String goodsCode);

    // 商品番号
    Integer getGoodsNo();

    void setGoodsNo(Integer goodsNo);

    // 商品名
    String getGoodsName();

    // 数量
    BigDecimal getGoodsNum();

    void setGoodsNum(BigDecimal goodsNum);

    // 単価
    BigDecimal getGoodsPrice();

    void setGoodsPrice(BigDecimal goodsPrice);

    // 注文・返品・配送日時
    LocalDateTime getOrderDateTime();

}
