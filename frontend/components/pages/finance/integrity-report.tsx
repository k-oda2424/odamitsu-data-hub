'use client'

import { useCallback, useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useRouter, useSearchParams } from 'next/navigation'
import { api, ApiError } from '@/lib/api-client'
import { handleApiError } from '@/lib/api-error-handler'
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
import { Loader2, RefreshCw, Search } from 'lucide-react'
import { toast } from 'sonner'
import type { IntegrityReportResponse } from '@/types/integrity-report'
import { MISMATCH_SEVERITY_CLASS, MISMATCH_SEVERITY_LABEL } from '@/types/integrity-report'
import { defaultToMonth } from '@/types/accounts-payable-ledger'
import {
  ConsistencyReviewDialog,
  type ConsistencyReviewActionType,
} from './ConsistencyReviewDialog'
import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'

const INTEGRITY_REPORT_QUERY_KEY = 'integrity-report' as const

interface IntegrityQueryArgs {
  shopNo: number
  fromMonth: string
  toMonth: string
  refresh: boolean
}

interface ReviewArgs {
  entryType: 'mfOnly' | 'selfOnly' | 'amountMismatch'
  entryKey: string
  transactionMonth: string
  actionType: ConsistencyReviewActionType
  selfSnapshot: number
  mfSnapshot: number
}

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
  const queryClient = useQueryClient()

  // 整合性レポートは期間を短めに (デフォルト 6 ヶ月、最大 12 ヶ月)
  const initialToMonth = urlParams.get('toMonth') || defaultToMonth()
  const initialFromMonth = urlParams.get('fromMonth') || shortDefaultFromMonth(initialToMonth)

  const [shopNo, setShopNo] = useState<number | undefined>(
    urlParams.get('shopNo') ? Number(urlParams.get('shopNo'))
      : isAdmin ? undefined : user?.shopNo,
  )
  const [fromMonth, setFromMonth] = useState<string>(initialFromMonth)
  const [toMonth, setToMonth] = useState<string>(initialToMonth)
  const [hideReconciled, setHideReconciled] = useState<boolean>(true)
  const [hideReviewed, setHideReviewed] = useState<boolean>(true)
  /** 検索ボタン押下後にセットされる「実際に問い合わせる」入力。null なら未検索状態。 */
  const [queryArgs, setQueryArgs] = useState<IntegrityQueryArgs | null>(null)
  /** Dialog 表示中の差分確認リクエスト。null ならダイアログ非表示。 */
  const [pendingReview, setPendingReview] = useState<ReviewArgs | null>(null)
  /**
   * 「手動検索ボタン押下後の最初の fetch 完了時のみ」toast を出すためのフラグ。
   * review 保存後の {@link invalidateReport} → refetch では発火させない。
   * MJ-02 (toast 多重発火修正, 2026-05-04)
   */
  const [wasSearched, setWasSearched] = useState<boolean>(false)

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

  // 整合性レポート取得 (TanStack Query 標準パターン)。
  // 検索ボタン押下で queryArgs を更新 → useQuery が再フェッチ。
  // review/delete 後は invalidateQueries で再取得。
  const reportQuery = useQuery({
    queryKey: [
      INTEGRITY_REPORT_QUERY_KEY,
      queryArgs?.shopNo,
      queryArgs?.fromMonth,
      queryArgs?.toMonth,
      queryArgs?.refresh,
    ],
    queryFn: async () => {
      if (!queryArgs) throw new Error('queryArgs not set')
      const sp = new URLSearchParams()
      sp.set('shopNo', String(queryArgs.shopNo))
      sp.set('fromMonth', queryArgs.fromMonth)
      sp.set('toMonth', queryArgs.toMonth)
      if (queryArgs.refresh) sp.set('refresh', 'true')
      return api.get<IntegrityReportResponse>(`/finance/accounts-payable/integrity-report?${sp.toString()}`)
    },
    enabled: queryArgs !== null,
    refetchOnWindowFocus: false,
    retry: false,
  })

  // MJ-02: 手動検索ボタン押下後の最初の fetch 完了時のみ summary toast を出す。
  // queryFn 内で toast を出すと invalidateQueries による refetch で多重発火する問題を回避。
  useEffect(() => {
    if (!wasSearched) return
    if (!reportQuery.data || reportQuery.isFetching) return
    const s = reportQuery.data.summary
    const total = s.mfOnlyCount + s.selfOnlyCount + s.amountMismatchCount
    const prefix = queryArgs?.refresh ? 'MF API から再取得しました。' : ''
    if (total === 0 && s.unmatchedSupplierCount === 0) {
      toast.success(`${prefix}全 supplier で MF と整合しました。`)
    } else {
      toast.warning(`${prefix}MF 側のみ ${s.mfOnlyCount} / 自社側のみ ${s.selfOnlyCount} / 金額差 ${s.amountMismatchCount} 件、要確認。`)
    }
    setWasSearched(false)
  }, [wasSearched, reportQuery.data, reportQuery.isFetching, queryArgs?.refresh])

  // useQuery エラーは toast 化 (onError は v5 で削除されたため effect で副作用化)
  useEffect(() => {
    const e = reportQuery.error
    if (!e) return
    if (e instanceof ApiError && e.status === 401) {
      toast.error('MF 認証エラー。「MF 連携状況」画面で再認証してください。')
    } else if (e instanceof ApiError && e.status === 403) {
      toast.error('MF scope 不足です。「MF 連携状況」で scope 更新 + 再認証してください。')
    } else if (e instanceof ApiError && e.status === 400) {
      toast.error(`入力エラー: ${e.message}`)
    } else if (e instanceof Error) {
      toast.error(e.message)
    }
  }, [reportQuery.error])

  const report = reportQuery.data ?? null
  const isFetching = reportQuery.isFetching

  const invalidateReport = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: [INTEGRITY_REPORT_QUERY_KEY] })
  }, [queryClient])

  // 差分確認 POST / DELETE (案 X+Y)
  const reviewMutation = useMutation({
    mutationFn: async (args: ReviewArgs & { note: string }) => {
      return api.post(`/finance/accounts-payable/integrity-report/reviews`, {
        shopNo,
        entryType: args.entryType,
        entryKey: args.entryKey,
        transactionMonth: args.transactionMonth,
        actionType: args.actionType,
        selfSnapshot: args.selfSnapshot,
        mfSnapshot: args.mfSnapshot,
        note: args.note || null,
      })
    },
    onSuccess: (_, v) => {
      toast.success(`${v.actionType === 'MF_APPLY' ? 'MF 金額で自社確定' : '確認済みマーク'}を保存しました`)
      setPendingReview(null)
      invalidateReport()
    },
    onError: (e) => handleApiError(e),
  })

  const reviewDeleteMutation = useMutation({
    mutationFn: async (args: { entryType: string; entryKey: string; transactionMonth: string }) => {
      const sp = new URLSearchParams()
      sp.set('shopNo', String(shopNo))
      sp.set('entryType', args.entryType)
      sp.set('entryKey', args.entryKey)
      sp.set('transactionMonth', args.transactionMonth)
      return api.delete(`/finance/accounts-payable/integrity-report/reviews?${sp.toString()}`)
    },
    onSuccess: () => {
      toast.success('確認履歴を取り消しました')
      invalidateReport()
    },
    onError: (e) => handleApiError(e),
  })

  const confirmReview = (args: ReviewArgs) => {
    setPendingReview(args)
  }

  const handleSearch = (refresh = false) => {
    if (shopNo === undefined) {
      toast.warning('ショップを選択してください')
      return
    }
    updateUrl()
    setWasSearched(true)
    setQueryArgs({ shopNo, fromMonth, toMonth, refresh })
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
              <Button onClick={() => handleSearch(false)} disabled={isFetching}>
                {isFetching
                  ? <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                  : <Search className="mr-1 h-4 w-4" />}
                整合性チェック
              </Button>
              <Button variant="outline" onClick={handleRefresh} disabled={isFetching}
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

      {report && (() => {
        const filterFn = <T extends { reconciledAtPeriodEnd?: boolean; reviewStatus?: string | null; snapshotStale?: boolean }>(arr: T[]) =>
          arr.filter((e) => {
            // 期末解消済みをデフォルト非表示
            if (hideReconciled && e.reconciledAtPeriodEnd) return false
            // 確認済み (review 付与かつ stale でない) をデフォルト非表示
            if (hideReviewed && e.reviewStatus && !e.snapshotStale) return false
            return true
          })
        const mfOnlyView = filterFn(report.mfOnly)
        const selfOnlyView = filterFn(report.selfOnly)
        const mismatchView = filterFn(report.amountMismatch)
        const reconciledTotal = report.summary.reconciledAtPeriodEndCount ?? 0
        return (
        <>
          <Card>
            <CardContent className="pt-4 space-y-3">
              <div className="grid grid-cols-2 gap-2 text-sm md:grid-cols-5">
                <SummaryTile label="MF 側のみ" count={report.summary.mfOnlyCount} amount={report.summary.totalMfOnlyAmount} color="red" />
                <SummaryTile label="自社側のみ" count={report.summary.selfOnlyCount} amount={report.summary.totalSelfOnlyAmount} color="red" />
                <SummaryTile label="金額差" count={report.summary.amountMismatchCount} amount={report.summary.totalMismatchAmount} color="amber" />
                <SummaryTile label="MF 未登録 supplier" count={report.summary.unmatchedSupplierCount} color="slate" />
                <div className="flex flex-col justify-center text-xs text-muted-foreground">
                  journals: {report.totalJournalCount} 件 / supplier: {report.supplierCount}
                </div>
              </div>
              <div className="flex flex-wrap items-center gap-4 pt-2 border-t">
                <label className="flex items-center gap-2 text-xs cursor-pointer">
                  <input type="checkbox" checked={hideReconciled}
                         onChange={(e) => setHideReconciled(e.target.checked)} />
                  期末累積残で解消済み ({reconciledTotal} 件) を隠す
                </label>
                <label className="flex items-center gap-2 text-xs cursor-pointer">
                  <input type="checkbox" checked={hideReviewed}
                         onChange={(e) => setHideReviewed(e.target.checked)} />
                  確認済み (IGNORED / MF_APPLIED、金額不変) を隠す
                </label>
              </div>
            </CardContent>
          </Card>

          <Tabs defaultValue="mfOnly">
            <TabsList>
              <TabsTrigger value="mfOnly">MF 側のみ ({mfOnlyView.length}{hideReconciled && mfOnlyView.length !== report.mfOnly.length ? `/${report.mfOnly.length}` : ''})</TabsTrigger>
              <TabsTrigger value="selfOnly">自社側のみ ({selfOnlyView.length}{hideReconciled && selfOnlyView.length !== report.selfOnly.length ? `/${report.selfOnly.length}` : ''})</TabsTrigger>
              <TabsTrigger value="mismatch">金額差 ({mismatchView.length}{hideReconciled && mismatchView.length !== report.amountMismatch.length ? `/${report.amountMismatch.length}` : ''})</TabsTrigger>
              <TabsTrigger value="unmatched">MF 未登録 ({report.unmatchedSuppliers.length})</TabsTrigger>
            </TabsList>

            <TabsContent value="mfOnly" className="mt-3">
              <Card><CardContent className="pt-4 overflow-x-auto">
                <table className="w-full text-sm tabular-nums">
                  <thead>
                    <tr className="border-b text-left">
                      <th className="py-2">月</th>
                      <th className="py-2">MF 補助科目</th>
                      <th className="py-2 text-right">貸方<AmountSourceTooltip source="MF_JOURNAL" /></th>
                      <th className="py-2 text-right">借方<AmountSourceTooltip source="MF_JOURNAL" /></th>
                      <th className="py-2 text-right">差額<AmountSourceTooltip source="MF_JOURNAL" /></th>
                      <th className="py-2 pr-4 text-right">明細数</th>
                      <th className="py-2 pl-2">MF 取引番号</th>
                      <th className="py-2">推定仕入先</th>
                      <th className="py-2 text-right">累積差 ({report.toMonth})</th>
                      <th className="py-2">備考</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mfOnlyView.length === 0 && <tr><td colSpan={10} className="py-6 text-center text-muted-foreground">該当なし</td></tr>}
                    {mfOnlyView.map((e, i) => (
                      <tr key={i} className={`border-b cursor-pointer ${e.reconciledAtPeriodEnd ? 'opacity-60 hover:bg-slate-50' : 'hover:bg-red-50'}`}
                          onClick={() => gotoLedger(e.guessedSupplierNo)}>
                        <td className="py-2">{e.transactionMonth}{e.reconciledAtPeriodEnd && <span className="ml-1 text-[10px] text-green-700">[解消済]</span>}</td>
                        <td className="py-2">{e.subAccountName}</td>
                        <td className="py-2 text-right">{formatCurrency(e.creditAmount)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.debitAmount)}</td>
                        <td className="py-2 text-right font-medium">{formatCurrency(e.periodDelta)}</td>
                        <td className="py-2 pr-4 text-right">{e.branchCount}</td>
                        <td className="py-2 pl-2 text-xs font-mono text-muted-foreground">
                          {e.journalNumbers && e.journalNumbers.length > 0
                            ? e.journalNumbers.length <= 5
                              ? e.journalNumbers.join(', ')
                              : `${e.journalNumbers.slice(0, 5).join(', ')} 他 ${e.journalNumbers.length - 5} 件`
                            : '-'}
                        </td>
                        <td className="py-2">{e.guessedSupplierCode ?? '-'}</td>
                        <td className={`py-2 text-right font-medium ${
                          e.supplierCumulativeDiff == null ? 'text-muted-foreground'
                          : e.supplierCumulativeDiff === 0 ? 'text-green-700'
                          : Math.abs(e.supplierCumulativeDiff) > 1000 ? 'text-red-700' : 'text-amber-700'
                        }`}>
                          {e.supplierCumulativeDiff == null ? '-' : formatCurrency(e.supplierCumulativeDiff)}
                        </td>
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
                      <th className="py-2 text-right">自社差額<AmountSourceTooltip source="CLOSING_CALC" /></th>
                      <th className="py-2 text-right">仕入<AmountSourceTooltip source="PAYABLE_SUMMARY" /></th>
                      <th className="py-2 text-right">支払反映<AmountSourceTooltip source="VERIFIED_AMOUNT" /></th>
                      <th className="py-2 text-right">税率行数</th>
                      <th className="py-2 text-right">累積差 ({report.toMonth})</th>
                      <th className="py-2">備考</th>
                    </tr>
                  </thead>
                  <tbody>
                    {selfOnlyView.length === 0 && <tr><td colSpan={9} className="py-6 text-center text-muted-foreground">該当なし</td></tr>}
                    {selfOnlyView.map((e, i) => (
                      <tr key={i} className={`border-b cursor-pointer ${e.reconciledAtPeriodEnd ? 'opacity-60 hover:bg-slate-50' : 'hover:bg-red-50'}`}
                          onClick={() => gotoLedger(e.supplierNo)}>
                        <td className="py-2">{e.transactionMonth}{e.reconciledAtPeriodEnd && <span className="ml-1 text-[10px] text-green-700">[解消済]</span>}</td>
                        <td className="py-2">{e.supplierCode}</td>
                        <td className="py-2 text-blue-600 hover:underline">{e.supplierName}</td>
                        <td className="py-2 text-right font-medium">{formatCurrency(e.selfDelta)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.changeTaxIncluded)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.paymentSettledTaxIncluded)}</td>
                        <td className="py-2 text-right">{e.taxRateRowCount}</td>
                        <td className={`py-2 text-right font-medium ${
                          e.supplierCumulativeDiff == null ? 'text-muted-foreground'
                          : e.supplierCumulativeDiff === 0 ? 'text-green-700'
                          : Math.abs(e.supplierCumulativeDiff) > 1000 ? 'text-red-700' : 'text-amber-700'
                        }`}>
                          {e.supplierCumulativeDiff == null ? '-' : formatCurrency(e.supplierCumulativeDiff)}
                        </td>
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
                      <th className="py-2 text-right">自社差額<AmountSourceTooltip source="CLOSING_CALC" /></th>
                      <th className="py-2 text-right">MF 差額<AmountSourceTooltip source="MF_JOURNAL" /></th>
                      <th className="py-2 text-right">差 (自社 − MF)</th>
                      <th className="py-2 text-right">累積差 ({report.toMonth})</th>
                      <th className="py-2">重大度</th>
                    </tr>
                  </thead>
                  <tbody>
                    {mismatchView.length === 0 && <tr><td colSpan={8} className="py-6 text-center text-muted-foreground">該当なし</td></tr>}
                    {mismatchView.map((e, i) => (
                      <tr key={i} className={`border-b cursor-pointer ${e.reconciledAtPeriodEnd ? 'opacity-60 hover:bg-slate-50' : 'hover:bg-amber-50'}`}
                          onClick={() => gotoLedger(e.supplierNo)}>
                        <td className="py-2">{e.transactionMonth}{e.reconciledAtPeriodEnd && <span className="ml-1 text-[10px] text-green-700">[解消済]</span>}</td>
                        <td className="py-2">{e.supplierCode}</td>
                        <td className="py-2 text-blue-600 hover:underline">{e.supplierName}</td>
                        <td className="py-2 text-right">{formatCurrency(e.selfDelta)}</td>
                        <td className="py-2 text-right">{formatCurrency(e.mfDelta)}</td>
                        <td className="py-2 text-right font-medium">{formatCurrency(e.diff)}</td>
                        <td className={`py-2 text-right font-medium ${
                          e.supplierCumulativeDiff == null
                            ? 'text-muted-foreground'
                            : e.supplierCumulativeDiff === 0
                            ? 'text-green-700'
                            : Math.abs(e.supplierCumulativeDiff) > 1000
                            ? 'text-red-700'
                            : 'text-amber-700'
                        }`}>
                          {e.supplierCumulativeDiff == null ? '-' : formatCurrency(e.supplierCumulativeDiff)}
                        </td>
                        <td className="py-2">
                          <div className="flex flex-wrap items-center gap-1">
                            <Badge variant="outline" className={`text-xs ${MISMATCH_SEVERITY_CLASS[e.severity]}`}>
                              {MISMATCH_SEVERITY_LABEL[e.severity]} ({e.severity})
                            </Badge>
                            {e.reviewStatus && (
                              <Badge variant="outline" className={`text-xs ${e.snapshotStale ? 'border-amber-500 text-amber-700' : 'border-green-500 text-green-700'}`}
                                     title={`${e.reviewedByName ?? ''} ${e.reviewedAt ?? ''} ${e.reviewNote ?? ''}${e.snapshotStale ? ' (金額変動 — 要再確認)' : ''}`}>
                                {e.reviewStatus === 'MF_APPLIED' ? '✓MF確定' : '✓確認'}{e.snapshotStale ? '⚠' : ''}
                              </Badge>
                            )}
                            {!e.reviewStatus && e.supplierNo && (
                              <div className="flex gap-1" onClick={(ev) => ev.stopPropagation()}>
                                <button className="text-[10px] text-blue-600 hover:underline"
                                  onClick={() => confirmReview({
                                    entryType: 'amountMismatch', entryKey: String(e.supplierNo),
                                    transactionMonth: e.transactionMonth, actionType: 'IGNORE',
                                    selfSnapshot: e.selfDelta, mfSnapshot: e.mfDelta,
                                  })}>確認済</button>
                                <button className="text-[10px] text-red-600 hover:underline"
                                  onClick={() => confirmReview({
                                    entryType: 'amountMismatch', entryKey: String(e.supplierNo),
                                    transactionMonth: e.transactionMonth, actionType: 'MF_APPLY',
                                    selfSnapshot: e.selfDelta, mfSnapshot: e.mfDelta,
                                  })}>MF確定</button>
                              </div>
                            )}
                            {e.reviewStatus && e.supplierNo && (
                              <button className="text-[10px] text-slate-500 hover:underline"
                                onClick={(ev) => {
                                  ev.stopPropagation()
                                  if (window.confirm('確認履歴を取り消しますか?')) {
                                    reviewDeleteMutation.mutate({
                                      entryType: 'amountMismatch',
                                      entryKey: String(e.supplierNo),
                                      transactionMonth: e.transactionMonth,
                                    })
                                  }
                                }}>取消</button>
                            )}
                          </div>
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
        )
      })()}

      <ConsistencyReviewDialog
        open={pendingReview !== null}
        onOpenChange={(o) => { if (!o) setPendingReview(null) }}
        actionLabel={pendingReview?.actionType === 'MF_APPLY'
          ? 'MF 金額で自社 verified_amount を上書き'
          : '確認済みとしてマーク (副作用なし)'}
        description={pendingReview
          ? `対象: ${pendingReview.entryType} / ${pendingReview.transactionMonth} / key=${pendingReview.entryKey}`
          : undefined}
        submitting={reviewMutation.isPending}
        onConfirm={(note) => {
          if (!pendingReview) return
          reviewMutation.mutate({ ...pendingReview, note })
        }}
      />
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
