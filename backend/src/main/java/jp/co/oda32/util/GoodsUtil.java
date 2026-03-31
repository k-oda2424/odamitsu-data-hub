package jp.co.oda32.util;

/**
 * 商品に関わるUtilクラス
 *
 * @author k_oda
 * @since 2024/02/12
 */
public class GoodsUtil {
    public static String removePriceFromName(String goodsName) {
        if (goodsName.contains("@")) {
            goodsName = goodsName.substring(0, goodsName.indexOf("@"));
        }
        return goodsName;
    }
}
