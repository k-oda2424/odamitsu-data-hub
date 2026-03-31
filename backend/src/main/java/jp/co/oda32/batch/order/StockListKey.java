package jp.co.oda32.batch.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author k_oda
 * @since 2019/11/13
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StockListKey {
    private Integer goodsNo;
    private Integer shopNo;
}
