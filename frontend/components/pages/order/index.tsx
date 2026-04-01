'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, usePartners } from '@/hooks/use-master-data'
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

function getOneMonthAgo(): string {
  const d = new Date()
  d.setMonth(d.getMonth() - 1)
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

export function OrderListPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const [partnerNo, setPartnerNo] = useState<string>('')
  const [slipNo, setSlipNo] = useState('')
  const [goodsName, setGoodsName] = useState('')
  const [goodsCode, setGoodsCode] = useState('')
  const [orderDetailStatus, setOrderDetailStatus] = useState<string>('')
  const [orderDateTimeFrom, setOrderDateTimeFrom] = useState(getOneMonthAgo())
  const [orderDateTimeTo, setOrderDateTimeTo] = useState('')
  const [slipDateFrom, setSlipDateFrom] = useState('')
  const [slipDateTo, setSlipDateTo] = useState('')
  const [selectedShopNo, setSelectedShopNo] = useState<string>(
    isAdmin ? '' : String(user?.shopNo ?? ''),
  )
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(null)

  const shopsQuery = useShops(isAdmin)
  const effectiveShopNo = isAdmin ? selectedShopNo : String(user?.shopNo ?? '')
  const partnersQuery = usePartners(effectiveShopNo)

  const listQuery = useQuery({
    queryKey: ['order-details', effectiveShopNo, searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      params.append('shopNo', effectiveShopNo)
      if (searchParams?.partnerNo) params.append('partnerNo', searchParams.partnerNo)
      if (searchParams?.slipNo) params.append('slipNo', searchParams.slipNo)
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams?.goodsCode) params.append('goodsCode', searchParams.goodsCode)
      if (searchParams?.orderDetailStatus) params.append('orderDetailStatus', searchParams.orderDetailStatus)
      if (searchParams?.orderDateTimeFrom) params.append('orderDateTimeFrom', searchParams.orderDateTimeFrom + ':00')
      if (searchParams?.orderDateTimeTo) params.append('orderDateTimeTo', searchParams.orderDateTimeTo + ':00')
      if (searchParams?.slipDateFrom) params.append('slipDateFrom', searchParams.slipDateFrom)
      if (searchParams?.slipDateTo) params.append('slipDateTo', searchParams.slipDateTo)
      return api.get<OrderDetailResponse[]>(`/orders/details?${params.toString()}`)
    },
    enabled: searchParams !== null && !!effectiveShopNo,
  })

  const handleSearch = () => {
    setSearchParams({
      partnerNo,
      slipNo,
      goodsName,
      goodsCode,
      orderDetailStatus,
      orderDateTimeFrom,
      orderDateTimeTo,
      slipDateFrom,
      slipDateTo,
    })
  }

  const handleReset = () => {
    setPartnerNo('')
    setSlipNo('')
    setGoodsName('')
    setGoodsCode('')
    setOrderDetailStatus('')
    setOrderDateTimeFrom(getOneMonthAgo())
    setOrderDateTimeTo('')
    setSlipDateFrom('')
    setSlipDateTo('')
    setSearchParams(null)
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader title="受注一覧" />

      <SearchForm onSearch={handleSearch} onReset={handleReset}>
        {isAdmin && (
          <div className="space-y-2">
            <Label>店舗</Label>
            <Select value={selectedShopNo} onValueChange={(v) => { setSelectedShopNo(v); setPartnerNo('') }}>
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
      ) : (
        <DataTable
          data={listQuery.data ?? []}
          columns={columns}
          searchPlaceholder="テーブル内を検索..."
        />
      )}
    </div>
  )
}
