package jp.co.oda32.dto.finance.cashbook;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class MfJournalRuleRequest {
    @NotBlank private String descriptionC;
    private String descriptionDKeyword;
    @NotNull private Integer priority;
    @NotBlank private String amountSource;
    @NotBlank private String debitAccount;
    private String debitSubAccount;
    private String debitDepartment;
    @NotBlank private String debitTaxResolver;
    @NotBlank private String creditAccount;
    private String creditSubAccount;
    private String creditSubAccountTemplate;
    private String creditDepartment;
    @NotBlank private String creditTaxResolver;
    @NotBlank private String summaryTemplate;
    @NotNull private Boolean requiresClientMapping;
}
