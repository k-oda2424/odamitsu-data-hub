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
