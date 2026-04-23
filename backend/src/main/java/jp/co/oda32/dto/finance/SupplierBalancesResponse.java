package jp.co.oda32.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 買掛 supplier 累積残一覧 レスポンス (軸 D)。
 * <p>
 * asOfMonth 時点での全 supplier の自社 / MF 累積残を突合し、MATCH / MINOR / MAJOR /
 * MF_MISSING / SELF_MISSING で分類する。
 * <p>
 * 設計書: claudedocs/design-supplier-balances-health.md §3
 *
 * @since 2026-04-23
 */
@Data
@Builder
public class SupplierBalancesResponse {
    private Integer shopNo;
    private LocalDate asOfMonth;
    private LocalDate mfStartDate;
    private Instant fetchedAt;
    private Integer totalJournalCount;
    private List<SupplierBalanceRow> rows;
    private Summary summary;

    @Data
    @Builder
    public static class SupplierBalanceRow {
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
        private BigDecimal selfBalance;
        private BigDecimal mfBalance;
        private BigDecimal diff;
        /** MATCH / MINOR / MAJOR / MF_MISSING / SELF_MISSING */
        private String status;
        private Boolean masterRegistered;
        private BigDecimal selfOpening;
        private BigDecimal selfChangeCumulative;
        private BigDecimal selfPaymentCumulative;
        private BigDecimal mfCreditCumulative;
        private BigDecimal mfDebitCumulative;
        private List<String> mfSubAccountNames;
    }

    @Data
    @Builder
    public static class Summary {
        private Integer totalSuppliers;
        private Integer matchedCount;
        private Integer minorCount;
        private Integer majorCount;
        private Integer mfMissingCount;
        private Integer selfMissingCount;
        private BigDecimal totalSelfBalance;
        private BigDecimal totalMfBalance;
        private BigDecimal totalDiff;
    }
}
