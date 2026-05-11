'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, usePartners } from '@/hooks/use-master-data'
import { useSearchParamsStorage } from '@/hooks/use-search-params-storage'
import type { PartnerGroup } from '@/types/partner-group'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchForm } from '@/components/features/common/SearchForm'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
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
import { formatDate, formatNumber } from '@/lib/utils'
import { emptyPage, type Paginated } from '@/types/paginated'
import type { OrderDetailResponse } from '@/types/order'
import {
  ORDER_DETAIL_STATUS_OPTIONS,
  getOrderDetailStatusLabel,
} from '@/types/order'

function StatusBadge({ status }: { status: string | null }) {
  if (!status) return null
  const label = getOrderDetailStatusLabel(status)
  const variantMap: Record<string, 'default' | 'outline' | 'secondary' | 'destructive'> = {
    '00': 'default',
    '01': 'outline',
    '10': 'secondary',
    '20': 'secondary',
    '90': 'destructive',
    '99': 'destructive',
  }
  return <Badge variant={variantMap[status] ?? 'default'}>{label}</Badge>
}

function formatDateTime(dt: string | null): string {
  if (!dt) return ''
  const d = new Date(dt)
  return d.toLocaleString('ja-JP', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  })
}

function getThreeMonthsAgo(): string {
  const d = new Date()
  d.setMonth(d.getMonth() - 3)
  return d.toISOString().slice(0, 16)
}

const columns: Column<OrderDetailResponse>[] = [
  {
    key: 'orderNo',
    header: '注文番号',
    sortable: true,
    render: (item) => `${item.orderNo}-${item.orderDetailNo}`,
  },
  {
    key: 'orderDateTime',
    header: '受注日時',
    sortable: true,
    render: (item) => formatDateTime(item.orderDateTime),
  },
  {
    key: 'slipDate',
    header: '伝票日付',
    sortable: true,
    render: (item) => (item.slipDate ? formatDate(item.slipDate) : ''),
  },
  {
    key: 'companyName',
    header: '得意先',
    sortable: true,
  },
  { key: 'slipNo', header: '伝票番号' },
  { key: 'goodsCode', header: '商品コード' },
  { key: 'goodsName', header: '商品名', sortable: true },
  {
    key: 'goodsPrice',
    header: '単価',
    render: (item) => (item.goodsPrice != null ? formatNumber(item.goodsPrice) : ''),
  },
  {
    key: 'orderNum',
    header: '数量',
    render: (item) => (item.orderNum != null ? formatNumber(item.orderNum) : ''),
  },
  {
    key: 'subtotal',
    header: '小計',
    render: (item) => (item.subtotal != null ? formatNumber(item.subtotal) : ''),
  },
  {
    key: 'orderDetailStatus',
    header: 'ステータス',
    render: (item) => <StatusBadge status={item.orderDetailStatus} />,
  },
]

interface OrderSearchState {
  partnerNo: string
  partnerGroupId: string
  slipNo: string
  goodsName: string
  goodsCode: string
  orderDetailStatus: string
  orderDateTimeFrom: string
  orderDateTimeTo: string
  slipDateFrom: string
  slipDateTo: string
  selectedShopNo: string
  searchParams: Record<string, string> | null
}

const MAX_FETCH_SIZE = 5000

