/**
 * 軸 D: 買掛 supplier 累積残一覧 型定義。
 * 設計書: claudedocs/design-supplier-balances-health.md §3
 */

export type SupplierBalanceStatus =
  | 'MATCH'
  | 'MINOR'
  | 'MAJOR'
  | 'MF_MISSING'
  | 'SELF_MISSING'

export interface SupplierBalanceRow {
  supplierNo: number | null
  supplierCode: string | null
  supplierName: string
  selfBalance: number
  mfBalance: number
  diff: number
  status: SupplierBalanceStatus
  masterRegistered: boolean
  selfOpening: number
  selfChangeCumulative: number
  selfPaymentCumulative: number
  mfCreditCumulative: number
  mfDebitCumulative: number
  mfSubAccountNames: string[]
}

export interface SupplierBalancesSummary {
  totalSuppliers: number
  matchedCount: number
  minorCount: number
  majorCount: number
  mfMissingCount: number
  selfMissingCount: number
  totalSelfBalance: number
  totalMfBalance: number
  totalDiff: number
}

export interface SupplierBalancesResponse {
  shopNo: number
  asOfMonth: string | null
  mfStartDate: string
  fetchedAt: string
  totalJournalCount: number
  rows: SupplierBalanceRow[]
  summary: SupplierBalancesSummary
}

export const STATUS_LABEL: Record<SupplierBalanceStatus, string> = {
  MATCH: '一致',
  MINOR: '金額差 (軽)',
  MAJOR: '金額差 (重)',
  MF_MISSING: 'MF 未計上',
  SELF_MISSING: '自社未計上',
}

export const STATUS_CLASS: Record<SupplierBalanceStatus, string> = {
  MATCH: 'border-green-500 bg-green-50 text-green-700',
  MINOR: 'border-amber-500 bg-amber-50 text-amber-700',
  MAJOR: 'border-red-600 bg-red-100 text-red-800',
  MF_MISSING: 'border-red-600 bg-red-100 text-red-800',
  SELF_MISSING: 'border-red-600 bg-red-100 text-red-800',
}
