package jp.co.oda32.dto.finance.cashbook;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CashBookPreviewRow {
    private int excelRowIndex;
    private Integer transactionNo;
    private String transactionDate;
    private String debitAccount;
    private String debitSubAccount;
    private String debitDepartment;
    private String debitClient;
    private String debitTax;
    private String debitInvoice;
    private Integer debitAmount;
    private String creditAccount;
    private String creditSubAccount;
    private String creditDepartment;
    private String creditClient;
    private String creditTax;
    private String creditInvoice;
    private Integer creditAmount;
    private String summary;
    private String tag;
    private String memo;
    private String descriptionC;
    private String descriptionD;
    private String errorType;
    private String errorMessage;
}