export function OrderListPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const defaultState: OrderSearchState = {
    partnerNo: '',
    partnerGroupId: '',
    slipNo: '',
    goodsName: '',
    goodsCode: '',
    orderDetailStatus: '',
    orderDateTimeFrom: getThreeMonthsAgo(),
    orderDateTimeTo: '',
    slipDateFrom: '',
    slipDateTo: '',
    selectedShopNo: isAdmin ? '' : String(user?.shopNo ?? ''),
    searchParams: null,
  }

  const [state, setState] = useSearchParamsStorage('order-list-search', defaultState)
  const {
    partnerNo, partnerGroupId, slipNo, goodsName, goodsCode, orderDetailStatus,
    orderDateTimeFrom, orderDateTimeTo, slipDateFrom, slipDateTo,
    selectedShopNo, searchParams,
  } = state
  const updateField = <K extends keyof OrderSearchState>(key: K, value: OrderSearchState[K]) => {
    setState({ ...state, [key]: value })
  }
  const setPartnerNo = (v: string) => updateField('partnerNo', v)
  const setPartnerGroupId = (v: string) => updateField('partnerGroupId', v)
  const setSlipNo = (v: string) => updateField('slipNo', v)
  const setGoodsName = (v: string) => updateField('goodsName', v)
  const setGoodsCode = (v: string) => updateField('goodsCode', v)
  const setOrderDetailStatus = (v: string) => updateField('orderDetailStatus', v)
  const setOrderDateTimeFrom = (v: string) => updateField('orderDateTimeFrom', v)
  const setOrderDateTimeTo = (v: string) => updateField('orderDateTimeTo', v)
  const setSlipDateFrom = (v: string) => updateField('slipDateFrom', v)
  const setSlipDateTo = (v: string) => updateField('slipDateTo', v)
  const setSelectedShopNo = (v: string) => updateField('selectedShopNo', v)
  const setSearchParams = (v: Record<string, string> | null) => updateField('searchParams', v)

  const shopsQuery = useShops(isAdmin)
  const effectiveShopNo = isAdmin ? selectedShopNo : String(user?.shopNo ?? '')
  const partnersQuery = usePartners(effectiveShopNo)

  const groupsQuery = useQuery({
    queryKey: ['partner-groups', effectiveShopNo],
    queryFn: () =>
      api.get<PartnerGroup[]>(`/finance/partner-groups?shopNo=${effectiveShopNo}`),
    enabled: !!effectiveShopNo,
  })

  const listQuery = useQuery({
    queryKey: ['order-details', effectiveShopNo, searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      params.append('shopNo', effectiveShopNo)
      if (searchParams?.partnerNo) params.append('partnerNo', searchParams.partnerNo)
      if (searchParams?.partnerGroupId) params.append('partnerGroupId', searchParams.partnerGroupId)
      if (searchParams?.slipNo) params.append('slipNo', searchParams.slipNo)
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams?.goodsCode) params.append('goodsCode', searchParams.goodsCode)
      if (searchParams?.orderDetailStatus) params.append('orderDetailStatus', searchParams.orderDetailStatus)
      if (searchParams?.orderDateTimeFrom) params.append('orderDateTimeFrom', searchParams.orderDateTimeFrom + ':00')
      if (searchParams?.orderDateTimeTo) params.append('orderDateTimeTo', searchParams.orderDateTimeTo + ':00')
      if (searchParams?.slipDateFrom) params.append('slipDateFrom', searchParams.slipDateFrom)
      if (searchParams?.slipDateTo) params.append('slipDateTo', searchParams.slipDateTo)
      params.append('page', '0')
      params.append('size', String(MAX_FETCH_SIZE))
      return api.get<Paginated<OrderDetailResponse>>(`/orders/details?${params.toString()}`)
    },
    enabled: searchParams !== null && !!effectiveShopNo,
  })

  const handleSearch = () => {
    setState({
      ...state,
      searchParams: {
        partnerNo,
        partnerGroupId,
        slipNo,
        goodsName,
        goodsCode,
        orderDetailStatus,
        orderDateTimeFrom,
        orderDateTimeTo,
        slipDateFrom,
        slipDateTo,
      },
    })
  }

  const handleReset = () => {
    setState({
      ...defaultState,
      selectedShopNo: state.selectedShopNo,
    })
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader title="受注一覧" />

      <SearchForm onSearch={handleSearch} onReset={handleReset}>
        {isAdmin && (
          <div className="space-y-2">
            <Label>店舗</Label>
            <Select value={selectedShopNo} onValueChange={(v) => setState({ ...state, selectedShopNo: v, partnerNo: '', partnerGroupId: '', searchParams: null })}>
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
            onValueChange={setPartnerNo}
            options={(partnersQuery.data ?? []).map((p) => ({
              value: String(p.partnerNo),
              label: `${p.partnerCode} ${p.partnerName}`,
            }))}
            searchPlaceholder="得意先を検索..."
          />
        </div>
        <div className="space-y-2">
          <Label>得意先グループ</Label>
          <SearchableSelect
            value={partnerGroupId}
            onValueChange={setPartnerGroupId}
            options={(groupsQuery.data ?? []).map((g) => ({
              value: String(g.partnerGroupId),
              label: `${g.groupName}（${g.partnerCodes.length}件）`,
            }))}
            searchPlaceholder="グループ名を検索..."
            placeholder={effectiveShopNo ? 'グループを選択（任意）' : '店舗を選択してください'}
            emptyMessage="グループが見つかりません"
            disabled={!effectiveShopNo}
          />
          <p className="text-xs text-muted-foreground">
            請求書一覧で登録した得意先グループで絞り込みます。手入力得意先は対象外です。
          </p>
        </div>
        <div className="space-y-2">
          <Label>伝票番号</Label>
          <Input
            placeholder="伝票番号を入力"
            value={slipNo}
            onChange={(e) => setSlipNo(e.target.value)}
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
          <Label>商品コード</Label>
          <Input
            placeholder="商品コードを入力"
            value={goodsCode}
            onChange={(e) => setGoodsCode(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>注文ステータス</Label>
          <Select value={orderDetailStatus} onValueChange={setOrderDetailStatus}>
            <SelectTrigger>
              <SelectValue placeholder="全て" />
            </SelectTrigger>
            <SelectContent>
              {ORDER_DETAIL_STATUS_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label>注文日時</Label>
          <div className="flex items-center gap-2">
            <Input
              type="datetime-local"
              value={orderDateTimeFrom}
              onChange={(e) => setOrderDateTimeFrom(e.target.value)}
            />
            <span className="text-sm text-muted-foreground">〜</span>
            <Input
              type="datetime-local"
              value={orderDateTimeTo}
              onChange={(e) => setOrderDateTimeTo(e.target.value)}
            />
          </div>
        </div>
        <div className="space-y-2">
          <Label>伝票日付</Label>
          <div className="flex items-center gap-2">
            <Input
              type="date"
              value={slipDateFrom}
              onChange={(e) => setSlipDateFrom(e.target.value)}
            />
            <span className="text-sm text-muted-foreground">〜</span>
            <Input
              type="date"
              value={slipDateTo}
              onChange={(e) => setSlipDateTo(e.target.value)}
            />
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
      ) : (() => {
        const page = listQuery.data ?? emptyPage<OrderDetailResponse>(MAX_FETCH_SIZE)
        const isOverflow = page.totalElements > page.content.length
        return (
          <div className="space-y-3">
            {isOverflow && (
              <div className="rounded-lg border border-amber-300 bg-amber-50 px-4 py-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200">
                該当 {page.totalElements.toLocaleString()} 件のうち {page.content.length.toLocaleString()} 件のみ表示しています。期間や条件を絞ってください。
              </div>
            )}
            <DataTable data={page.content} columns={columns} />
          </div>
        )
      })()}
    </div>
  )
}
