'use client'

import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, useSuppliers } from '@/hooks/use-master-data'
import { useSearchParamsStorage } from '@/hooks/use-search-params-storage'
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
import { Button } from '@/components/ui/button'
import { useRouter } from 'next/navigation'
import { Plus } from 'lucide-react'
import { PriceChangeDialog } from './PriceChangeDialog'
import type { PurchasePriceResponse, PriceScope } from '@/types/purchase-price'
import { Badge } from '@/components/ui/badge'
import { PRICE_SCOPE_OPTIONS, isPartnerSpecificPrice } from '@/types/purchase-price'

const columns: Column<PurchasePriceResponse>[] = [
  {
    key: 'partnerNo',
    header: '種別',
    render: (item) =>
      isPartnerSpecificPrice(item.partnerNo, item.destinationNo) ? (
        <Badge variant="secondary">得意先別</Badge>
      ) : (
        <Badge variant="outline">標準</Badge>
      ),
  },
  { key: 'goodsCode', header: '商品コード', sortable: true },
  { key: 'goodsName', header: '商品名', sortable: true },
  { key: 'supplierName', header: '仕入先' },
  {
    key: 'partnerName',
    header: '得意先',
    render: (item) => item.partnerName ?? '-',
  },
  {
    key: 'destinationName',
    header: '配送先',
    render: (item) => item.destinationName ?? '-',
  },
  {
    key: 'goodsPrice',
    header: '仕入価格',
    render: (item) => item.goodsPrice?.toLocaleString() ?? '',
  },
  { key: 'lastPurchaseDate', header: '直近仕入日' },
]

