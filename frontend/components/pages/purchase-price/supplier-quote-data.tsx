'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, useSuppliers, useMakers } from '@/hooks/use-master-data'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchForm } from '@/components/features/common/SearchForm'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { SupplierQuoteHistoryDialog } from './SupplierQuoteHistoryDialog'
import type { SupplierQuoteDataResponse } from '@/types/supplier-quote-data'

const columns: Column<SupplierQuoteDataResponse>[] = [
  { key: 'janCode', header: 'JANコード', sortable: true },
  { key: 'quoteGoodsName', header: '商品名', sortable: true },
  { key: 'specification', header: '規格' },
  { key: 'quantityPerCase', header: '入数' },
  { key: 'currentPrice', header: '現行単価', render: (item) => item.currentPrice?.toLocaleString() ?? '-' },
  { key: 'effectiveDate', header: '適用日', sortable: true },
  { key: 'supplierName', header: '仕入先' },
  { key: 'makerName', header: 'メーカー', sortable: true },
]

export function SupplierQuoteDataPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const [goodsName, setGoodsName] = useState('')
  const [makerNo, setMakerNo] = useState('')
  const [supplierCode, setSupplierCode] = useState('')
  const [selectedShopNo, setSelectedShopNo] = useState<string>(
    isAdmin ? '' : String(user?.shopNo ?? '')
  )
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(null)

  const [dialogOpen, setDialogOpen] = useState(false)
  const [selectedItem, setSelectedItem] = useState<SupplierQuoteDataResponse | null>(null)

  const shopsQuery = useShops(isAdmin)
  const effectiveShopNo = isAdmin ? selectedShopNo : String(user?.shopNo ?? '')
  const suppliersQuery = useSuppliers(effectiveShopNo)
  const makersQuery = useMakers()

  const listQuery = useQuery({
    queryKey: ['supplier-quote-data', effectiveShopNo, searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      if (effectiveShopNo) params.append('shopNo', effectiveShopNo)
      if (searchParams?.supplierCode) params.append('supplierCode', searchParams.supplierCode)
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams?.makerNo) params.append('makerNo', searchParams.makerNo)
      return api.get<SupplierQuoteDataResponse[]>(`/supplier-quote-data?${params.toString()}`)
    },
    enabled: searchParams !== null && !!effectiveShopNo,
  })

  const handleSearch = () => {
    setSearchParams({ supplierCode, goodsName, makerNo })
  }

  const handleReset = () => {
    setSupplierCode('')
    setGoodsName('')
    setMakerNo('')
    setSearchParams(null)
  }

  const handleRowClick = (item: SupplierQuoteDataResponse) => {
    setSelectedItem(item)
    setDialogOpen(true)
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader title="仕入先見積データ" />

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
        <div className="space-y-2">
          <Label>仕入先</Label>
          <SearchableSelect
            value={supplierCode}
            onValueChange={setSupplierCode}
            options={(suppliersQuery.data ?? []).map((s) => ({
              value: s.supplierCode ?? String(s.supplierNo),
              label: s.supplierName,
            }))}
            searchPlaceholder="仕入先を検索..."
          />
        </div>
        <div className="space-y-2">
          <Label>商品名</Label>
          <Input
            placeholder="商品名を入力"
            value={goodsName}
            onChange={(e) => setGoodsName(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>メーカー</Label>
          <SearchableSelect
            value={makerNo}
            onValueChange={setMakerNo}
            options={(makersQuery.data ?? []).map((m) => ({
              value: String(m.makerNo),
              label: m.makerName,
            }))}
            placeholder="メーカーを選択"
            searchPlaceholder="メーカーを検索..."
            clearable
          />
        </div>
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
          columns={columns}
          searchPlaceholder="テーブル内を検索..."
          onRowClick={handleRowClick}
        />
      )}

      <SupplierQuoteHistoryDialog
        item={selectedItem}
        shopNo={effectiveShopNo}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
      />
    </div>
  )
}
