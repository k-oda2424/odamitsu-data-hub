export interface PurchasePriceResponse {
  purchasePriceNo: number
  goodsNo: number
  goodsCode: string | null
  goodsName: string | null
  makerName: string | null
  supplierNo: number
  supplierName: string | null
  supplierCode: string | null
  shopNo: number
  partnerNo: number | null
  partnerName: string | null
  destinationNo: number | null
  destinationName: string | null
  goodsPrice: number | null
  includeTaxGoodsPrice: number | null
  taxRate: number | null
  taxCategory: number
  includeTaxFlg: string | null
  periodFrom: string | null
  periodTo: string | null
  note: string | null
  lastPurchaseDate: string | null
}

export interface PurchasePriceCreateRequest {
  shopNo: number
  goodsNo: number
  supplierNo: number
  partnerNo: number | null
  destinationNo: number | null
  goodsPrice: number
  taxRate: number | null
  includeTaxFlg: boolean  // API送信時はboolean、レスポンスのincludeTaxFlgは'0'/'1'文字列（バックエンドで変換）
  periodFrom: string | null
  periodTo: string | null
  note: string | null
}

export interface PurchasePriceChangePlanResponse {
  purchasePriceChangePlanNo: number
  shopNo: number
  goodsCode: string
  goodsName: string | null
  janCode: string | null
  supplierCode: string
  supplierName: string | null
  beforePrice: number | null
  afterPrice: number | null
  changePlanDate: string
  changeReason: string
  changeContainNum: number | null
  partnerNo: number | null
  partnerName: string | null
  destinationNo: number | null
  destinationName: string | null
  partnerPriceChangePlanCreated: boolean
  purchasePriceReflect: boolean
}

/** 価格スコープ: 標準/特値/全て */
export const PRICE_SCOPE_OPTIONS = [
  { value: 'all', label: '全て' },
  { value: 'standard', label: '標準のみ' },
  { value: 'partner', label: '得意先別のみ' },
] as const

export type PriceScope = (typeof PRICE_SCOPE_OPTIONS)[number]['value']

/** 価格の種別を判定（標準 or 得意先別） */
export function isPartnerSpecificPrice(partnerNo: number | null, destinationNo: number | null): boolean {
  return (partnerNo != null && partnerNo !== 0) || (destinationNo != null && destinationNo !== 0)
}

export const CHANGE_REASON_OPTIONS = [
  { value: 'PU', label: '値上' },
  { value: 'PD', label: '値下' },
  { value: 'ES', label: '販売終了' },
] as const

export function getChangeReasonLabel(code: string): string {
  return CHANGE_REASON_OPTIONS.find((o) => o.value === code)?.label ?? code
}
