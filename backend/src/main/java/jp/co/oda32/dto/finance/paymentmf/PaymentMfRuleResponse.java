package jp.co.oda32.dto.finance.paymentmf;

import jp.co.oda32.domain.model.finance.MPaymentMfRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentMfRuleResponse {
    private Integer id;
    private String sourceName;
    private String paymentSupplierCode;
    private String ruleKind;
    private String debitAccount;
    private String debitSubAccount;
    private String debitDepartment;
    private String debitTaxCategory;
    private String creditAccount;
    private String creditSubAccount;
    private String creditDepartment;
    private String creditTaxCategory;
    private String summaryTemplate;
    private String tag;
    private Integer priority;

    public static PaymentMfRuleResponse from(MPaymentMfRule r) {
        return PaymentMfRuleResponse.builder()
                .id(r.getId())
                .sourceName(r.getSourceName())
                .paymentSupplierCode(r.getPaymentSupplierCode())
                .ruleKind(r.getRuleKind())
                .debitAccount(r.getDebitAccount())
                .debitSubAccount(r.getDebitSubAccount())
                .debitDepartment(r.getDebitDepartment())
                .debitTaxCategory(r.getDebitTaxCategory())
                .creditAccount(r.getCreditAccount())
                .creditSubAccount(r.getCreditSubAccount())
                .creditDepartment(r.getCreditDepartment())
                .creditTaxCategory(r.getCreditTaxCategory())
                .summaryTemplate(r.getSummaryTemplate())
                .tag(r.getTag())
                .priority(r.getPriority())
                .build();
    }
}
