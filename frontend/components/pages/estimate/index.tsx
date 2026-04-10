'use client'

import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useSearchParamsStorage } from '@/hooks/use-search-params-storage'
import { useShops, usePartners } from '@/hooks/use-master-data'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchForm } from '@/components/features/common/SearchForm'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Checkbox } from '@/components/ui/checkbox'
import { useRouter } from 'next/navigation'
import { Plus } from 'lucide-react'
import { formatDate } from '@/lib/utils'
import type { EstimateResponse } from '@/types/estimate'
import {
  ESTIMATE_STATUS_OPTIONS,
  getEstimateStatusLabel,
} from '@/types/estimate'

function StatusBadge({ status }: { status: string | null }) {
  if (!status) return null
  const label = getEstimateStatusLabel(status)
  const variant =
    status === '00' || status === '20'
      ? 'default'
      : status === '10' || status === '30'
        ? 'secondary'
        : status === '70'
          ? 'outline'
          : 'destructive'
  return <Badge variant={variant as 'default' | 'secondary' | 'outline' | 'destructive'}>{label}</Badge>
}

const columns: Column<EstimateResponse>[] = [
  { key: 'estimateNo', header: '見積番号', sortable: true },
  {
    key: 'estimateDate',
    header: '見積日',
    sortable: true,
    render: (item) => (item.estimateDate ? formatDate(item.estimateDate) : ''),
  },
  {
    key: 'priceChangeDate',
    header: '価格変更日',
    sortable: true,
    render: (item) =>
      item.priceChangeDate ? formatDate(item.priceChangeDate) : '',
  },
  {
    key: 'partnerName',
    header: '得意先',
    sortable: true,
    render: (item) => {
      const code = item.partnerCode ? `【${item.partnerCode}】` : ''
      return `${code}${item.partnerName ?? ''}`
    },
  },
  {
    key: 'estimateStatus',
    header: '見積ステータス',
    render: (item) => <StatusBadge status={item.estimateStatus} />,
  },
]

const DEFAULT_STATUSES = ['00', '10', '20', '30', '70']

interface EstimateSearchParams {
  estimateNo: string
  partnerNo: string
  goodsName: string
  goodsCode: string
  estimateStatus: string
  estimateDateFrom: string
  estimateDateTo: string
  priceChangeDateFrom: string
  priceChangeDateTo: string
}

interface EstimateSearchState {
  estimateNo: string
  partnerNo: string
  goodsName: string
  goodsCode: string
  selectedStatuses: string[]
  estimateDateFrom: string
  estimateDateTo: string
  priceChangeDateFrom: string
  priceChangeDateTo: string
  selectedShopNo: string
  searchParams: EstimateSearchParams | null
}

