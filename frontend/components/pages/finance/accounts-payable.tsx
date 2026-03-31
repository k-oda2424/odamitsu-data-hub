'use client'

import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { formatCurrency, formatDate } from '@/lib/utils'

interface AccountsPayable {
  shopNo: number
  supplierNo: number
  supplierName: string
  transactionMonth: string
  taxRate: number
  purchaseAmount: number
  taxAmount: number
  totalAmount: number
  [key: string]: unknown
}

const columns: Column<AccountsPayable>[] = [
  { key: 'supplierNo', header: '仕入先No', sortable: true },
  { key: 'supplierName', header: '仕入先名', sortable: true },
  { key: 'transactionMonth', header: '取引月', render: (item) => item.transactionMonth ? formatDate(item.transactionMonth) : '' },
  { key: 'taxRate', header: '税率', render: (item) => `${item.taxRate}%` },
  { key: 'purchaseAmount', header: '仕入額', render: (item) => formatCurrency(item.purchaseAmount ?? 0) },
  { key: 'taxAmount', header: '税額', render: (item) => formatCurrency(item.taxAmount ?? 0) },
  { key: 'totalAmount', header: '合計', render: (item) => formatCurrency(item.totalAmount ?? 0) },
]

export function AccountsPayablePage() {
  const apQuery = useQuery({
    queryKey: ['accounts-payable'],
    queryFn: () => api.get<AccountsPayable[]>('/finance/accounts-payable'),
  })

  if (apQuery.isLoading) return <LoadingSpinner />
  if (apQuery.isError) return <ErrorMessage onRetry={() => apQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader title="買掛金一覧" />
      <DataTable data={apQuery.data ?? []} columns={columns} searchPlaceholder="仕入先で検索..." />
    </div>
  )
}
