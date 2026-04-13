/**
 * Spring Data Page<T> のフロントエンド型。
 * Backend が `Page<T>` を JSON にシリアライズしたときの構造に合わせる。
 */
export interface Paginated<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number      // current page index (0-based)
  size: number        // page size
  first: boolean
  last: boolean
  numberOfElements: number
  empty: boolean
}

export function emptyPage<T>(size = 50): Paginated<T> {
  return {
    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size,
    first: true,
    last: true,
    numberOfElements: 0,
    empty: true,
  }
}
