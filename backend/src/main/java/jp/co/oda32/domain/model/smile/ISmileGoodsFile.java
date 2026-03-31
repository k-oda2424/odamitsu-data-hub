package jp.co.oda32.domain.model.smile;

import java.math.BigDecimal;

/**
 * @author k_oda
 * @since 2024/09/12
 */
public interface ISmileGoodsFile {

    // 商品名を取得するメソッド
    String getShouhinMei();

    // 課税区分を取得するメソッド
    String getKazeiKubun();

    // 消費税分類を取得するメソッド
    Integer getShouhizeiBunrui();

    // 入数を取得するメソッド
    BigDecimal getIrisu();

    // 商品コードを取得するメソッド
    String getShouhinCode();

    // 店舗番号を取得するメソッド
    Integer getShopNo();

    // 仕入価格または販売価格を取得するメソッド
    BigDecimal getTanka();
}
