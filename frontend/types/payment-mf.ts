/**
 * 仕訳ルール種別。
 * - PAYABLE: 買掛金 (主行)。借方=買掛金、貸方=普通預金 (= 振込金額)。
 * - EXPENSE: 経費 (荷造運賃・消耗品費・車両費等)。
 * - DIRECT_PURCHASE: 合計行以降の仕入高 (20日払いセクション)。
 *   合計行以降の PAYABLE ルールは即払い (買掛金経由しない) のため自動で DIRECT_PURCHASE に降格される。
 * - SUMMARY: 旧 合計仕訳 (P1-03 案 D で撤去。履歴 aux_row のみ)。
 * - PAYABLE_FEE / PAYABLE_DISCOUNT / PAYABLE_EARLY / PAYABLE_OFFSET:
 *   P1-03 案 D で追加。5日払い PAYABLE の supplier 別 振込手数料値引/値引/早払/相殺 副行。
 *   借方=買掛金、貸方=該当勘定。PaymentMfPreviewRow / CSV 出力でのみ使用 (aux_row には保存しない)。
 * - DIRECT_PURCHASE_FEE / DIRECT_PURCHASE_DISCOUNT / DIRECT_PURCHASE_EARLY / DIRECT_PURCHASE_OFFSET:
 *   P1-07 案 D で追加。20日払い DIRECT_PURCHASE (自動降格 + 元 DIRECT_PURCHASE) の supplier 別副行。
 *   借方=仕入高、貸方=該当勘定 (買掛金経由しない即払いのため)。
 *   PaymentMfPreviewRow / CSV 出力でのみ使用 (aux_row には保存しない)。
 */
export type RuleKind =
  | 'PAYABLE'
  | 'EXPENSE'
  | 'DIRECT_PURCHASE'
  | 'SUMMARY'
  | 'PAYABLE_FEE'
  | 'PAYABLE_DISCOUNT'
  | 'PAYABLE_EARLY'
  | 'PAYABLE_OFFSET'
  | 'DIRECT_PURCHASE_FEE'
  | 'DIRECT_PURCHASE_DISCOUNT'
  | 'DIRECT_PURCHASE_EARLY'
  | 'DIRECT_PURCHASE_OFFSET'

export type MatchStatus = 'MATCHED' | 'DIFF' | 'UNMATCHED' | 'NA'

/** ruleKind の日本語ラベル (preview / 一覧表示用)。 */
export const RULE_KIND_LABEL: Record<RuleKind, string> = {
  PAYABLE: '買掛金',
  EXPENSE: '経費',
  DIRECT_PURCHASE: '直接仕入高',
  SUMMARY: '合計仕訳(旧)',
  PAYABLE_FEE: '振込手数料値引(仕入先別)',
  PAYABLE_DISCOUNT: '値引(仕入先別)',
  PAYABLE_EARLY: '早払収益(仕入先別)',
  PAYABLE_OFFSET: '相殺(仕入先別)',
  DIRECT_PURCHASE_FEE: '振込手数料値引(仕入先別/直接仕入高)',
  DIRECT_PURCHASE_DISCOUNT: '値引(仕入先別/直接仕入高)',
  DIRECT_PURCHASE_EARLY: '早払収益(仕入先別/直接仕入高)',
  DIRECT_PURCHASE_OFFSET: '相殺(仕入先別/直接仕入高)',
}

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

/**
 * P1-08 L1: 同一 SHA-256 ハッシュの過去取込検知時に preview に含まれる警告。
 * null = 重複なし。
 */
export interface DuplicateWarning {
  previousUploadedAt: string         // ISO 8601 OffsetDateTime
  previousFilename: string | null
  previousUploadedByUserNo: number | null
}

/**
 * P1-08 L2: 同 (shop, transferDate) で applyVerification 実行済の場合の警告。
 * null = 未確定 (初回確定)。
 */
export interface AppliedWarning {
  appliedAt: string                  // ISO 8601 OffsetDateTime
  appliedByUserNo: number | null
  transactionMonth: string           // ISO LocalDate
  transferDate: string               // ISO LocalDate
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
  /** P1-08 L1: 同一 hash の過去取込警告。null=重複なし。 */
  duplicateWarning: DuplicateWarning | null
  /** P1-08 L2: 同 (shop, transferDate) 確定済警告。null=未確定。 */
  appliedWarning: AppliedWarning | null
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

  // P1-03 案 D チェック3: per-supplier 振込金額合計 == E(合計行 振込金額)
  perSupplierTransferSum: number
  perSupplierTransferDiff: number
  perSupplierTransferMatched: boolean
  perSupplierFeeSum: number
  perSupplierDiscountSum: number
  perSupplierEarlySum: number
  perSupplierOffsetSum: number
  /** per-supplier 1 円整合性違反行のメッセージ一覧。空なら全行 OK。 */
  perSupplierMismatches: string[]
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

/**
 * G2-M2 (2026-05-06): {@code POST /finance/payment-mf/verify/{uploadId}} のリクエストボディ。
 *
 * - `force=false` (既定): per-supplier 1 円不一致が 1 件でもあれば 422 + `PER_SUPPLIER_MISMATCH` で拒否。
 * - `force=true`: 違反を許容して反映。サーバー側 audit log に違反詳細が記録される。
 */
export interface PaymentMfApplyRequest {
  force: boolean
}

export interface PaymentMfVerifyResult {
  transferDate: string
  transactionMonth: string
  matchedCount: number
  diffCount: number
  notFoundCount: number
  skippedCount: number
  /** P1-08 Q3-(ii): 単一 verify 由来 verified_manually=true で保護された supplier 数。 */
  skippedManuallyVerifiedCount: number
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
