'use client'

import { useCallback, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { useRouter, useSearchParams } from 'next/navigation'
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
import { formatCurrency } from '@/lib/utils'
import { Loader2, RefreshCw, Search } from 'lucide-react'
import { toast } from 'sonner'
import {
  STATUS_CLASS,
  STATUS_LABEL,
  type SupplierBalancesResponse,
  type SupplierBalanceStatus,
} from '@/types/supplier-balances'
import { defaultToMonth } from '@/types/accounts-payable-ledger'

export function SupplierBalancesPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const router = useRouter()
  const urlParams = useSearchParams()

  const [shopNo, setShopNo] = useState<number | undefined>(
    urlParams.get('shopNo') ? Number(urlParams.get('shopNo'))
      : isAdmin ? undefined : user?.shopNo,
  )
  const [asOfMonth, setAsOfMonth] = useState<string>(urlParams.get('asOfMonth') || defaultToMonth())
  const [report, setReport] = useState<SupplierBalancesResponse | null>(null)
  const [statusFilter, setStatusFilter] = useState<Set<SupplierBalanceStatus>>(
    new Set(['MINOR', 'MAJOR', 'MF_MISSING', 'SELF_MISSING']),
  )

  const shopsQuery = useShops(isAdmin)
  const shopOptions = (shopsQuery.data ?? []).map((s) => ({
    value: String(s.shopNo),
    label: `${s.shopNo}: ${s.shopName}`,
  }))

  const updateUrl = useCallback(() => {
    const sp = new URLSearchParams()
    if (shopNo !== undefined) sp.set('shopNo', String(shopNo))
    sp.set('asOfMonth', asOfMonth)
    router.replace(`?${sp.toString()}`, { scroll: false })
  }, [router, shopNo, asOfMonth])

  const runMutation = useMutation({
    mutationFn: async (refresh: boolean = false) => {
      if (shopNo === undefined) throw new Error('ショップを選択してください')
      const sp = new URLSearchParams()
      sp.set('shopNo', String(shopNo))
      sp.set('asOfMonth', asOfMonth)
      if (refresh) sp.set('refresh', 'true')
      return api.get<SupplierBalancesResponse>(`/finance/accounts-payable/supplier-balances?${sp.toString()}`)
    },
    onSuccess: (res, refresh) => {
      setReport(res)
      const s = res.summary
      const issues = s.minorCount + s.majorCount + s.mfMissingCount + s.selfMissingCount
      const prefix = refresh ? 'MF API 再取得: ' : ''
      if (issues === 0) toast.success(`${prefix}全 ${s.totalSuppliers} supplier 一致。`)
      else toast.warning(`${prefix}MAJOR ${s.majorCount} / MINOR ${s.minorCount} / MF欠 ${s.mfMissingCount} / 自社欠 ${s.selfMissingCount}`)
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) toast.error('MF 認証エラー。「MF 連携状況」画面で再認証してください。')
      else if (e instanceof ApiError && e.status === 403) toast.error('MF scope 不足です。「MF 連携状況」で scope 更新してください。')
      else if (e instanceof ApiError && e.status === 400) toast.error(`入力エラー: ${e.message}`)
      else toast.error(e.message)
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
    if (!window.confirm('MF API から最新データを再取得します。10〜15 秒かかる場合があります。')) return
    handleSearch(true)
  }

  const gotoLedger = (supplierNo: number | null) => {
    if (!supplierNo || shopNo === undefined || !report?.asOfMonth) return
    const sp = new URLSearchParams()
    sp.set('shopNo', String(shopNo))
    sp.set('supplierNo', String(supplierNo))
    // 過去 12 ヶ月
    sp.set('fromMonth', report.mfStartDate)
    sp.set('toMonth', report.asOfMonth)
    window.open(`/finance/accounts-payable-ledger?${sp.toString()}`, '_blank')
  }

  const toggleFilter = (status: SupplierBalanceStatus) => {
    const next = new Set(statusFilter)
    if (next.has(status)) next.delete(status)
    else next.add(status)
    setStatusFilter(next)
  }

  const visibleRows = (report?.rows ?? []).filter((r) => statusFilter.has(r.status))

  return (
    <div className="space-y-4">
      <PageHeader title="買掛 supplier 累積残一覧" />

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
              <Label htmlFor="as-of-month">基準月 (20日締め)</Label>
              <Input id="as-of-month" type="month" value={asOfMonth.slice(0, 7)}
                     onChange={(e) => setAsOfMonth(`${e.target.value}-20`)} />
            </div>
            <div className="flex items-end gap-2">
              <Button onClick={() => handleSearch(false)} disabled={runMutation.isPending}>
                {runMutation.isPending ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Search className="mr-1 h-4 w-4" />}
                残高チェック
              </Button>
              <Button variant="outline" onClick={handleRefresh} disabled={runMutation.isPending}
                      title="MF API から再取得 (キャッシュ無視)">
                <RefreshCw className="mr-1 h-4 w-4" /> 最新取得
              </Button>
            </div>
          </div>
          <p className="text-xs text-muted-foreground">
            期首 (2025-05-20) 〜 基準月 の全 supplier 累積残を自社 / MF で突合。MF /journals はキャッシュ共有のため 2 回目以降は高速。
          </p>
          {report?.fetchedAt && (
            <p className="text-xs text-muted-foreground">
              取得日時: {new Date(report.fetchedAt).toLocaleString('ja-JP')}
              {' / '} journals: {report.totalJournalCount} 件
              {' / '} MF 採用開始日: {report.mfStartDate}
            </p>
          )}
        </CardContent>
      </Card>

      {report && (
        <>
          <Card>
            <CardContent className="pt-4">
              <div className="grid grid-cols-2 gap-2 text-sm md:grid-cols-5">
                <SummaryTile label="一致" count={report.summary.matchedCount} color="green"
                             selected={statusFilter.has('MATCH')} onClick={() => toggleFilter('MATCH')} />
                <SummaryTile label="金額差 (軽)" count={report.summary.minorCount} color="amber"
                             selected={statusFilter.has('MINOR')} onClick={() => toggleFilter('MINOR')} />
                <SummaryTile label="金額差 (重)" count={report.summary.majorCount} color="red"
                             selected={statusFilter.has('MAJOR')} onClick={() => toggleFilter('MAJOR')} />
                <SummaryTile label="MF 未計上" count={report.summary.mfMissingCount} color="red"
                             selected={statusFilter.has('MF_MISSING')} onClick={() => toggleFilter('MF_MISSING')} />
                <SummaryTile label="自社未計上" count={report.summary.selfMissingCount} color="red"
                             selected={statusFilter.has('SELF_MISSING')} onClick={() => toggleFilter('SELF_MISSING')} />
              </div>
              <div className="mt-3 grid grid-cols-3 gap-2 text-xs text-muted-foreground">
                <div>self 合計: <span className="font-mono text-foreground">{formatCurrency(report.summary.totalSelfBalance)}</span></div>
                <div>MF 合計: <span className="font-mono text-foreground">{formatCurrency(report.summary.totalMfBalance)}</span></div>
                <div>diff 合計: <span className={`font-mono ${Math.abs(report.summary.totalDiff) > 100 ? 'text-red-700' : 'text-foreground'}`}>{formatCurrency(report.summary.totalDiff)}</span></div>
              </div>
            </CardContent>
          </Card>

          <Card><CardContent className="pt-4 overflow-x-auto">
            <table className="w-full text-sm tabular-nums">
              <thead>
                <tr className="border-b text-left">
                  <th className="py-2">supplier</th>
                  <th className="py-2 text-right">self 残</th>
                  <th className="py-2 text-right">MF 残</th>
                  <th className="py-2 text-right">diff</th>
                  <th className="py-2">status</th>
                  <th className="py-2 text-right">opening</th>
                  <th className="py-2 text-right">Σchange</th>
                  <th className="py-2 text-right">Σpayment</th>
                </tr>
              </thead>
              <tbody>
                {visibleRows.length === 0 && (
                  <tr>
                    <td colSpan={8} className="py-6 text-center text-muted-foreground">該当 supplier なし</td>
                  </tr>
                )}
                {visibleRows.map((r) => (
                  <tr key={`${r.supplierNo ?? 'null'}-${r.mfSubAccountNames[0] ?? ''}`}
                      className="border-b hover:bg-accent cursor-pointer"
                      onClick={() => gotoLedger(r.supplierNo)}>
                    <td className="py-2">
                      <div>{r.supplierName}</div>
                      {r.supplierCode && <div className="text-xs text-muted-foreground">{r.supplierCode}</div>}
                    </td>
                    <td className="py-2 text-right">{formatCurrency(r.selfBalance)}</td>
                    <td className="py-2 text-right">{formatCurrency(r.mfBalance)}</td>
                    <td className={`py-2 text-right font-medium ${Math.abs(r.diff) > 1000 ? 'text-red-700' : Math.abs(r.diff) > 100 ? 'text-amber-700' : 'text-muted-foreground'}`}>
                      {formatCurrency(r.diff)}
                    </td>
                    <td className="py-2">
                      <Badge variant="outline" className={STATUS_CLASS[r.status]}>
                        {STATUS_LABEL[r.status]}
                      </Badge>
                      {r.status === 'MF_MISSING' && !r.masterRegistered && (
                        <span className="ml-1 text-xs text-red-600">(master 未登録)</span>
                      )}
                    </td>
                    <td className="py-2 text-right text-muted-foreground">{formatCurrency(r.selfOpening)}</td>
                    <td className="py-2 text-right text-muted-foreground">{formatCurrency(r.selfChangeCumulative)}</td>
                    <td className="py-2 text-right text-muted-foreground">{formatCurrency(r.selfPaymentCumulative)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </CardContent></Card>
        </>
      )}
    </div>
  )
}

function SummaryTile({
  label, count, color, selected, onClick,
}: { label: string; count: number; color: 'red' | 'amber' | 'slate' | 'green'; selected?: boolean; onClick?: () => void }) {
  const colorClass = {
    red: 'border-red-600 text-red-700',
    amber: 'border-amber-500 text-amber-700',
    slate: 'border-slate-400 text-slate-700',
    green: 'border-green-500 text-green-700',
  }[color]
  return (
    <button
      type="button"
      onClick={onClick}
      className={`flex flex-col items-start rounded border px-3 py-2 text-left transition ${colorClass} ${selected ? 'bg-accent' : 'opacity-50 hover:opacity-100'}`}
    >
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-lg font-semibold">{count}</span>
    </button>
  )
}
