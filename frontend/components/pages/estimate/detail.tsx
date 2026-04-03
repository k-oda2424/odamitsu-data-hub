'use client'

import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { ArrowLeft, Printer } from 'lucide-react'
import { formatNumber } from '@/lib/utils'
import { toast } from 'sonner'
import type { EstimateResponse } from '@/types/estimate'
import { ESTIMATE_STATUS_OPTIONS, getEstimateStatusLabel } from '@/types/estimate'

interface EstimateDetailPageProps {
  estimateNo: number
}

function statusVariant(code: string | null): 'default' | 'secondary' | 'outline' | 'destructive' {
  switch (code) {
    case '00': case '20': return 'default'
    case '10': case '30': return 'secondary'
    case '70': return 'outline'
    case '50': case '99': return 'destructive'
    default: return 'outline'
  }
}

function fmt(val: number | null | undefined): string {
  if (val == null) return '-'
  return formatNumber(val)
}

export function EstimateDetailPage({ estimateNo }: EstimateDetailPageProps) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const estimateQuery = useQuery({
    queryKey: ['estimate', estimateNo],
    queryFn: () => api.get<EstimateResponse>(`/estimates/${estimateNo}`),
  })

  const statusMutation = useMutation({
    mutationFn: (status: string) =>
      api.put<EstimateResponse>(`/estimates/${estimateNo}/status`, { estimateStatus: status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['estimate', estimateNo] })
      toast.success('ステータスを更新しました')
    },
    onError: () => toast.error('ステータスの更新に失敗しました'),
  })

  if (estimateQuery.isLoading) return <LoadingSpinner />
  if (estimateQuery.isError) return <ErrorMessage onRetry={() => estimateQuery.refetch()} />
  if (!estimateQuery.data) return <ErrorMessage onRetry={() => estimateQuery.refetch()} />

  const est = estimateQuery.data
  const details = est.details ?? []

  return (
    <div className="space-y-6">
      {/* Screen header (hidden on print) */}
      <div className="print:hidden">
        <PageHeader
          title={`見積明細 #${est.estimateNo}`}
          actions={
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => window.print()}>
                <Printer className="mr-2 h-4 w-4" />
                印刷
              </Button>
              <Button variant="outline" onClick={() => router.push('/estimates')}>
                <ArrowLeft className="mr-2 h-4 w-4" />
                一覧に戻る
              </Button>
            </div>
          }
        />
      </div>

      {/* Print header */}
      <div className="hidden print:block text-center mb-6">
        <h1 className="text-2xl font-bold">御 見 積 書</h1>
      </div>

      {/* Header info */}
      <Card className="print:border-0 print:shadow-none">
        <CardContent className="pt-4">
          <div className="grid gap-3 text-sm md:grid-cols-2">
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground w-20 shrink-0">見積番号</span>
                <span className="font-medium">{est.estimateNo}</span>
                <Badge variant={statusVariant(est.estimateStatus)} className="print:hidden">
                  {getEstimateStatusLabel(est.estimateStatus)}
                </Badge>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground w-20 shrink-0">得意先</span>
                <span className="font-medium">
                  {est.partnerCode && <span className="text-xs text-muted-foreground mr-1">{est.partnerCode}</span>}
                  {est.partnerName ?? '-'}
                </span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground w-20 shrink-0">見積日</span>
                <span>{est.estimateDate ?? '-'}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground w-20 shrink-0">価格改定日</span>
                <span>{est.priceChangeDate ?? '-'}</span>
              </div>
              {est.note && (
                <div className="flex items-start gap-2">
                  <span className="text-muted-foreground w-20 shrink-0">備考</span>
                  <span>{est.note}</span>
                </div>
              )}
            </div>

            {/* Status change (screen only) */}
            <div className="flex items-start justify-end print:hidden">
              <div className="flex items-center gap-2">
                <span className="text-sm text-muted-foreground">ステータス変更:</span>
                <Select
                  value={est.estimateStatus ?? ''}
                  onValueChange={(val) => statusMutation.mutate(val)}
                  disabled={statusMutation.isPending}
                >
                  <SelectTrigger className="w-48">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {ESTIMATE_STATUS_OPTIONS.map((opt) => (
                      <SelectItem key={opt.value} value={opt.value}>{opt.label}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Detail table */}
      <div className="rounded-lg border shadow-sm print:border-0 print:shadow-none">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b bg-muted/50 print:bg-gray-100">
              <th className="px-3 py-2 text-left font-medium">コード</th>
              <th className="px-3 py-2 text-left font-medium">商品名</th>
              <th className="px-3 py-2 text-right font-medium">単価</th>
              <th className="px-3 py-2 text-right font-medium">入数</th>
              <th className="px-3 py-2 text-right font-medium">ケース価格</th>
              <th className="px-3 py-2 text-left font-medium">備考</th>
              {/* 管理者向け粗利情報（印刷非表示） */}
              {isAdmin && (
                <>
                  <th className="px-3 py-2 text-right font-medium print:hidden">原価</th>
                  <th className="px-3 py-2 text-right font-medium print:hidden">粗利率</th>
                  <th className="px-3 py-2 text-right font-medium print:hidden">粗利</th>
                </>
              )}
            </tr>
          </thead>
          <tbody>
            {details.length === 0 ? (
              <tr>
                <td colSpan={isAdmin ? 9 : 6} className="px-3 py-8 text-center text-muted-foreground">
                  明細データがありません
                </td>
              </tr>
            ) : (
              details.map((d) => {
                const casePrice = (d.goodsPrice ?? 0) * (d.containNum ?? 1)
                const profit = (d.goodsPrice ?? 0) - (d.purchasePrice ?? 0)
                return (
                  <tr key={d.estimateDetailNo} className="border-b hover:bg-muted/30 print:hover:bg-transparent">
                    <td className="px-3 py-2 font-mono text-xs text-muted-foreground">{d.goodsCode}</td>
                    <td className="px-3 py-2">
                      {d.goodsName}
                      {d.specification && <span className="ml-1 text-xs text-muted-foreground">{d.specification}</span>}
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums">{fmt(d.goodsPrice)}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{d.containNum ?? '-'}</td>
                    <td className="px-3 py-2 text-right tabular-nums font-medium">{fmt(casePrice)}</td>
                    <td className="px-3 py-2 text-muted-foreground">{d.detailNote || ''}</td>
                    {isAdmin && (
                      <>
                        <td className="px-3 py-2 text-right tabular-nums print:hidden">{fmt(d.purchasePrice)}</td>
                        <td className="px-3 py-2 text-right tabular-nums print:hidden">
                          {d.profitRate != null ? `${d.profitRate}%` : '-'}
                        </td>
                        <td className="px-3 py-2 text-right tabular-nums print:hidden">{fmt(profit)}</td>
                      </>
                    )}
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>

      {/* Tax disclaimer */}
      <div className="text-sm text-muted-foreground">
        {est.isIncludeTaxDisplay ? '(税込です)' : '(消費税は含まれておりません)'}
      </div>
    </div>
  )
}
