package jp.co.oda32.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 買掛帳 整合性検出機能 (軸 B + 軸 C) のレスポンス。
 * <p>
 * 期間内の全 supplier を一括診断し、mfOnly / selfOnly / amountMismatch / unmatchedSuppliers
 * の 4 カテゴリに分類する。
 * <p>
 * 設計書: claudedocs/design-integrity-report.md §7
 *
 * @since 2026-04-22
 */
@Data
@Builder
public class IntegrityReportResponse {
    private Integer shopNo;
    private LocalDate fromMonth;
    private LocalDate toMonth;
    private Instant fetchedAt;
    /** MF から取得した全仕訳件数 (全 supplier 合計、supplier 別ではない)。 */
    private Integer totalJournalCount;
    /** 期間内に self or MF どちらかに出現した supplier 数 (unmatched supplier も含む union、R10 反映)。 */
    private Integer supplierCount;

    private List<MfOnlyEntry> mfOnly;
    private List<SelfOnlyEntry> selfOnly;
    private List<AmountMismatchEntry> amountMismatch;
    private List<UnmatchedSupplierEntry> unmatchedSuppliers;
    private Summary summary;

    /** MF にあって自社に無い (supplier × 月 単位)。SELF_MISSING。 */
    @Data
    @Builder
    public static class MfOnlyEntry {
        private LocalDate transactionMonth;
        private String subAccountName;
        private BigDecimal creditAmount;
        private BigDecimal debitAmount;
        private BigDecimal periodDelta;          // credit - debit
        private Integer branchCount;
        /** mf_account_master.search_key → supplier_code 逆引きで解決した supplierNo (null なら未登録)。 */
        private Integer guessedSupplierNo;
        private String guessedSupplierCode;
        private String reason;
        /** MF /journals の number (取引番号) リスト。自社取込漏れ疑いのときの確認用。昇順・重複排除。 */
        private List<Integer> journalNumbers;
        /** toMonth 時点での supplier 累積残が MATCH かどうか。true なら期末で自社と MF が一致 = ノイズとして除外可。 */
        private Boolean reconciledAtPeriodEnd;
        /** toMonth 時点での supplier 単位累積残 diff (= selfBalance - mfBalance)。null なら軸 D 計算不可。 */
        private BigDecimal supplierCumulativeDiff;
        /** 差分確認状態: null | IGNORED | MF_APPLIED (案 X+Y)。 */
        private String reviewStatus;
        private Instant reviewedAt;
        private String reviewedByName;
        private String reviewNote;
        /** 現在値と snapshot が ±¥100 超で乖離していれば true (再確認必要)。 */
        private Boolean snapshotStale;
    }

    /** 自社にあって MF に無い (supplier × 月 単位)。MF_MISSING。 */
    @Data
    @Builder
    public static class SelfOnlyEntry {
        private LocalDate transactionMonth;
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
        private BigDecimal selfDelta;            // change - payment_settled
        private BigDecimal changeTaxIncluded;
        private BigDecimal paymentSettledTaxIncluded;
        private Integer taxRateRowCount;
        private String reason;
        /** toMonth 時点での supplier 累積残が MATCH かどうか。true なら期末で解消済 = ノイズ。 */
        private Boolean reconciledAtPeriodEnd;
        /** toMonth 時点での supplier 単位累積残 diff (= selfBalance - mfBalance)。 */
        private BigDecimal supplierCumulativeDiff;
        /** 差分確認状態: null | IGNORED | MF_APPLIED (案 X+Y)。 */
        private String reviewStatus;
        private Instant reviewedAt;
        private String reviewedByName;
        private String reviewNote;
        /** 現在値と snapshot が ±¥100 超で乖離していれば true (再確認必要)。 */
        private Boolean snapshotStale;
    }

    /** ペアあり、金額差 (supplier × 月 単位)。AMOUNT_DIFF。 */
    @Data
    @Builder
    public static class AmountMismatchEntry {
        private LocalDate transactionMonth;
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
        private BigDecimal selfDelta;
        private BigDecimal mfDelta;
        private BigDecimal diff;                  // = self - mf (符号あり)
        /** MINOR (100 < |diff| ≤ 1000) / MAJOR (|diff| > 1000)。 */
        private String severity;
        /** toMonth 時点での supplier 累積残が MATCH かどうか。true なら期末で解消済 = ノイズ。 */
        private Boolean reconciledAtPeriodEnd;
        /** toMonth 時点での supplier 単位累積残 diff (= selfBalance - mfBalance)。 */
        private BigDecimal supplierCumulativeDiff;
        /** 差分確認状態: null | IGNORED | MF_APPLIED (案 X+Y)。 */
        private String reviewStatus;
        private Instant reviewedAt;
        private String reviewedByName;
        private String reviewNote;
        /** 現在値と snapshot が ±¥100 超で乖離していれば true (再確認必要)。 */
        private Boolean snapshotStale;
    }

    /** supplier 単位: mf_account_master に登録無し (MF_UNMATCHED、R11 反映で supplier 単位に格上げ)。 */
    @Data
    @Builder
    public static class UnmatchedSupplierEntry {
        private Integer supplierNo;
        private String supplierCode;
        private String supplierName;
        private String reason;
    }

    @Data
    @Builder
    public static class Summary {
        private Integer mfOnlyCount;
        private Integer selfOnlyCount;
        private Integer amountMismatchCount;
        private Integer unmatchedSupplierCount;
        /** 期末解消済 (reconciledAtPeriodEnd=true) のエントリ数 (3 カテゴリ合計)。ノイズ抑制用の参考値。 */
        private Integer reconciledAtPeriodEndCount;
        /** R10 反映: 絶対値ベースの集計。 */
        private BigDecimal totalMfOnlyAmount;
        private BigDecimal totalSelfOnlyAmount;
        private BigDecimal totalMismatchAmount;
    }
}
