package jp.co.oda32.batch.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 納品履歴登録APIリクエストフォーム
 *
 * @author k_oda
 * @since 2019/12/27
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class DeliveryRegisterForm {
    protected Integer deliveryNo;
    protected String matId;
    protected LocalDateTime deliveryTime;
    protected Integer companyNo;
    protected Integer warehouseNo;
    protected Integer goodsNo;
    protected String goodsCode;
    protected BigDecimal deliveryNum;
    protected String extOrderNo;
    protected String deliveryDateTime;
    protected String message;
}
