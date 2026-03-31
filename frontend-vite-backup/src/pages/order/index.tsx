import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { formatCurrency, formatDate } from '@/lib/utils'

interface Order {
  orderNo: number
  shopNo: number
  partnerNo: number
  destinationNo: number
  orderDateTime: string
  totalAmount: number
  [key: string]: unknown
}

const columns: Column<Order>[] = [
  { key: 'orderNo', header: '受注No', sortable: true },
  { key: 'partnerNo', header: '得意先No', sortable: true },
  { key: 'totalAmount', header: '合計金額', render: (item) => formatCurrency(item.totalAmount ?? 0) },
  { key: 'orderDateTime', header: '受注日時', render: (item) => item.orderDateTime ? formatDate(item.orderDateTime) : '' },
]

export function OrderListPage() {
  const orderQuery = useQuery({
    queryKey: ['orders'],
    queryFn: () => api.get<Order[]>('/orders'),
  })

  if (orderQuery.isLoading) return <LoadingSpinner />
  if (orderQuery.isError) return <ErrorMessage onRetry={() => orderQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader title="受注一覧" />
      <DataTable data={orderQuery.data ?? []} columns={columns} searchPlaceholder="得意先で検索..." />
    </div>
  )
}
