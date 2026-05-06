package jp.co.oda32.dto.finance.paymentmf;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentMfPreviewRow {
    private int excelRowIndex;
    private String paymentSupplierCode;
    private String sourceName;
    private Long amount;

    /** CSV 取引日列の値。NULL 時は writer 側で transactionMonth にフォールバックする。 */
    private LocalDate transactionDate;

    private String ruleKind;          // PAYABLE / EXPENSE / DIRECT_PURCHASE / SUMMARY
    private String debitAccount;
    private String debitSubAccount;
    private String debitDepartment;
    private String debitTax;
    private Long debitAmount;
    private String creditAccount;
    private String creditSubAccount;
    private String creditDepartment;
    private String creditTax;
    private Long creditAmount;
    private String summary;
    private String tag;

    private String matchStatus;       // MATCHED / DIFF / UNMATCHED / NA
    private Long payableAmount;
    private Long payableDiff;
    private Integer supplierNo;       // 買掛金一覧詳細リンク用

    private String errorType;         // UNREGISTERED / null
    private String errorMessage;
}
