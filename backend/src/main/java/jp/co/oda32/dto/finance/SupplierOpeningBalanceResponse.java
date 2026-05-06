package jp.co.oda32.dto.finance;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * /finance/supplier-opening-balance API のレスポンス。
 *
 * @since 2026-04-24
 */
@Builder
public record SupplierOpeningBalanceResponse(
        Integer shopNo,
        LocalDate openingDate,
        List<Row> rows,
        Summary summary
) {
    @Builder
    public record Row(
            Integer supplierNo,
            String supplierCode,
            String supplierName,
            BigDecimal mfBalance,
            BigDecimal manualAdjustment,
            BigDecimal effectiveBalance,
            Integer sourceJournalNumber,
            String sourceSubAccountName,
            Instant lastMfFetchedAt,
            String adjustmentReason,
            String note,
            boolean unmatched
    ) {}

    @Builder
    public record Summary(
            int totalRowCount,
            int mfSourcedCount,
            int manuallyAdjustedCount,
            int unmatchedCount,
            BigDecimal totalMfBalance,
            BigDecimal totalManualAdjustment,
            BigDecimal totalEffectiveBalance,
            BigDecimal mfTrialBalanceClosing,
            BigDecimal validationDiff,
            String validationLevel
    ) {}
}
