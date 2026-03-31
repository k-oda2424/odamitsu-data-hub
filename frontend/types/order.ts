export interface OrderDetailResponse {
  orderNo: number
  orderDetailNo: number
  shopNo: number
  companyName: string | null
  partnerCode: string | null
  partnerNo: number | null
  orderDetailStatus: string | null
  orderDateTime: string | null
  goodsCode: string | null
  goodsName: string | null
  goodsPrice: number | null
  orderNum: number | null
  cancelNum: number | null
  returnNum: number | null
  unitContainNum: number | null
  unitNum: number | null
  subtotal: number | null
  slipNo: string | null
  slipDate: string | null
  deliveryNo: number | null
}

export const ORDER_DETAIL_STATUS_OPTIONS = [
  { value: '00', label: '注文受付' },
  { value: '01', label: '入荷待ち' },
  { value: '10', label: '在庫引当' },
  { value: '20', label: '納品済' },
  { value: '90', label: 'キャンセル' },
  { value: '99', label: '返品' },
] as const

export function getOrderDetailStatusLabel(code: string | null): string {
  if (!code) return ''
  return ORDER_DETAIL_STATUS_OPTIONS.find((s) => s.value === code)?.label ?? code
}
