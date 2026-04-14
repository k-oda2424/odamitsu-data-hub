export interface PurchaseHeaderResponse {
  purchaseNo: number
  purchaseCode: string | null
  purchaseDate: string | null
  shopNo: number
  supplierNo: number
  supplierCode: string | null
  supplierName: string | null
  purchaseAmount: number | null
  includeTaxAmount: number | null
  taxAmount: number | null
  taxRate: number | null
  note: string | null
}

export interface PurchaseListResponse {
  rows: PurchaseHeaderResponse[]
  summary: PurchaseListSummary
}

export interface PurchaseListSummary {
  totalRows: number
  totalAmountExcTax: number
  totalTaxAmount: number
  totalAmountIncTax: number
  byTaxRate: TaxRateBreakdown[]
}

export interface TaxRateBreakdown {
  taxRate: number
  rows: number
  amountExcTax: number
  taxAmount: number
  amountIncTax: number
}

export interface PurchaseDetailResponse {
  purchaseNo: number
  purchaseDetailNo: number
  purchaseDate: string | null
  goodsNo: number | null
  goodsCode: string | null
  goodsName: string | null
  goodsPrice: number | null
  goodsNum: number | null
  subtotal: number | null
  includeTaxSubtotal: number | null
  taxRate: number | null
  taxPrice: number | null
  taxType: string | null
  note: string | null
}
