export interface EstimateDetailResponse {
  estimateDetailNo: number
  goodsNo: number | null
  goodsCode: string | null
  goodsName: string | null
  specification: string | null
  goodsPrice: number | null
  containNum: number | null
  changeContainNum: number | null
  estimateCaseNum: number | null
  estimateNum: number | null
  purchasePrice: number | null
  profitRate: number | null
  detailNote: string | null
  displayOrder: number
  pricePlanInfo: string | null
}

export interface EstimateResponse {
  estimateNo: number
  shopNo: number
  partnerNo: number
  partnerCode: string | null
  partnerName: string | null
  destinationNo: number | null
  destinationName: string | null
  destinationCode: string | null
  estimateDate: string | null
  priceChangeDate: string | null
  estimateStatus: string | null
  note: string | null
  requirement: string | null
  recipientName: string | null
  proposalMessage: string | null
  isIncludeTaxDisplay?: boolean
  details?: EstimateDetailResponse[]
}

export const ESTIMATE_STATUS_OPTIONS = [
  { value: '00', label: '作成' },
  { value: '10', label: '提出済' },
  { value: '20', label: '修正' },
  { value: '30', label: '修正後提出済' },
  { value: '40', label: '他同グループ提出済' },
  { value: '50', label: '削除' },
  { value: '60', label: '都度見積のため不要' },
  { value: '70', label: '価格反映済' },
  { value: '90', label: '入札関係のため不要' },
  { value: '99', label: '取引なし' },
] as const

export function getEstimateStatusLabel(code: string | null): string {
  if (!code) return ''
  return ESTIMATE_STATUS_OPTIONS.find((s) => s.value === code)?.label ?? code
}

/**
 * 印刷/PDF出力時のステータス自動遷移ルール。
 * 旧システム EstimateUtil.getNotifiedStatus() と同等。
 *
 * - 00 (作成)    → 10 (提出済)
 * - 20 (修正)    → 30 (修正後提出済)
 * - 10, 30       → 変化なし（そのまま返す）
 * - 40以上       → null（自動更新対象外）
 */
export function getNotifiedStatus(currentStatus: string | null): string | null {
  switch (currentStatus) {
    case '00':
      return '10'
    case '20':
      return '30'
    case '10':
    case '30':
      return currentStatus // 変化なし
    default:
      return null // 40以上や不明なステータスは自動更新しない
  }
}

export interface EstimateDetailCreateRequest {
  goodsNo: number | null
  goodsCode: string
  goodsName: string
  specification: string
  goodsPrice: number
  purchasePrice: number | null
  containNum: number | null
  changeContainNum: number | null
  profitRate: number | null
  detailNote: string
  displayOrder: number
  supplierNo: number | null
}

export interface EstimateCreateRequest {
  shopNo: number
  partnerNo: number
  destinationNo: number | null
  estimateDate: string
  priceChangeDate: string
  note: string
  requirement: string | null
  recipientName: string | null
  proposalMessage: string | null
  details: EstimateDetailCreateRequest[]
}

export interface EstimateGoodsSearchResponse {
  goodsNo: number | null
  goodsCode: string
  goodsName: string
  specification: string | null
  purchasePrice: number | null
  containNum: number | null
  changeContainNum: number | null
  nowGoodsPrice: number | null
  /** m_partner_goods.goods_price 由来の現行販売単価（得意先/届先一致のみ） */
  currentSalesPrice: number | null
  pricePlanInfo: string | null
  janCode: string | null
  source: 'GOODS' | 'PRICE_PLAN' | 'QUOTE_IMPORT'
  purchasePriceChangePlanNo: number | null
  supplierNo: number | null
}

/** compare-goods API response (for registered goods with goodsNo) */
export interface CompareGoodsResponse {
  goodsNo: number
  goodsCode: string
  goodsName: string
  specification: string | null
  janCode: string | null
  makerName: string | null
  supplierName: string | null
  supplierNo: number | null
  purchasePrice: number | null
  nowGoodsPrice: number | null
  containNum: number | null
  changeContainNum: number | null
  pricePlanInfo: string | null
  planAfterPrice: number | null
}

/** Unified goods data for comparison table (API + unregistered goods) */
export interface ComparisonGoodsData {
  goodsNo: number | null
  goodsCode: string
  goodsName: string
  specification: string | null
  janCode: string | null
  makerName: string | null
  supplierName: string | null
  supplierNo: number | null
  purchasePrice: number | null
  nowGoodsPrice: number | null
  containNum: number | null
  changeContainNum: number | null
  pricePlanInfo: string | null
  planAfterPrice: number | null
  source: 'GOODS' | 'PRICE_PLAN' | 'QUOTE_IMPORT'
}

export interface ComparisonItem {
  id: string
  goods: ComparisonGoodsData
  isBase: boolean
  simulatedPrice: number | null
  simulatedQty: number | null
}
