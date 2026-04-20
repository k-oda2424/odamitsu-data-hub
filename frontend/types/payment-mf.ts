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
  /** PAYABLE ルールがマッチしたが payment_supplier_code 未設定の送り先（CSV除外予備軍）。 */
  rulesMissingSupplierCode: string[]
  /** 請求額-値引-早払 と 振込金額合計 の整合性チェック結果。 */
  amountReconciliation: AmountReconciliation | null
}

export interface AmountReconciliation {
  // チェック1: Excel 合計行の列間整合 (C - F - H = E)
  summaryInvoiceTotal: number   // 合計行 C列
  summaryFee: number            // 合計行 F列
  summaryEarly: number          // 合計行 H列
  summaryTransferAmount: number // 合計行 E列
  expectedTransferAmount: number // C - F - H
  excelDifference: number       // E - (C - F - H)
  excelMatched: boolean

  // チェック2: 明細行 読取り整合 (sum明細 = C合計行)
  preTotalInvoiceSum: number    // 合計行前の明細 請求額合計
  readDifference: number        // sum明細 - C合計行
  readMatched: boolean

  directPurchaseTotal: number   // 合計行後セクション（参考）
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

export type BackfillStatus = 'MATCHED' | 'AMBIGUOUS' | 'NOT_FOUND'

export interface BackfillItem {
  ruleId: number
  sourceName: string
  status: BackfillStatus
  candidateCode?: string | null
  candidateName?: string | null
  alternatives?: string[] | null
}

export interface BackfillResult {
  dryRun: boolean
  matchedCount: number
  ambiguousCount: number
  notFoundCount: number
  skippedCount: number
  items: BackfillItem[]
}

/** 買掛仕入MF 補助行（EXPENSE/SUMMARY/DIRECT_PURCHASE）1 件。 */
export interface PaymentMfAuxRow {
  auxRowId: number
  transactionMonth: string          // yyyy-MM-dd
  transferDate: string              // yyyy-MM-dd
  ruleKind: 'EXPENSE' | 'SUMMARY' | 'DIRECT_PURCHASE'
  sequenceNo: number
  sourceName: string
  paymentSupplierCode: string | null
  amount: number
  debitAccount: string
  debitSubAccount: string | null
  debitDepartment: string | null
  debitTax: string
  creditAccount: string
  creditSubAccount: string | null
  creditDepartment: string | null
  creditTax: string
  summary: string | null
  tag: string | null
  sourceFilename: string | null
}

export type AuxRuleKind = PaymentMfAuxRow['ruleKind']

/** 検証済みCSV出力ダイアログのプレビュー用レスポンス。 */
export interface VerifiedExportPreview {
  transactionMonth: string
  payableCount: number
  payableTotalAmount: number
  auxBreakdown: {
    transferDate: string
    ruleKind: AuxRuleKind
    count: number
    totalAmount: number
  }[]
  warnings: string[]
  skippedSuppliers: string[]
}

export const RULE_KINDS: RuleKind[] = ['PAYABLE', 'EXPENSE', 'DIRECT_PURCHASE']

export const TAX_CATEGORIES = [
  '対象外',
  '課税仕入 10%',
  '課税仕入-返還等 10%',
  '非課税売上',
] as const
