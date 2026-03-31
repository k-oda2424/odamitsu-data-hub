import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Button } from '@/components/ui/button'
import { useNavigate } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { formatCurrency, formatDate } from '@/lib/utils'

interface Estimate {
  estimateNo: number
  shopNo: number
  partnerNo: number
  partnerName: string
  estimateDate: string
  totalAmount: number
  [key: string]: unknown
}

const columns: Column<Estimate>[] = [
  { key: 'estimateNo', header: '見積No', sortable: true },
  { key: 'partnerName', header: '得意先', sortable: true },
  { key: 'totalAmount', header: '合計金額', render: (item) => formatCurrency(item.totalAmount ?? 0) },
  { key: 'estimateDate', header: '見積日', render: (item) => item.estimateDate ? formatDate(item.estimateDate) : '' },
]

export function EstimateListPage() {
  const navigate = useNavigate()
  const estimateQuery = useQuery({
    queryKey: ['estimates'],
    queryFn: () => api.get<Estimate[]>('/estimates'),
  })

  if (estimateQuery.isLoading) return <LoadingSpinner />
  if (estimateQuery.isError) return <ErrorMessage onRetry={() => estimateQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="見積一覧"
        actions={
          <Button onClick={() => navigate('/estimates/create')}>
            <Plus className="mr-2 h-4 w-4" />
            見積作成
          </Button>
        }
      />
      <DataTable
        data={estimateQuery.data ?? []}
        columns={columns}
        searchPlaceholder="得意先で検索..."
        onRowClick={(item) => navigate(`/estimates/${item.estimateNo}`)}
      />
    </div>
  )
}
