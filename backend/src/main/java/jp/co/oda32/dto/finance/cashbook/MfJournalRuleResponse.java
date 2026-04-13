package jp.co.oda32.dto.finance.cashbook;

import jp.co.oda32.domain.model.finance.MMfJournalRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MfJournalRuleResponse {
    private Integer id;
    private String descriptionC;
    private String descriptionDKeyword;
    private Integer priority;
    private String amountSource;
    private String debitAccount;
    private String debitSubAccount;
    private String debitDepartment;
    private String debitTaxResolver;
    private String creditAccount;
    private String creditSubAccount;
    private String creditSubAccountTemplate;
    private String creditDepartment;
    private String creditTaxResolver;
    private String summaryTemplate;
    private Boolean requiresClientMapping;

    public static MfJournalRuleResponse from(MMfJournalRule e) {
        return MfJournalRuleResponse.builder()
                .id(e.getId())
                .descriptionC(e.getDescriptionC())
                .descriptionDKeyword(e.getDescriptionDKeyword())
                .priority(e.getPriority())
                .amountSource(e.getAmountSource())
                .debitAccount(e.getDebitAccount())
                .debitSubAccount(e.getDebitSubAccount())
                .debitDepartment(e.getDebitDepartment())
                .debitTaxResolver(e.getDebitTaxResolver())
                .creditAccount(e.getCreditAccount())
                .creditSubAccount(e.getCreditSubAccount())
                .creditSubAccountTemplate(e.getCreditSubAccountTemplate())
                .creditDepartment(e.getCreditDepartment())
                .creditTaxResolver(e.getCreditTaxResolver())
                .summaryTemplate(e.getSummaryTemplate())
                .requiresClientMapping(e.getRequiresClientMapping())
                .build();
    }
}
