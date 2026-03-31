import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { formatCurrency } from '@/lib/utils'

interface Invoice {
  invoiceId: number
  partnerCode: string
  partnerName: string
  closingDate: string
  previousBalance: number
  netSalesIncludingTax: number
  currentBillingAmount: number
  shopNo: number
  [key: string]: unknown
}

const columns: Column<Invoice>[] = [
  { key: 'invoiceId', header: 'ID', sortable: true },
  { key: 'partnerCode', header: '得意先コード', sortable: true },
  { key: 'partnerName', header: '得意先名', sortable: true },
  { key: 'closingDate', header: '締日' },
  { key: 'previousBalance', header: '前回残高', render: (item) => formatCurrency(item.previousBalance ?? 0) },
  { key: 'netSalesIncludingTax', header: '税込売上', render: (item) => formatCurrency(item.netSalesIncludingTax ?? 0) },
  { key: 'currentBillingAmount', header: '今回請求額', render: (item) => formatCurrency(item.currentBillingAmount ?? 0) },
]

export function InvoiceListPage() {
  const invoiceQuery = useQuery({
    queryKey: ['invoices'],
    queryFn: () => api.get<Invoice[]>('/finance/invoices'),
  })

  if (invoiceQuery.isLoading) return <LoadingSpinner />
  if (invoiceQuery.isError) return <ErrorMessage onRetry={() => invoiceQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader title="請求書一覧" />
      <DataTable data={invoiceQuery.data ?? []} columns={columns} searchPlaceholder="得意先で検索..." />
    </div>
  )
}
