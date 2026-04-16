package jp.co.oda32.dto.finance.paymentmf;

import jp.co.oda32.domain.model.finance.TPaymentMfAuxRow;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 買掛仕入MF 補助行の API レスポンス DTO。
 * 補助行タブ表示 / プレビュー API 用。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentMfAuxRowResponse {
    private Long auxRowId;
    private LocalDate transactionMonth;
    private LocalDate transferDate;
    private String ruleKind;              // EXPENSE / SUMMARY / DIRECT_PURCHASE
    private Integer sequenceNo;
    private String sourceName;
    private String paymentSupplierCode;
    private BigDecimal amount;
    private String debitAccount;
    private String debitSubAccount;
    private String debitDepartment;
    private String debitTax;
    private String creditAccount;
    private String creditSubAccount;
    private String creditDepartment;
    private String creditTax;
    private String summary;
    private String tag;
    private String sourceFilename;

    public static PaymentMfAuxRowResponse from(TPaymentMfAuxRow r) {
        return PaymentMfAuxRowResponse.builder()
                .auxRowId(r.getAuxRowId())
                .transactionMonth(r.getTransactionMonth())
                .transferDate(r.getTransferDate())
                .ruleKind(r.getRuleKind())
                .sequenceNo(r.getSequenceNo())
                .sourceName(r.getSourceName())
                .paymentSupplierCode(r.getPaymentSupplierCode())
                .amount(r.getAmount())
                .debitAccount(r.getDebitAccount())
                .debitSubAccount(r.getDebitSubAccount())
                .debitDepartment(r.getDebitDepartment())
                .debitTax(r.getDebitTax())
                .creditAccount(r.getCreditAccount())
                .creditSubAccount(r.getCreditSubAccount())
                .creditDepartment(r.getCreditDepartment())
                .creditTax(r.getCreditTax())
                .summary(r.getSummary())
                .tag(r.getTag())
                .sourceFilename(r.getSourceFilename())
                .build();
    }
}
