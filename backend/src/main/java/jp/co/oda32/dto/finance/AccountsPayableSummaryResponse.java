package jp.co.oda32.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
public class AccountsPayableSummaryResponse {
    private LocalDate transactionMonth;
    private long totalCount;
    private long unverifiedCount;
    private long unmatchedCount;
    private long matchedCount;
    private BigDecimal unmatchedDifferenceSum;
}
