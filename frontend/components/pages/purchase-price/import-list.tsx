'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import type { QuoteImportHeaderResponse } from '@/types/quote-import'
import { getChangeReasonLabel } from '@/types/purchase-price'

export function QuoteImportListPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [deleteTarget, setDeleteTarget] = useState<QuoteImportHeaderResponse | null>(null)

  const listQuery = useQuery({
    queryKey: ['quote-imports'],
    queryFn: () => api.get<QuoteImportHeaderResponse[]>('/quote-imports'),
  })

  const deleteMutation = useMutation({
    mutationFn: (importId: number) => api.delete(`/quote-imports/${importId}`),
    onSuccess: () => {
      toast.success('見積取込データを削除しました')
      queryClient.invalidateQueries({ queryKey: ['quote-imports'] })
      setDeleteTarget(null)
    },
    onError: () => {
      toast.error('削除に失敗しました')
    },
  })

  if (listQuery.isLoading) return <LoadingSpinner />
  if (listQuery.isError) return <ErrorMessage onRetry={() => listQuery.refetch()} />

  const imports = listQuery.data ?? []

  return (
    <div className="space-y-6">
      <PageHeader title="AI見積取込一覧" />

      {imports.length === 0 ? (
        <Card>
          <CardContent className="py-8 text-center text-muted-foreground">
            取込データがありません。Claude Code の /quote-import コマンドで見積ファイルを取り込んでください。
          </CardContent>
        </Card>
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/50">
                <th className="px-3 py-2 text-left font-medium">仕入先</th>
                <th className="px-3 py-2 text-left font-medium">ファイル名</th>
                <th className="px-3 py-2 text-left font-medium">適用日</th>
                <th className="px-3 py-2 text-left font-medium">理由</th>
                <th className="px-3 py-2 text-left font-medium">進捗</th>
                <th className="px-3 py-2 text-left font-medium">仕入先突合</th>
                <th className="px-3 py-2 text-left font-medium">操作</th>
              </tr>
            </thead>
            <tbody>
              {imports.map((item) => {
                const processed = item.totalCount - item.remainingCount
                const progressPct = item.totalCount > 0
                  ? Math.round((processed / item.totalCount) * 100)
                  : 0

                return (
                  <tr key={item.quoteImportId} className="border-b hover:bg-muted/30">
                    <td className="px-3 py-2">{item.supplierName ?? '-'}</td>
                    <td className="px-3 py-2 max-w-[200px] truncate">{item.fileName ?? '-'}</td>
                    <td className="px-3 py-2">{item.effectiveDate ?? '-'}</td>
                    <td className="px-3 py-2">
                      {item.changeReason ? getChangeReasonLabel(item.changeReason) : '-'}
                    </td>
                    <td className="px-3 py-2">
                      <div className="flex items-center gap-2">
                        <div className="w-20 h-2 bg-muted rounded-full overflow-hidden">
                          <div
                            className="h-full bg-primary rounded-full transition-all"
                            style={{ width: `${progressPct}%` }}
                          />
                        </div>
                        <span className="text-xs text-muted-foreground">
                          {processed}/{item.totalCount}
                        </span>
                      </div>
                    </td>
                    <td className="px-3 py-2">
                      {item.supplierCode ? (
                        <Badge variant="secondary">済</Badge>
                      ) : (
                        <Badge variant="outline">未</Badge>
                      )}
                    </td>
                    <td className="px-3 py-2">
                      <div className="flex items-center gap-1">
                        <Button
                          size="sm"
                          variant="outline"
                          onClick={() => router.push(`/purchase-prices/imports/${item.quoteImportId}`)}
                        >
                          突合画面へ
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          className="text-destructive hover:text-destructive"
                          onClick={() => setDeleteTarget(item)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}

      <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>見積取込データの削除</AlertDialogTitle>
            <AlertDialogDescription>
              「{deleteTarget?.supplierName ?? '—'}／{deleteTarget?.fileName ?? '—'}」を削除しますか？
              関連する明細データ・仕入価格変更予定もすべて削除されます。この操作は取り消せません。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>キャンセル</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.quoteImportId)}
              disabled={deleteMutation.isPending}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {deleteMutation.isPending ? '削除中...' : '削除'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
