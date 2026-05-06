package jp.co.oda32.domain.service.finance;

import java.time.LocalDate;

/**
 * MF 連携と買掛金集計に共通する期間境界定数。
 * 4 つの異なる「期首日」を明示的に区別する:
 * <ul>
 *   <li>{@link #MF_FISCAL_YEAR_START}: MF 会計年度開始 (取引 1 件目)。journal #1 の {@code transaction_date}</li>
 *   <li>{@link #SELF_BACKFILL_START}: 自社 backfill 起点 (前月 20 日締め日)</li>
 *   <li>{@link #FIRST_PAYABLE_BUCKET}: MF debit 上書きを開始する初回 bucket
 *       (= {@code transactionMonth} 比較の閾値)</li>
 *   <li>{@link #MF_JOURNALS_FETCH_FROM}: MF /journals fetch 開始日 (fiscal year 跨ぎ fallback 含)</li>
 * </ul>
 * これら 4 つは別々の意味を持つため統合せず、用途別に参照する。
 */
public final class MfPeriodConstants {

    /** MF 会計年度開始 (取引 1 件目)。journal #1 の {@code transaction_date}。 */
    public static final LocalDate MF_FISCAL_YEAR_START = LocalDate.of(2025, 6, 21);

    /**
     * 自社 backfill 起点 (前月 20 日締め日)。
     * <p>{@code m_supplier_opening_balance} との join キー (MF fiscal year 直前日)
     * 兼 backfill バッチの fromMonth 固定値。
     */
    public static final LocalDate SELF_BACKFILL_START = LocalDate.of(2025, 6, 20);

    /**
     * MF debit 上書きを開始する初回 bucket (= {@code transactionMonth} 比較の閾値)。
     * <p>fiscal year 2025-06-21 以降の取引 → {@code toClosingMonthDay20} で 2025-07-20 bucket になる最初の月。
     */
    public static final LocalDate FIRST_PAYABLE_BUCKET = LocalDate.of(2025, 7, 20);

    /**
     * MF /journals fetch 開始日 (fiscal year 跨ぎ fallback 含)。
     * <p>累積残計算の起点として使用。fiscal year 開始 (2025-06-21) より前の日付を指定し、
     * 期首残高仕訳 (journal #1) を取り込む。
     */
    public static final LocalDate MF_JOURNALS_FETCH_FROM = LocalDate.of(2025, 5, 20);

    private MfPeriodConstants() {}
}
