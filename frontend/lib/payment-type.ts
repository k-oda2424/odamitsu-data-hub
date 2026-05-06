/**
 * 支払・締め日の cutoff 値をラベル化するユーティリティ。
 *
 * 仕様 (`m_partner.cutoff_date` / `m_payment_supplier.cutoff_date`):
 * - `null` → `'-'`（未設定）
 * - `0` → `'月末'`
 * - `-1` → `'都度現金'`
 * - その他正値 → `'{n}日'`（例: `15` → `'15日'`）
 *
 * SF-E18: 売掛 `accounts-receivable.tsx` の `cutoffDateLabel` を集約。
 * 買掛 `accounts-payable.tsx` 側は現時点で同等関数を持たないため、
 * 締め日列を表示する追加導線が出来た際にもこのユーティリティを参照する。
 */
export function paymentTypeLabel(cutoff: number | null | undefined): string {
  if (cutoff == null) return '-'
  if (cutoff === 0) return '月末'
  if (cutoff === -1) return '都度現金'
  return `${cutoff}日`
}
