export interface Shop {
  shopNo: number
  shopName: string
}

export interface Maker {
  makerNo: number
  makerName: string

}

export interface Supplier {
  supplierNo: number
  supplierCode: string | null
  supplierName: string
  shopNo: number
}

export interface GoodsResponse {
  goodsNo: number
  goodsName: string
  janCode: string
  makerNo: number
  makerName: string
  keyword: string
  taxCategory: number
  specification: string
  discontinuedFlg: string
  caseContainNum: number
  applyReducedTaxRate: boolean
  delFlg: string

}

export interface SalesGoodsDetailResponse {
  shopNo: number
  goodsNo: number
  goodsCode: string
  goodsSkuCode: string
  goodsName: string
  categoryNo: number
  referencePrice: number
  purchasePrice: number
  goodsPrice: number
  supplierNo: number
  supplierName: string
  catchphrase: string
  goodsIntroduction: string
  goodsDescription1: string
  goodsDescription2: string
  directShippingFlg: string
  leadTime: number
  keyword: string
  delFlg: string
  isWork: boolean
  janCode: string
  makerName: string
  specification: string

}

export interface GoodsDetailResponse {
  goodsNo: number
  goodsName: string
  janCode: string
  makerNo: number
  makerName: string
  keyword: string
  taxCategory: number
  specification: string
  discontinuedFlg: string
  caseContainNum: number
  applyReducedTaxRate: boolean
  delFlg: string
  salesGoodsList: SalesGoodsDetailResponse[]
}

export interface SalesGoodsCreateRequest {
  shopNo: number
  goodsNo: number
  goodsCode: string
  goodsSkuCode?: string | null
  goodsName: string
  keyword?: string | null
  categoryNo?: number | null
  supplierNo: number
  goodsPrice: number
  referencePrice?: number | null
  purchasePrice: number
  catchphrase?: string | null
  goodsIntroduction?: string | null
  goodsDescription1?: string | null
  goodsDescription2?: string | null
}

export interface SalesGoodsUpdateRequest {
  goodsCode: string
  goodsSkuCode?: string | null
  goodsName: string
  keyword?: string | null
  categoryNo?: number | null
  supplierNo: number
  purchasePrice: number
  goodsPrice: number
  referencePrice?: number | null
  catchphrase?: string | null
  goodsIntroduction?: string | null
  goodsDescription1?: string | null
  goodsDescription2?: string | null
  directShippingFlg?: string | null
  leadTime?: number | null
}
