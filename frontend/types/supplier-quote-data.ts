export interface SupplierQuoteDataResponse {
  janCode: string
  quoteGoodsName: string
  specification: string | null
  quantityPerCase: number | null
  currentPrice: number | null
  currentBoxPrice: number | null
  effectiveDate: string
  supplierName: string | null
  supplierCode: string | null
  quoteImportDetailId: number
}

export interface SupplierQuoteHistoryResponse {
  quoteImportDetailId: number
  quoteDate: string | null
  effectiveDate: string | null
  oldPrice: number | null
  newPrice: number | null
  oldBoxPrice: number | null
  newBoxPrice: number | null
  fileName: string | null
  changeReason: string | null
  supplierName: string | null
  latest: boolean
}
