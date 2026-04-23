/**
 * 買掛帳 整合性検出機能 (軸 B + 軸 C) のフロント型。
 * 設計書: claudedocs/design-integrity-report.md §7
 */

export interface IntegrityReportSummary {
  mfOnlyCount: number
  selfOnlyCount: number
  amountMismatchCount: number
  unmatchedSupplierCount: number
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
