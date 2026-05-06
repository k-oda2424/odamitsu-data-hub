package jp.co.oda32.domain.service.finance.mf;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** MF /api/v3/journals のレスポンス要素。複合仕訳対応のため branches[] を持つ。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MfJournal(
        String id,
        @JsonProperty("transaction_date") LocalDate transactionDate,
        Integer number,
        @JsonProperty("is_realized") Boolean isRealized,
        @JsonProperty("journal_type") String journalType,
        String memo,
        List<MfBranch> branches
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MfBranch(
            MfSide creditor,
            MfSide debitor,
            String remark
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record MfSide(
            @JsonProperty("account_id") String accountId,
            @JsonProperty("account_name") String accountName,
            @JsonProperty("sub_account_id") String subAccountId,
            @JsonProperty("sub_account_name") String subAccountName,
            @JsonProperty("department_name") String departmentName,
            @JsonProperty("tax_name") String taxName,
            @JsonProperty("tax_value") BigDecimal taxValue,
            BigDecimal value
    ) {}
}
