package jp.co.oda32.dto.finance.paymentmf;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PaymentMfRuleRequest {
    @NotBlank
    private String sourceName;
    private String paymentSupplierCode;
    @NotBlank
    private String ruleKind;
    @NotBlank
    private String debitAccount;
    private String debitSubAccount;
    private String debitDepartment;
    @NotBlank
    private String debitTaxCategory;
    private String creditAccount;
    private String creditSubAccount;
    private String creditDepartment;
    private String creditTaxCategory;
    @NotBlank
    private String summaryTemplate;
    private String tag;
    private Integer priority;
}
