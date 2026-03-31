export interface PartnerGoodsResponse {
  partnerNo: number
  goodsNo: number
  destinationNo: number
  shopNo: number
  companyNo: number
  shopName: string
  companyName: string
  goodsCode: string
  goodsName: string
  goodsPrice: number
  keyword: string
  reflectedEstimateNo: number | null
  lastSalesDate: string | null
  lastPriceUpdateDate: string | null
}

export interface OrderHistoryResponse {
  orderDateTime: string
  goodsCode: string
  goodsName: string
  goodsPrice: number | null
  goodsNum: number
}

export interface PartnerGoodsDetailResponse extends PartnerGoodsResponse {
  orderHistory: OrderHistoryResponse[]
}

export interface PartnerGoodsUpdateRequest {
  goodsName?: string
  keyword?: string
  goodsPrice?: number
}

export interface PartnerResponse {
  partnerNo: number
  partnerName: string
  partnerCode: string
  shopNo: number
  companyNo: number
}

export interface DeliveryDestinationResponse {
  destinationNo: number
  destinationName: string
  destinationCode: string
  partnerNo: number
}
