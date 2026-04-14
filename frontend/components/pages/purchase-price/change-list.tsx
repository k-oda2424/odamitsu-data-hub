'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useMutation, useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ApiError, api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchForm } from '@/components/features/common/SearchForm'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Plus, Play } from 'lucide-react'
import type { PurchasePriceChangePlanResponse, PriceScope } from '@/types/purchase-price'
import { CHANGE_REASON_OPTIONS, getChangeReasonLabel, PRICE_SCOPE_OPTIONS, isPartnerSpecificPrice } from '@/types/purchase-price'
import { emptyPage, type Paginated } from '@/types/paginated'

const PAGE_SIZE = 50

const columns: Column<PurchasePriceChangePlanResponse>[] = [
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
    key: 'beforePrice',
    header: '変更前価格',
    render: (item) => item.beforePrice?.toLocaleString() ?? '',
  },
  {
    key: 'afterPrice',
    header: '変更後価格',
    render: (item) => item.afterPrice?.toLocaleString() ?? '',
  },
  { key: 'changePlanDate', header: '変更予定日', sortable: true },
  {
    key: 'changeReason',
    header: '変更理由',
    render: (item) => getChangeReasonLabel(item.changeReason),
  },
  {
    key: 'purchasePriceReflect',
    header: '反映',
    render: (item) =>
      item.purchasePriceReflect ? (
        <Badge variant="secondary">反映済</Badge>
      ) : (
        <Badge variant="outline">未反映</Badge>
      ),
  },
]

export function PurchasePriceChangeListPage() {
  const router = useRouter()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const [supplierCode, setSupplierCode] = useState('')
  const [goodsCode, setGoodsCode] = useState('')
  const [janCode, setJanCode] = useState('')
  const [changeReason, setChangeReason] = useState('')
  const [changePlanDateFrom, setChangePlanDateFrom] = useState('')
  const [changePlanDateTo, setChangePlanDateTo] = useState('')
  const [scope, setScope] = useState<PriceScope>('all')
  const [selectedShopNo, setSelectedShopNo] = useState<string>(
    isAdmin ? '' : String(user?.shopNo ?? '')
  )
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(null)
  const [page, setPage] = useState(0)

  const shopsQuery = useShops(isAdmin)
  const effectiveShopNo = isAdmin ? selectedShopNo : String(user?.shopNo ?? '')

  const listQuery = useQuery({
    queryKey: ['purchase-price-changes', effectiveShopNo, searchParams, page],
    queryFn: () => {
      const params = new URLSearchParams()
      if (effectiveShopNo) params.append('shopNo', effectiveShopNo)
      if (searchParams?.supplierCode) params.append('supplierCode', searchParams.supplierCode)
      if (searchParams?.goodsCode) params.append('goodsCode', searchParams.goodsCode)
      if (searchParams?.janCode) params.append('janCode', searchParams.janCode)
      if (searchParams?.changeReason) params.append('changeReason', searchParams.changeReason)
      if (searchParams?.changePlanDateFrom) params.append('changePlanDateFrom', searchParams.changePlanDateFrom)
      if (searchParams?.changePlanDateTo) params.append('changePlanDateTo', searchParams.changePlanDateTo)
      if (searchParams?.scope && searchParams.scope !== 'all') params.append('scope', searchParams.scope)
      params.append('page', String(page))
      params.append('size', String(PAGE_SIZE))
      return api.get<Paginated<PurchasePriceChangePlanResponse>>(`/purchase-price-changes?${params.toString()}`)
    },
    enabled: searchParams !== null && !!effectiveShopNo,
  })

  const handleSearch = () => {
    setPage(0)
    setSearchParams({ supplierCode, goodsCode, janCode, changeReason, changePlanDateFrom, changePlanDateTo, scope })
  }

  const runEstimateBatchMutation = useMutation({
    mutationFn: async () =>
      api.post<{ message: string }>('/batch/execute/partnerPriceChangePlanCreate'),
    onSuccess: (res) => {
      toast.success(res?.message ?? '得意先価格変更予定作成・見積自動生成バッチを起動しました')
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 429) {
        toast.error('他のバッチが実行中です。しばらく待ってから再実行してください')
      } else {
        toast.error(e.message)
      }
    },
  })

  const handleRunEstimateBatch = () => {
    if (runEstimateBatchMutation.isPending) return
    if (!window.confirm('仕入価格変更予定を元に、得意先価格変更予定と見積を自動生成します。実行しますか？')) return
    runEstimateBatchMutation.mutate()
  }

  const handleReset = () => {
    setSupplierCode('')
    setGoodsCode('')
    setJanCode('')
    setChangeReason('')
    setChangePlanDateFrom('')
    setChangePlanDateTo('')
    setScope('all')
    setSearchParams(null)
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader
        title="仕入価格変更一覧"
        actions={
          <div className="flex gap-2">
            <Button
              variant="outline"
              onClick={handleRunEstimateBatch}
              disabled={runEstimateBatchMutation.isPending}
            >
              <Play className="mr-2 h-4 w-4" />
              {runEstimateBatchMutation.isPending ? '実行中...' : '得意先見積自動生成'}
            </Button>
            <Button onClick={() => router.push('/purchase-prices/changes/bulk-input')}>
              <Plus className="mr-2 h-4 w-4" />
              一括入力
            </Button>
          </div>
        }
      />

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
          <Label>仕入先コード</Label>
          <Input
            placeholder="仕入先コードを入力"
            value={supplierCode}
            onChange={(e) => setSupplierCode(e.target.value)}
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
          <Label>JANコード</Label>
          <Input
            placeholder="JANコードを入力"
            value={janCode}
            onChange={(e) => setJanCode(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>変更理由</Label>
          <Select value={changeReason} onValueChange={setChangeReason}>
            <SelectTrigger>
              <SelectValue placeholder="選択してください" />
            </SelectTrigger>
            <SelectContent>
              {CHANGE_REASON_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label>変更予定日（From）</Label>
          <Input
            type="date"
            value={changePlanDateFrom}
            onChange={(e) => setChangePlanDateFrom(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>変更予定日（To）</Label>
          <Input
            type="date"
            value={changePlanDateTo}
            onChange={(e) => setChangePlanDateTo(e.target.value)}
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
      ) : (() => {
        const p = listQuery.data ?? emptyPage<PurchasePriceChangePlanResponse>(PAGE_SIZE)
        return (
          <DataTable
            data={p.content}
            columns={columns}
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