export function EstimateListPage() {
  const router = useRouter()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const defaultState: EstimateSearchState = {
    estimateNo: '',
    partnerNo: '',
    goodsName: '',
    goodsCode: '',
    selectedStatuses: [...DEFAULT_STATUSES],
    estimateDateFrom: '',
    estimateDateTo: '',
    priceChangeDateFrom: '',
    priceChangeDateTo: '',
    selectedShopNo: isAdmin ? '' : String(user?.shopNo ?? ''),
    searchParams: null,
  }

  const [state, setState, resetState] = useSearchParamsStorage('estimate-list-search', defaultState)

  const estimateNo = state.estimateNo
  const partnerNo = state.partnerNo
  const goodsName = state.goodsName
  const goodsCode = state.goodsCode
  const selectedStatuses = state.selectedStatuses
  const estimateDateFrom = state.estimateDateFrom
  const estimateDateTo = state.estimateDateTo
  const priceChangeDateFrom = state.priceChangeDateFrom
  const priceChangeDateTo = state.priceChangeDateTo
  const selectedShopNo = state.selectedShopNo
  const searchParams = state.searchParams

  const updateField = <K extends keyof EstimateSearchState>(key: K, value: EstimateSearchState[K]) => {
    setState({ ...state, [key]: value })
  }

  const shopsQuery = useShops(isAdmin)
  const effectiveShopNo = isAdmin ? selectedShopNo : String(user?.shopNo ?? '')
  const partnersQuery = usePartners(effectiveShopNo)

  const listQuery = useQuery({
    queryKey: ['estimates', effectiveShopNo, searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      if (effectiveShopNo) params.append('shopNo', effectiveShopNo)
      if (searchParams?.estimateNo) params.append('estimateNo', searchParams.estimateNo)
      if (searchParams?.partnerNo) params.append('partnerNo', searchParams.partnerNo)
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams?.goodsCode) params.append('goodsCode', searchParams.goodsCode)
      if (searchParams?.estimateDateFrom) params.append('estimateDateFrom', searchParams.estimateDateFrom)
      if (searchParams?.estimateDateTo) params.append('estimateDateTo', searchParams.estimateDateTo)
      if (searchParams?.priceChangeDateFrom) params.append('priceChangeDateFrom', searchParams.priceChangeDateFrom)
      if (searchParams?.priceChangeDateTo) params.append('priceChangeDateTo', searchParams.priceChangeDateTo)
      const statuses = searchParams?.estimateStatus
      if (statuses) {
        statuses.split(',').forEach((s) => params.append('estimateStatus', s))
      }
      return api.get<EstimateResponse[]>(`/estimates?${params.toString()}`)
    },
    enabled: searchParams !== null && !!effectiveShopNo,
  })

  const handleSearch = () => {
    updateField('searchParams', {
      estimateNo,
      partnerNo,
      goodsName,
      goodsCode,
      estimateStatus: selectedStatuses.join(','),
      estimateDateFrom,
      estimateDateTo,
      priceChangeDateFrom,
      priceChangeDateTo,
    })
  }

  const handleReset = () => {
    resetState()
  }

  const toggleStatus = (value: string) => {
    const next = selectedStatuses.includes(value)
      ? selectedStatuses.filter((s) => s !== value)
      : [...selectedStatuses, value]
    updateField('selectedStatuses', next)
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader
        title="見積一覧"
        actions={
          <Button onClick={() => router.push('/estimates/create')}>
            <Plus className="mr-2 h-4 w-4" />
            見積作成
          </Button>
        }
      />

      <SearchForm onSearch={handleSearch} onReset={handleReset}>
        {isAdmin && (
          <div className="space-y-2">
            <Label>店舗</Label>
            <Select value={selectedShopNo} onValueChange={(v: string) => updateField('selectedShopNo', v)}>
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
          <Label>得意先</Label>
          <SearchableSelect
            value={partnerNo}
            onValueChange={(v) => updateField('partnerNo', v)}
            options={(partnersQuery.data ?? []).map((p) => ({
              value: String(p.partnerNo),
              label: `${p.partnerCode} ${p.partnerName}`,
            }))}
            placeholder="得意先を選択"
            searchPlaceholder="得意先を検索..."
          />
        </div>
        <div className="space-y-2">
          <Label>商品コード</Label>
          <Input
            placeholder="商品コードを入力"
            value={goodsCode}
            onChange={(e) => updateField('goodsCode', e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>商品名</Label>
          <Input
            placeholder="商品名を入力"
            value={goodsName}
            onChange={(e) => updateField('goodsName', e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>見積番号</Label>
          <Input
            type="number"
            placeholder="見積番号を入力"
            value={estimateNo}
            onChange={(e) => updateField('estimateNo', e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>見積日</Label>
          <div className="flex items-center gap-2">
            <Input
              type="date"
              value={estimateDateFrom}
              onChange={(e) => updateField('estimateDateFrom', e.target.value)}
            />
            <span className="text-sm text-muted-foreground">〜</span>
            <Input
              type="date"
              value={estimateDateTo}
              onChange={(e) => updateField('estimateDateTo', e.target.value)}
            />
          </div>
        </div>
        <div className="space-y-2">
          <Label>価格変更日</Label>
          <div className="flex items-center gap-2">
            <Input
              type="date"
              value={priceChangeDateFrom}
              onChange={(e) => updateField('priceChangeDateFrom', e.target.value)}
            />
            <span className="text-sm text-muted-foreground">〜</span>
            <Input
              type="date"
              value={priceChangeDateTo}
              onChange={(e) => updateField('priceChangeDateTo', e.target.value)}
            />
          </div>
        </div>
        <div className="col-span-full space-y-2">
          <Label>見積ステータス</Label>
          <div className="flex flex-wrap gap-3">
            {ESTIMATE_STATUS_OPTIONS.map((opt) => (
              <label key={opt.value} className="flex items-center gap-1.5 text-sm">
                <Checkbox
                  checked={selectedStatuses.includes(opt.value)}
                  onCheckedChange={() => toggleStatus(opt.value)}
                />
                {opt.label}
              </label>
            ))}
          </div>
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
          defaultSortKey="estimateDate"
          defaultSortDir="desc"
          onRowClick={(item) => router.push(`/estimates/${item.estimateNo}`)}
        />
      )}
    </div>
  )
}
