package jp.co.oda32.dto.finance;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * MF journal #1 取得結果。
 * upsert 後の結果内訳 + 未解決 sub_account_name を報告する。
 *
 * @since 2026-04-24
 */
@Builder
public record MfOpeningBalanceFetchResponse(
        Integer shopNo,
        LocalDate openingDate,
        LocalDate journalTransactionDate,
        Integer journalNumber,
        BigDecimal journalCreditSum,
        int branchCount,
        int matchedCount,
        int upsertedCount,
        int preservedManualCount,
        List<UnmatchedBranch> unmatchedBranches,
        BigDecimal mfTrialBalanceClosing,
        BigDecimal validationDiff,
        String validationLevel,
        Instant fetchedAt
) {
    @Builder
    public record UnmatchedBranch(
            String subAccountName,
            BigDecimal amount
    ) {}
}
