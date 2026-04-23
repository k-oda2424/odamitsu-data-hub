'use client'

import { useCallback, useMemo, useState } from 'react'
import { useQuery, useMutation } from '@tanstack/react-query'
import { useRouter, useSearchParams } from 'next/navigation'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { usePaymentSuppliers, useShops } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { formatCurrency } from '@/lib/utils'
import { AlertCircle, CheckCircle2, Loader2, Play, RefreshCw, Search } from 'lucide-react'
import { toast } from 'sonner'
import {
  ANOMALY_BADGE_CLASS,
  ANOMALY_SHORT_LABEL,
  type AccountsPayableLedgerResponse,
  type MfSupplierLedgerResponse,
  defaultFromMonth,
  defaultToMonth,
  highestSeverity,
  rowBgClass,
} from '@/types/accounts-payable-ledger'
import { computeMfMatchStatus } from '@/types/integrity-report'
import Link from 'next/link'

/**
 * 買掛帳: 1 仕入先の月次推移画面。
 * 設計書: claudedocs/design-accounts-payable-ledger.md §8
 */
export function AccountsPayableLedgerPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const router = useRouter()
  const urlParams = useSearchParams()

  const initialToMonth = defaultToMonth()
  const initialFromMonth = defaultFromMonth(initialToMonth)

  // 入力値 (pending) と確定値 (committed) を分離 (M1 反映)
  const [shopNo, setShopNo] = useState<number | undefined>(
    urlParams.get('shopNo') ? Number(urlParams.get('shopNo'))
      : isAdmin ? undefined : user?.shopNo,
  )
  const [supplierNo, setSupplierNo] = useState<number | undefined>(
    urlParams.get('supplierNo') ? Number(urlParams.get('supplierNo')) : undefined,
  )
  const [fromMonth, setFromMonth] = useState<string>(
    urlParams.get('fromMonth') || initialFromMonth,
  )
  const [toMonth, setToMonth] = useState<string>(
    urlParams.get('toMonth') || initialToMonth,
  )

  // 確定値 (検索ボタン押下 or URL 初期値)
  const [committed, setCommitted] = useState<{
    shopNo?: number; supplierNo?: number; fromMonth: string; toMonth: string
  }>(() => ({
    shopNo: urlParams.get('shopNo') ? Number(urlParams.get('shopNo')) : (isAdmin ? undefined : user?.shopNo),
    supplierNo: urlParams.get('supplierNo') ? Number(urlParams.get('supplierNo')) : undefined,
    fromMonth: urlParams.get('fromMonth') || initialFromMonth,
    toMonth: urlParams.get('toMonth') || initialToMonth,
  }))

  // URL 同期
  const updateUrl = useCallback((params: {
    shopNo?: number; supplierNo?: number; fromMonth?: string; toMonth?: string
  }) => {
    const sp = new URLSearchParams()
    if (params.shopNo !== undefined) sp.set('shopNo', String(params.shopNo))
    if (params.supplierNo !== undefined) sp.set('supplierNo', String(params.supplierNo))
    if (params.fromMonth) sp.set('fromMonth', params.fromMonth)
    if (params.toMonth) sp.set('toMonth', params.toMonth)
    const qs = sp.toString()
    router.replace(qs ? `?${qs}` : window.location.pathname, { scroll: false })
  }, [router])

  const shopsQuery = useShops(isAdmin)
  const paymentSuppliersQuery = usePaymentSuppliers(shopNo)

  const shopOptions = (shopsQuery.data ?? []).map((s) => ({
    value: String(s.shopNo),
    label: `${s.shopNo}: ${s.shopName}`,
  }))
  const supplierOptions = (paymentSuppliersQuery.data ?? []).map((s) => ({
    value: String(s.paymentSupplierNo),
    label: `${s.paymentSupplierCode ?? ''} ${s.paymentSupplierName}`.trim(),
  }))

  const enabled = committed.supplierNo !== undefined && committed.shopNo !== undefined
  const ledgerQuery = useQuery({
    queryKey: ['accounts-payable-ledger', committed.shopNo, committed.supplierNo, committed.fromMonth, committed.toMonth],
    queryFn: () => {
      const sp = new URLSearchParams()
      sp.set('shopNo', String(committed.shopNo))
      sp.set('supplierNo', String(committed.supplierNo))
      sp.set('fromMonth', committed.fromMonth)
      sp.set('toMonth', committed.toMonth)
      return api.get<AccountsPayableLedgerResponse>(`/finance/accounts-payable/ledger?${sp.toString()}`)
    },
    enabled,
  })

  const [mfLedger, setMfLedger] = useState<MfSupplierLedgerResponse | null>(null)
  const mfMutation = useMutation({
    mutationFn: async (refresh: boolean = false) => {
      const sp = new URLSearchParams()
      sp.set('shopNo', String(committed.shopNo))
      sp.set('supplierNo', String(committed.supplierNo))
      sp.set('fromMonth', committed.fromMonth)
      sp.set('toMonth', committed.toMonth)
      if (refresh) sp.set('refresh', 'true')
      return api.get<MfSupplierLedgerResponse>(`/finance/accounts-payable/ledger/mf?${sp.toString()}`)
    },
    onSuccess: (res, refresh) => {
      setMfLedger(res)
      const prefix = refresh ? 'MF API 再取得: ' : ''
      if (res.matchedSubAccountNames.length === 0) {
        toast.warning(`${prefix}MF 側で対応する sub_account が見つかりませんでした`)
      } else {
        toast.success(`${prefix}MF 取得完了 (journals ${res.totalJournalCount} 件)`)
      }
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) {
        toast.error('MF 認証エラー。「MF連携状況」画面で再認証してください。')
      } else if (e instanceof ApiError && e.status === 403) {
        toast.error('MF scope 不足です。「MF連携状況」画面で scope 更新 + 再認証してください。')
      } else {
        toast.error(e.message)
      }
    },
  })

  const handleSearch = () => {
    if (!shopNo || !supplierNo) {
      toast.warning('仕入先を選択してください')
      return
    }
    if (new Date(fromMonth) > new Date(toMonth)) {
      toast.error('開始月は終了月以前である必要があります')
      return
    }
    setCommitted({ shopNo, supplierNo, fromMonth, toMonth })
    updateUrl({ shopNo, supplierNo, fromMonth, toMonth })
    setMfLedger(null) // 検索条件変化時は MF 結果クリア
  }

  // MF delta を月キーで lookup
  const mfDeltaByMonth = useMemo(() => {
    const m = new Map<string, number>()
    for (const r of mfLedger?.rows ?? []) m.set(r.transactionMonth, r.mfPeriodDelta)
    return m
  }, [mfLedger])

  const data = ledgerQuery.data

  return (
    <div className="space-y-4">
      <PageHeader title="買掛帳" />

      {/* 検索フォーム */}
      <Card>
        <CardContent className="pt-4 space-y-3">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-5">
            {isAdmin && (
              <div>
                <Label>ショップ</Label>
                <SearchableSelect
                  value={shopNo !== undefined ? String(shopNo) : ''}
                  onValueChange={(v) => {
                    setShopNo(v ? Number(v) : undefined)
                    setSupplierNo(undefined)
                  }}
                  options={shopOptions}
                  placeholder="選択してください"
                  clearable
                />
              </div>
            )}
            <div className={isAdmin ? '' : 'md:col-span-2'}>
              <Label>仕入先 *</Label>
              <SearchableSelect
                value={supplierNo !== undefined ? String(supplierNo) : ''}
                onValueChange={(v) => setSupplierNo(v ? Number(v) : undefined)}
                options={supplierOptions}
                placeholder="選択してください"
                clearable
              />
            </div>
            <div>
              <Label htmlFor="from-month">開始月</Label>
              <Input
                id="from-month"
                type="month"
                value={fromMonth.slice(0, 7)}
                onChange={(e) => setFromMonth(`${e.target.value}-20`)}
              />
            </div>
            <div>
              <Label htmlFor="to-month">終了月</Label>
              <Input
                id="to-month"
                type="month"
                value={toMonth.slice(0, 7)}
                onChange={(e) => setToMonth(`${e.target.value}-20`)}
              />
            </div>
            <div className="flex items-end">
              <Button onClick={handleSearch} disabled={!supplierNo}>
                <Search className="mr-1 h-4 w-4" />
                検索
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {ledgerQuery.isLoading && enabled && <LoadingSpinner />}
      {ledgerQuery.isError && <ErrorMessage onRetry={() => ledgerQuery.refetch()} />}

      {data && (
        <>
          {/* 仕入先情報 + サマリ */}
          <Card>
            <CardContent className="pt-4 space-y-2">
              <div className="flex items-center gap-3">
                <span className="text-muted-foreground text-xs">仕入先</span>
                <span className="font-semibold">
                  {data.supplier.supplierCode} {data.supplier.supplierName}
                </span>
              </div>
              <div className="grid grid-cols-2 gap-2 text-sm md:grid-cols-6 tabular-nums">
                <InfoTile
                  label={`期間開始 opening (${data.fromMonth})`}
                  value={data.rows[0]?.openingBalanceTaxIncluded ?? 0}
                  hint="期首残 (DB backfill で算出)"
                />
                <InfoTile label="期間累計 仕入" value={data.summary.totalChangeTaxIncluded} />
                <InfoTile label="期間累計 検証" value={data.summary.totalVerified} />
                <InfoTile label="期間累計 支払反映" value={data.summary.totalPaymentSettled} />
                <InfoTile label="最終残 (closing)" value={data.summary.finalClosing} emphasize />
                <div className="flex flex-col gap-0.5">
                  <span className="text-xs text-muted-foreground">警告</span>
                  <span className="text-xs">
                    未検証 {data.summary.unverifiedMonthCount}件 / 値引繰越 {data.summary.negativeClosingMonthCount}件
                    {data.summary.continuityBreakCount > 0 && (
                      <span className="text-red-600"> / 不整合 {data.summary.continuityBreakCount}件</span>
                    )}
                    {data.summary.monthGapCount > 0 && (
                      <span className="text-orange-600"> / 月抜け {data.summary.monthGapCount}件</span>
                    )}
                  </span>
                </div>
              </div>
            </CardContent>
          </Card>

          {/* MF 比較トリガ + 整合性レポートリンク */}
          <Card>
            <CardContent className="pt-4 flex flex-wrap items-center gap-3">
              <Button
                variant="outline"
                onClick={() => mfMutation.mutate(false)}
                disabled={mfMutation.isPending}
              >
                {mfMutation.isPending ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Play className="mr-1 h-4 w-4" />}
                MF と比較を取得
              </Button>
              {mfLedger && (
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => {
                    if (window.confirm('MF API から最新データを再取得します。続行しますか?')) {
                      mfMutation.mutate(true)
                    }
                  }}
                  disabled={mfMutation.isPending}
                  title="MF API から再取得 (キャッシュ無視)"
                >
                  <RefreshCw className="mr-1 h-3 w-3" /> 最新取得
                </Button>
              )}
              {mfLedger && (
                <span className="text-xs text-muted-foreground">
                  matched: {mfLedger.matchedSubAccountNames.join(', ') || '(なし)'} / journals {mfLedger.totalJournalCount} 件
                  {mfLedger.fetchedAt && (
                    <span className="ml-2">
                      / 取得: {new Date(mfLedger.fetchedAt).toLocaleString('ja-JP', { dateStyle: 'short', timeStyle: 'short' })}
                    </span>
                  )}
                </span>
              )}
              {mfLedger && mfLedger.unmatchedCandidates.length > 0 && (
                <span className="flex items-center gap-1 text-xs text-amber-700">
                  <AlertCircle className="h-3 w-3" /> MF 側で {mfLedger.unmatchedCandidates.join(', ')} が見つかりません
                </span>
              )}
              <div className="ml-auto">
                <Link
                  href={`/finance/accounts-payable-ledger/integrity?shopNo=${committed.shopNo ?? ''}&fromMonth=${committed.fromMonth}&toMonth=${committed.toMonth}`}
                  className="text-xs text-blue-600 hover:underline"
                >
                  整合性レポート (全仕入先) →
                </Link>
              </div>
            </CardContent>
          </Card>

          {/* 月次明細テーブル */}
          <Card>
            <CardContent className="pt-4 overflow-x-auto">
              <table className="w-full text-sm tabular-nums">
                <thead>
                  <tr className="border-b text-left">
                    <th className="py-2">月</th>
                    <th className="py-2 text-right">前月繰越</th>
                    <th className="py-2 text-right">仕入</th>
                    <th className="py-2 text-right">検証額</th>
                    <th className="py-2 text-right">支払反映</th>
                    <th className="py-2 text-right">当月残</th>
                    {mfLedger && (
                      <>
                        <th className="py-2 text-right">MF delta</th>
                        <th className="py-2 text-right">Δ(自社-MF)</th>
                      </>
                    )}
                    <th className="py-2">ステータス</th>
                  </tr>
                </thead>
                <tbody>
                  {data.rows.length === 0 && (
                    <tr>
                      <td colSpan={mfLedger ? 9 : 7} className="py-6 text-center text-muted-foreground">
                        期間内にデータがありません
                      </td>
                    </tr>
                  )}
                  {data.rows.map((row) => {
                    const sev = highestSeverity(row.anomalies)
                    const bg = rowBgClass(sev)
                    const mfDelta = mfLedger ? mfDeltaByMonth.get(row.transactionMonth) ?? 0 : null
                    // C1 反映: バックエンド AccountsPayableIntegrityService と整合させるため effectiveChange を使用
                    // (手動確定行 verified_manually=1 の月で誤発火を防ぐ)
                    const selfDelta = row.effectiveChangeTaxIncluded - row.paymentSettledTaxIncluded
                    const diff = mfDelta !== null ? selfDelta - mfDelta : null
                    // R7 反映: MFA (MINOR amber) / MFA! (MAJOR red) / MFM (MF 側欠落 or 自社欠落) に細分化
                    const selfHasActivity = row.effectiveChangeTaxIncluded !== 0 || row.paymentSettledTaxIncluded !== 0
                    const mfHasActivity = mfDelta !== null && mfDelta !== 0
                    const mfStatus = mfDelta === null
                      ? { code: null, label: '', className: '' }
                      : computeMfMatchStatus(selfDelta, mfDelta, selfHasActivity, mfHasActivity)
                    const mfMismatch = mfStatus.code !== null && mfStatus.code !== 'MATCH'
                    return (
                      <tr key={row.transactionMonth} className={`border-b ${bg}`}>
                        <td className="py-2">{row.transactionMonth}</td>
                        <td className={`py-2 text-right ${row.openingBalanceTaxIncluded < 0 ? 'text-amber-700' : ''}`}>
                          {formatCurrency(row.openingBalanceTaxIncluded)}
                        </td>
                        <td className="py-2 text-right">{formatCurrency(row.changeTaxIncluded)}</td>
                        <td className={`py-2 text-right ${row.verifiedAmount === 0 && row.changeTaxIncluded > 0 ? 'text-red-600' : ''}`}>
                          {row.verifiedAmount === 0 ? '—' : formatCurrency(row.verifiedAmount)}
                        </td>
                        <td className="py-2 text-right">
                          {row.paymentSettledTaxIncluded === 0 ? '—' : formatCurrency(row.paymentSettledTaxIncluded)}
                        </td>
                        <td className={`py-2 text-right font-medium ${row.closingBalanceTaxIncluded < 0 ? 'text-amber-700' : ''}`}>
                          {formatCurrency(row.closingBalanceTaxIncluded)}
                        </td>
                        {mfLedger && (
                          <>
                            <td className="py-2 text-right">{formatCurrency(mfDelta ?? 0)}</td>
                            <td className={`py-2 text-right ${
                              mfStatus.code === 'MFA_MAJOR' || mfStatus.code === 'MFM_SELF' || mfStatus.code === 'MFM_MF'
                                ? 'text-red-700 font-medium'
                                : mfStatus.code === 'MFA_MINOR'
                                ? 'text-amber-700 font-medium'
                                : 'text-muted-foreground'
                            }`}>
                              {formatCurrency(diff ?? 0)}
                            </td>
                          </>
                        )}
                        <td className="py-2">
                          <div className="flex flex-wrap items-center gap-1">
                            {row.hasPaymentOnly && (
                              <Badge variant="outline" className="text-slate-600 text-xs">支払のみ</Badge>
                            )}
                            {row.hasVerifiedManually && (
                              <Badge variant="outline" className="text-xs">手動</Badge>
                            )}
                            <TooltipProvider>
                              {row.anomalies.map((a) => (
                                <Tooltip key={a.code}>
                                  <TooltipTrigger asChild>
                                    <Badge variant="outline" className={`text-xs cursor-help ${ANOMALY_BADGE_CLASS[a.code]}`}>
                                      {ANOMALY_SHORT_LABEL[a.code]}
                                    </Badge>
                                  </TooltipTrigger>
                                  <TooltipContent>
                                    <p className="text-xs">{a.message}</p>
                                  </TooltipContent>
                                </Tooltip>
                              ))}
                            </TooltipProvider>
                            {mfMismatch && mfStatus.code !== null && (
                              <TooltipProvider>
                                <Tooltip>
                                  <TooltipTrigger asChild>
                                    <Badge variant="outline" className={`text-xs cursor-help ${mfStatus.className}`}>
                                      {mfStatus.label}
                                    </Badge>
                                  </TooltipTrigger>
                                  <TooltipContent>
                                    <p className="text-xs">
                                      {mfStatus.code === 'MFM_SELF' && '自社にあって MF に無い (MF CSV 出力漏れ疑い)'}
                                      {mfStatus.code === 'MFM_MF' && 'MF にあって自社に無い (自社取込漏れ疑い)'}
                                      {(mfStatus.code === 'MFA_MINOR' || mfStatus.code === 'MFA_MAJOR') && (
                                        <>自社 delta {formatCurrency(selfDelta)} − MF delta {formatCurrency(mfDelta ?? 0)} = {formatCurrency(diff ?? 0)}</>
                                      )}
                                    </p>
                                  </TooltipContent>
                                </Tooltip>
                              </TooltipProvider>
                            )}
                            {row.anomalies.length === 0 && !row.hasPaymentOnly && !mfMismatch && (
                              <span className="flex items-center gap-1 text-xs text-emerald-600">
                                <CheckCircle2 className="h-3 w-3" /> OK
                              </span>
                            )}
                          </div>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
            </CardContent>
          </Card>

          <p className="text-xs text-muted-foreground">
            <b>closing</b> = opening + effectiveChange − payment_settled の T 勘定定義 (Phase B')。
            手動確定月は effectiveChange = verified_amount、それ以外は taxIncludedAmountChange を使用。
            検証額は振込明細 Excel 取込 or 手動確定で記録された金額。
            支払反映は「前月 supplier の検証額を当月 change 比で按分」したもの (Phase B')。
          </p>
          <p className="text-xs text-muted-foreground">
            <b>期首残</b>: 期間開始 opening は DB backfill で 2025-06-20 以前のデータから累積計算された値。
            2025-05 以前にデータがない supplier は opening=0 から始まるため、本来の MF 期首残とは差が出る。
          </p>
          <p className="text-xs text-muted-foreground">
            <b>MF 比較</b>: 月次 delta (= credit − debit) で比較する方式。
            自社 delta (= change − payment_settled) との月次差が MFX バッジ発火 (閾値 ¥10,000)。
            MF 側は期間内 journals の累積のみで、期間開始時点の supplier 別 MF 残は取得対象外 (sub_account 粒度の期首残取得は将来の Phase で対応予定)。
          </p>
        </>
      )}
    </div>
  )
}

function InfoTile({ label, value, emphasize, hint }: { label: string; value: number; emphasize?: boolean; hint?: string }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-muted-foreground" title={hint}>{label}</span>
      <span className={`tabular-nums ${emphasize ? 'font-semibold' : ''} ${value < 0 ? 'text-amber-700' : ''}`}>
        {formatCurrency(value)}
      </span>
    </div>
  )
}
