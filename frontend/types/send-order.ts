export interface SendOrderDetailResponse {
  sendOrderNo: number
  sendOrderDetailNo: number
  shopNo: number
  supplierNo: number | null
  supplierName: string | null
  warehouseNo: number | null
  warehouseName: string | null
  sendOrderDateTime: string | null
  desiredDeliveryDate: string | null
  goodsNo: number
  goodsCode: string | null
  goodsName: string | null
  goodsPrice: number | null
  sendOrderNum: number
  sendOrderCaseNum: number | null
  containNum: number | null
  subtotal: number | null
  arrivePlanDate: string | null
  arrivedDate: string | null
  arrivedNum: number | null
  sendOrderDetailStatus: string | null
}

export interface SendOrderResponse {
  sendOrderNo: number
  shopNo: number
  supplierNo: number
  supplierName: string | null
  warehouseNo: number
  warehouseName: string | null
  sendOrderDateTime: string
  desiredDeliveryDate: string | null
  sendOrderStatus: string | null
  details: SendOrderDetailResponse[] | null
}

export interface SendOrderCreateRequest {
  shopNo: number
  warehouseNo: number
  supplierNo: number
  sendOrderDateTime: string
  desiredDeliveryDate: string | null
  details: {
    goodsNo: number
    goodsCode: string
    goodsName: string
    goodsPrice: number
    sendOrderNum: number
    containNum: number | null
  }[]
}

export const SEND_ORDER_DETAIL_STATUS_OPTIONS = [
  { value: '00', label: '発注済' },
  { value: '10', label: '納期回答' },
  { value: '20', label: '入荷済' },
  { value: '30', label: '仕入入力済' },
  { value: '99', label: 'キャンセル' },
] as const

export function getSendOrderDetailStatusLabel(code: string | null): string {
  if (!code) return ''
  return SEND_ORDER_DETAIL_STATUS_OPTIONS.find((s) => s.value === code)?.label ?? code
}

export interface WarehouseResponse {
  warehouseNo: number
  warehouseName: string
}
