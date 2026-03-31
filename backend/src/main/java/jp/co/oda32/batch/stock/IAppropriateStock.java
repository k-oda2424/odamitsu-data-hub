package jp.co.oda32.batch.stock;

import java.math.BigDecimal;

/**
 * 適正在庫算出用インターフェース
 *
 * @author k_oda
 * @since 2019/05/22
 */
public interface IAppropriateStock {
    // 安全係数の定義
    // 90%
    BigDecimal SERVICE90 = new BigDecimal(1.28);
    // 95%
    BigDecimal SERVICE95 = new BigDecimal(1.65);
    // 97.7%
    BigDecimal SERVICE97 = new BigDecimal(2);
    // 99%
    BigDecimal SERVICE99 = new BigDecimal(2.33);
}
