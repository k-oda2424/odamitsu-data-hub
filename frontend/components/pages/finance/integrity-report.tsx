'use client'

import { useCallback, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useRouter, useSearchParams } from 'next/navigation'
import Link from 'next/link'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatCurrency } from '@/lib/utils'
import { AlertCircle, Loader2, RefreshCw, Search } from 'lucide-react'
import { toast } from 'sonner'
import type { IntegrityReportResponse } from '@/types/integrity-report'
import { MISMATCH_SEVERITY_CLASS, MISMATCH_SEVERITY_LABEL } from '@/types/integrity-report'
import { defaultFromMonth, defaultToMonth } from '@/types/accounts-payable-ledger'

/**
 * 買掛帳 整合性レポート画面 (軸 B + 軸 C)。
 * 期間内の全 supplier を一括診断し、MF 側のみ / 自社側のみ / 金額差 / MF 未登録 を 4 タブで表示。
 * 設計書: claudedocs/design-integrity-report.md §9
 */
export function IntegrityReportPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const router = useRouter()
  const urlParams = useSearchParams()

  // 整合性レポートは期間を短めに (デフォルト 6 ヶ月、最大 12 ヶ月)
  const initialToMonth = urlParams.get('toMonth') || defaultToMonth()
  const initialFromMonth = urlParams.get('fromMonth') || shortDefaultFromMonth(initialToMonth)

  const [shopNo, setShopNo] = useState<number | undefined>(
    urlParams.get('shopNo') ? Number(urlParams.get('shopNo'))
      : isAdmin ? undefined : user?.shopNo,
  )
  const [fromMonth, setFromMonth] = useState<string>(initialFromMonth)
  const [toMonth, setToMonth] = useState<string>(initialToMonth)
  const [report, setReport] = useState<IntegrityReportResponse | null>(null)

  const shopsQuery = useShops(isAdmin)
  const shopOptions = (shopsQuery.data ?? []).map((s) => ({
    value: String(s.shopNo),
    label: `${s.shopNo}: ${s.shopName}`,
  }))

  const updateUrl = useCallback(() => {
    const sp = new URLSearchParams()
    if (shopNo !== undefined) sp.set('shopNo', String(shopNo))
    sp.set('fromMonth', fromMonth)
    sp.set('toMonth', toMonth)
    router.replace(`?${sp.toString()}`, { scroll: false })
  }, [router, shopNo, fromMonth, toMonth])

  const runMutation = useMutation({
    mutationFn: async (refresh: boolean = false) => {
      if (shopNo === undefined) {
        throw new Error('ショップを選択してください')
      }
      const sp = new URLSearchParams()
      sp.set('shopNo', String(shopNo))
      sp.set('fromMonth', fromMonth)
      sp.set('toMonth', toMonth)
      if (refresh) sp.set('refresh', 'true')
      return api.get<IntegrityReportResponse>(`/finance/accounts-payable/integrity-report?${sp.toString()}`)
    },
    onSuccess: (res, refresh) => {
      setReport(res)
      const s = res.summary
      const total = s.mfOnlyCount + s.selfOnlyCount + s.amountMismatchCount
      const prefix = refresh ? 'MF API から再取得しました。' : ''
      if (total === 0 && s.unmatchedSupplierCount === 0) {
        toast.success(`${prefix}全 supplier で MF と整合しました。`)
      } else {
        toast.warning(`${prefix}MF 側のみ ${s.mfOnlyCount} / 自社側のみ ${s.selfOnlyCount} / 金額差 ${s.amountMismatchCount} 件、要確認。`)
      }
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) {
        toast.error('MF 認証エラー。「MF 連携状況」画面で再認証してください。')
      } else if (e instanceof ApiError && e.status === 403) {
        toast.error('MF scope 不足です。「MF 連携状況」で scope 更新 + 再認証してください。')
      } else if (e instanceof ApiError && e.status === 400) {
        toast.error(`入力エラー: ${e.message}`)
      } else {
        toast.error(e.message)
      }
    },
  })

  const handleSearch = (refresh = false) => {
    if (shopNo === undefined) {
      toast.warning('ショップを選択してください')
      return
    }
    updateUrl()
    runMutation.mutate(refresh)
  }

  const handleRefresh = () => {
    if (!window.confirm('MF API から最新データを再取得します。10〜15 秒かかる場合があります。続行しますか?')) return
    handleSearch(true)
  }

  const gotoLedger = (supplierNo?: number | null) => {
    if (!supplierNo || shopNo === undefined) return
    const sp = new URLSearchParams()
    sp.set('shopNo', String(shopNo))
    sp.set('supplierNo', String(supplierNo))
    sp.set('fromMonth', fromMonth)
    sp.set('toMonth', toMonth)
    window.open(`/finance/accounts-payable-ledger?${sp.toString()}`, '_blank')
  }

  return (
    <div className="space-y-4">
      <PageHeader title="買掛帳 整合性レポート (全仕入先)" />

      <Card>
        <CardContent className="pt-4 space-y-3">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
            {isAdmin && (
              <div>
                <Label>ショップ *</Label>
                <SearchableSelect
                  value={shopNo !== undefined ? String(shopNo) : ''}
                  onValueChange={(v) => setShopNo(v ? Number(v) : undefined)}
                  options={shopOptions}
                  placeholder="選択してください"
                  clearable
                />
              </div>
            )}
            <div>
              <Label htmlFor="from-month">開始月</Label>
              <Input id="from-month" type="month" value={fromMonth.slice(0, 7)}
                     onChange={(e) => setFromMonth(`${e.target.value}-20`)} />
            </div>
            <div>
              <Label htmlFor="to-month">終了月</Label>
              <Input id="to-month" type="month" value={toMonth.slice(0, 7)}
                     onChange={(e) => setToMonth(`${e.target.value}-20`)} />
            </div>
            <div className="flex items-end gap-2">
              <Button onClick={() => handleSearch(false)} disabled={runMutation.isPending}>
                {runMutation.isPending
                  ? <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                  : <Search className="mr-1 h-4 w-4" />}
                整合性チェック
              </Button>
              <Button variant="outline" onClick={handleRefresh} disabled={runMutation.isPending}
                      title="MF API から再取得 (キャッシュ無視)">
                <RefreshCw className="mr-1 h-4 w-4" />
                最新取得
              </Button>
            </div>
          </div>
          <p className="text-xs text-muted-foreground">
            通常は月単位キャッシュから表示 (初回のみ MF API 通信)。MF 側で仕訳を変更した後は「最新取得」で再取得してください。
          </p>
          {report?.fetchedAt && (
            <p className="text-xs text-muted-foreground">
              取得日時: {new Date(report.fetchedAt).toLocaleString('ja-JP', { dateStyle: 'short', timeStyle: 'short' })}
              <span className="ml-2">(期間内の最古キャッシュ時刻)</span>
            </p>
          )}
        </CardContent>
      </Card>

      {report && (
        <>
          <Card>
            <CardContent className="pt-4">
              <div className="grid grid-cols-2 gap-2 text-sm md:grid-cols-5">
                <SummaryTile label="MF 側のみ" count={report.summary.mfOnlyCount} amount={report.summary.totalMfOnlyAmount} color="red" />
                <SummaryTile label="自社側のみ" count={report.summary.selfOnlyCount} amount={report.summary.totalSelfOnlyAmount} color="red" />
                <SummaryTile label="金額差" count={report.summary.amountMismatchCount} amount={report.summary.totalMismatchAmount} color="amber" />
                <SummaryTile label="MF 未登録 supplier" count={report.summary.unmatchedSupplierCount} color="slate" />
                <div className="flex flex-col justify-center text-xs text-muted-foreground">
                  journals: {report.totalJournalCount} 件 / supplier: {report.supplierCount}
                </div>
              </div>
            </CardContent>
          </Card>

          <Tabs defaultValue="mfOnly">
            <TabsList>
              <TabsTrigger value="mfOnly">MF 側のみ ({report.mfOnly.length})</TabsTrigger>
              <TabsTrigger value="selfOnly">自社側のみ ({report.selfOnly.length})</TabsTrigger>
              <TabsTrigger value="mismatch">金額差 ({report.amountMismatch.length})</TabsTrigger>
              <TabsTrigger value="unmatched">MF 未登録 ({report.unmatchedSuppliers.length})</TabsTrigger>
            </TabsList>

            <TabsContent value="mfOnly" className="mt-3">
              <Card><CardContent className="pt-4 overflow-x-auto">
                <table className="w-full text-sm tabular-nums">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-2">月</th>
                      <th className="py-2">MF sub_account</th>
                      <th className="py-2 text-right">credit</th>
                      <th className="py-2 text-right">debit</th>
                      <th className="py-2 text-right">delta</th>
                      <th className="py-2 text-right">branch 数</th>
                      <th className="py-2">推定仕入先</th>
                      <th className="py-2">備考</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.mfOnly.length === 0 && <tr><td colSpan={8} className="py-6 text-center text-muted-foreground">該当なし</td></tr>}
                    {report.mfOnly.map((e, i) => (
                      <tr key={i} className="border-b hover:bg-red-50 cursor-pointer"
                          onClick={() => gotoLedger(e.guessedSupplierNo)}>
                        <td className="py-2">{e.transactionMonth}</td>
                        <td className="py-2">{e.subAccountName}</td>
                        <td className="py-2 text-right">{formatCurrency(e.creditAmount)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.debitAmount)}</td>
                        <td className="py-2 text-right font-medium">{formatCurrency(e.periodDelta)}</td>
                        <td className="py-2 text-right">{e.branchCount}</td>
                        <td className="py-2">{e.guessedSupplierCode ?? '-'}</td>
                        <td className="py-2 text-xs text-muted-foreground">{e.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent></Card>
            </TabsContent>

            <TabsContent value="selfOnly" className="mt-3">
              <Card><CardContent className="pt-4 overflow-x-auto">
                <table className="w-full text-sm tabular-nums">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-2">月</th>
                      <th className="py-2">仕入先コード</th>
                      <th className="py-2">仕入先名</th>
                      <th className="py-2 text-right">self delta</th>
                      <th className="py-2 text-right">仕入</th>
                      <th className="py-2 text-right">支払反映</th>
                      <th className="py-2 text-right">税率行数</th>
                      <th className="py-2">備考</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.selfOnly.length === 0 && <tr><td colSpan={8} className="py-6 text-center text-muted-foreground">該当なし</td></tr>}
                    {report.selfOnly.map((e, i) => (
                      <tr key={i} className="border-b hover:bg-red-50 cursor-pointer"
                          onClick={() => gotoLedger(e.supplierNo)}>
                        <td className="py-2">{e.transactionMonth}</td>
                        <td className="py-2">{e.supplierCode}</td>
                        <td className="py-2 text-blue-600 hover:underline">{e.supplierName}</td>
                        <td className="py-2 text-right font-medium">{formatCurrency(e.selfDelta)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.changeTaxIncluded)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.paymentSettledTaxIncluded)}</td>
                        <td className="py-2 text-right">{e.taxRateRowCount}</td>
                        <td className="py-2 text-xs text-muted-foreground">{e.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent></Card>
            </TabsContent>

            <TabsContent value="mismatch" className="mt-3">
              <Card><CardContent className="pt-4 overflow-x-auto">
                <table className="w-full text-sm tabular-nums">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-2">月</th>
                      <th className="py-2">仕入先コード</th>
                      <th className="py-2">仕入先名</th>
                      <th className="py-2 text-right">self delta</th>
                      <th className="py-2 text-right">MF delta</th>
                      <th className="py-2 text-right">差 (self - MF)</th>
                      <th className="py-2">severity</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.amountMismatch.length === 0 && <tr><td colSpan={7} className="py-6 text-center text-muted-foreground">該当なし</td></tr>}
                    {report.amountMismatch.map((e, i) => (
                      <tr key={i} className="border-b hover:bg-amber-50 cursor-pointer"
                          onClick={() => gotoLedger(e.supplierNo)}>
                        <td className="py-2">{e.transactionMonth}</td>
                        <td className="py-2">{e.supplierCode}</td>
                        <td className="py-2 text-blue-600 hover:underline">{e.supplierName}</td>
                        <td className="py-2 text-right">{formatCurrency(e.selfDelta)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.mfDelta)}</td>
                        <td className="py-2 text-right font-medium">{formatCurrency(e.diff)}</td>
                        <td className="py-2">
                          <Badge variant="outline" className={`text-xs ${MISMATCH_SEVERITY_CLASS[e.severity]}`}>
                            {MISMATCH_SEVERITY_LABEL[e.severity]} ({e.severity})
                          </Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent></Card>
            </TabsContent>

            <TabsContent value="unmatched" className="mt-3">
              <Card><CardContent className="pt-4 overflow-x-auto">
                <p className="text-xs text-muted-foreground mb-2">
                  mf_account_master の「買掛金」sub_account に未登録の仕入先です。MF 側で補助科目登録するか、別 sub_account で運用中か確認してください。
                </p>
                <table className="w-full text-sm">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-2">仕入先コード</th>
                      <th className="py-2">仕入先名</th>
                      <th className="py-2">備考</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.unmatchedSuppliers.length === 0 && <tr><td colSpan={3} className="py-6 text-center text-muted-foreground">該当なし</td></tr>}
                    {report.unmatchedSuppliers.map((e, i) => (
                      <tr key={i} className="border-b hover:bg-slate-50 cursor-pointer"
                          onClick={() => gotoLedger(e.supplierNo)}>
                        <td className="py-2">{e.supplierCode}</td>
                        <td className="py-2 text-blue-600 hover:underline">{e.supplierName}</td>
                        <td className="py-2 text-xs text-muted-foreground">{e.reason}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </CardContent></Card>
            </TabsContent>
          </Tabs>

          <p className="text-xs text-muted-foreground">
            row クリックで該当仕入先の買掛帳画面を新規タブで開きます。
          </p>
        </>
      )}
    </div>
  )
}

function SummaryTile({ label, count, amount, color }: {
  label: string; count: number; amount?: number; color: 'red' | 'amber' | 'slate'
}) {
  const textClass = color === 'red' ? 'text-red-700' : color === 'amber' ? 'text-amber-700' : 'text-slate-600'
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-muted-foreground">{label}</span>
      <div className="flex items-baseline gap-2">
        <span className={`font-semibold ${count > 0 ? textClass : ''}`}>{count} 件</span>
        {amount !== undefined && <span className="text-xs tabular-nums text-muted-foreground">¥{amount.toLocaleString('ja-JP')}</span>}
      </div>
    </div>
  )
}

/** 整合性レポートのデフォルト開始月: toMonth の 6 ヶ月前。 */
function shortDefaultFromMonth(toMonthIso: string): string {
  const [y, m] = toMonthIso.split('-').map(Number)
  const d = new Date(y, m - 1 - 6, 20)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-20`
}
