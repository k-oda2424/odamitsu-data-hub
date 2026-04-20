package jp.co.oda32.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 売掛金一覧画面のサマリカウント（買掛側 AccountsPayableSummaryResponse と対称）
 */
@Data
@Builder
public class AccountsReceivableSummaryResponse {
    private LocalDate fromDate;
    private LocalDate toDate;
    private long totalCount;
    private long unverifiedCount;
    private long unmatchedCount;
    private long matchedCount;
    private BigDecimal unmatchedDifferenceSum;
}
