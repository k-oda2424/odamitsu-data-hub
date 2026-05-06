'use client'

import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { handleApiError } from '@/lib/api-error-handler'
import { useAuth } from '@/lib/auth'
import { useShops, usePaymentSuppliers } from '@/hooks/use-master-data'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { formatCurrency } from '@/lib/utils'
import { emptyPage, type Paginated } from '@/types/paginated'
import { AlertCircle, CheckCircle2, Download, FileDown, Loader2, RefreshCw, ShieldCheck, Upload, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import { useRouter, useSearchParams } from 'next/navigation'
import { BulkVerifyDialog } from './BulkVerifyDialog'
import { PaymentMfAuxRowsTable } from './PaymentMfAuxRowsTable'
import { VerifyDialog } from './VerifyDialog'
import { VerifiedCsvExportDialog } from './VerifiedCsvExportDialog'
import { PurchaseJournalCsvExportDialog } from './PurchaseJournalCsvExportDialog'
import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'
import {
  type AccountsPayable,
  type AccountsPayableSummary,
  type AccountsPayableWithBalance,
  type BalanceFilter,
  type VerificationFilter,
  BALANCE_FILTER_LABELS,
  VERIFICATION_FILTER_LABELS,
  defaultTransactionMonth,
  fromMonthInput,
  hasBalance,
  toMonthInput,
} from '@/types/accounts-payable'

const PAGE_SIZE = 50

// モジュールスコープ（毎レンダー再生成を防ぎ、useEffect 依存配列から除外可能にする）
const BATCH_JOBS = {
  AGGREGATION: 'accountsPayableAggregation',
  VERIFICATION: 'accountsPayableVerification',
  PURCHASE_IMPORT: 'purchaseFileImport',
} as const
type BatchJobName = (typeof BATCH_JOBS)[keyof typeof BATCH_JOBS]
const BATCH_JOB_LABELS: Record<BatchJobName, string> = {
  [BATCH_JOBS.AGGREGATION]: '再集計',
  [BATCH_JOBS.VERIFICATION]: '再検証(SMILE)',
  [BATCH_JOBS.PURCHASE_IMPORT]: '仕入明細取込(SMILE)',
}

type JobStatus = 'COMPLETED' | 'FAILED' | 'STARTED' | 'STARTING' | 'STOPPED' | 'ABANDONED' | 'UNKNOWN'
interface BatchStatusPayload {
  status: JobStatus | string
  startTime?: string
  exitMessage?: string
}

const toYyyyMmDd = (isoDate: string) => isoDate.replaceAll('-', '')

/**
 * 20日締め取引月から対応する仕入期間を算出する。
 * transactionMonth=2026-02-20 → fromDate=2026-01-21, toDate=2026-02-20
 * Date は local TZ で生成し YYYY-MM-DD にフォーマット（UTC 変換は挟まないので TZ 非依存）。
 */
function purchaseDateRange(transactionMonth: string): { fromDate: string; toDate: string } {
  const [y, m, d] = transactionMonth.split('-').map(Number)
  const to = new Date(y, m - 1, d)       // 当月20日（20日締め）
  const from = new Date(y, m - 2, d + 1) // 前月21日: m-2 = 1月なら前年12月へ自動繰り下がる
  const fmt = (x: Date) =>
    `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  return { fromDate: fmt(from), toDate: fmt(to) }
}

interface SearchParams {
  shopNo?: number
  supplierNo?: number
  transactionMonth: string // yyyy-MM-dd
  verificationFilter: VerificationFilter
  /** 累積残の符号フィルタ (showBalance=true の時のみ反映)。クライアント側フィルタ。 */
  balanceFilter: BalanceFilter
}

/**
 * バッチ実行ボタン（実行中ローダー＋完了/失敗バッジ付き）。
 * 親コンポーネントが pollingJobs 配列で進行状態を持つ。
 */
function BatchButton({
  job, label, icon, running, status, onClick,
}: {
  job: BatchJobName
  label: string
  icon: React.ReactNode
  running: boolean
  status: JobStatus | string | undefined
  onClick: () => void
}) {
  return (
    <div className="flex items-center gap-1">
      <Button
        variant="outline"
        size="sm"
        onClick={onClick}
        disabled={running}
        data-testid={`batch-button-${job}`}
      >
        {running ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : icon}
        {running ? '実行中...' : label}
      </Button>
      {!running && status === 'COMPLETED' && (
        <span className="flex items-center gap-0.5 text-xs text-emerald-600" title={`${job} 完了`}>
          <CheckCircle2 className="h-3.5 w-3.5" />
        </span>
      )}
      {!running && status === 'FAILED' && (
        <span className="flex items-center gap-0.5 text-xs text-destructive" title={`${job} 失敗`}>
          <XCircle className="h-3.5 w-3.5" />
        </span>
      )}
    </div>
  )
}

function VerificationBadge({ row }: { row: AccountsPayable }) {
  // 「手動」バッジは手入力検証 (verificationSource=MANUAL) の時だけ表示。
  // 振込明細一括検証 (BULK) は verifiedManually=true だが手動操作ではないため表示しない。
  // verificationSource=null (移行期旧データ等) で verifiedManually=true の場合は、
  // 安全側に倒して手動扱い（BULK 以外は手動）とする。
  const showManualBadge =
    row.verifiedManually === true && row.verificationSource !== 'BULK'
  // V026: 自動調整 (振込明細金額と自社計算の差を合わせた痕跡) を ±¥N で表示
  const adj = row.autoAdjustedAmount ?? 0
  const showAdjBadge = adj !== 0
  const adjClass = Math.abs(adj) > 100
    ? 'border-red-500 text-red-700'
    : 'border-amber-500 text-amber-700'
  const AdjBadge = showAdjBadge ? (
    <Badge variant="outline" className={`text-xs ${adjClass}`} title={`振込明細取込で ${adj > 0 ? '+' : ''}¥${adj.toLocaleString('ja-JP')} 自動調整`}>
      調整 {adj > 0 ? '+' : ''}¥{Math.abs(adj).toLocaleString('ja-JP')}
    </Badge>
  ) : null
  if (row.verificationResult === 1) {
    return (
      <div className="flex flex-wrap items-center gap-1">
        <Badge className="bg-green-600 hover:bg-green-700">一致</Badge>
        {showManualBadge && <Badge variant="outline" className="text-xs">手動</Badge>}
        {AdjBadge}
      </div>
    )
  }
  if (row.verificationResult === 0) {
    return (
      <div className="flex flex-wrap items-center gap-1">
        <Badge variant="destructive">不一致</Badge>
        {showManualBadge && <Badge variant="outline" className="text-xs">手動</Badge>}
        {AdjBadge}
      </div>
    )
  }
  return <Badge variant="secondary">未検証</Badge>
}

export function AccountsPayablePage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const queryClient = useQueryClient()

  const [params, setParams] = useState<SearchParams>(() => ({
    shopNo: isAdmin ? undefined : user?.shopNo,
    transactionMonth: defaultTransactionMonth(),
    verificationFilter: 'all',
    balanceFilter: 'all',
  }))
  /**
   * 累積残 (opening/closing) を表示するかどうか。OFF 時は API に include=balance を付けず
   * ペイロードを抑える。日常運用は OFF、月次突合時のみ ON にする想定。
   * 設計書: design-supplier-partner-ledger-balance.md §4.5
   */
  const [showBalance, setShowBalance] = useState(false)
  const [page, setPage] = useState(0)
  const [dialogRow, setDialogRow] = useState<AccountsPayable | null>(null)
  const [bulkDialog, setBulkDialog] = useState(false)
  const [verifiedExportDialog, setVerifiedExportDialog] = useState(false)
  const [purchaseJournalExportDialog, setPurchaseJournalExportDialog] = useState(false)

  // タブ状態は URL ?tab=payable|aux で永続化
  const router = useRouter()
  const urlSearchParams = useSearchParams()
  const tabParam = urlSearchParams.get('tab')
  const currentTab: 'payable' | 'aux' = tabParam === 'aux' ? 'aux' : 'payable'
  const setCurrentTab = (tab: 'payable' | 'aux') => {
    const sp = new URLSearchParams(urlSearchParams.toString())
    if (tab === 'payable') sp.delete('tab')
    else sp.set('tab', tab)
    const qs = sp.toString()
    const nextUrl = qs ? `?${qs}` : window.location.pathname
    router.replace(nextUrl, { scroll: false })
  }
  const [auxCount, setAuxCount] = useState<number | null>(null)

  const shopsQuery = useShops(isAdmin)
  const paymentSuppliersQuery = usePaymentSuppliers(params.shopNo ?? (isAdmin ? undefined : user?.shopNo))

  const queryString = useMemo(() => {
    const sp = new URLSearchParams()
    sp.set('page', String(page))
    sp.set('size', String(PAGE_SIZE))
    sp.set('transactionMonth', params.transactionMonth)
    if (params.shopNo !== undefined) sp.set('shopNo', String(params.shopNo))
    if (params.supplierNo !== undefined) sp.set('supplierNo', String(params.supplierNo))
    if (params.verificationFilter !== 'all') sp.set('verificationFilter', params.verificationFilter)
    if (showBalance) sp.set('include', 'balance')
    return sp.toString()
  }, [page, params.transactionMonth, params.shopNo, params.supplierNo, params.verificationFilter, showBalance])

  const apQuery = useQuery({
    queryKey: ['accounts-payable', queryString],
    queryFn: () => api.get<Paginated<AccountsPayable>>(`/finance/accounts-payable?${queryString}`),
  })

  const summaryQuery = useQuery({
    queryKey: ['accounts-payable-summary', params.transactionMonth, params.shopNo ?? null],
    queryFn: () => {
      const sp = new URLSearchParams()
      sp.set('transactionMonth', params.transactionMonth)
      if (params.shopNo !== undefined) sp.set('shopNo', String(params.shopNo))
      return api.get<AccountsPayableSummary>(`/finance/accounts-payable/summary?${sp.toString()}`)
    },
  })

  const invalidate = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['accounts-payable'] })
    queryClient.invalidateQueries({ queryKey: ['accounts-payable-summary'] })
    queryClient.invalidateQueries({ queryKey: ['payment-mf-aux-rows'] })
    queryClient.invalidateQueries({ queryKey: ['verified-export-preview'] })
  }, [queryClient])

  const verifyMutation = useMutation({
    mutationFn: async ({ row, verifiedAmount, note }: { row: AccountsPayable; verifiedAmount: number; note: string }) => {
      const path = `/finance/accounts-payable/${row.shopNo}/${row.supplierNo}/${row.transactionMonth}/${row.taxRate}/verify`
      return api.put<AccountsPayable>(path, { verifiedAmount, note: note || null })
    },
    onSuccess: () => {
      toast.success('検証結果を更新しました')
      setDialogRow(null)
      invalidate()
    },
    onError: (e) => handleApiError(e, { fallbackMessage: '検証結果の更新に失敗しました' }),
  })

  const releaseManualMutation = useMutation({
    mutationFn: async (row: AccountsPayable) => {
      const path = `/finance/accounts-payable/${row.shopNo}/${row.supplierNo}/${row.transactionMonth}/${row.taxRate}/manual-lock`
      return api.deleteWithResponse<AccountsPayable>(path)
    },
    onSuccess: () => {
      toast.success('手動確定を解除しました')
      setDialogRow(null)
      invalidate()
    },
    onError: (e) => handleApiError(e, { fallbackMessage: '手動確定の解除に失敗しました' }),
  })

  const mfExportMutation = useMutation({
    mutationFn: async ({ row, enabled }: { row: AccountsPayable; enabled: boolean }) => {
      const path = `/finance/accounts-payable/${row.shopNo}/${row.supplierNo}/${row.transactionMonth}/${row.taxRate}/mf-export`
      return api.patch<AccountsPayable>(path, { enabled })
    },
    onSuccess: () => {
      toast.success('MF出力可否を更新しました')
      invalidate()
    },
    onError: (e) => handleApiError(e, { fallbackMessage: 'MF出力可否の更新に失敗しました' }),
  })

  const [pollingJobs, setPollingJobs] = useState<Set<BatchJobName>>(new Set())
  // 起動時刻は「古い COMPLETED 誤検知」防止のガードにのみ使うので Ref で十分（再レンダー不要）
  const launchedAtRef = useRef<Record<string, string>>({})
  // 購入明細取込のConfirmDialog制御
  const [confirmPurchaseImport, setConfirmPurchaseImport] = useState(false)

  // 実行中ジョブのステータスを5秒間隔でポーリング
  // queryKey は Set の挿入順を排除するため sort して安定化
  const sortedPolling = useMemo(() => Array.from(pollingJobs).sort(), [pollingJobs])
  const batchStatusQuery = useQuery({
    queryKey: ['ap-batch-status', sortedPolling.join(',')],
    queryFn: async () => {
      const entries = await Promise.all(
        sortedPolling.map(async (jobName) => {
          const data = await api.get<BatchStatusPayload>(`/batch/status/${jobName}`)
          return [jobName, data] as const
        }),
      )
      return Object.fromEntries(entries) as Record<string, BatchStatusPayload>
    },
    enabled: sortedPolling.length > 0,
    refetchInterval: sortedPolling.length > 0 ? 5000 : false,
    staleTime: 0,
  })

  useEffect(() => {
    if (!batchStatusQuery.data) return
    const finished: BatchJobName[] = []
    for (const [jobName, status] of Object.entries(batchStatusQuery.data)) {
      const launched = launchedAtRef.current[jobName]
      if (launched && status.startTime && new Date(status.startTime).getTime() < new Date(launched).getTime()) continue
      const label = BATCH_JOB_LABELS[jobName as BatchJobName] ?? jobName
      if (status.status === 'COMPLETED') {
        toast.success(`${label} が完了しました`)
        finished.push(jobName as BatchJobName)
      } else if (status.status === 'FAILED') {
        toast.error(`${label} が失敗しました${status.exitMessage ? ': ' + status.exitMessage : ''}`)
        finished.push(jobName as BatchJobName)
      }
    }
    if (finished.length === 0) return
    setPollingJobs((prev) => {
      const next = new Set(prev)
      finished.forEach((j) => next.delete(j))
      return next
    })
    // 集計/検証ジョブ完了時は一覧を再取得
    if (finished.some((j) => j !== BATCH_JOBS.PURCHASE_IMPORT)) invalidate()
  }, [batchStatusQuery.data, invalidate])

  const runBatchMutation = useMutation({
    mutationFn: async (jobName: BatchJobName) => {
      const sp = new URLSearchParams()
      if (jobName !== BATCH_JOBS.PURCHASE_IMPORT) {
        sp.set('targetDate', toYyyyMmDd(params.transactionMonth))
      }
      const qs = sp.toString()
      return api.post<{ message: string }>(`/batch/execute/${jobName}${qs ? `?${qs}` : ''}`)
    },
    onMutate: (jobName) => {
      launchedAtRef.current[jobName] = new Date().toISOString()
      // ポーリング対象に即時追加（まだジョブレコード未生成でも status 応答は UNKNOWN として扱われ、launched 時刻以前の古い COMPLETED は ref ガードで無視される）
      setPollingJobs((prev) => new Set(prev).add(jobName))
    },
    onSuccess: (res, jobName) => {
      toast.info(res?.message ?? `${BATCH_JOB_LABELS[jobName]} を起動しました`)
    },
    onError: (e: Error, jobName) => {
      setPollingJobs((prev) => {
        const next = new Set(prev)
        next.delete(jobName)
        return next
      })
      if (e instanceof ApiError && e.status === 429) {
        toast.error('他のバッチが実行中です。しばらく待ってから再実行してください')
        return
      }
      handleApiError(e, { fallbackMessage: 'バッチ起動に失敗しました' })
    },
  })

  const isRunning = (job: BatchJobName) => pollingJobs.has(job) || runBatchMutation.isPending
  const lastStatus = (job: BatchJobName): JobStatus | string | undefined =>
    batchStatusQuery.data?.[job]?.status

  const balanceColumns: Column<AccountsPayable>[] = [
    {
      key: 'openingBalanceTaxIncluded',
      header: <>前月繰越<AmountSourceTooltip source="OPENING_BALANCE" /></>,
      render: (r) => {
        if (!hasBalance(r)) return <span className="text-muted-foreground">-</span>
        const v = r.openingBalanceTaxIncluded
        const cls = v < 0 ? 'text-amber-700 tabular-nums' : 'tabular-nums'
        return <span className={cls}>{formatCurrency(v)}</span>
      },
    },
    {
      key: 'paymentSettledTaxIncluded',
      header: <>当月支払<AmountSourceTooltip source="VERIFIED_AMOUNT" /></>,
      render: (r) => {
        if (!hasBalance(r)) return <span className="text-muted-foreground">-</span>
        const v = r.paymentSettledTaxIncluded
        if (v === 0) return <span className="text-muted-foreground">—</span>
        return (
          <div className="flex items-center gap-1">
            <span className="tabular-nums">{formatCurrency(v)}</span>
            {r.isPaymentOnly && (
              <Badge variant="outline" className="text-slate-600 text-xs">支払のみ</Badge>
            )}
          </div>
        )
      },
    },
    {
      key: 'closingBalanceTaxIncluded',
      header: <>累積残<AmountSourceTooltip source="CLOSING_CALC" /></>,
      render: (r) => {
        if (!hasBalance(r)) return <span className="text-muted-foreground">-</span>
        const v = r.closingBalanceTaxIncluded
        if (v < 0) {
          // バッジ分岐 (設計書 §6.2): payment-only なら「支払超過」、それ以外は「値引繰越」
          const label = r.isPaymentOnly ? '支払超過' : '値引繰越'
          return (
            <div className="flex items-center gap-1">
              <span className="text-amber-700 font-medium tabular-nums">{formatCurrency(v)}</span>
              <Badge variant="outline" className="border-amber-500 text-amber-700 text-xs">{label}</Badge>
            </div>
          )
        }
        return <span className="tabular-nums">{formatCurrency(v)}</span>
      },
    },
  ]

  const baseColumns: Column<AccountsPayable>[] = [
    {
      key: 'supplierCode',
      header: '仕入先コード',
      sortable: true,
      render: (r) => {
        // supplierCode 列: 仕入一覧への遷移 (既存導線を保持)
        const { fromDate, toDate } = purchaseDateRange(r.transactionMonth)
        const sp = new URLSearchParams()
        sp.set('shopNo', '1')
        sp.set('paymentSupplierNo', String(r.supplierNo))
        sp.set('fromDate', fromDate)
        sp.set('toDate', toDate)
        sp.set('transactionMonth', r.transactionMonth)
        if (r.supplierName) sp.set('supplierName', r.supplierName)
        return (
          <a
            href={`/purchases?${sp.toString()}`}
            target="_blank"
            rel="noreferrer"
            className="text-blue-600 hover:underline"
            title="仕入一覧を新しいタブで開く"
          >
            {r.supplierCode ?? '-'}
          </a>
        )
      },
    },
    {
      key: 'supplierName',
      header: '仕入先名',
      sortable: true,
      render: (r) => {
        // supplierName 列: 買掛帳 (月次推移) への遷移
        const sp = new URLSearchParams()
        sp.set('shopNo', String(r.shopNo))
        sp.set('supplierNo', String(r.supplierNo))
        return (
          <a
            href={`/finance/accounts-payable-ledger?${sp.toString()}`}
            target="_blank"
            rel="noreferrer"
            className="text-blue-600 hover:underline"
            title="買掛帳 (月次推移) を新しいタブで開く"
          >
            {r.supplierName ?? '不明'}
          </a>
        )
      },
    },
    { key: 'taxRate', header: '税率', render: (r) => `${r.taxRate}%` },
    {
      key: 'taxExcludedAndTaxAmount',
      header: '税抜 / 消費税',
      render: (r) => {
        const inc = r.taxIncludedAmountChange ?? 0
        const exc = r.taxExcludedAmountChange ?? 0
        return (
          <div className="flex flex-col tabular-nums leading-tight">
            <span>{formatCurrency(exc)}</span>
            <span className="text-xs text-muted-foreground">{formatCurrency(inc - exc)}</span>
          </div>
        )
      },
    },
    {
      key: 'taxIncludedAmountChange',
      header: <>買掛金額(税込)<AmountSourceTooltip source="PAYABLE_SUMMARY" /></>,
      render: (r) => <span className="tabular-nums">{formatCurrency(r.taxIncludedAmountChange ?? 0)}</span>,
    },
    {
      key: 'verifiedAmount',
      header: <>振込明細額<AmountSourceTooltip source="VERIFIED_AMOUNT" /></>,
      render: (r) => (
        <span className="tabular-nums">
          {r.verifiedAmount == null ? '-' : formatCurrency(r.verifiedAmount)}
        </span>
      ),
    },
    {
      key: 'paymentDifference',
      header: '差額',
      render: (r) => {
        if (r.paymentDifference == null) return '-'
        const cls = r.verificationResult === 0 ? 'text-red-600 font-medium tabular-nums' : 'tabular-nums'
        return <span className={cls}>{formatCurrency(r.paymentDifference)}</span>
      },
    },
    { key: 'verificationResult', header: '検証状態', render: (r) => <VerificationBadge row={r} /> },
    {
      key: 'mfTransferDate',
      header: '支払予定日',
      render: (r) => (
        <span className="tabular-nums text-xs">
          {r.mfTransferDate ?? '-'}
        </span>
      ),
    },
    {
      key: 'mfExportEnabled',
      header: 'MF出力',
      render: (r) => (
        <Switch
          checked={!!r.mfExportEnabled}
          onCheckedChange={(checked) => mfExportMutation.mutate({ row: r, enabled: checked })}
          disabled={mfExportMutation.isPending}
          aria-label="MF出力可否"
        />
      ),
    },
    {
      key: 'actions',
      header: '操作',
      render: (r) => (
        <Button size="sm" variant="outline" onClick={() => setDialogRow(r)}>
          {r.verificationResult == null ? '検証' : '詳細'}
        </Button>
      ),
    },
  ]

  // 残高表示 ON 時は「支払予定日」の前に opening/closing 列を挿入 (支払予定日と操作は右端維持)
  const columns: Column<AccountsPayable>[] = showBalance
    ? (() => {
        const insertAt = baseColumns.findIndex((c) => c.key === 'mfTransferDate')
        return insertAt < 0
          ? [...baseColumns, ...balanceColumns]
          : [...baseColumns.slice(0, insertAt), ...balanceColumns, ...baseColumns.slice(insertAt)]
      })()
    : baseColumns

  if (apQuery.isLoading && !apQuery.data) return <LoadingSpinner />
  if (apQuery.isError) return <ErrorMessage onRetry={() => apQuery.refetch()} />

  const p = apQuery.data ?? emptyPage<AccountsPayable>(PAGE_SIZE)
  const summary = summaryQuery.data

  // 累積残フィルタ (クライアント側、現ページ内のみ)。balanceFilter=all or showBalance=false の時は素通し
  const filteredContent: AccountsPayable[] =
    showBalance && params.balanceFilter !== 'all'
      ? p.content.filter((r) => {
          if (!hasBalance(r)) return false
          return params.balanceFilter === 'negative'
            ? r.closingBalanceTaxIncluded < 0
            : r.closingBalanceTaxIncluded >= 0
        })
      : p.content
  // 現ページ内の負残件数 (banner 用、payment-only 区別)
  const negativeOnPage = showBalance
    ? p.content.filter((r): r is AccountsPayableWithBalance =>
        hasBalance(r) && r.closingBalanceTaxIncluded < 0,
      )
    : []
  const negativeSum = negativeOnPage.reduce((s, r) => s + r.closingBalanceTaxIncluded, 0)
  const negativePaymentOnlyCount = negativeOnPage.filter((r) => r.isPaymentOnly).length

  const shopOptions = (shopsQuery.data ?? []).map((s) => ({
    value: String(s.shopNo),
    label: `${s.shopNo}: ${s.shopName}`,
  }))
  const supplierOptions = (paymentSuppliersQuery.data ?? []).map((s) => ({
    value: String(s.paymentSupplierNo),
    label: `${s.paymentSupplierCode ?? ''} ${s.paymentSupplierName}`.trim(),
  }))

  return (
    <div className="space-y-4">
      <PageHeader
        title="買掛金一覧"
        actions={
          <>
            <Button
              variant="outline"
              size="sm"
              onClick={() => setBulkDialog(true)}
            >
              <Upload className="mr-1 h-4 w-4" />
              振込明細で一括検証
            </Button>
            {isAdmin && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setVerifiedExportDialog(true)}
              >
                <FileDown className="mr-1 h-4 w-4" />
                買掛支払CSV出力（MF）
              </Button>
            )}
            {isAdmin && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPurchaseJournalExportDialog(true)}
              >
                <FileDown className="mr-1 h-4 w-4" />
                仕入仕訳CSV出力（MF）
              </Button>
            )}
            {isAdmin && (
              <>
                <BatchButton
                  job={BATCH_JOBS.PURCHASE_IMPORT}
                  label="仕入明細取込(SMILE)"
                  icon={<Download className="mr-1 h-4 w-4" />}
                  running={isRunning(BATCH_JOBS.PURCHASE_IMPORT)}
                  status={lastStatus(BATCH_JOBS.PURCHASE_IMPORT)}
                  onClick={() => setConfirmPurchaseImport(true)}
                />
                <BatchButton
                  job={BATCH_JOBS.AGGREGATION}
                  label="再集計"
                  icon={<RefreshCw className="mr-1 h-4 w-4" />}
                  running={isRunning(BATCH_JOBS.AGGREGATION)}
                  status={lastStatus(BATCH_JOBS.AGGREGATION)}
                  onClick={() => runBatchMutation.mutate(BATCH_JOBS.AGGREGATION)}
                />
                <BatchButton
                  job={BATCH_JOBS.VERIFICATION}
                  label="再検証(SMILE)"
                  icon={<ShieldCheck className="mr-1 h-4 w-4" />}
                  running={isRunning(BATCH_JOBS.VERIFICATION)}
                  status={lastStatus(BATCH_JOBS.VERIFICATION)}
                  onClick={() => runBatchMutation.mutate(BATCH_JOBS.VERIFICATION)}
                />
              </>
            )}
          </>
        }
      />

      <div className="rounded border p-4 space-y-3">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
          <div>
            <Label htmlFor="ap-month">取引月</Label>
            <Input
              id="ap-month"
              type="month"
              value={toMonthInput(params.transactionMonth)}
              onChange={(e) => {
                setParams((prev) => ({ ...prev, transactionMonth: fromMonthInput(e.target.value) }))
                setPage(0)
              }}
            />
          </div>
          {isAdmin && (
            <div>
              <Label>ショップ</Label>
              <SearchableSelect
                value={params.shopNo !== undefined ? String(params.shopNo) : ''}
                onValueChange={(v) => {
                  setParams((prev) => ({ ...prev, shopNo: v ? Number(v) : undefined, supplierNo: undefined }))
                  setPage(0)
                }}
                options={shopOptions}
                placeholder="すべて"
                clearable
              />
            </div>
          )}
          <div>
            <Label>仕入先</Label>
            <SearchableSelect
              value={params.supplierNo !== undefined ? String(params.supplierNo) : ''}
              onValueChange={(v) => {
                setParams((prev) => ({ ...prev, supplierNo: v ? Number(v) : undefined }))
                setPage(0)
              }}
              options={supplierOptions}
              placeholder="すべて"
              clearable
            />
          </div>
          <div>
            <Label>検証状態</Label>
            <Select
              value={params.verificationFilter}
              onValueChange={(v) => {
                setParams((prev) => ({ ...prev, verificationFilter: v as VerificationFilter }))
                setPage(0)
              }}
            >
              <SelectTrigger>
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(VERIFICATION_FILTER_LABELS) as VerificationFilter[]).map((k) => (
                  <SelectItem key={k} value={k}>
                    {VERIFICATION_FILTER_LABELS[k]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <div className="flex items-center gap-4 pt-1">
          <div className="flex items-center gap-2">
            <Switch
              id="ap-show-balance"
              checked={showBalance}
              onCheckedChange={(checked) => {
                setShowBalance(checked)
                // OFF → 残高フィルタも自動 all へ戻す
                if (!checked) {
                  setParams((prev) => ({ ...prev, balanceFilter: 'all' }))
                }
              }}
            />
            <Label htmlFor="ap-show-balance" className="cursor-pointer">累積残を表示</Label>
          </div>
          {showBalance && (
            <div className="flex items-center gap-2">
              <Label className="text-muted-foreground text-xs">累積残フィルタ</Label>
              <Select
                value={params.balanceFilter}
                onValueChange={(v) => {
                  setParams((prev) => ({ ...prev, balanceFilter: v as BalanceFilter }))
                }}
              >
                <SelectTrigger className="h-8 w-40">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(Object.keys(BALANCE_FILTER_LABELS) as BalanceFilter[]).map((k) => (
                    <SelectItem key={k} value={k}>
                      {BALANCE_FILTER_LABELS[k]}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
        </div>
      </div>

      {summary && (summary.unverifiedCount > 0 || summary.unmatchedCount > 0) && (
        <div
          role="alert"
          className="rounded border border-orange-300 bg-orange-50 p-3 text-sm text-orange-800"
        >
          <div className="flex items-center gap-2 font-medium">
            <AlertCircle className="h-4 w-4" />
            要対応: 未検証 {summary.unverifiedCount}件 / 不一致 {summary.unmatchedCount}件
            {summary.unmatchedCount > 0 && (
              <span>（差額合計 {formatCurrency(summary.unmatchedDifferenceSum)}）</span>
            )}
          </div>
        </div>
      )}

      {showBalance && negativeOnPage.length > 0 && (
        <div
          role="alert"
          className="rounded border border-amber-300 bg-amber-50 p-3 text-sm text-amber-800"
        >
          <div className="flex items-center gap-2 font-medium">
            <AlertCircle className="h-4 w-4" />
            累積負残: {negativeOnPage.length}件
            {negativePaymentOnlyCount > 0 && (
              <span className="text-xs">（うち支払超過 {negativePaymentOnlyCount}件）</span>
            )}
            （合計 {formatCurrency(negativeSum)}）※ 値引/支払超過による繰越負残。現在のページ内集計
          </div>
        </div>
      )}

      <Tabs value={currentTab} onValueChange={(v) => setCurrentTab(v as 'payable' | 'aux')}>
        <TabsList>
          <TabsTrigger value="payable">
            買掛金一覧
            {p.totalElements > 0 && (
              <span className="ml-2 text-xs text-muted-foreground">({p.totalElements})</span>
            )}
          </TabsTrigger>
          <TabsTrigger value="aux">
            MF補助行
            {auxCount !== null && (
              <span className="ml-2 text-xs text-muted-foreground">({auxCount})</span>
            )}
          </TabsTrigger>
        </TabsList>
        <TabsContent value="payable" className="mt-3">
          <DataTable
            data={filteredContent}
            columns={columns}
            serverPagination={{
              page: p.number,
              pageSize: p.size,
              totalElements: p.totalElements,
              totalPages: p.totalPages,
              onPageChange: setPage,
            }}
          />
        </TabsContent>
        <TabsContent value="aux" className="mt-3">
          <PaymentMfAuxRowsTable
            transactionMonth={params.transactionMonth}
            onCountChange={setAuxCount}
          />
        </TabsContent>
      </Tabs>

      <VerifyDialog
        key={dialogRow
          ? `${dialogRow.shopNo}-${dialogRow.supplierNo}-${dialogRow.transactionMonth}-${dialogRow.taxRate}`
          : 'closed'}
        row={dialogRow}
        onClose={() => setDialogRow(null)}
        onSubmit={(verifiedAmount, note) =>
          dialogRow && verifyMutation.mutate({ row: dialogRow, verifiedAmount, note })
        }
        onReleaseManualLock={() => dialogRow && releaseManualMutation.mutate(dialogRow)}
        submitting={verifyMutation.isPending}
        releasing={releaseManualMutation.isPending}
        isAdmin={isAdmin}
      />

      <BulkVerifyDialog
        open={bulkDialog}
        onOpenChange={setBulkDialog}
        onApplied={invalidate}
      />

      <VerifiedCsvExportDialog
        open={verifiedExportDialog}
        onOpenChange={setVerifiedExportDialog}
        transactionMonth={params.transactionMonth}
      />

      <PurchaseJournalCsvExportDialog
        open={purchaseJournalExportDialog}
        onOpenChange={setPurchaseJournalExportDialog}
        transactionMonth={params.transactionMonth}
      />

      <ConfirmDialog
        open={confirmPurchaseImport}
        onOpenChange={setConfirmPurchaseImport}
        title="仕入明細取込(SMILE)"
        description="SMILEから仕入明細を取り込みます。よろしいですか？（取込後は「再集計」で買掛金に反映してください）"
        confirmLabel="取込実行"
        onConfirm={() => runBatchMutation.mutate(BATCH_JOBS.PURCHASE_IMPORT)}
      />
    </div>
  )
}
