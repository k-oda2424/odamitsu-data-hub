package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * G2-M8: OFFSET 仕訳マスタの create / update リクエスト DTO。
 */
@Data
public class OffsetJournalRuleRequest {

    @NotNull
    private Integer shopNo;

    @NotBlank
    private String creditAccount;

    private String creditSubAccount;

    private String creditDepartment;

    @NotBlank
    private String creditTaxCategory;

    private String summaryPrefix;
}