export function PurchasePriceListPage() {
  const router = useRouter()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  interface PurchasePriceSearchFilters {
    goodsName?: string
    goodsCode?: string
    supplierNo?: string
    scope?: PriceScope
    paymentSupplierNo?: string
  }
  interface PurchasePriceSearchState {
    goodsName: string
    goodsCode: string
    supplierNo: string
    scope: PriceScope
    selectedShopNo: string
    searchParams: PurchasePriceSearchFilters | null
  }
  const defaultState: PurchasePriceSearchState = {
    goodsName: '',
    goodsCode: '',
    supplierNo: '',
    scope: 'all',
    selectedShopNo: isAdmin ? '' : String(user?.shopNo ?? ''),
    searchParams: null,
  }
  const [state, setState] = useSearchParamsStorage('purchase-price-list-search', defaultState)
  const { goodsName, goodsCode, supplierNo, scope, selectedShopNo, searchParams } = state
  const updateField = <K extends keyof PurchasePriceSearchState>(key: K, value: PurchasePriceSearchState[K]) => {
    setState({ ...state, [key]: value })
  }
  const setGoodsName = (v: string) => updateField('goodsName', v)
  const setGoodsCode = (v: string) => updateField('goodsCode', v)
  const setSupplierNo = (v: string) => updateField('supplierNo', v)
  const setScope = (v: PriceScope) => updateField('scope', v)
  const setSelectedShopNo = (v: string) => updateField('selectedShopNo', v)

  const [dialogOpen, setDialogOpen] = useState(false)
  const [selectedPrice, setSelectedPrice] = useState<PurchasePriceResponse | null>(null)

  const shopsQuery = useShops(isAdmin)
  const effectiveShopNo = isAdmin ? selectedShopNo : String(user?.shopNo ?? '')
  const suppliersQuery = useSuppliers(effectiveShopNo)

  // URL query からの drill-down (買掛金一覧から paymentSupplierNo 等で遷移してくる)
  const urlParams = useSearchParams()
  const paymentSupplierNoParam = urlParams.get('paymentSupplierNo')
  const supplierNameParam = urlParams.get('supplierName')
  const [drilldownApplied, setDrilldownApplied] = useState(false)

  const drilldownPatch = useMemo(() => {
    const shopParam = urlParams.get('shopNo')
    const supplierParam = urlParams.get('supplierNo')
    const paymentSupplierParam = urlParams.get('paymentSupplierNo')
    if (!shopParam && !supplierParam && !paymentSupplierParam) return null
    return { shopParam, supplierParam, paymentSupplierParam }
  }, [urlParams])

  useEffect(() => {
    if (drilldownApplied) return
    // shopsQuery がロード完了してから適用（Radix Select が option に存在しない値で onValueChange('') を発火するのを防ぐ）
    if (isAdmin && !shopsQuery.data) return
    if (!drilldownPatch) {
      setDrilldownApplied(true)
      return
    }
    const { shopParam, supplierParam, paymentSupplierParam } = drilldownPatch
    const nextSupplier = supplierParam ?? ''
    const filters: PurchasePriceSearchFilters = {
      goodsName: '',
      goodsCode: '',
      supplierNo: nextSupplier,
      scope: 'all',
    }
    if (paymentSupplierParam) filters.paymentSupplierNo = paymentSupplierParam
    setState({
      ...state,
      selectedShopNo: shopParam ?? state.selectedShopNo,
      supplierNo: nextSupplier,
      searchParams: filters,
    })
    setDrilldownApplied(true)
    // state は drilldownApplied ガードで初回のみ参照されるため意図的に依存配列から除外
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [shopsQuery.data, isAdmin, drilldownApplied, drilldownPatch])

  const listQuery = useQuery({
    queryKey: ['purchase-prices', effectiveShopNo, searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      if (effectiveShopNo) params.append('shopNo', effectiveShopNo)
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams?.goodsCode) params.append('goodsCode', searchParams.goodsCode)
      if (searchParams?.supplierNo) params.append('supplierNo', searchParams.supplierNo)
      if (searchParams?.paymentSupplierNo) params.append('paymentSupplierNo', searchParams.paymentSupplierNo)
      if (searchParams?.scope && searchParams.scope !== 'all') params.append('scope', searchParams.scope)
      return api.get<PurchasePriceResponse[]>(`/purchase-prices?${params.toString()}`)
    },
    enabled: searchParams !== null && !!effectiveShopNo,
  })

  const handleSearch = () => {
    setState({ ...state, searchParams: { goodsName, goodsCode, supplierNo, scope } })
  }

  const handleReset = () => {
    setState({ ...defaultState, selectedShopNo: state.selectedShopNo })
  }

  const handleRowClick = (item: PurchasePriceResponse) => {
    setSelectedPrice(item)
    setDialogOpen(true)
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader
        title="仕入価格一覧"
        actions={
          <Button onClick={() => router.push('/purchase-prices/create')}>
            <Plus className="mr-2 h-4 w-4" />
            新規登録
          </Button>
        }
      />

      {paymentSupplierNoParam && searchParams?.paymentSupplierNo && (
        <div className="flex items-center justify-between rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm">
          <div>
            🔍 <b>支払先で絞り込み中</b>
            {supplierNameParam && <>: <span className="font-medium">{decodeURIComponent(supplierNameParam)}</span></>}
            （支払先コード配下の全子仕入先を対象）
          </div>
          <Button size="sm" variant="outline" onClick={() => {
            setState({ ...state, searchParams: { goodsName: '', goodsCode: '', supplierNo: '', scope: 'all' } })
          }}>絞り込み解除</Button>
        </div>
      )}

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
            value={
              searchParams?.paymentSupplierNo && !supplierNo
                ? `__payment__${searchParams.paymentSupplierNo}`
                : supplierNo
            }
            onValueChange={(v) => {
              if (v.startsWith('__payment__')) return
              // 通常の子仕入先を選んだ場合は、親絞り込みをクリアして子で再検索
              const base: PurchasePriceSearchFilters = searchParams ?? {
                goodsName: '', goodsCode: '', supplierNo: '', scope: 'all',
              }
              const { paymentSupplierNo: _drop, ...rest } = base
              void _drop
              setState({
                ...state,
                supplierNo: v,
                searchParams: { ...rest, supplierNo: v },
              })
            }}
            options={[
              ...(searchParams?.paymentSupplierNo && supplierNameParam
                ? [{
                    value: `__payment__${searchParams.paymentSupplierNo}`,
                    label: `${decodeURIComponent(supplierNameParam)}（支払先配下の全仕入先）`,
                  }]
                : []),
              ...(suppliersQuery.data ?? []).map((s) => ({
                value: String(s.supplierNo),
                label: s.supplierName,
              })),
            ]}
            searchPlaceholder="仕入先を検索..."
          />
        </div>
        <div className="space-y-2">
          <Label>価格種別</Label>
          <Select value={scope} onValueChange={(v) => setScope(v as PriceScope)}>
            <SelectTrigger>
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {PRICE_SCOPE_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
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
