package jp.co.oda32.constant;

import java.time.LocalDate;

/**
 * 財務系の期首日 / 基準日に関する設定値。
 * <p>
 * supplier 期首残・累積残高・買掛帳・MF 連携の各サービスは「期首日 (= 自社 backfill 起点 = 前月 20 日締め日)」
 * を共通の基準として扱う。本クラスはその初期値 / デフォルト値を集約する。
 * <p>
 * <strong>NOTE:</strong> Cluster D で先行集約された
 * {@link jp.co.oda32.domain.service.finance.MfPeriodConstants#SELF_BACKFILL_START}
 * と意味的に同値 (2025-06-20)。両者は当面並行して維持し、将来は
 * {@code MfPeriodConstants} に一本化する方針。
 * <ul>
 *   <li>{@code MfPeriodConstants}: MF API 連携 / バッチ層の期間境界 (4 種類の期首日を区別管理)</li>
 *   <li>{@code FinancePeriodConfig}: web/UI 層の期首日デフォルト値 (将来 admin が画面から上書き可能化する候補)</li>
 * </ul>
 *
 * @since 2026-05-04 (SAFE-FIX SF-G05)
 */
public final class FinancePeriodConfig {

    /**
     * supplier opening balance のデフォルト基準日 (運用初期は 2025-06-20)。
     *
     * <p><b>現時点では未参照 (予約定数)。</b>将来、admin が画面から期首日を上書きする機能 (DD-BGH-XX) や
     * フロント {@code DEFAULT_OPENING_DATE} (frontend/types/supplier-opening-balance.ts) との同期 API が
     * 必要になった時点で参照を開始する想定。
     *
     * <p>NOTE: Cluster D の {@link jp.co.oda32.domain.service.finance.MfPeriodConstants#SELF_BACKFILL_START}
     * と同値 (2025-06-20)。業務的に同じ「期首日」を表すが、用途が異なる:
     * <ul>
     *   <li>{@link jp.co.oda32.domain.service.finance.MfPeriodConstants#SELF_BACKFILL_START}: バッチ処理 (backfill) の起点</li>
     *   <li>{@link #OPENING_DATE_DEFAULT}: m_supplier_opening_balance の opening_date デフォルト値 (将来用)</li>
     * </ul>
     * 将来一本化する場合は {@code MfPeriodConstants} 側に統合推奨。
     * IDE auto-complete で誤って参照しないよう、現時点では新規 import を避け、
     * 既存の {@code MfPeriodConstants.SELF_BACKFILL_START} を使うこと。
     */
    public static final LocalDate OPENING_DATE_DEFAULT = LocalDate.of(2025, 6, 20);

    private FinancePeriodConfig() {}
}
