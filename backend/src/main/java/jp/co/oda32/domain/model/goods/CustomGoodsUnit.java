package jp.co.oda32.domain.model.goods;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 商品単位マスタEntityをカスタマイズ(拡張)したクラス
 *
 * @author k_oda
 * @since 2018/07/20
 */
@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Data
public class CustomGoodsUnit extends MGoodsUnit {
    // ケースの個数を保持する
    private BigDecimal unitNum;
}
