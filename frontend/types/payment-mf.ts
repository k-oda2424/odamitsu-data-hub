export type RuleKind = 'PAYABLE' | 'EXPENSE' | 'DIRECT_PURCHASE' | 'SUMMARY'
export type MatchStatus = 'MATCHED' | 'DIFF' | 'UNMATCHED' | 'NA'

export interface PaymentMfPreviewRow {
  excelRowIndex: number
  paymentSupplierCode: string | null
  sourceName: string | null
  amount: number | null

  ruleKind: RuleKind | null
  debitAccount: string | null
  debitSubAccount: string | null
  debitDepartment: string | null
  debitTax: string | null
  debitAmount: number | null
  creditAccount: string | null
  creditSubAccount: string | null
  creditDepartment: string | null
  creditTax: string | null
  creditAmount: number | null
  summary: string | null
  tag: string | null

  matchStatus: MatchStatus | null
  payableAmount: number | null
  payableDiff: number | null
  supplierNo: number | null

  errorType: string | null
  errorMessage: string | null
}

export interface PaymentMfPreviewResponse {
  uploadId: string
  fileName: string
  transferDate: string | null        // ISO LocalDate
  transactionMonth: string | null

  totalRows: number
  totalAmount: number
  matchedCount: number
  diffCount: number
  unmatchedCount: number
  errorCount: number

  rows: PaymentMfPreviewRow[]
  unregisteredSources: string[]
}

export interface PaymentMfRule {
  id: number
  sourceName: string
  paymentSupplierCode: string | null
  ruleKind: RuleKind
  debitAccount: string
  debitSubAccount: string | null
  debitDepartment: string | null
  debitTaxCategory: string
  creditAccount: string
  creditSubAccount: string | null
  creditDepartment: string | null
  creditTaxCategory: string
  summaryTemplate: string
  tag: string | null
  priority: number
}

export interface PaymentMfRuleRequest {
  sourceName: string
  paymentSupplierCode?: string | null
  ruleKind: RuleKind
  debitAccount: string
  debitSubAccount?: string | null
  debitDepartment?: string | null
  debitTaxCategory: string
  creditAccount?: string
  creditSubAccount?: string | null
  creditDepartment?: string | null
  creditTaxCategory?: string
  summaryTemplate: string
  tag?: string | null
  priority?: number
}

export interface PaymentMfHistory {
  id: number
  shopNo: number
  transferDate: string
  sourceFilename: string
  csvFilename: string
  rowCount: number
  totalAmount: number
  matchedCount: number
  diffCount: number
  unmatchedCount: number
  addDateTime: string
}

export interface PaymentMfVerifyResult {
  transferDate: string
  transactionMonth: string
  matchedCount: number
  diffCount: number
  notFoundCount: number
  skippedCount: number
  unmatchedSuppliers: string[]
}

export const RULE_KINDS: RuleKind[] = ['PAYABLE', 'EXPENSE', 'DIRECT_PURCHASE']

export const TAX_CATEGORIES = [
  '対象外',
  '課税仕入 10%',
  '課税仕入-返還等 10%',
  '非課税売上',
] as const
