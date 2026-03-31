'use client'

import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { formatCurrency, formatDate } from '@/lib/utils'

interface Purchase {
  purchaseNo: number
  shopNo: number
  supplierNo: number
  supplierName: string
  totalAmount: number
  purchaseDateTime: string
  [key: string]: unknown
}

const columns: Column<Purchase>[] = [
  { key: 'purchaseNo', header: '仕入No', sortable: true },
  { key: 'supplierName', header: '仕入先', sortable: true },
  { key: 'totalAmount', header: '合計金額', render: (item) => formatCurrency(item.totalAmount ?? 0) },
  { key: 'purchaseDateTime', header: '仕入日時', render: (item) => item.purchaseDateTime ? formatDate(item.purchaseDateTime) : '' },
]

export function PurchaseListPage() {
  const purchaseQuery = useQuery({
    queryKey: ['purchases'],
    queryFn: () => api.get<Purchase[]>('/purchases'),
  })

  if (purchaseQuery.isLoading) return <LoadingSpinner />
  if (purchaseQuery.isError) return <ErrorMessage onRetry={() => purchaseQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader title="仕入一覧" />
      <DataTable data={purchaseQuery.data ?? []} columns={columns} searchPlaceholder="仕入先で検索..." />
    </div>
  )
}
