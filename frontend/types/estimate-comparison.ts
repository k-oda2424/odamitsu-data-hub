// 比較見積 API レスポンス型（一覧 + 詳細共用）
export interface ComparisonResponse {
  comparisonNo: number
  shopNo: number
  partnerNo: number | null
  partnerName: string | null
  destinationNo: number | null
  destinationName: string | null
  comparisonDate: string
  comparisonStatus: string
  sourceEstimateNo: number | null
  title: string | null
  note: string | null
  groupCount: number
  groups: ComparisonGroupResponse[]
}

export interface ComparisonGroupResponse {
  groupNo: number
  baseGoodsNo: number | null
  baseGoodsCode: string | null
  baseGoodsName: string
  baseSpecification: string | null
  basePurchasePrice: number | null
  baseGoodsPrice: number | null
  baseContainNum: number | null
  displayOrder: number
  groupNote: string | null
  details: ComparisonDetailResponse[]
}

export interface ComparisonDetailResponse {
  detailNo: number
  goodsNo: number | null
  goodsCode: string | null
  goodsName: string
  specification: string | null
  purchasePrice: number | null
  proposedPrice: number | null
  containNum: number | null
  profitRate: number | null
  detailNote: string | null
  displayOrder: number
  supplierNo: number | null
}

// リクエスト型
export interface ComparisonCreateRequest {
  shopNo: number
  partnerNo: number | null
  destinationNo: number | null
  comparisonDate: string
  title: string | null
  note: string | null
  groups: ComparisonGroupCreateRequest[]
}

export interface ComparisonGroupCreateRequest {
  baseGoodsNo: number | null
  baseGoodsCode: string | null
  baseGoodsName: string
  baseSpecification: string | null
  basePurchasePrice: number | null
  baseGoodsPrice: number | null
  baseContainNum: number | null
  displayOrder: number
  groupNote: string | null
  details: ComparisonDetailCreateRequest[]
}

export interface ComparisonDetailCreateRequest {
  goodsNo: number | null
  goodsCode: string | null
  goodsName: string
  specification: string | null
  purchasePrice: number | null
  proposedPrice: number | null
  containNum: number | null
  detailNote: string | null
  displayOrder: number
  supplierNo: number | null
}

// フォーム用ローカル型
export interface GroupRow {
  id: string
  baseGoodsNo: number | null
  baseGoodsCode: string
  baseGoodsName: string
  baseSpecification: string
  basePurchasePrice: number | null
  baseGoodsPrice: number | null
  baseContainNum: number | null
  groupNote: string
  details: DetailRow[]
}

export interface DetailRow {
  id: string
  goodsNo: number | null
  goodsCode: string
  goodsName: string
  specification: string
  purchasePrice: number | null
  proposedPrice: number | null
  containNum: number | null
  detailNote: string
  supplierNo: number | null
}

export function createEmptyGroup(): GroupRow {
  return {
    id: crypto.randomUUID(),
    baseGoodsNo: null,
    baseGoodsCode: '',
    baseGoodsName: '',
    baseSpecification: '',
    basePurchasePrice: null,
    baseGoodsPrice: null,
    baseContainNum: null,
    groupNote: '',
    details: [],
  }
}

export function createEmptyDetail(): DetailRow {
  return {
    id: crypto.randomUUID(),
    goodsNo: null,
    goodsCode: '',
    goodsName: '',
    specification: '',
    purchasePrice: null,
    proposedPrice: null,
    containNum: null,
    detailNote: '',
    supplierNo: null,
  }
}
