package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * MF /api/v3/accounts の勘定科目レスポンス。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfAccount(
        String id,
        String name,
        @JsonProperty("account_group") String accountGroup,    // ASSET / LIABILITY / EQUITY / REVENUE / EXPENSE
        String category,
        @JsonProperty("financial_statement_type") String financialStatementType,
        Boolean available,
        @JsonProperty("search_key") String searchKey,
        @JsonProperty("tax_id") String taxId,
        @JsonProperty("sub_accounts") List<MfSubAccount> subAccounts
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MfSubAccount(
            String id,
            String name,
            @JsonProperty("account_id") String accountId,
            @JsonProperty("search_key") String searchKey,
            @JsonProperty("tax_id") String taxId
    ) {}
}
