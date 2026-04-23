package jp.co.oda32.dto.finance;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * MF 仕入先別 ledger API `/accounts-payable/ledger/mf` のレスポンス DTO。
 * <p>
 * 期間累積 closing ではなく月次 delta (= credit − debit) を返す (設計書 §4.2 / §7、R8 反映)。
 * <p>
 * 設計書: claudedocs/design-accounts-payable-ledger.md §7
 *
 * @since 2026-04-22 (買掛帳画面)
 */
@Data
@Builder
public class MfSupplierLedgerResponse {
    private Integer shopNo;
    private Integer supplierNo;
    private String supplierName;
    private LocalDate fromMonth;
    private LocalDate toMonth;
    /** mf_account_master から解決された sub_account_name 候補 (空なら MF 側にデータなし)。 */
    private List<String> matchedSubAccountNames;
    /** mf_account_master で解決できなかったときの supplier_name 候補 (UI 警告用)。 */
    private List<String> unmatchedCandidates;
    private List<MfLedgerRow> rows;
    private Instant fetchedAt;
    private Integer totalJournalCount;
    /**
     * MF /journals 取得の実際の開始日 (fiscal year 境界 fallback で採用された値)。
     * 2025-05-20 設定でも MF 会計期首が 2025-06-21 の場合はそれが返る。
     */
    private LocalDate mfStartDate;
    /** MF /journals 取得の終了日 (= toMonth)。 */
    private LocalDate mfEndDate;

    @Data
    @Builder
    public static class MfLedgerRow {
        /** 20日締め月 (自社と合わせる)。 */
        private LocalDate transactionMonth;
        /** 当月 MF の credit (= 仕入計上) 合計。 */
        private BigDecimal mfCreditInMonth;
        /** 当月 MF の debit (= 支払取崩) 合計。 */
        private BigDecimal mfDebitInMonth;
        /** 月次 delta = credit − debit (+ なら買掛増、- なら支払で減)。 */
        private BigDecimal mfPeriodDelta;
        /** MF 累積残 = 期首 (2025-05-20) 〜当月の Σ(credit − debit)。自社 closing との差比較用。 */
        private BigDecimal mfCumulativeBalance;
    }
}
