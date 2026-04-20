package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 売掛金集計バッチ起動リクエスト。
 */
@Data
public class AccountsReceivableAggregateRequest {
    /** yyyyMMdd 形式 */
    @NotBlank
    @Pattern(regexp = "\\d{8}")
    private String targetDate;

    /** "all" | "15" | "20" | "month_end"（null/空は "all"） */
    private String cutoffType;
}
