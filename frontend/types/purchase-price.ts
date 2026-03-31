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
  partnerNo: number
  destinationNo: number
  goodsPrice: number | null
  includeTaxGoodsPrice: number | null
  taxRate: number | null
  taxCategory: number
  lastPurchaseDate: string | null
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
  partnerNo: number
  destinationNo: number
  partnerPriceChangePlanCreated: boolean
  purchasePriceReflect: boolean
}

export const CHANGE_REASON_OPTIONS = [
  { value: 'PU', label: '値上' },
  { value: 'PD', label: '値下' },
  { value: 'ES', label: '販売終了' },
] as const

export function getChangeReasonLabel(code: string): string {
  return CHANGE_REASON_OPTIONS.find((o) => o.value === code)?.label ?? code
}
