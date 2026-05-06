/**
 * supplier 毎の前期繰越 (期首残) API 型定義。
 *
 * @since 2026-04-24
 */

export interface SupplierOpeningBalanceRow {
  supplierNo: number
  supplierCode: string | null
  supplierName: string | null
  mfBalance: number | null
  manualAdjustment: number
  effectiveBalance: number
  sourceJournalNumber: number | null
  sourceSubAccountName: string | null
  lastMfFetchedAt: string | null
  adjustmentReason: string | null
  note: string | null
  unmatched: boolean
}

export type ValidationLevel = 'MATCH' | 'MINOR' | 'MAJOR' | 'UNKNOWN'

export interface SupplierOpeningBalanceSummary {
  totalRowCount: number
  mfSourcedCount: number
  manuallyAdjustedCount: number
  unmatchedCount: number
  totalMfBalance: number
  totalManualAdjustment: number
  totalEffectiveBalance: number
  mfTrialBalanceClosing: number | null
  validationDiff: number | null
  validationLevel: ValidationLevel
}

export interface SupplierOpeningBalanceResponse {
  shopNo: number
  openingDate: string
  rows: SupplierOpeningBalanceRow[]
  summary: SupplierOpeningBalanceSummary
}

export interface MfOpeningBalanceFetchResponse {
  shopNo: number
  openingDate: string
  journalTransactionDate: string
  journalNumber: number | null
  journalCreditSum: number
  branchCount: number
  matchedCount: number
  upsertedCount: number
  preservedManualCount: number
  unmatchedBranches: { subAccountName: string; amount: number }[]
  mfTrialBalanceClosing: number | null
  validationDiff: number | null
  validationLevel: ValidationLevel
  fetchedAt: string
}

export interface SupplierOpeningBalanceUpdateRequest {
  shopNo: number
  openingDate: string
  supplierNo: number
  manualAdjustment: number
  adjustmentReason?: string | null
  note?: string | null
}

export const VALIDATION_BADGE_CLASS: Record<ValidationLevel, string> = {
  MATCH: 'bg-emerald-50 text-emerald-700 border-emerald-300',
  MINOR: 'bg-amber-50 text-amber-700 border-amber-300',
  MAJOR: 'bg-red-50 text-red-700 border-red-300',
  UNKNOWN: 'bg-slate-50 text-slate-700 border-slate-300',
}

export const VALIDATION_LABEL: Record<ValidationLevel, string> = {
  MATCH: '✓ 一致 (±¥100)',
  MINOR: '△ 微差 (±¥1,000)',
  MAJOR: '✗ 要調査',
  UNKNOWN: '? 未検証',
}

/** デフォルトの opening_date: MF fiscal year 直前日 (2025-06-20)。将来的には config 化。 */
export const DEFAULT_OPENING_DATE = '2025-06-20'
