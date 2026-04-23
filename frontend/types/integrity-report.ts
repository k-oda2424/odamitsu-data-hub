/**
 * 買掛帳 整合性検出機能 (軸 B + 軸 C) のフロント型。
 * 設計書: claudedocs/design-integrity-report.md §7
 */

export interface IntegrityReportSummary {
  mfOnlyCount: number
  selfOnlyCount: number
  amountMismatchCount: number
  unmatchedSupplierCount: number
  /** 期末解消済 (reconciledAtPeriodEnd=true) エントリの総数。ノイズ抑制用の参考値。 */
  reconciledAtPeriodEndCount?: number
  totalMfOnlyAmount: number
  totalSelfOnlyAmount: number
  totalMismatchAmount: number
}

export interface MfOnlyEntry {
  transactionMonth: string // yyyy-MM-dd
  subAccountName: string
  creditAmount: number
  debitAmount: number
  periodDelta: number
  branchCount: number
  guessedSupplierNo: number | null
  guessedSupplierCode: string | null
  reason: string
  /** MF /journals の number (取引番号) 昇順・重複排除。自社取込漏れ特定用。 */
  journalNumbers: number[]
  /** toMonth 時点での supplier 累積残が MATCH なら true = 期末解消済ノイズ。 */
  reconciledAtPeriodEnd?: boolean
  /** toMonth 時点での supplier 単位累積残 diff (= selfBalance - mfBalance)。null なら計算不能。 */
  supplierCumulativeDiff?: number | null
  /** 差分確認 (案 X+Y)。 */
  reviewStatus?: 'IGNORED' | 'MF_APPLIED' | null
  reviewedAt?: string | null
  reviewedByName?: string | null
  reviewNote?: string | null
  /** true なら現在値が snapshot と乖離、再確認要 (確認済みでも再表示する)。 */
  snapshotStale?: boolean
}

export interface SelfOnlyEntry {
  transactionMonth: string
  supplierNo: number
  supplierCode: string | null
  supplierName: string | null
  selfDelta: number
  changeTaxIncluded: number
  paymentSettledTaxIncluded: number
  taxRateRowCount: number
  reason: string
  reconciledAtPeriodEnd?: boolean
  supplierCumulativeDiff?: number | null
  reviewStatus?: 'IGNORED' | 'MF_APPLIED' | null
  reviewedAt?: string | null
  reviewedByName?: string | null
  reviewNote?: string | null
  snapshotStale?: boolean
}

export interface AmountMismatchEntry {
  transactionMonth: string
  supplierNo: number
  supplierCode: string | null
  supplierName: string | null
  selfDelta: number
  mfDelta: number
  diff: number
  severity: 'MINOR' | 'MAJOR'
  reconciledAtPeriodEnd?: boolean
  supplierCumulativeDiff?: number | null
  reviewStatus?: 'IGNORED' | 'MF_APPLIED' | null
  reviewedAt?: string | null
  reviewedByName?: string | null
  reviewNote?: string | null
  snapshotStale?: boolean
}

export interface UnmatchedSupplierEntry {
  supplierNo: number
  supplierCode: string | null
  supplierName: string | null
  reason: string
}

export interface IntegrityReportResponse {
  shopNo: number
  fromMonth: string
  toMonth: string
  fetchedAt: string
  totalJournalCount: number
  supplierCount: number
  mfOnly: MfOnlyEntry[]
  selfOnly: SelfOnlyEntry[]
  amountMismatch: AmountMismatchEntry[]
  unmatchedSuppliers: UnmatchedSupplierEntry[]
  summary: IntegrityReportSummary
}

// UI ラベル
export const MISMATCH_SEVERITY_LABEL: Record<'MINOR' | 'MAJOR', string> = {
  MINOR: 'MFA',
  MAJOR: 'MFA!',
}

export const MISMATCH_SEVERITY_CLASS: Record<'MINOR' | 'MAJOR', string> = {
  MINOR: 'border-amber-500 bg-amber-50 text-amber-700',
  MAJOR: 'border-red-600 bg-red-100 text-red-800',
}

/**
 * self_delta と mf_delta から MF 比較状態を算出 (買掛帳画面 row 用)。
 * 設計書 §3.2 / §8.2 (R7 反映で MAJOR は red) 準拠。
 */
export type MfMatchCode = 'MATCH' | 'MFA_MINOR' | 'MFA_MAJOR' | 'MFM_SELF' | 'MFM_MF' | null

export function computeMfMatchStatus(
  selfDelta: number,
  mfDelta: number | null,
  selfHasActivity: boolean,
  mfHasActivity: boolean,
): { code: MfMatchCode; label: string; className: string } {
  if (!selfHasActivity && !mfHasActivity) return { code: null, label: '', className: '' }
  if (mfDelta === null) return { code: null, label: '', className: '' }
  if (selfHasActivity && !mfHasActivity) {
    return {
      code: 'MFM_SELF',
      label: 'MFM',
      className: 'border-red-600 bg-red-100 text-red-800',
    }
  }
  if (!selfHasActivity && mfHasActivity) {
    return {
      code: 'MFM_MF',
      label: 'MFM',
      className: 'border-red-600 bg-red-100 text-red-800',
    }
  }
  const diff = Math.abs(selfDelta - mfDelta)
  if (diff <= 100) return { code: 'MATCH', label: '', className: '' }
  if (diff <= 1000) {
    return {
      code: 'MFA_MINOR',
      label: 'MFA',
      className: 'border-amber-500 bg-amber-50 text-amber-700',
    }
  }
  return {
    code: 'MFA_MAJOR',
    label: 'MFA!',
    className: 'border-red-600 bg-red-100 text-red-800',
  }
}
