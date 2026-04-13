export interface CashBookPreviewRow {
  excelRowIndex: number
  transactionNo: number | null
  transactionDate: string | null
  debitAccount: string | null
  debitSubAccount: string | null
  debitDepartment: string | null
  debitClient: string | null
  debitTax: string | null
  debitInvoice: string | null
  debitAmount: number | null
  creditAccount: string | null
  creditSubAccount: string | null
  creditDepartment: string | null
  creditClient: string | null
  creditTax: string | null
  creditInvoice: string | null
  creditAmount: number | null
  summary: string | null
  tag: string | null
  memo: string | null
  descriptionC: string | null
  descriptionD: string | null
  errorType: string | null
  errorMessage: string | null
}

export interface CashBookPreviewResponse {
  uploadId: string
  fileName: string
  totalRows: number
  errorCount: number
  rows: CashBookPreviewRow[]
  unmappedClients: string[]
  unknownDescriptions: string[]
}

export interface MfClientMapping {
  id: number
  alias: string
  mfClientName: string
}

export interface MfClientMappingRequest {
  alias: string
  mfClientName: string
}

export interface MfJournalRule {
  id: number
  descriptionC: string
  descriptionDKeyword: string | null
  priority: number
  amountSource: 'INCOME' | 'PAYMENT'
  debitAccount: string
  debitSubAccount: string
  debitDepartment: string
  debitTaxResolver: string
  creditAccount: string
  creditSubAccount: string
  creditSubAccountTemplate: string
  creditDepartment: string
  creditTaxResolver: string
  summaryTemplate: string
  requiresClientMapping: boolean
}

/**
 * サーバー送信用の仕訳ルール Request。
 * 任意項目も常に string として送信し、サーバー側で空文字→NULL 正規化が期待される。
 * undefined を送らないことで UI との整合を取る（Input value は常に string）。
 */
export interface MfJournalRuleRequest {
  descriptionC: string
  descriptionDKeyword: string
  priority: number
  amountSource: 'INCOME' | 'PAYMENT'
  debitAccount: string
  debitSubAccount: string
  debitDepartment: string
  debitTaxResolver: string
  creditAccount: string
  creditSubAccount: string
  creditSubAccountTemplate: string
  creditDepartment: string
  creditTaxResolver: string
  summaryTemplate: string
  requiresClientMapping: boolean
}

export const TAX_RESOLVERS = [
  'OUTSIDE',
  'OUTSIDE_PURCHASE_FULL',
  'OUTSIDE_PURCHASE_SHORT',
  'SALES_10',
  'PURCHASE_10',
  'PURCHASE_10_TRAVEL',
  'SALES_AUTO',
  'PURCHASE_AUTO',
  'PURCHASE_AUTO_WIDE',
] as const
