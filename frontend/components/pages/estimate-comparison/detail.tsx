'use client'

import { useRouter } from 'next/navigation'
import { useCallback, useState } from 'react'
import { usePrintWithStatusUpdate, printConfirmDescription } from '@/hooks/use-print-with-status-update'
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
import { ArrowLeft, Printer, Pencil, Trash2 } from 'lucide-react'
import { fmt, stripPrintParens } from '@/lib/estimate-calc'
import { formatDateJP } from '@/lib/utils'
import { toast } from 'sonner'
import type { ComparisonResponse } from '@/types/estimate-comparison'
import { ESTIMATE_STATUS_OPTIONS, getEstimateStatusLabel, getNotifiedStatus } from '@/types/estimate'
import { ComparisonGroupSection } from './ComparisonGroupSection'
import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'

interface Props {
  comparisonNo: number
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

export function ComparisonDetailPage({ comparisonNo }: Props) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const detailQuery = useQuery({
    queryKey: ['estimate-comparison', comparisonNo],
    queryFn: () => api.get<ComparisonResponse>(`/estimate-comparisons/${comparisonNo}`),
  })

  const isEditable = (c: ComparisonResponse | null | undefined) => {
    const s = c?.comparisonStatus
    return s === '00' || s === '20'
  }

  const deleteMutation = useMutation({
    mutationFn: () => api.delete(`/estimate-comparisons/${comparisonNo}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['estimate-comparisons'] })
      toast.success('比較見積を削除しました')
      router.push('/estimate-comparisons')
    },
    onError: () => toast.error('比較見積の削除に失敗しました'),
  })

  const statusMutation = useMutation({
    mutationFn: (status: string) =>
      api.put<ComparisonResponse>(`/estimate-comparisons/${comparisonNo}/status`, { comparisonStatus: status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['estimate-comparison', comparisonNo] })
      queryClient.invalidateQueries({ queryKey: ['estimate-comparisons'] })
      toast.success('ステータスを更新しました')
    },
    onError: () => toast.error('ステータスの更新に失敗しました'),
  })

  const [deleteOpen, setDeleteOpen] = useState(false)

  const print = usePrintWithStatusUpdate(
    detailQuery.data?.comparisonStatus,
    useCallback(async (notified) => {
      await api.put(`/estimate-comparisons/${comparisonNo}/status`, { comparisonStatus: notified })
      queryClient.invalidateQueries({ queryKey: ['estimate-comparison', comparisonNo] })
      queryClient.invalidateQueries({ queryKey: ['estimate-comparisons'] })
    }, [comparisonNo, queryClient]),
  )
  const handlePrint = print.trigger

  if (detailQuery.isLoading) return <LoadingSpinner />
  if (detailQuery.isError) return <ErrorMessage onRetry={() => detailQuery.refetch()} />
  if (!detailQuery.data) return <ErrorMessage onRetry={() => detailQuery.refetch()} />

  const c = detailQuery.data
  const groups = c.groups ?? []

  return (
    <div className="space-y-6">
      {/* ===== 画面表示用ヘッダ（印刷非表示） ===== */}
      <div className="print:hidden">
        <PageHeader
          title={`比較見積 #${c.comparisonNo}`}
          actions={
            <div className="flex gap-2">
              {isEditable(c) && (
                <>
                  <Button onClick={() => router.push(`/estimate-comparisons/${c.comparisonNo}/edit`)}>
                    <Pencil className="mr-2 h-4 w-4" />
                    修正
                  </Button>
                  <Button
                    variant="destructive"
                    onClick={() => setDeleteOpen(true)}
                    disabled={deleteMutation.isPending}
                  >
                    <Trash2 className="mr-2 h-4 w-4" />
                    削除
                  </Button>
                </>
              )}
              <Button variant="outline" onClick={handlePrint}>
                <Printer className="mr-2 h-4 w-4" />
                印刷
              </Button>
              <Button variant="outline" onClick={() => router.back()}>
                <ArrowLeft className="mr-2 h-4 w-4" />
                戻る
              </Button>
            </div>
          }
        />
      </div>

      {/* ===== 画面表示用メタ情報（印刷非表示） ===== */}
      <Card className="print:hidden">
        <CardContent className="pt-4">
          <div className="grid gap-3 text-sm md:grid-cols-2">
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground w-24 shrink-0">比較見積番号</span>
                <span className="font-medium">{c.comparisonNo}</span>
                <Badge variant={statusVariant(c.comparisonStatus)}>
                  {getEstimateStatusLabel(c.comparisonStatus)}
                </Badge>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground w-24 shrink-0">得意先</span>
                <span className="font-medium">{c.partnerName ?? '-'}</span>
              </div>
              {c.destinationName && (
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground w-24 shrink-0">納品先</span>
                  <span className="font-medium">{c.destinationName}</span>
                </div>
              )}
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground w-24 shrink-0">作成日</span>
                <span>{c.comparisonDate ?? '-'}</span>
              </div>
              {c.sourceEstimateNo && (
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground w-24 shrink-0">元見積</span>
                  <Button
                    variant="link"
                    className="h-auto p-0"
                    onClick={() => router.push(`/estimates/${c.sourceEstimateNo}`)}
                  >
                    #{c.sourceEstimateNo}
                  </Button>
                </div>
              )}
              {c.title && (
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground w-24 shrink-0">タイトル</span>
                  <span>{c.title}</span>
                </div>
              )}
              {c.note && (
                <div className="flex items-start gap-2">
                  <span className="text-muted-foreground w-24 shrink-0">社内メモ</span>
                  <span className="whitespace-pre-wrap">{c.note}</span>
                </div>
              )}
            </div>
            <div className="flex items-start justify-end">
              <div className="flex items-center gap-2">
                <span className="text-sm text-muted-foreground">ステータス変更:</span>
                <Select
                  value={c.comparisonStatus ?? ''}
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

      {/* ===== グループ比較表（画面用・印刷非表示） ===== */}
      <div className="space-y-4 print:hidden">
        {groups.length === 0 ? (
          <div className="rounded-lg border p-8 text-center text-muted-foreground">
            グループがありません
          </div>
        ) : (
          groups.map((group) => (
            <ComparisonGroupSection key={group.groupNo} group={group} isAdmin={isAdmin} />
          ))
        )}
      </div>

      {/* ===== 印刷用 御見積書レイアウト ===== */}
      <div className="hidden print:block print:text-sm">
        {/* タイトル + 日付 */}
        <div className="flex items-end justify-between mb-4">
          <div className="flex-1" />
          <h1 className="text-2xl font-semibold tracking-[0.3em]">御見積書</h1>
          <div className="flex-1 text-right text-sm">
            <span>御見積日　{formatDateJP(c.comparisonDate)}</span>
          </div>
        </div>

        {/* 得意先名 */}
        <div className="mb-2">
          <span className="inline-block border-b-2 border-black pb-1">
            <span className="text-xl font-bold mr-4">{stripPrintParens(c.partnerName)}</span>
            <span className="text-lg ml-4">御中</span>
          </span>
        </div>

        {/* 挨拶文 + 会社情報 */}
        <div className="flex justify-between mb-4">
          <div className="w-[55%]">
            <p className="leading-relaxed mb-3">
              下記の通り御見積り申し上げますので<br />
              何卒ご用命賜りますようお願い申し上げます。
            </p>
            {c.title && (
              <p className="text-sm font-bold mb-2">件名: {c.title}</p>
            )}
          </div>
          <div className="w-[40%] flex justify-end">
            <div className="text-sm leading-relaxed">
              <p className="text-xl font-bold mb-1">小田光株式会社</p>
              <p>〒739-0615 広島県大竹市元町四丁目2-10</p>
              <p>TEL 0827-53-2227 / FAX 0827-53-2228</p>
              <p>登録番号:T5240001028409</p>
              <p className="mt-2">担当：{user?.userName ?? ''}</p>
            </div>
          </div>
        </div>

        {/* 各グループ比較表（印刷用 — 仕入情報非表示） */}
        {groups.map((group) => (
          <div key={group.groupNo} className="mb-6 break-inside-avoid">
            <h3 className="font-bold text-sm mb-1 border-b border-black pb-1">
              {group.baseGoodsName}
              {group.baseSpecification && (
                <span className="ml-2 font-normal text-xs">{group.baseSpecification}</span>
              )}
            </h3>
            <table className="w-full text-sm border-collapse">
              <thead>
                <tr className="border-t border-b border-black">
                  <th className="py-1 text-left font-bold px-1 w-20"></th>
                  <th className="py-1 text-left font-bold px-1">現行品</th>
                  {group.details.map((d, idx) => (
                    <th key={d.detailNo} className="py-1 text-left font-bold px-1">
                      提案{idx + 1}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                <tr className="border-b border-gray-300">
                  <td className="py-1 px-1 font-bold">ｺｰﾄﾞ</td>
                  <td className="py-1 px-1 font-mono text-xs">{group.baseGoodsCode ?? '-'}</td>
                  {group.details.map((d) => (
                    <td key={d.detailNo} className="py-1 px-1 font-mono text-xs">{d.goodsCode ?? '-'}</td>
                  ))}
                </tr>
                <tr className="border-b border-gray-300">
                  <td className="py-1 px-1 font-bold">商品名</td>
                  <td className="py-1 px-1">{group.baseGoodsName}</td>
                  {group.details.map((d) => (
                    <td key={d.detailNo} className="py-1 px-1">{d.goodsName}</td>
                  ))}
                </tr>
                <tr className="border-b border-gray-300">
                  <td className="py-1 px-1 font-bold">規格</td>
                  <td className="py-1 px-1">{group.baseSpecification ?? '-'}</td>
                  {group.details.map((d) => (
                    <td key={d.detailNo} className="py-1 px-1">{d.specification ?? '-'}</td>
                  ))}
                </tr>
                <tr className="border-b border-gray-300">
                  <td className="py-1 px-1 font-bold">単価※</td>
                  <td className="py-1 px-1 text-right tabular-nums">{fmt(group.baseGoodsPrice)}</td>
                  {group.details.map((d) => (
                    <td key={d.detailNo} className="py-1 px-1 text-right tabular-nums">{fmt(d.proposedPrice)}</td>
                  ))}
                </tr>
                <tr className="border-b border-gray-300">
                  <td className="py-1 px-1 font-bold">入数</td>
                  <td className="py-1 px-1 text-right tabular-nums">{group.baseContainNum ?? '-'}</td>
                  {group.details.map((d) => (
                    <td key={d.detailNo} className="py-1 px-1 text-right tabular-nums">{d.containNum ?? '-'}</td>
                  ))}
                </tr>
                {group.details.some((d) => d.detailNote) && (
                  <tr className="border-b border-gray-300">
                    <td className="py-1 px-1 font-bold">備考</td>
                    <td className="py-1 px-1">-</td>
                    {group.details.map((d) => (
                      <td key={d.detailNo} className="py-1 px-1">{d.detailNote || '-'}</td>
                    ))}
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        ))}

        {/* フッタ */}
        <div className="text-right text-sm">
          <p>※上記価格に消費税は含まれておりません。</p>
        </div>
      </div>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="削除確認"
        description="この比較見積を削除しますか？"
        confirmLabel="削除"
        variant="destructive"
        onConfirm={() => deleteMutation.mutate()}
      />

      <ConfirmDialog
        open={print.pendingNotified !== null}
        onOpenChange={(o) => { if (!o) print.setPendingNotified(null) }}
        title="印刷確認"
        description={printConfirmDescription(print.pendingNotified, '印刷')}
        onConfirm={() => void print.execute()}
      />
    </div>
  )
}
