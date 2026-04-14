export interface AccountsPayable {
  shopNo: number
  supplierNo: number
  supplierCode: string | null
  supplierName: string | null
  transactionMonth: string // yyyy-MM-dd
  taxRate: number
  taxIncludedAmount: number | null
  taxExcludedAmount: number | null
  taxIncludedAmountChange: number | null
  taxExcludedAmountChange: number | null
  paymentDifference: number | null
  verificationResult: 0 | 1 | null
  mfExportEnabled: boolean | null
  verifiedManually: boolean
  verificationNote: string | null
  [key: string]: unknown
}

export interface AccountsPayableSummary {
  transactionMonth: string
  totalCount: number
  unverifiedCount: number
  unmatchedCount: number
  matchedCount: number
  unmatchedDifferenceSum: number
}

export type VerificationFilter = 'all' | 'unverified' | 'unmatched' | 'matched'

export const VERIFICATION_FILTER_LABELS: Record<VerificationFilter, string> = {
  all: 'すべて',
  unverified: '未検証',
  unmatched: '不一致',
  matched: '一致',
}

export interface VerifyRequest {
  verifiedAmount: number
  note?: string | null
}

export function defaultTransactionMonth(today = new Date()): string {
  const d = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  if (d.getDate() < 20) {
    d.setMonth(d.getMonth() - 1)
  }
  d.setDate(20)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  return `${y}-${m}-20`
}

export function toMonthInput(dateStr: string): string {
  return dateStr.slice(0, 7)
}

export function fromMonthInput(monthStr: string): string {
  return `${monthStr}-20`
}
