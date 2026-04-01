export interface QuoteImportHeaderResponse {
  quoteImportId: number
  shopNo: number
  supplierName: string | null
  supplierCode: string | null
  supplierNo: number | null
  fileName: string | null
  quoteDate: string | null
  effectiveDate: string | null
  changeReason: string | null
  priceType: string | null
  totalCount: number
  remainingCount: number
  addDateTime: string | null
}

export interface QuoteImportDetailResponse {
  quoteImportDetailId: number
  rowNo: number | null
  janCode: string | null
  quoteGoodsName: string
  quoteGoodsCode: string | null
  specification: string | null
  quantityPerCase: number | null
  oldPrice: number | null
  newPrice: number | null
  oldBoxPrice: number | null
  newBoxPrice: number | null
}

export interface QuoteImportDetailData {
  header: QuoteImportHeaderResponse
  details: QuoteImportDetailResponse[]
}
