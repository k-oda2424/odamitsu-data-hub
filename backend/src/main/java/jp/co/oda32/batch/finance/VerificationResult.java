package jp.co.oda32.batch.finance;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * SMILE支払情報との照合結果を保持するクラス
 */
@Getter
@AllArgsConstructor
@ToString
public class VerificationResult {
    // 仕入先コード
    private final String supplierCode;
    // 税率（全税率の合計を比較するためnullになる可能性がある）
    private final BigDecimal taxRate;
    // 買掛金額（税込）
    private final BigDecimal accountsPayableAmount;
    // SMILE支払額
    private final BigDecimal smilePaymentAmount;
    // 差額（SMILE支払額 - 買掛金額）
    private final BigDecimal difference;

    /**
     * 金額が一致しているかチェックします。
     * 差額が許容範囲内（±100円）であれば一致とみなします。
     */
    public boolean isMatched() {
        return difference.abs().compareTo(BigDecimal.valueOf(100)) <= 0;
    }
}
