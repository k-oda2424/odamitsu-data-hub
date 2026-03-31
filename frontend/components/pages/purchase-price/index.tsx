'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, useSuppliers } from '@/hooks/use-master-data'
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
import { PriceChangeDialog } from './PriceChangeDialog'
import type { PurchasePriceResponse } from '@/types/purchase-price'

const columns: Column<PurchasePriceResponse>[] = [
  { key: 'goodsCode', header: '商品コード', sortable: true },
  { key: 'goodsName', header: '商品名', sortable: true },
  { key: 'supplierName', header: '仕入先' },
  {
    key: 'goodsPrice',
    header: '仕入価格',
    render: (item) => item.goodsPrice?.toLocaleString() ?? '',
  },
  {
    key: 'includeTaxGoodsPrice',
    header: '税込価格',
    render: (item) => item.includeTaxGoodsPrice?.toLocaleString() ?? '',
  },
  { key: 'lastPurchaseDate', header: '直近仕入日' },
]

export function PurchasePriceListPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const [goodsName, setGoodsName] = useState('')
  const [goodsCode, setGoodsCode] = useState('')
  const [supplierNo, setSupplierNo] = useState<string>('')
  const [selectedShopNo, setSelectedShopNo] = useState<string>(
    isAdmin ? '' : String(user?.shopNo ?? '')
  )
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(null)

  const [dialogOpen, setDialogOpen] = useState(false)
  const [selectedPrice, setSelectedPrice] = useState<PurchasePriceResponse | null>(null)

  const shopsQuery = useShops()
  const effectiveShopNo = isAdmin ? selectedShopNo : String(user?.shopNo ?? '')
  const suppliersQuery = useSuppliers(effectiveShopNo)

  const listQuery = useQuery({
    queryKey: ['purchase-prices', effectiveShopNo, searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      if (effectiveShopNo) params.append('shopNo', effectiveShopNo)
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams?.goodsCode) params.append('goodsCode', searchParams.goodsCode)
      if (searchParams?.supplierNo) params.append('supplierNo', searchParams.supplierNo)
      return api.get<PurchasePriceResponse[]>(`/purchase-prices?${params.toString()}`)
    },
    enabled: searchParams !== null && !!effectiveShopNo,
  })

  const handleSearch = () => {
    setSearchParams({ goodsName, goodsCode, supplierNo })
  }

  const handleReset = () => {
    setGoodsName('')
    setGoodsCode('')
    setSupplierNo('')
    setSearchParams(null)
  }

  const handleRowClick = (item: PurchasePriceResponse) => {
    setSelectedPrice(item)
    setDialogOpen(true)
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader title="仕入価格一覧" />

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
          <Label>商品名</Label>
          <Input
            placeholder="商品名を入力"
            value={goodsName}
            onChange={(e) => setGoodsName(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>商品コード</Label>
          <Input
            placeholder="商品コードを入力"
            value={goodsCode}
            onChange={(e) => setGoodsCode(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>仕入先</Label>
          <SearchableSelect
            value={supplierNo}
            onValueChange={setSupplierNo}
            options={(suppliersQuery.data ?? []).map((s) => ({
              value: String(s.supplierNo),
              label: s.supplierName,
            }))}
            searchPlaceholder="仕入先を検索..."
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

      <PriceChangeDialog
        purchasePrice={selectedPrice}
        open={dialogOpen}
        onOpenChange={setDialogOpen}
        onSuccess={() => listQuery.refetch()}
      />
    </div>
  )
}
