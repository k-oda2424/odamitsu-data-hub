/**
 * G2-M8: OFFSET 仕訳マスタ (PaymentMfImport の OFFSET 副行貸方科目) の型定義。
 */
export interface OffsetJournalRule {
  id: number
  shopNo: number
  creditAccount: string
  creditSubAccount: string | null
  creditDepartment: string | null
  creditTaxCategory: string
  summaryPrefix: string
}

export interface OffsetJournalRuleRequest {
  shopNo: number
  creditAccount: string
  creditSubAccount?: string | null
  creditDepartment?: string | null
  creditTaxCategory: string
  summaryPrefix: string
}
