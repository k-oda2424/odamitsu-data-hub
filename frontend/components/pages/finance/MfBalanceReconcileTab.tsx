'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { AlertCircle, CheckCircle2, Loader2, Play } from 'lucide-react'
import { toast } from 'sonner'
import type { MfBalanceReconcileReport } from '@/types/mf-integration'

/**
 * MF 残高突合タブ (Phase B 最小版)。
 * 取引月末日を選んで「突合実行」→ MF 試算表 /trial_balance_bs の買掛金 closing と
 * 自社 t_accounts_payable_summary の累積残 (opening + change) 合計を比較して差分表示。
 * 設計書: claudedocs/design-supplier-partner-ledger-balance.md §5
 */
export function MfBalanceReconcileTab() {
  const [period, setPeriod] = useState<string>(() => {
    const now = new Date()
    const prev = new Date(now.getFullYear(), now.getMonth() - 1, 20)
    return `${prev.getFullYear()}-${String(prev.getMonth() + 1).padStart(2, '0')}-${String(prev.getDate()).padStart(2, '0')}`
  })
  const [report, setReport] = useState<MfBalanceReconcileReport | null>(null)

  const runMutation = useMutation({
    mutationFn: (d: string) =>
      api.get<MfBalanceReconcileReport>(`/finance/mf-integration/balance-reconcile?period=${d}`),
    onSuccess: (res) => {
      setReport(res)
      if (res.payable.diffForMf === 0) {
        toast.success('残高が完全一致しました。')
      } else {
        toast.warning(`残高差分 ¥${fmt(res.payable.diffForMf)} あり。詳細を確認してください。`)
      }
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) {
        toast.error('MF 認証エラー。「接続」タブで再認証してください。')
      } else if (e instanceof ApiError && e.status === 403) {
        toast.error('scope 不足です。「接続」タブで scope に report.read が含まれているか確認し、再認証してください。')
      } else {
        toast.error(e.message)
      }
    },
  })

  const fmt = (n: number) => n.toLocaleString('ja-JP')

  return (
    <div className="space-y-3">
      <Card>
        <CardContent className="pt-4 space-y-3">
          <div className="flex items-end gap-3">
            <div>
              <Label htmlFor="balance-period">月末日</Label>
              <Input
                id="balance-period"
                type="date"
                value={period}
                onChange={(e) => setPeriod(e.target.value)}
                className="w-44"
              />
            </div>
            <Button
              onClick={() => runMutation.mutate(period)}
              disabled={runMutation.isPending}
            >
              {runMutation.isPending ? (
                <Loader2 className="mr-1 h-4 w-4 animate-spin" />
              ) : (
                <Play className="mr-1 h-4 w-4" />
              )}
              残高突合
            </Button>
          </div>
          <p className="text-xs text-muted-foreground">
            自社 t_accounts_payable_summary の累積残 (opening + change) 合計と、
            MF 試算表 /trial_balance_bs の「買掛金」closing_balance を比較します。
            Phase A の opening_balance 列が前提です。
          </p>
        </CardContent>
      </Card>

      {report && (
        <Card>
          <CardContent className="pt-4 space-y-4">
            <div className="flex items-center justify-between">
              <h3 className="font-semibold">買掛金 残高突合 ({report.mfEndDate} 時点)</h3>
              {report.payable.diffForMf === 0 ? (
                <span className="flex items-center gap-1 text-emerald-600 text-sm">
                  <CheckCircle2 className="h-4 w-4" /> 一致
                </span>
              ) : (
                <span className="flex items-center gap-1 text-amber-700 text-sm">
                  <AlertCircle className="h-4 w-4" /> 差分あり
                </span>
              )}
            </div>

            {!report.payable.mfAccountFound && (
              <div role="alert" className="rounded border border-red-300 bg-red-50 p-3 text-sm text-red-800">
                MF 試算表に「買掛金」account が見つかりませんでした。MF 側データを確認してください。
              </div>
            )}

            <table className="w-full text-sm tabular-nums">
              <thead>
                <tr className="border-b text-left">
                  <th className="py-2">項目</th>
                  <th className="py-2 text-right">金額</th>
                  <th className="py-2 text-right">件数</th>
                </tr>
              </thead>
              <tbody>
                <tr className="border-b">
                  <td className="py-2">MF 買掛金 closing</td>
                  <td className="py-2 text-right">¥{fmt(report.payable.mfClosing)}</td>
                  <td className="py-2 text-right text-muted-foreground">—</td>
                </tr>
                <tr className="border-b">
                  <td className="py-2">自社 累積残 (MF 突合対象のみ)</td>
                  <td className="py-2 text-right">¥{fmt(report.payable.selfClosingForMf)}</td>
                  <td className="py-2 text-right">{report.payable.selfMfTargetRowCount}</td>
                </tr>
                <tr className="border-b bg-muted/30 font-medium">
                  <td className="py-2">差分 (MF − 自社 MF対象)</td>
                  <td className={`py-2 text-right ${report.payable.diffForMf === 0 ? 'text-emerald-600' : 'text-amber-700'}`}>
                    ¥{fmt(report.payable.diffForMf)}
                  </td>
                  <td className="py-2 text-right text-muted-foreground">—</td>
                </tr>
                <tr className="border-b text-muted-foreground">
                  <td className="py-2">自社 累積残 (全 row)</td>
                  <td className="py-2 text-right">¥{fmt(report.payable.selfClosingAll)}</td>
                  <td className="py-2 text-right">{report.payable.selfRowCount}</td>
                </tr>
                <tr className="text-muted-foreground">
                  <td className="py-2">差分 (MF − 自社 全)</td>
                  <td className={`py-2 text-right ${report.payable.diffAll === 0 ? 'text-emerald-600' : ''}`}>
                    ¥{fmt(report.payable.diffAll)}
                  </td>
                  <td className="py-2 text-right">—</td>
                </tr>
              </tbody>
            </table>

            <p className="text-xs text-muted-foreground">
              「MF 突合対象」は <code>mf_export_enabled=true</code> または <code>verified_manually=true</code> の row のみ。
              「全 row」はその他 row も含めた自社サマリ合計 (参考値)。
              メインの突合指標は <b>差分 (MF − 自社 MF対象)</b>。
            </p>
            <p className="text-xs text-muted-foreground">
              Phase B' 適用後の closing は <code>opening + change − payment_settled</code> の T 勘定定義。
              自社 DB に期首残高 (2025-06-20 より前の累積) が入っていないため、
              <b>約 ¥14,700,000 (MF 2025-05-20 時点買掛金残)</b> 程度の差が残る想定。これは Phase B'' (期首残注入) スコープ。
            </p>
            <p className="text-xs text-muted-foreground">
              仕入先別ドリルダウンは本 Phase 未対応。
              MF 試算表は account 単位で sub_account 粒度を含まないため、
              内訳特定は /journals 累積 fallback で後続対応予定。
            </p>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
