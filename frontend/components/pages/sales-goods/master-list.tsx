'use client'

import { useState, useCallback } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, useSuppliers } from '@/hooks/use-master-data'
import { DataTable } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchForm } from '@/components/features/common/SearchForm'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import type { SalesGoodsDetailResponse } from '@/types/goods'
import { emptyPage, type Paginated } from '@/types/paginated'
import { salesGoodsColumns, SalesGoodsSearchFields } from './shared'

const PAGE_SIZE = 50

export function SalesGoodsMasterListPage() {
  const router = useRouter()
  const urlParams = useSearchParams()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  // URLパラメータから検索状態を復元
  const initialSearched = urlParams.get('searched') === '1'
  const [goodsName, setGoodsName] = useState(urlParams.get('goodsName') ?? '')
  const [goodsCode, setGoodsCode] = useState(urlParams.get('goodsCode') ?? '')
  const [keyword, setKeyword] = useState(urlParams.get('keyword') ?? '')
  const [supplierNo, setSupplierNo] = useState<string>(urlParams.get('supplierNo') ?? '')
  const [selectedShopNo, setSelectedShopNo] = useState<string>(
    urlParams.get('shopNo') ?? (isAdmin ? '' : String(user?.shopNo ?? ''))
  )
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(
    initialSearched ? { goodsName: urlParams.get('goodsName') ?? '', goodsCode: urlParams.get('goodsCode') ?? '', keyword: urlParams.get('keyword') ?? '', supplierNo: urlParams.get('supplierNo') ?? '' } : null
  )
  const [page, setPage] = useState(0)

  const shopsQuery = useShops(isAdmin)
  const effectiveShopNo = isAdmin ? selectedShopNo : String(user?.shopNo ?? '')
  const suppliersQuery = useSuppliers(effectiveShopNo)

  const listQuery = useQuery({
    queryKey: ['sales-goods-master', effectiveShopNo, searchParams, page],
    queryFn: () => {
      const params = new URLSearchParams()
      params.append('shopNo', effectiveShopNo)
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams?.goodsCode) params.append('goodsCode', searchParams.goodsCode)
      if (searchParams?.keyword) params.append('keyword', searchParams.keyword)
      if (searchParams?.supplierNo) params.append('supplierNo', searchParams.supplierNo)
      params.append('page', String(page))
      params.append('size', String(PAGE_SIZE))
      return api.get<Paginated<SalesGoodsDetailResponse>>(`/sales-goods/master?${params.toString()}`)
    },
    enabled: searchParams !== null && !!effectiveShopNo,
  })

  const syncUrl = useCallback((params: Record<string, string> | null, shopNo: string) => {
    const url = new URLSearchParams()
    if (params) {
      url.set('searched', '1')
      if (shopNo) url.set('shopNo', shopNo)
      if (params.goodsName) url.set('goodsName', params.goodsName)
      if (params.goodsCode) url.set('goodsCode', params.goodsCode)
      if (params.keyword) url.set('keyword', params.keyword)
      if (params.supplierNo) url.set('supplierNo', params.supplierNo)
    }
    const qs = url.toString()
    router.replace(`/sales-goods${qs ? `?${qs}` : ''}`, { scroll: false })
  }, [router])

  const handleSearch = () => {
    const params = { goodsName, goodsCode, keyword, supplierNo }
    setPage(0)
    setSearchParams(params)
    syncUrl(params, effectiveShopNo)
  }

  const handleReset = () => {
    setGoodsName('')
    setGoodsCode('')
    setKeyword('')
    setSupplierNo('')
    setPage(0)
    setSearchParams(null)
    router.replace('/sales-goods', { scroll: false })
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader title="販売商品マスタ一覧" />

      <SearchForm onSearch={handleSearch} onReset={handleReset}>
        {isAdmin && (
          <div className="space-y-2">
            <Label>店舗</Label>
            <Select value={selectedShopNo} onValueChange={setSelectedShopNo}>
              <SelectTrigger>
                <SelectValue placeholder="店舗を選択してください" />
              </SelectTrigger>
              <SelectContent>
                {(shopsQuery.data ?? []).map((shop) => (
                  <SelectItem key={shop.shopNo} value={String(shop.shopNo)}>
                    {shop.shopName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        <SalesGoodsSearchFields
          goodsName={goodsName}
          onGoodsNameChange={setGoodsName}
          goodsCode={goodsCode}
          onGoodsCodeChange={setGoodsCode}
          keyword={keyword}
          onKeywordChange={setKeyword}
          supplierNo={supplierNo}
          onSupplierNoChange={setSupplierNo}
          suppliers={suppliersQuery.data ?? []}
        />
      </SearchForm>

      {!hasSearched ? (
        <div className="rounded-lg border p-8 text-center text-muted-foreground">
          検索条件を入力して「検索」ボタンを押してください
        </div>
      ) : listQuery.isLoading ? (
        <LoadingSpinner />
      ) : listQuery.isError ? (
        <ErrorMessage onRetry={() => listQuery.refetch()} />
      ) : (() => {
        const p = listQuery.data ?? emptyPage<SalesGoodsDetailResponse>(PAGE_SIZE)
        return (
          <DataTable
            data={p.content}
            columns={salesGoodsColumns}
            onRowClick={(item) => router.push(`/sales-goods/${item.shopNo}/${item.goodsNo}`)}
            serverPagination={{
              page: p.number,
              pageSize: p.size,
              totalElements: p.totalElements,
              totalPages: p.totalPages,
              onPageChange: setPage,
            }}
          />
        )
      })()}
    </div>
  )
}
