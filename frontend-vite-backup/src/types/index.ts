export interface User {
  loginUserNo: number
  userName: string
  loginId: string
  companyNo: number
  companyType: string
  shopNo: number
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
}

export interface SalesSummary {
  shopNo: number
  month: string
  totalSales: number
}
