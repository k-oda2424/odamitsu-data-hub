package jp.co.oda32.batch.finance.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 買掛金集計のキーを表すクラス
 */
@AllArgsConstructor
@Getter
@EqualsAndHashCode
public class SummaryKey {
    private final Integer shopNo;              // ショップ番号
    private final Integer paymentSupplierNo;   // 支払先番号
    private final String paymentSupplierCode;  // 支払先コード
    private final BigDecimal taxRate;          // 税率
}
