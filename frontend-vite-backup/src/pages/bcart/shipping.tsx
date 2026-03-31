import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { toast } from 'sonner'

interface BCartShipping {
  orderId: number
  compName: string
  status: string
  shippingNumber: string
  [key: string]: unknown
}

const statusMap: Record<string, { label: string; variant: 'default' | 'secondary' | 'destructive' | 'outline' }> = {
  UNSHIPPED: { label: '未発送', variant: 'destructive' },
  SHIPPING_ORDERED: { label: '発送指示', variant: 'default' },
  SHIPPED: { label: '発送済', variant: 'secondary' },
  EXCLUDED: { label: '対象外', variant: 'outline' },
}

export function BCartShippingPage() {
  const queryClient = useQueryClient()

  const shippingQuery = useQuery({
    queryKey: ['bcart-shipping'],
    queryFn: () => api.get<BCartShipping[]>('/bcart/shipping'),
  })

  const updateStatus = useMutation({
    mutationFn: ({ orderId, status }: { orderId: number; status: string }) =>
      api.put(`/bcart/shipping/${orderId}/status?status=${status}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['bcart-shipping'] })
      toast.success('ステータスを更新しました')
    },
    onError: () => toast.error('更新に失敗しました'),
  })

  const columns: Column<BCartShipping>[] = [
    { key: 'orderId', header: '受注ID', sortable: true },
    { key: 'compName', header: '会社名', sortable: true },
    {
      key: 'status',
      header: 'ステータス',
      render: (item) => {
        const s = statusMap[item.status] ?? { label: item.status, variant: 'outline' as const }
        return <Badge variant={s.variant}>{s.label}</Badge>
      },
    },
    {
      key: 'actions',
      header: '操作',
      render: (item) => (
        <div className="flex gap-1">
          {item.status !== 'SHIPPED' && item.status !== 'EXCLUDED' && (
            <Button
              size="sm"
              variant="outline"
              onClick={(e) => {
                e.stopPropagation()
                updateStatus.mutate({ orderId: item.orderId, status: 'SHIPPED' })
              }}
            >
              発送済
            </Button>
          )}
          {item.status !== 'EXCLUDED' && (
            <Button
              size="sm"
              variant="ghost"
              onClick={(e) => {
                e.stopPropagation()
                updateStatus.mutate({ orderId: item.orderId, status: 'EXCLUDED' })
              }}
            >
              対象外
            </Button>
          )}
        </div>
      ),
    },
  ]

  if (shippingQuery.isLoading) return <LoadingSpinner />
  if (shippingQuery.isError) return <ErrorMessage onRetry={() => shippingQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader title="B-CART 出荷情報" />
      <DataTable data={shippingQuery.data ?? []} columns={columns} searchPlaceholder="会社名で検索..." />
    </div>
  )
}
