'use client'

import { Fragment, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { AlertCircle, CheckCircle2, ChevronDown, ChevronRight, Loader2, Play, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import type { MfReconcileReport, MfReconcileRow } from '@/types/mf-integration'

/**
 * MF 仕訳突合タブ。
 * 取引月を選んで「突合実行」→ 3 種別（仕入仕訳/売上仕訳/買掛支払）の自社件数・金額と
 * MF 側件数・金額を並べて差分表示。
 */
export function MfReconcileTab() {
  const [transactionMonth, setTransactionMonth] = useState<string>(() => {
    // デフォルトは前月 20 日（20 日締めの典型値）
    const now = new Date()
    const prev = new Date(now.getFullYear(), now.getMonth() - 1, 20)
    return `${prev.getFullYear()}-${String(prev.getMonth() + 1).padStart(2, '0')}-${String(prev.getDate()).padStart(2, '0')}`
  })
  const [report, setReport] = useState<MfReconcileReport | null>(null)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())

  const toggleExpand = (kind: string) => {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(kind)) next.delete(kind)
      else next.add(kind)
      return next
    })
  }

  const runMutation = useMutation({
    mutationFn: (d: string) =>
      api.get<MfReconcileReport>(`/finance/mf-integration/reconcile?transactionMonth=${d}`),
    onSuccess: (res) => {
      setReport(res)
      const allMatched = res.rows.every((r) => r.matched)
      if (allMatched) toast.success('全種別で差分なし。完全一致です。')
      else toast.warning('差分ありの種別があります。詳細を確認してください。')
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) {
        toast.error('MF 認証エラー。「接続」タブで再認証してください。')
      } else {
        toast.error(e.message)
      }
    },
  })

  const fmtAmount = (n: number) => n.toLocaleString('ja-JP')

  return (
    <div className="space-y-3">
      <Card>
        <CardContent className="pt-4 space-y-3">
          <div className="flex items-center gap-3">
            <Label htmlFor="reconcile-date" className="text-sm">
              取引月 (締め日):
            </Label>
            <Input
              id="reconcile-date"
              type="date"
              className="w-48"
              value={transactionMonth}
              onChange={(e) => setTransactionMonth(e.target.value)}
            />
            <Button
              onClick={() => runMutation.mutate(transactionMonth)}
              disabled={runMutation.isPending || !transactionMonth}
            >
              {runMutation.isPending ? (
                <>
                  <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                  突合中...
                </>
              ) : (
                <>
                  <Play className="mr-1 h-4 w-4" />
                  突合実行
                </>
              )}
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            指定日の MF 仕訳と自社 CSV 出力元データを突合します。仕訳取込日は通常「締め日」
            （20 日締めなら当月 20 日、15 日締めなら当月 15 日）。
          </p>
        </CardContent>
      </Card>

      {report && (
        <Card>
          <CardContent className="pt-4 space-y-3">
            <div className="flex items-center justify-between">
              <div>
                <span className="text-sm font-medium">突合結果: </span>
                <span className="text-xs text-muted-foreground">
                  取引月 {report.transactionMonth} / 取得時刻{' '}
                  {new Date(report.fetchedAt).toLocaleString('ja-JP')}
                </span>
              </div>
              {report.mfUnknownBranchCount > 0 && (
                <Badge variant="outline" className="border-amber-400 text-amber-700">
                  未分類 MF branch: {report.mfUnknownBranchCount}
                </Badge>
              )}
            </div>

            <div className="overflow-x-auto rounded border">
              <table className="w-full text-sm">
                <thead className="bg-muted/50">
                  <tr>
                    <th className="w-8 px-2 py-2"></th>
                    <th className="px-3 py-2 text-left font-medium">種別</th>
                    <th className="px-3 py-2 text-right font-medium">自社件数</th>
                    <th className="px-3 py-2 text-right font-medium">自社合計</th>
                    <th className="px-3 py-2 text-right font-medium">MF件数</th>
                    <th className="px-3 py-2 text-right font-medium">MF合計</th>
                    <th className="px-3 py-2 text-right font-medium">件数差</th>
                    <th className="px-3 py-2 text-right font-medium">金額差</th>
                    <th className="px-3 py-2 text-center font-medium">状態</th>
                  </tr>
                </thead>
                <tbody>
                  {report.rows.map((r) => {
                    const isOpen = expanded.has(r.kind)
                    const canExpand = r.localItems.length > 0 || r.mfItems.length > 0
                    return (
                      <Fragment key={r.kind}>
                        <tr className="border-t">
                          <td className="px-2 py-2 text-center">
                            {canExpand && (
                              <button
                                type="button"
                                onClick={() => toggleExpand(r.kind)}
                                aria-label="詳細"
                                className="text-muted-foreground hover:text-foreground"
                              >
                                {isOpen ? (
                                  <ChevronDown className="h-4 w-4" />
                                ) : (
                                  <ChevronRight className="h-4 w-4" />
                                )}
                              </button>
                            )}
                          </td>
                          <td className="px-3 py-2 font-medium">{r.kindLabel}</td>
                          <td className="px-3 py-2 text-right tabular-nums">{r.localCount}</td>
                          <td className="px-3 py-2 text-right tabular-nums">¥{fmtAmount(r.localAmount)}</td>
                          <td className="px-3 py-2 text-right tabular-nums">{r.mfCount}</td>
                          <td className="px-3 py-2 text-right tabular-nums">¥{fmtAmount(r.mfAmount)}</td>
                          <td
                            className={
                              'px-3 py-2 text-right tabular-nums ' +
                              (r.countDiff !== 0 ? 'text-destructive font-medium' : '')
                            }
                          >
                            {r.countDiff > 0 ? `+${r.countDiff}` : r.countDiff}
                          </td>
                          <td
                            className={
                              'px-3 py-2 text-right tabular-nums ' +
                              (r.amountDiff !== 0 ? 'text-destructive font-medium' : '')
                            }
                          >
                            {r.amountDiff > 0 ? '+' : ''}¥{fmtAmount(r.amountDiff)}
                          </td>
                          <td className="px-3 py-2 text-center">
                            {r.matched ? (
                              <Badge className="bg-emerald-600 hover:bg-emerald-700">
                                <CheckCircle2 className="mr-1 h-3 w-3" />
                                一致
                              </Badge>
                            ) : (
                              <Badge variant="destructive">
                                <XCircle className="mr-1 h-3 w-3" />
                                不一致
                              </Badge>
                            )}
                          </td>
                        </tr>
                        {isOpen && (
                          <tr className="border-t bg-muted/20">
                            <td colSpan={9} className="p-3">
                              <RowDetail row={r} />
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    )
                  })}
                </tbody>
              </table>
            </div>

            {/* 未分類 branch 内訳 */}
            {report.unclassified.length > 0 && (
              <details className="rounded border">
                <summary className="cursor-pointer select-none bg-muted/50 px-3 py-2 text-sm font-medium">
                  未分類 branch 内訳 ({report.unclassified.length} 種別、合計 {report.mfUnknownBranchCount} 件)
                </summary>
                <div className="p-2">
                  <table className="w-full text-xs">
                    <thead className="bg-muted/30">
                      <tr>
                        <th className="px-3 py-1.5 text-left font-medium">借方科目</th>
                        <th className="px-3 py-1.5 text-left font-medium">貸方科目</th>
                        <th className="px-3 py-1.5 text-right font-medium">件数</th>
                        <th className="px-3 py-1.5 text-right font-medium">金額合計</th>
                      </tr>
                    </thead>
                    <tbody>
                      {report.unclassified.map((u, i) => (
                        <tr key={i} className="border-t">
                          <td className="px-3 py-1">{u.debitAccount ?? '-'}</td>
                          <td className="px-3 py-1">{u.creditAccount ?? '-'}</td>
                          <td className="px-3 py-1 text-right tabular-nums">{u.count}</td>
                          <td className="px-3 py-1 text-right tabular-nums">¥{fmtAmount(u.totalAmount)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </details>
            )}

            <div className="rounded border border-muted p-3 text-[11px] text-muted-foreground space-y-1">
              <p className="flex items-start gap-2">
                <AlertCircle className="h-3.5 w-3.5 mt-0.5 shrink-0" />
                <span>
                  分類ルール: <b>PURCHASE</b>=借方「仕入高」+貸方「買掛金」 /{' '}
                  <b>SALES</b>=借方「売掛金」or「未収入金」+貸方「売上高」or「仮払金」 /{' '}
                  <b>PAYMENT</b>=貸方「普通預金」「当座預金」「現金」いずれか
                </span>
              </p>
              <p>
                件数単位: PURCHASE/SALES は (supplier × taxRate) 組合せ / PAYMENT は PAYABLE (supplier 集約) + MF 補助行
              </p>
              <p>
                未分類 MF branch: 自社 CSV 起源ではない手動入力の仕訳など。差異の原因確認の参考に。
              </p>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}

function RowDetail({ row }: { row: MfReconcileRow }) {
  const fmt = (n: number | null) =>
    n == null ? '-' : n.toLocaleString('ja-JP')
  const nameHeader = row.kind === 'SALES' ? '得意先名' : '仕入先名'
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
      <DetailTable
        title={`自社側 ${row.localItems.length} 件`}
        rows={row.localItems.map((l) => ({
          key: `${l.source}-${l.partyNo ?? l.partyCode}-${l.taxRate ?? ''}`,
          unmatched: l.unmatched,
          cols: [
            l.partyCode ?? String(l.partyNo ?? '-'),
            l.partyName ?? '',
            l.taxRate != null ? `${l.taxRate}%` : l.note ?? '',
            `¥${fmt(l.amount)}`,
          ],
        }))}
        headers={['コード', nameHeader, '税率/備考', '金額']}
      />
      <DetailTable
        title={`MF側 ${row.mfItems.length} 件`}
        rows={row.mfItems.map((m) => ({
          key: m.journalId,
          unmatched: m.unmatched,
          cols: [
            m.journalNumber != null ? `#${m.journalNumber}` : '-',
            m.tradePartnerName ?? m.creditSubAccount ?? m.debitSubAccount ?? '',
            m.taxName ?? '対象外',
            `¥${fmt(m.amount)}`,
          ],
        }))}
        headers={['Journal#', nameHeader, '税区分', '金額']}
      />
    </div>
  )
}

function DetailTable({
  title,
  headers,
  rows,
}: {
  title: string
  headers: string[]
  rows: { key: string; unmatched?: boolean; cols: string[] }[]
}) {
  return (
    <div className="rounded border">
      <div className="bg-muted/50 px-3 py-1.5 text-xs font-medium">{title}</div>
      <div className="max-h-64 overflow-auto">
        <table className="w-full text-[11px]">
          <thead className="sticky top-0 bg-muted/40">
            <tr>
              {headers.map((h, i) => (
                <th
                  key={i}
                  className={
                    'px-2 py-1 ' + (i === headers.length - 1 ? 'text-right' : 'text-left')
                  }
                >
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr>
                <td
                  colSpan={headers.length}
                  className="px-2 py-2 text-center text-muted-foreground"
                >
                  なし
                </td>
              </tr>
            )}
            {rows.map((r) => (
              <tr
                key={r.key}
                className={
                  'border-t ' +
                  (r.unmatched ? 'bg-amber-100/70 hover:bg-amber-100' : '')
                }
                title={r.unmatched ? '相手側にマッチする行なし' : undefined}
              >
                {r.cols.map((c, i) => (
                  <td
                    key={i}
                    className={
                      'px-2 py-1 ' +
                      (i === r.cols.length - 1 ? 'text-right tabular-nums' : '')
                    }
                  >
                    {c}
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  )
}
