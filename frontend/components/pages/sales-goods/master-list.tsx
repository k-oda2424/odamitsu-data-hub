'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
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
import { salesGoodsColumns, SalesGoodsSearchFields } from './shared'

export function SalesGoodsMasterListPage() {
  const router = useRouter()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const [goodsName, setGoodsName] = useState('')
  const [goodsCode, setGoodsCode] = useState('')
  const [keyword, setKeyword] = useState('')
  const [supplierNo, setSupplierNo] = useState<string>('')
  const [selectedShopNo, setSelectedShopNo] = useState<string>(isAdmin ? '' : String(user?.shopNo ?? ''))
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(null)

  const shopsQuery = useShops(isAdmin)
  const effectiveShopNo = isAdmin ? selectedShopNo : String(user?.shopNo ?? '')
  const suppliersQuery = useSuppliers(effectiveShopNo)

  const listQuery = useQuery({
    queryKey: ['sales-goods-master', effectiveShopNo, searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      params.append('shopNo', effectiveShopNo)
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams?.goodsCode) params.append('goodsCode', searchParams.goodsCode)
      if (searchParams?.keyword) params.append('keyword', searchParams.keyword)
      if (searchParams?.supplierNo) params.append('supplierNo', searchParams.supplierNo)
      return api.get<SalesGoodsDetailResponse[]>(`/sales-goods/master?${params.toString()}`)
    },
    enabled: searchParams !== null && !!effectiveShopNo,
  })

  const handleSearch = () => {
    setSearchParams({ goodsName, goodsCode, keyword, supplierNo })
  }

  const handleReset = () => {
    setGoodsName('')
    setGoodsCode('')
    setKeyword('')
    setSupplierNo('')
    setSearchParams(null)
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
      ) : (
        <DataTable
          data={listQuery.data ?? []}
          columns={salesGoodsColumns}
          searchPlaceholder="テーブル内を検索..."
          onRowClick={(item) => router.push(`/sales-goods/${item.shopNo}/${item.goodsNo}`)}
        />
      )}
    </div>
  )
}
