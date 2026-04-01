'use client'

import { useRouter } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import type { QuoteImportHeaderResponse } from '@/types/quote-import'
import { getChangeReasonLabel } from '@/types/purchase-price'

export function QuoteImportListPage() {
  const router = useRouter()

  const listQuery = useQuery({
    queryKey: ['quote-imports'],
    queryFn: () => api.get<QuoteImportHeaderResponse[]>('/quote-imports'),
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
                      <Button
                        size="sm"
                        variant="outline"
                        onClick={() => router.push(`/purchase-prices/imports/${item.quoteImportId}`)}
                      >
                        突合画面へ
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
