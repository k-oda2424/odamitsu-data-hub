package jp.co.oda32.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 買掛帳 API `/accounts-payable/ledger` のレスポンス DTO。
 * <p>
 * 設計書: claudedocs/design-accounts-payable-ledger.md §7
 *
 * @since 2026-04-22 (買掛帳画面)
 */
@Data
@Builder
public class AccountsPayableLedgerResponse {
    private SupplierInfo supplier;
    private LocalDate fromMonth;
    private LocalDate toMonth;
    private List<LedgerRow> rows;
    private LedgerSummary summary;

    @Data
    @Builder
    public static class SupplierInfo {
        private Integer shopNo;
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
    }

    @Data
    @Builder
    public static class LedgerRow {
        private LocalDate transactionMonth;
        private BigDecimal openingBalanceTaxIncluded;
        /** 当月の仕入 change (生値: tax_included_amount_change の税率別合算)。 */
        private BigDecimal changeTaxIncluded;
        /**
         * closing 算出で実際に使われた change (Phase B' 仕様: 手動確定時は verified_amount 優先)。
         * {@code closing = opening + effectiveChangeTaxIncluded − paymentSettled} が成立する。
         */
        private BigDecimal effectiveChangeTaxIncluded;
        private BigDecimal verifiedAmount;
        private BigDecimal paymentSettledTaxIncluded;
        private BigDecimal closingBalanceTaxIncluded;
        private Integer taxRateCount;
        private List<TaxRateInfo> taxRateBreakdown;
        private boolean hasPaymentOnly;
        private boolean hasVerifiedManually;
        private List<Anomaly> anomalies;
        private boolean continuityOk;
    }

    @Data
    @Builder
    public static class TaxRateInfo {
        private BigDecimal taxRate;
        private Boolean verifiedManually;
        private Integer verificationResult;
        private Boolean isPaymentOnly;
        private Boolean mfExportEnabled;
        private LocalDate mfTransferDate;
    }

    @Data
    @Builder
    public static class Anomaly {
        private String code;      // UNVERIFIED, VERIFY_DIFF, NEGATIVE_CLOSING, PAYMENT_OVER, CONTINUITY_BREAK, MONTH_GAP
        private String severity;  // CRITICAL, WARN, INFO
        private String message;
    }

    @Data
    @Builder
    public static class LedgerSummary {
        private BigDecimal totalChangeTaxIncluded;
        private BigDecimal totalVerified;
        private BigDecimal totalPaymentSettled;
        private BigDecimal finalClosing;
        private Integer unverifiedMonthCount;
        private Integer continuityBreakCount;
        private Integer negativeClosingMonthCount;
        private Integer paymentOnlyMonthCount;
        private Integer monthGapCount;
    }
}
