'use client'

import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Button } from '@/components/ui/button'
import { useRouter } from 'next/navigation'
import { Plus } from 'lucide-react'
import { formatNumber } from '@/lib/utils'

interface Stock {
  goodsNo: number
  warehouseNo: number
  goodsName: string
  warehouseName: string
  shopNo: number
  unit1StockNum: number
  unit2StockNum: number
  unit3StockNum: number
  enoughStock: number
  [key: string]: unknown
}

const columns: Column<Stock>[] = [
  { key: 'goodsNo', header: '商品No', sortable: true },
  { key: 'goodsName', header: '商品名', sortable: true },
  { key: 'warehouseName', header: '倉庫名', sortable: true },
  { key: 'unit1StockNum', header: '在庫1', render: (item) => formatNumber(item.unit1StockNum ?? 0) },
  { key: 'unit2StockNum', header: '在庫2', render: (item) => formatNumber(item.unit2StockNum ?? 0) },
  { key: 'unit3StockNum', header: '在庫3', render: (item) => formatNumber(item.unit3StockNum ?? 0) },
  {
    key: 'enoughStock',
    header: '過不足',
    sortable: true,
    render: (item) => {
      const val = item.enoughStock
      if (val == null) return '-'
      const color = val < 0 ? 'text-destructive' : 'text-green-600'
      return <span className={color}>{formatNumber(val)}</span>
    },
  },
]

export function StockListPage() {
  const router = useRouter()
  const stockQuery = useQuery({
    queryKey: ['stock'],
    queryFn: () => api.get<Stock[]>('/stock'),
  })

  if (stockQuery.isLoading) return <LoadingSpinner />
  if (stockQuery.isError) return <ErrorMessage onRetry={() => stockQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="在庫一覧"
        actions={
          <Button onClick={() => router.push('/stock/create')}>
            <Plus className="mr-2 h-4 w-4" />
            在庫登録
          </Button>
        }
      />
      <DataTable data={stockQuery.data ?? []} columns={columns} searchPlaceholder="商品名・倉庫名で検索..." />
    </div>
  )
}
