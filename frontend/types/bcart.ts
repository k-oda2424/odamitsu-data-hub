export interface BCartCategory {
  id: number
  name: string
  description: string
  rvDescription: string
  parentCategoryId: number | null
  parentCategoryName?: string
  headerImage: string | null
  bannerImage: string | null
  menuImage: string | null
  metaTitle: string
  metaKeywords: string
  metaDescription: string
  priority: number
  flag: number // 1=表示, 0=非表示
  bCartReflected: boolean
  version: number
  createdAt: string
  updatedAt: string | null
  children?: BCartCategory[]
  productCount?: number
}

export interface BCartCategoryUpdateRequest {
  name: string
  description: string
  rvDescription: string
  parentCategoryId: number | null
  metaTitle: string
  metaKeywords: string
  metaDescription: string
  priority: number
  flag: number
  version: number
}

export interface BCartProduct {
  id: number
  mainNo: string
  name: string
  catchCopy: string
  categoryId: number | null
  categoryName: string | null
  subCategoryId: string | null
  description: string
  size: string
  sozai: string
  caution: string
  tag: string | null
  metaTitle: string
  metaKeywords: string
  metaDescription: string
  image: string | null
  prependText: string
  appendText: string
  middleText: string
  rvPrependText: string
  rvAppendText: string
  rvMiddleText: string
  flag: string // '表示' or '非表示'
  priority: number
  updatedAt: string | null
  setCount: number
  sets?: BCartProductSet[]
}

export interface BCartProductSet {
  id: number
  productNo: string
  janCode: string
  name: string
  unitPrice: number | null
  purchasePrice: number | null
  stock: number | null
  setFlag: string
  bCartPriceReflected: boolean
}

export interface BCartProductDescriptionUpdate {
  name?: string
  catchCopy?: string
  description?: string
  prependText?: string
  appendText?: string
  middleText?: string
  rvPrependText?: string
  rvAppendText?: string
  rvMiddleText?: string
  metaTitle?: string
  metaKeywords?: string
  metaDescription?: string
}

export interface BCartChangeHistory {
  id: number
  targetType: string
  targetId: number
  changeType: string
  fieldName: string
  beforeValue: string
  afterValue: string
  changedBy: number
  changedAt: string
  bCartReflected: boolean
  bCartReflectedAt: string | null
}
