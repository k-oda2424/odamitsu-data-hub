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
import type { ComparisonResponse } from '@/types/estimate-comparison'
import {
  ESTIMATE_STATUS_OPTIONS,
  getEstimateStatusLabel,
} from '@/types/estimate'

function StatusBadge({ status }: { status: string | null }) {
  if (!status) return null
  const label = getEstimateStatusLabel(status)
  const variant: 'default' | 'secondary' | 'outline' | 'destructive' =
    status === '00' || status === '20'
      ? 'default'
      : status === '10' || status === '30'
        ? 'secondary'
        : status === '70'
          ? 'outline'
          : 'destructive'
  return <Badge variant={variant}>{label}</Badge>
}

const columns: Column<ComparisonResponse>[] = [
  { key: 'comparisonNo', header: '比較見積番号', sortable: true },
  {
    key: 'comparisonDate',
    header: '作成日',
    sortable: true,
    render: (item) => (item.comparisonDate ? formatDate(item.comparisonDate) : ''),
  },
  {
    key: 'partnerName',
    header: '得意先',
    sortable: true,
    render: (item) => item.partnerName ?? '',
  },
  {
    key: 'title',
    header: 'タイトル',
    sortable: true,
    render: (item) => item.title ?? '',
  },
  {
    key: 'groupCount',
    header: 'グループ数',
    sortable: true,
    render: (item) => item.groupCount,
  },
  {
    key: 'comparisonStatus',
    header: 'ステータス',
    render: (item) => <StatusBadge status={item.comparisonStatus} />,
  },
]

const DEFAULT_STATUSES = ['00', '20']

interface ComparisonSearchParams {
  partnerNo: string
  comparisonStatus: string
  comparisonDateFrom: string
  comparisonDateTo: string
  title: string
}

interface ComparisonSearchState {
  partnerNo: string
  selectedStatuses: string[]
  comparisonDateFrom: string
  comparisonDateTo: string
  title: string
  selectedShopNo: string
  searchParams: ComparisonSearchParams | null
}

export function ComparisonListPage() {
  const router = useRouter()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const defaultState: ComparisonSearchState = {
    partnerNo: '',
    selectedStatuses: [...DEFAULT_STATUSES],
    comparisonDateFrom: '',
    comparisonDateTo: '',
    title: '',
    selectedShopNo: isAdmin ? '' : String(user?.shopNo ?? ''),
    searchParams: null,
  }

  const [state, setState, resetState] = useSearchParamsStorage('comparison-list-search', defaultState)

  const {
    partnerNo,
    selectedStatuses,
    comparisonDateFrom,
    comparisonDateTo,
    title,
    selectedShopNo,
    searchParams,
  } = state

  const updateField = <K extends keyof ComparisonSearchState>(key: K, value: ComparisonSearchState[K]) => {
    setState({ ...state, [key]: value })
  }

  const shopsQuery = useShops(isAdmin)
  const effectiveShopNo = isAdmin ? selectedShopNo : String(user?.shopNo ?? '')
  const partnersQuery = usePartners(effectiveShopNo)

  const listQuery = useQuery({
    queryKey: ['estimate-comparisons', effectiveShopNo, searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      if (effectiveShopNo) params.append('shopNo', effectiveShopNo)
      if (searchParams?.partnerNo) params.append('partnerNo', searchParams.partnerNo)
      if (searchParams?.comparisonDateFrom) params.append('comparisonDateFrom', searchParams.comparisonDateFrom)
      if (searchParams?.comparisonDateTo) params.append('comparisonDateTo', searchParams.comparisonDateTo)
      if (searchParams?.title) params.append('title', searchParams.title)
      const statuses = searchParams?.comparisonStatus
      if (statuses) {
        statuses.split(',').forEach((s) => params.append('comparisonStatus', s))
      }
      return api.get<ComparisonResponse[]>(`/estimate-comparisons?${params.toString()}`)
    },
    enabled: searchParams !== null && !!effectiveShopNo,
  })

  const handleSearch = () => {
    updateField('searchParams', {
      partnerNo,
      comparisonStatus: selectedStatuses.join(','),
      comparisonDateFrom,
      comparisonDateTo,
      title,
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
        title="比較見積一覧"
        actions={
          <Button onClick={() => router.push('/estimate-comparisons/create')}>
            <Plus className="mr-2 h-4 w-4" />
            新規作成
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
          <Label>タイトル</Label>
          <Input
            placeholder="タイトルを入力"
            value={title}
            onChange={(e) => updateField('title', e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>作成日</Label>
          <div className="flex items-center gap-2">
            <Input
              type="date"
              value={comparisonDateFrom}
              onChange={(e) => updateField('comparisonDateFrom', e.target.value)}
            />
            <span className="text-sm text-muted-foreground">〜</span>
            <Input
              type="date"
              value={comparisonDateTo}
              onChange={(e) => updateField('comparisonDateTo', e.target.value)}
            />
          </div>
        </div>
        <div className="col-span-full space-y-2">
          <Label>ステータス</Label>
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
          defaultSortKey="comparisonDate"
          defaultSortDir="desc"
          onRowClick={(item) => router.push(`/estimate-comparisons/${item.comparisonNo}`)}
        />
      )}
    </div>
  )
}
