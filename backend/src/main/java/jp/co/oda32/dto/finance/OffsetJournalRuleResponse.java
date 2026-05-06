package jp.co.oda32.dto.finance;

import jp.co.oda32.domain.model.finance.MOffsetJournalRule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * G2-M8: OFFSET 仕訳マスタの一覧 / 詳細レスポンス DTO。
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OffsetJournalRuleResponse {
    private Integer id;
    private Integer shopNo;
    private String creditAccount;
    private String creditSubAccount;
    private String creditDepartment;
    private String creditTaxCategory;
    private String summaryPrefix;

    public static OffsetJournalRuleResponse from(MOffsetJournalRule e) {
        return OffsetJournalRuleResponse.builder()
                .id(e.getId())
                .shopNo(e.getShopNo())
                .creditAccount(e.getCreditAccount())
                .creditSubAccount(e.getCreditSubAccount())
                .creditDepartment(e.getCreditDepartment())
                .creditTaxCategory(e.getCreditTaxCategory())
                .summaryPrefix(e.getSummaryPrefix())
                .build();
    }
}
