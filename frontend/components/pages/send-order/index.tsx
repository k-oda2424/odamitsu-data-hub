'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, useSuppliers, useWarehouses } from '@/hooks/use-master-data'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useRouter } from 'next/navigation'
import { Plus } from 'lucide-react'
import { formatNumber } from '@/lib/utils'
import type { SendOrderDetailResponse } from '@/types/send-order'
import { SEND_ORDER_DETAIL_STATUS_OPTIONS, getSendOrderDetailStatusLabel } from '@/types/send-order'

function StatusBadge({ status }: { status: string | null }) {
  if (!status) return null
  const label = getSendOrderDetailStatusLabel(status)
  const variantMap: Record<string, 'default' | 'outline' | 'secondary' | 'destructive'> = {
    '00': 'default',
    '10': 'outline',
    '20': 'secondary',
    '30': 'secondary',
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

const columns: Column<SendOrderDetailResponse>[] = [
  {
    key: 'sendOrderNo',
    header: '発注番号',
    sortable: true,
    render: (item) => `${item.sendOrderNo}-${item.sendOrderDetailNo}`,
  },
  {
    key: 'sendOrderDateTime',
    header: '発注日時',
    sortable: true,
    render: (item) => formatDateTime(item.sendOrderDateTime),
  },
  { key: 'supplierName', header: '仕入先', sortable: true },
  { key: 'goodsCode', header: '商品コード' },
  { key: 'goodsName', header: '商品名', sortable: true },
  {
    key: 'goodsPrice',
    header: '仕入単価',
    render: (item) => item.goodsPrice != null ? formatNumber(item.goodsPrice) : '',
  },
  {
    key: 'sendOrderNum',
    header: '発注数量',
    render: (item) => formatNumber(item.sendOrderNum),
  },
  {
    key: 'subtotal',
    header: '小計',
    render: (item) => item.subtotal != null ? formatNumber(item.subtotal) : '',
  },
  {
    key: 'arrivePlanDate',
    header: '入荷予定日',
    render: (item) => item.arrivePlanDate ?? '',
  },
  {
    key: 'sendOrderDetailStatus',
    header: 'ステータス',
    render: (item) => <StatusBadge status={item.sendOrderDetailStatus} />,
  },
]

export function SendOrderListPage() {
  const router = useRouter()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const [shopNo, setShopNo] = useState<string>(isAdmin ? '' : String(user?.shopNo ?? ''))
  const [warehouseNo, setWarehouseNo] = useState<string>('')
  const [supplierNo, setSupplierNo] = useState<string>('')
  const [sendOrderDetailStatus, setSendOrderDetailStatus] = useState<string>('')
  const [sendOrderDateTimeFrom, setSendOrderDateTimeFrom] = useState('')
  const [sendOrderDateTimeTo, setSendOrderDateTimeTo] = useState('')
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(null)

  const shopsQuery = useShops(isAdmin)
  const suppliersQuery = useSuppliers(shopNo)
  const warehousesQuery = useWarehouses(undefined)

  const listQuery = useQuery({
    queryKey: ['send-order-details', searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      params.append('shopNo', searchParams?.shopNo ?? shopNo)
      if (searchParams?.warehouseNo) params.append('warehouseNo', searchParams.warehouseNo)
      if (searchParams?.supplierNo) params.append('supplierNo', searchParams.supplierNo)
      if (searchParams?.sendOrderDetailStatus) params.append('sendOrderDetailStatus', searchParams.sendOrderDetailStatus)
      if (searchParams?.sendOrderDateTimeFrom) params.append('sendOrderDateTimeFrom', searchParams.sendOrderDateTimeFrom + ':00')
      if (searchParams?.sendOrderDateTimeTo) params.append('sendOrderDateTimeTo', searchParams.sendOrderDateTimeTo + ':00')
      return api.get<SendOrderDetailResponse[]>(`/send-orders/details?${params.toString()}`)
    },
    enabled: searchParams !== null && !!shopNo,
  })

  const handleSearch = () => {
    setSearchParams({
      shopNo,
      warehouseNo,
      supplierNo,
      sendOrderDetailStatus,
      sendOrderDateTimeFrom,
      sendOrderDateTimeTo,
    })
  }

  const handleReset = () => {
    setWarehouseNo('')
    setSupplierNo('')
    setSendOrderDetailStatus('')
    setSendOrderDateTimeFrom('')
    setSendOrderDateTimeTo('')
    setSearchParams(null)
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader
        title="発注一覧"
        actions={
          <Button onClick={() => router.push('/send-orders/create')}>
            <Plus className="mr-2 h-4 w-4" />
            発注入力
          </Button>
        }
      />

      <SearchForm onSearch={handleSearch} onReset={handleReset}>
        {isAdmin && (
          <div className="space-y-2">
            <Label>ショップ</Label>
            <Select value={shopNo} onValueChange={(v) => { setShopNo(v); setSupplierNo('') }}>
              <SelectTrigger>
                <SelectValue placeholder="選択してください" />
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
          <Label>倉庫</Label>
          <Select value={warehouseNo} onValueChange={setWarehouseNo}>
            <SelectTrigger>
              <SelectValue placeholder="全て" />
            </SelectTrigger>
            <SelectContent>
              {(warehousesQuery.data ?? []).map((w) => (
                <SelectItem key={w.warehouseNo} value={String(w.warehouseNo)}>
                  {w.warehouseName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label>仕入先</Label>
          <SearchableSelect
            value={supplierNo}
            onValueChange={setSupplierNo}
            options={(suppliersQuery.data ?? []).map((s) => ({
              value: String(s.supplierNo),
              label: `${s.supplierCode ?? ''} ${s.supplierName}`.trim(),
            }))}
            searchPlaceholder="仕入先を検索..."
          />
        </div>
        <div className="space-y-2">
          <Label>ステータス</Label>
          <Select value={sendOrderDetailStatus} onValueChange={setSendOrderDetailStatus}>
            <SelectTrigger>
              <SelectValue placeholder="全て" />
            </SelectTrigger>
            <SelectContent>
              {SEND_ORDER_DETAIL_STATUS_OPTIONS.map((opt) => (
                <SelectItem key={opt.value} value={opt.value}>
                  {opt.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="space-y-2">
          <Label>発注日時</Label>
          <div className="flex items-center gap-2">
            <Input
              type="datetime-local"
              value={sendOrderDateTimeFrom}
              onChange={(e) => setSendOrderDateTimeFrom(e.target.value)}
            />
            <span className="text-sm text-muted-foreground">〜</span>
            <Input
              type="datetime-local"
              value={sendOrderDateTimeTo}
              onChange={(e) => setSendOrderDateTimeTo(e.target.value)}
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
