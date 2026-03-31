import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { formatNumber } from '@/lib/utils'

interface StockLog {
  stockLogNo: number
  goodsNo: number
  goodsName: string
  warehouseNo: number
  beforeNum: number
  afterNum: number
  changeNum: number
  reason: string
  [key: string]: unknown
}

const columns: Column<StockLog>[] = [
  { key: 'stockLogNo', header: 'No', sortable: true },
  { key: 'goodsName', header: '商品名', sortable: true },
  { key: 'beforeNum', header: '変更前', render: (item) => formatNumber(item.beforeNum ?? 0) },
  { key: 'afterNum', header: '変更後', render: (item) => formatNumber(item.afterNum ?? 0) },
  { key: 'changeNum', header: '変動数', render: (item) => formatNumber(item.changeNum ?? 0) },
  { key: 'reason', header: '理由' },
]

export function StockLogPage() {
  const logQuery = useQuery({
    queryKey: ['stock-log'],
    queryFn: () => api.get<StockLog[]>('/stock/log'),
  })

  if (logQuery.isLoading) return <LoadingSpinner />
  if (logQuery.isError) return <ErrorMessage onRetry={() => logQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader title="在庫変動ログ" />
      <DataTable data={logQuery.data ?? []} columns={columns} searchPlaceholder="商品名で検索..." />
    </div>
  )
}
