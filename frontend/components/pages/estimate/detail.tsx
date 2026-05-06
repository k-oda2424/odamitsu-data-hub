'use client'

import { useRouter } from 'next/navigation'
import { useCallback, useState } from 'react'
import { flushSync } from 'react-dom'
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
import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'
import { ArrowLeft, Printer, Pencil, Trash2, Download, ArrowLeftRight } from 'lucide-react'
import type { ComparisonResponse } from '@/types/estimate-comparison'
import { fmt, stripPrintParens } from '@/lib/estimate-calc'
import { formatDateJP } from '@/lib/utils'
import { toast } from 'sonner'
import type { EstimateResponse } from '@/types/estimate'
import { ESTIMATE_STATUS_OPTIONS, getEstimateStatusLabel, getNotifiedStatus } from '@/types/estimate'

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



export function EstimateDetailPage({ estimateNo }: EstimateDetailPageProps) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const estimateQuery = useQuery({
    queryKey: ['estimate', estimateNo],
    queryFn: () => api.get<EstimateResponse>(`/estimates/${estimateNo}`),
  })

  const [pendingOutput, setPendingOutput] = useState<{ type: 'print' | 'pdf'; notified: string } | null>(null)
  const [deleteOpen, setDeleteOpen] = useState(false)

  const isEditable = (e: EstimateResponse | null | undefined) => {
    const s = e?.estimateStatus
    return s === '00' || s === '20'
  }

  const deleteMutation = useMutation({
    mutationFn: () => api.delete(`/estimates/${estimateNo}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['estimates'] })
      toast.success('見積を削除しました')
      router.push('/estimates')
    },
    onError: () => toast.error('見積の削除に失敗しました'),
  })

  const statusMutation = useMutation({
    mutationFn: (status: string) =>
      api.put<EstimateResponse>(`/estimates/${estimateNo}/status`, { estimateStatus: status }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['estimate', estimateNo] })
      queryClient.invalidateQueries({ queryKey: ['estimates'] })
      toast.success('ステータスを更新しました')
    },
    onError: () => toast.error('ステータスの更新に失敗しました'),
  })

  const createComparisonMutation = useMutation({
    mutationFn: () =>
      api.post<ComparisonResponse>(`/estimate-comparisons/from-estimate/${estimateNo}`),
    onSuccess: (data) => {
      toast.success('比較見積を作成しました')
      router.push(`/estimate-comparisons/${data.comparisonNo}/edit`)
    },
    onError: () => toast.error('比較見積の作成に失敗しました'),
  })

  const updateStatusOnOutput = useCallback(async (currentStatus: string | null) => {
    const notified = getNotifiedStatus(currentStatus)
    if (notified === null || notified === currentStatus) return
    try {
      await api.put(`/estimates/${estimateNo}/status`, { estimateStatus: notified })
      queryClient.invalidateQueries({ queryKey: ['estimate', estimateNo] })
      queryClient.invalidateQueries({ queryKey: ['estimates'] })
      toast.success(`ステータスを「${getEstimateStatusLabel(notified)}」に更新しました`)
    } catch {
      toast.error('ステータスの更新に失敗しました')
    }
  }, [estimateNo, queryClient])

  // 印刷＋ステータス自動更新: 設計意図は hooks/use-print-with-status-update.ts 参照。
  // 当画面は印刷/PDFで同一 pendingOutput state を共有するため、
  // 共通hookではなく専用実装を維持する（hookはcomparison画面で利用）。
  const executePrint = useCallback(async () => {
    const currentStatus = estimateQuery.data?.estimateStatus ?? null
    const notified = getNotifiedStatus(currentStatus)
    // window.print() blocks until the preview closes. flushSync forces the
    // confirm dialog to unmount first so it isn't captured in the preview.
    flushSync(() => setPendingOutput(null))
    window.print()
    if (notified !== null && notified !== currentStatus) {
      await updateStatusOnOutput(currentStatus)
    }
  }, [estimateQuery.data?.estimateStatus, updateStatusOnOutput])

  const executeDownloadPdf = useCallback(async () => {
    const currentStatus = estimateQuery.data?.estimateStatus ?? null
    const notified = getNotifiedStatus(currentStatus)
    try {
      const params = new URLSearchParams()
      if (user?.userName) params.append('userName', user.userName)
      const { blob, filename } = await api.download(
        `/estimates/${estimateNo}/pdf?${params.toString()}`,
      )
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename ?? `見積書_${estimateNo}.pdf`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
    } catch {
      toast.error('PDFのダウンロードに失敗しました')
      return
    }
    if (notified !== null && notified !== currentStatus) {
      await updateStatusOnOutput(currentStatus)
    }
  }, [estimateNo, user?.userName, estimateQuery.data?.estimateStatus, updateStatusOnOutput])

  const handlePrint = useCallback(() => {
    const currentStatus = estimateQuery.data?.estimateStatus ?? null
    const notified = getNotifiedStatus(currentStatus)
    if (notified !== null && notified !== currentStatus) {
      setPendingOutput({ type: 'print', notified })
      return
    }
    void executePrint()
  }, [estimateQuery.data?.estimateStatus, executePrint])

  const handleDownloadPdf = useCallback(() => {
    const currentStatus = estimateQuery.data?.estimateStatus ?? null
    const notified = getNotifiedStatus(currentStatus)
    if (notified !== null && notified !== currentStatus) {
      setPendingOutput({ type: 'pdf', notified })
      return
    }
    void executeDownloadPdf()
  }, [estimateQuery.data?.estimateStatus, executeDownloadPdf])

  if (estimateQuery.isLoading) return <LoadingSpinner />
  if (estimateQuery.isError) return <ErrorMessage onRetry={() => estimateQuery.refetch()} />
  if (!estimateQuery.data) return <ErrorMessage onRetry={() => estimateQuery.refetch()} />

  const est = estimateQuery.data
  const details = est.details ?? []
  const validityText = est.priceChangeDate === est.estimateDate
    ? '御見積日より1ヵ月'
    : `${formatDateJP(est.priceChangeDate)} 納品分より`

  return (
    <div className="space-y-6">
      {/* ===== 画面表示用ヘッダ（印刷非表示） ===== */}
      <div className="print:hidden">
        <PageHeader
          title={`見積明細 #${est.estimateNo}`}
          actions={
            <div className="flex gap-2">
              {isEditable(est) && (
                <>
                  <Button onClick={() => router.push(`/estimates/${est.estimateNo}/edit`)}>
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
              {isEditable(est) && (
                <Button
                  variant="outline"
                  onClick={() => createComparisonMutation.mutate()}
                  disabled={createComparisonMutation.isPending}
                >
                  <ArrowLeftRight className="mr-2 h-4 w-4" />
                  {createComparisonMutation.isPending ? '作成中...' : '比較見積を作成'}
                </Button>
              )}
              <Button variant="outline" onClick={handlePrint}>
                <Printer className="mr-2 h-4 w-4" />
                印刷
              </Button>
              <Button variant="outline" onClick={handleDownloadPdf}>
                <Download className="mr-2 h-4 w-4" />
                PDF
              </Button>
              <Button variant="outline" onClick={() => router.push('/estimates')}>
                <ArrowLeft className="mr-2 h-4 w-4" />
                戻る
              </Button>
            </div>
          }
        />
      </div>

      {/* ===== 画面表示用ヘッダ情報（印刷非表示） ===== */}
      <Card className="print:hidden">
        <CardContent className="pt-4">
          <div className="grid gap-3 text-sm md:grid-cols-2">
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground w-20 shrink-0">見積番号</span>
                <span className="font-medium">{est.estimateNo}</span>
                <Badge variant={statusVariant(est.estimateStatus)}>
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
              {est.destinationName && (
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground w-20 shrink-0">納品先</span>
                  <span className="font-medium">{est.destinationName}</span>
                </div>
              )}
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground w-20 shrink-0">見積日</span>
                <span>{est.estimateDate ?? '-'}</span>
              </div>
              <div className="flex items-center gap-2">
                <span className="text-muted-foreground w-20 shrink-0">価格改定日</span>
                <span>{est.priceChangeDate ?? '-'}</span>
              </div>
              {est.recipientName && (
                <div className="flex items-center gap-2">
                  <span className="text-muted-foreground w-20 shrink-0">担当者</span>
                  <span className="font-medium">{est.recipientName}</span>
                </div>
              )}
              {est.requirement && (
                <div className="flex items-start gap-2">
                  <span className="text-muted-foreground w-20 shrink-0">要件</span>
                  <span className="whitespace-pre-wrap">{est.requirement}</span>
                </div>
              )}
              {est.proposalMessage && (
                <div className="flex items-start gap-2">
                  <span className="text-muted-foreground w-20 shrink-0">提案文</span>
                  <span className="whitespace-pre-wrap">{est.proposalMessage}</span>
                </div>
              )}
              {est.note && (
                <div className="flex items-start gap-2">
                  <span className="text-muted-foreground w-20 shrink-0">社内メモ</span>
                  <span>{est.note}</span>
                </div>
              )}
            </div>
            <div className="flex items-start justify-end">
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

      {/* ===== 印刷用 御見積書レイアウト ===== */}
      <div className="hidden print:block print:text-sm">

        {/* タイトル + 見積日 */}
        <div className="flex items-end justify-between mb-4">
          <div className="flex-1" />
          <h1 className="text-2xl font-semibold tracking-[0.3em]">御見積書</h1>
          <div className="flex-1 text-right text-sm">
            <span>御見積日　{formatDateJP(est.estimateDate)}</span>
          </div>
        </div>

        {/* 得意先名（担当者名がある場合は「{partnerName}　{recipientName} 様」） */}
        <div className="mb-2">
          <span className="inline-block border-b-2 border-black pb-1">
            <span className="text-xl font-bold mr-4">{stripPrintParens(est.partnerName)}</span>
            {est.recipientName ? (
              <>
                <span className="text-xl font-bold mr-4">{est.recipientName}</span>
                <span className="text-lg">様</span>
              </>
            ) : (
              <span className="text-lg ml-4">御中</span>
            )}
          </span>
        </div>

        {/* 挨拶文 + 会社情報（横並び） */}
        <div className="flex justify-between mb-2">
          {/* 左: 挨拶文 + 条件 */}
          <div className="w-[55%]">
            <p className="leading-relaxed mb-3">
              下記の通り御見積り申し上げますので<br />
              何卒ご用命賜りますようお願い申し上げます。
            </p>
            <table className="text-sm border-collapse">
              <tbody>
                <tr>
                  <td className="font-bold py-1 pr-4 border-b border-gray-400">受け渡し場所</td>
                  <td className="py-1 border-b border-gray-400 px-2">{est.destinationName && est.destinationName !== '標準届け先' ? est.destinationName : '貴社指定場所'}</td>
                </tr>
                <tr>
                  <td className="font-bold py-1 pr-4 border-b border-gray-400">有効期限</td>
                  <td className="py-1 border-b border-gray-400 px-2">{validityText}</td>
                </tr>
              </tbody>
            </table>
          </div>
          {/* 右: 会社情報（エリア右寄せ、テキスト左揃え） */}
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

        {/* 要件（社内メモnoteは印刷しない） */}
        {est.requirement && (
          <div className="border border-gray-400 p-2 mb-3 text-sm">
            <span className="font-bold">要件: </span>{est.requirement}
          </div>
        )}

        {/* 単位 */}
        <div className="text-right text-sm mb-1">単位：円</div>

        {/* 商品テーブル */}
        <table className="w-full text-sm border-collapse mb-3">
          <thead>
            <tr className="border-t border-b border-black">
              <th className="py-1 text-left font-bold px-1">ｺｰﾄﾞ</th>
              <th className="py-1 text-left font-bold px-1">商品名</th>
              <th className="py-1 text-right font-bold px-1">単価※</th>
              <th className="py-1 text-right font-bold px-1">入数</th>
              <th className="py-1 text-right font-bold px-1">ｹｰｽ価格※</th>
              <th className="py-1 text-left font-bold px-1">備考</th>
            </tr>
          </thead>
          <tbody>
            {details.map((d) => {
              const casePrice = (d.goodsPrice ?? 0) * (d.containNum ?? 1)
              return (
                <tr key={d.estimateDetailNo} className="border-b border-gray-300">
                  <td className="py-1 px-1 font-mono">{d.goodsCode}</td>
                  <td className="py-1 px-1">{d.goodsName}</td>
                  <td className="py-1 px-1 text-right tabular-nums">{fmt(d.goodsPrice)}</td>
                  <td className="py-1 px-1 text-right tabular-nums">{d.containNum ?? '-'}</td>
                  <td className="py-1 px-1 text-right tabular-nums font-bold">{fmt(casePrice)}</td>
                  <td className="py-1 px-1">{d.detailNote || ''}</td>
                </tr>
              )
            })}
          </tbody>
        </table>

        {/* フッタ */}
        <div className="text-right text-sm">
          <p>{est.isIncludeTaxDisplay ? '※上記価格は税込です。' : '※上記価格に消費税は含まれておりません。'}</p>
        </div>

        {/* 提案文（明細テーブルの後に表示） */}
        {est.proposalMessage && (
          <div className="mt-4 leading-relaxed whitespace-pre-wrap">
            {est.proposalMessage}
          </div>
        )}
      </div>

      {/* ===== 画面表示用テーブル（印刷非表示） ===== */}
      <div className="rounded-lg border shadow-sm print:hidden">
        <table className="w-full text-sm">
          <thead>
            <tr className="border-b bg-muted/50">
              <th className="px-3 py-2 text-left font-medium">コード</th>
              <th className="px-3 py-2 text-left font-medium">商品名</th>
              <th className="px-3 py-2 text-right font-medium">単価</th>
              <th className="px-3 py-2 text-right font-medium">入数</th>
              <th className="px-3 py-2 text-right font-medium">ケース価格</th>
              <th className="px-3 py-2 text-left font-medium">備考</th>
              {isAdmin && (
                <>
                  <th className="px-3 py-2 text-right font-medium">原価</th>
                  <th className="px-3 py-2 text-right font-medium">粗利率</th>
                  <th className="px-3 py-2 text-right font-medium">粗利</th>
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
                  <tr key={d.estimateDetailNo} className="border-b hover:bg-muted/30">
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
                        <td className="px-3 py-2 text-right tabular-nums">{fmt(d.purchasePrice)}</td>
                        <td className="px-3 py-2 text-right tabular-nums">
                          {d.profitRate != null ? `${d.profitRate}%` : '-'}
                        </td>
                        <td className="px-3 py-2 text-right tabular-nums">{fmt(profit)}</td>
                      </>
                    )}
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>

      {/* 税表示（画面のみ） */}
      <div className="text-sm text-muted-foreground print:hidden">
        {est.isIncludeTaxDisplay ? '(税込です)' : '(消費税は含まれておりません)'}
      </div>

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="削除確認"
        description="この見積を削除しますか？"
        confirmLabel="削除"
        variant="destructive"
        onConfirm={() => deleteMutation.mutate()}
      />

      <ConfirmDialog
        open={pendingOutput !== null}
        onOpenChange={(o) => { if (!o) setPendingOutput(null) }}
        title={pendingOutput?.type === 'pdf' ? 'PDFダウンロード確認' : '印刷確認'}
        description={
          pendingOutput
            ? `${pendingOutput.type === 'pdf' ? 'PDFをダウンロード' : '印刷'}しますか？ステータスが「${getEstimateStatusLabel(pendingOutput.notified)}」に更新されます。`
            : ''
        }
        onConfirm={() => {
          if (pendingOutput?.type === 'pdf') void executeDownloadPdf()
          else if (pendingOutput?.type === 'print') void executePrint()
        }}
      />
    </div>
  )
}
