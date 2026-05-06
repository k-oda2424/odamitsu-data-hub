package jp.co.oda32.dto.finance;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 手動補正額の更新リクエスト。
 *
 * @since 2026-04-24
 */
public record SupplierOpeningBalanceUpdateRequest(
        @NotNull Integer shopNo,
        @NotNull LocalDate openingDate,
        @NotNull Integer supplierNo,
        @NotNull BigDecimal manualAdjustment,
        @Size(max = 500) String adjustmentReason,
        @Size(max = 500) String note
) {}
