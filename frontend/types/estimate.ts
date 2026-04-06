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
}

export interface EstimateResponse {
  estimateNo: number
  shopNo: number
  partnerNo: number
  partnerCode: string | null
  partnerName: string | null
  destinationNo: number | null
  estimateDate: string | null
  priceChangeDate: string | null
  estimateStatus: string | null
  note: string | null
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
}

export interface EstimateCreateRequest {
  shopNo: number
  partnerNo: number
  destinationNo: number | null
  estimateDate: string
  priceChangeDate: string
  note: string
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
  pricePlanInfo: string | null
  janCode: string | null
  source: 'GOODS' | 'PRICE_PLAN' | 'QUOTE_IMPORT'
  purchasePriceChangePlanNo: number | null
}
