'use client'

import { useState, useMemo, useEffect } from 'react'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight, ArrowUpDown, Search } from 'lucide-react'
import { normalizeForSearch } from '@/lib/utils'

export interface Column<T> {
  key: string
  /**
   * 列ヘッダー。文字列または ReactNode を受け取る。
   * AmountSourceTooltip 等のアイコン併記時は ReactNode 形式 (<>...</>) で渡す。
   */
  header: React.ReactNode
  render?: (item: T) => React.ReactNode
  sortable?: boolean
}

export interface ServerPagination {
  page: number          // 0-based
  pageSize: number
  totalElements: number
  totalPages: number
  onPageChange: (page: number) => void
}

interface DataTableProps<T> {
  data: T[]
  columns: Column<T>[]
  pageSize?: number
  searchPlaceholder?: string
  onRowClick?: (item: T) => void
  rowKey?: (item: T, index: number) => string | number
  defaultSortKey?: string
  defaultSortDir?: 'asc' | 'desc'
  /**
   * 指定時はサーバーサイドページング。data はすでに現ページ分に絞られた内容。
   * クライアント側のフィルタ/ソート/ページングは無効になる。
   */
  serverPagination?: ServerPagination
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export function DataTable<T extends Record<string, any>>({
  data,
  columns,
  pageSize = 20,
  searchPlaceholder = 'テーブル内を検索...',
  onRowClick,
  rowKey,
  defaultSortKey,
  defaultSortDir = 'asc',
  serverPagination,
}: DataTableProps<T>) {
  const [page, setPage] = useState(0)
  const [search, setSearch] = useState('')
  const isServerMode = serverPagination != null

  // データが変わったらページを先頭に戻す（クライアントモードのみ）
  useEffect(() => { if (!isServerMode) setPage(0) }, [data, isServerMode])
  const [sortKey, setSortKey] = useState<string | null>(defaultSortKey ?? null)
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>(defaultSortDir)

  const filtered = useMemo(() => {
    if (isServerMode) return data
    if (search === '') return data
    const normalizedSearch = normalizeForSearch(search.toLowerCase())
    return data.filter((item) =>
      Object.values(item).some((v) =>
        normalizeForSearch(String(v ?? '').toLowerCase()).includes(normalizedSearch)
      )
    )
  }, [data, search, isServerMode])

  const sorted = useMemo(() => {
    if (isServerMode || !sortKey) return filtered
    return [...filtered].sort((a, b) => {
      const aRaw = a[sortKey]
      const bRaw = b[sortKey]
      if (typeof aRaw === 'number' || typeof bRaw === 'number') {
        const aNum = Number(aRaw ?? 0)
        const bNum = Number(bRaw ?? 0)
        return sortDir === 'asc' ? aNum - bNum : bNum - aNum
      }
      const aVal = String(aRaw ?? '')
      const bVal = String(bRaw ?? '')
      return sortDir === 'asc' ? aVal.localeCompare(bVal) : bVal.localeCompare(aVal)
    })
  }, [filtered, sortKey, sortDir, isServerMode])

  const effectivePageSize = serverPagination ? serverPagination.pageSize : pageSize
  const effectiveTotal = serverPagination ? serverPagination.totalElements : sorted.length
  const totalPages = serverPagination
    ? Math.max(1, serverPagination.totalPages)
    : Math.max(1, Math.ceil(sorted.length / pageSize))
  const currentPage = serverPagination ? serverPagination.page : page
  const paged = serverPagination ? sorted : sorted.slice(page * pageSize, (page + 1) * pageSize)
  const goToPage = (n: number) => {
    if (serverPagination) serverPagination.onPageChange(n)
    else setPage(n)
  }

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDir(sortDir === 'asc' ? 'desc' : 'asc')
    } else {
      setSortKey(key)
      setSortDir('asc')
    }
  }

  return (
    <div className="space-y-3">
      {/* Search bar (クライアントモードのみ) */}
      {!isServerMode && (
        <div className="relative max-w-sm">
          <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder={searchPlaceholder}
            value={search}
            onChange={(e) => { setSearch(e.target.value); setPage(0) }}
            className="h-9 pl-8 text-sm"
          />
        </div>
      )}

      {/* Table */}
      <div className="rounded-lg border shadow-sm">
        <Table>
          <TableHeader>
            <TableRow className="bg-muted/40 hover:bg-muted/40">
              {columns.map((col) => (
                <TableHead
                  key={col.key}
                  className={`text-xs font-semibold uppercase tracking-wider text-muted-foreground ${col.sortable ? 'cursor-pointer select-none hover:text-foreground transition-colors' : ''}`}
                  onClick={() => col.sortable && handleSort(col.key)}
                >
                  <div className="flex items-center gap-1">
                    {col.header}
                    {col.sortable && (
                      <ArrowUpDown className={`h-3 w-3 ${sortKey === col.key ? 'text-foreground' : 'opacity-40'}`} />
                    )}
                  </div>
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {paged.length === 0 ? (
              <TableRow>
                <TableCell colSpan={columns.length} className="text-center text-muted-foreground py-12">
                  データがありません
                </TableCell>
              </TableRow>
            ) : (
              paged.map((item, i) => (
                <TableRow
                  key={rowKey ? rowKey(item, i) : i}
                  className={`text-sm ${onRowClick ? 'cursor-pointer' : ''} ${i % 2 === 1 ? 'bg-muted/20' : ''}`}
                  onClick={() => onRowClick?.(item)}
                >
                  {columns.map((col) => (
                    <TableCell key={col.key} className="py-2.5">
                      {col.render ? col.render(item) : String(item[col.key] ?? '')}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      <div className="flex items-center justify-between text-sm">
        <span className="text-muted-foreground">
          全 {effectiveTotal} 件中 {effectiveTotal > 0 ? currentPage * effectivePageSize + 1 : 0}–{Math.min((currentPage + 1) * effectivePageSize, effectiveTotal)} 件
        </span>
        <div className="flex items-center gap-1">
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => goToPage(0)} disabled={currentPage === 0} aria-label="最初のページ">
            <ChevronsLeft className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => goToPage(currentPage - 1)} disabled={currentPage === 0} aria-label="前のページ">
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="min-w-[4rem] text-center text-muted-foreground">
            {currentPage + 1} / {totalPages}
          </span>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => goToPage(currentPage + 1)} disabled={currentPage >= totalPages - 1} aria-label="次のページ">
            <ChevronRight className="h-4 w-4" />
          </Button>
          <Button variant="ghost" size="icon" className="h-8 w-8" onClick={() => goToPage(totalPages - 1)} disabled={currentPage >= totalPages - 1} aria-label="最後のページ">
            <ChevronsRight className="h-4 w-4" />
          </Button>
        </div>
      </div>
    </div>
  )
}
