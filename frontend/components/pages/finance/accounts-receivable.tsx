'use client'

import { useCallback, useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, usePartners } from '@/hooks/use-master-data'
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
import { formatCurrency, normalizeForSearch } from '@/lib/utils'
import { emptyPage, type Paginated } from '@/types/paginated'
import {
  AlertCircle,
  CheckCircle2,
  FileDown,
  RefreshCw,
  ShieldCheck,
  Upload,
  XCircle,
} from 'lucide-react'
import { toast } from 'sonner'
import {
  type AccountsReceivable,
  type AccountsReceivableSummary,
  type VerificationFilter,
  type CutoffType,
  type BulkVerifyResponse,
  type AggregateResponse,
  VERIFICATION_FILTER_LABELS,
  defaultDateRange,
  pkToPath,
} from '@/types/accounts-receivable'
import { AccountsReceivableVerifyDialog } from './AccountsReceivableVerifyDialog'
import { AccountsReceivableAggregateDialog } from './AccountsReceivableAggregateDialog'
import { InvoiceImportDialog } from './InvoiceImportDialog'

const PAGE_SIZE = 50
/** 「全件表示」ON 時のページサイズ上限（実運用の月次売掛は 1000 行超えない想定）。 */
const ALL_PAGE_SIZE = 10000

interface SearchParams {
  shopNo?: number
  partnerNo?: number
  fromDate: string
  toDate: string
  verificationFilter: VerificationFilter
}

export default function AccountsReceivablePage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const isAdmin = user?.shopNo === 0

  const defaults = useMemo(() => defaultDateRange(), [])
  const initialSearch: SearchParams = useMemo(() => ({
    shopNo: user?.shopNo && user.shopNo !== 0 ? user.shopNo : undefined,
    partnerNo: undefined,
    fromDate: defaults.fromDate,
    toDate: defaults.toDate,
    verificationFilter: 'all',
  }), [user?.shopNo, defaults.fromDate, defaults.toDate])

  // フォームへの入力状態（一文字ずつ反映）
  const [search, setSearch] = useState<SearchParams>(initialSearch)
  // 実際に一覧に適用されている条件（検索ボタン押下時のみ更新）
  const [appliedSearch, setAppliedSearch] = useState<SearchParams>(initialSearch)
  const [page, setPage] = useState(0)
  // 全件表示モード: ページング無効で一度に ALL_PAGE_SIZE 件取得して合計を検算できるようにする
  const [showAll, setShowAll] = useState(false)
  const [verifyRow, setVerifyRow] = useState<AccountsReceivable | null>(null)
  const [aggregateOpen, setAggregateOpen] = useState(false)
  const [invoiceImportOpen, setInvoiceImportOpen] = useState(false)
  const [confirmBulkVerify, setConfirmBulkVerify] = useState(false)
  const [confirmExport, setConfirmExport] = useState(false)

  const { data: shopsData } = useShops(isAdmin)
  // 得意先絞り込み用。shop 未選択 (admin が「すべて」) の場合は partners API が使えないため
  // SearchableSelect を disabled にする。
  const partnersQuery = usePartners(search.shopNo ?? (isAdmin ? undefined : user?.shopNo))
  const partnerOptions = useMemo(() => (partnersQuery.data ?? []).map((p) => ({
    value: String(p.partnerNo),
    label: `${p.partnerCode} ${p.partnerName}`,
  })), [partnersQuery.data])

  // クエリは appliedSearch のみに依存する（キーストローク毎に発火しない）
  const effectivePageSize = showAll ? ALL_PAGE_SIZE : PAGE_SIZE
  const effectivePage = showAll ? 0 : page
  const listQuery = useQuery({
    queryKey: ['accounts-receivable', appliedSearch, effectivePage, effectivePageSize],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (appliedSearch.shopNo != null) params.set('shopNo', String(appliedSearch.shopNo))
      if (appliedSearch.partnerNo != null) params.set('partnerNo', String(appliedSearch.partnerNo))
      if (appliedSearch.fromDate) params.set('fromDate', appliedSearch.fromDate)
      if (appliedSearch.toDate) params.set('toDate', appliedSearch.toDate)
      if (appliedSearch.verificationFilter !== 'all') params.set('verificationFilter', appliedSearch.verificationFilter)
      params.set('page', String(effectivePage))
      params.set('size', String(effectivePageSize))
      return api.get<Paginated<AccountsReceivable>>(`/finance/accounts-receivable?${params.toString()}`)
    },
  })

  const summaryQuery = useQuery({
    queryKey: ['accounts-receivable-summary', appliedSearch.shopNo, appliedSearch.partnerNo, appliedSearch.fromDate, appliedSearch.toDate],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (appliedSearch.shopNo != null) params.set('shopNo', String(appliedSearch.shopNo))
      if (appliedSearch.partnerNo != null) params.set('partnerNo', String(appliedSearch.partnerNo))
      if (appliedSearch.fromDate) params.set('fromDate', appliedSearch.fromDate)
      if (appliedSearch.toDate) params.set('toDate', appliedSearch.toDate)
      return api.get<AccountsReceivableSummary>(`/finance/accounts-receivable/summary?${params.toString()}`)
    },
  })

  const verifyMutation = useMutation({
    mutationFn: async (args: { row: AccountsReceivable; taxIncludedAmount: number; taxExcludedAmount: number; note: string; mfExportEnabled: boolean }) => {
      return api.put<AccountsReceivable>(
        `/finance/accounts-receivable/${pkToPath(args.row)}/verify`,
        {
          taxIncludedAmount: args.taxIncludedAmount,
          taxExcludedAmount: args.taxExcludedAmount,
          note: args.note || null,
          mfExportEnabled: args.mfExportEnabled,
        },
      )
    },
    onSuccess: () => {
      toast.success('検証を確定しました')
      setVerifyRow(null)
      invalidate()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const releaseMutation = useMutation({
    mutationFn: async (row: AccountsReceivable) => {
      return api.delete(`/finance/accounts-receivable/${pkToPath(row)}/manual-lock`)
    },
    onSuccess: () => {
      toast.success('手動確定を解除しました')
      setVerifyRow(null)
      invalidate()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const mfExportToggleMutation = useMutation({
    mutationFn: async (args: { row: AccountsReceivable; enabled: boolean }) => {
      return api.patch<AccountsReceivable>(
        `/finance/accounts-receivable/${pkToPath(args.row)}/mf-export`,
        { enabled: args.enabled },
      )
    },
    onSuccess: () => invalidate(),
    onError: (e: Error) => toast.error(e.message),
  })

  const aggregateMutation = useMutation({
    mutationFn: async (args: { targetDate: string; cutoffType: CutoffType }) => {
      return api.post<AggregateResponse>('/finance/accounts-receivable/aggregate', args)
    },
    onSuccess: (r) => {
      toast.success(`再集計を開始しました (cutoffType=${r.cutoffType})。完了後に再度検索してください`)
      setAggregateOpen(false)
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 403) {
        toast.error('再集計には管理者権限が必要です')
      } else {
        toast.error(e.message)
      }
    },
  })

  const bulkVerifyMutation = useMutation({
    mutationFn: async () => {
      return api.post<BulkVerifyResponse>('/finance/accounts-receivable/bulk-verify', {
        shopNo: appliedSearch.shopNo ?? null,
        fromDate: appliedSearch.fromDate,
        toDate: appliedSearch.toDate,
      })
    },
    onSuccess: (r) => {
      toast.success(
        `一括検証完了: 一致 ${r.matchedCount} / 不一致 ${r.mismatchCount} / 請求書なし ${r.notFoundCount}`
        + (r.skippedManualCount > 0 ? ` / 手動スキップ ${r.skippedManualCount}` : ''),
      )
      if (r.reconciledPartners > 0) {
        const details = r.reconciledDetails?.length ? `\n${r.reconciledDetails.slice(0, 5).join(', ')}` +
          (r.reconciledDetails.length > 5 ? ` ...他 ${r.reconciledDetails.length - 5}件` : '') : ''
        toast.info(
          `請求書の締め日に合わせて ${r.reconciledPartners} 得意先を自動再集計しました` +
          ` (旧${r.reconciledDeletedRows}件→新${r.reconciledInsertedRows}件)` +
          (r.reconciledSkippedManualPartners > 0 ? ` / 手動確定スキップ ${r.reconciledSkippedManualPartners}件` : '') +
          details,
          { duration: 10000 },
        )
      }
      setConfirmBulkVerify(false)
      invalidate()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const invalidate = useCallback(() => {
    queryClient.invalidateQueries({ queryKey: ['accounts-receivable'] })
    queryClient.invalidateQueries({ queryKey: ['accounts-receivable-summary'] })
  }, [queryClient])

  const handleExportCsv = useCallback(() => {
    const params = new URLSearchParams()
    params.set('fromDate', appliedSearch.fromDate)
    params.set('toDate', appliedSearch.toDate)
    // ブラウザのネイティブDL動作
    const url = `/api/v1/finance/accounts-receivable/export-mf-csv?${params.toString()}`
    const token = typeof window !== 'undefined' ? localStorage.getItem('token') : null
    // fetch + blob 経由で Authorization ヘッダを付与してDL
    ;(async () => {
      try {
        const resp = await fetch(url, {
          headers: token ? { Authorization: `Bearer ${token}` } : {},
        })
        if (!resp.ok) {
          const text = await resp.text()
          throw new Error(text || `HTTP ${resp.status}`)
        }
        const blob = await resp.blob()
        const cd = resp.headers.get('Content-Disposition') ?? ''
        const filenameMatch = /filename="([^"]+)"/.exec(cd)
        const filename = filenameMatch?.[1] ?? 'accounts_receivable_to_sales_journal.csv'
        const downloadUrl = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = downloadUrl
        a.download = filename
        document.body.appendChild(a)
        a.click()
        a.remove()
        URL.revokeObjectURL(downloadUrl)
        toast.success('MF CSV をダウンロードしました')
        setConfirmExport(false)
        invalidate()
      } catch (e) {
        toast.error((e as Error).message || 'CSVダウンロードに失敗しました')
      }
    })()
  }, [appliedSearch.fromDate, appliedSearch.toDate, invalidate])

  const page$ = listQuery.data ?? emptyPage<AccountsReceivable>()
  const summary = summaryQuery.data

  // テーブル内絞込 (現在のページ 50 行に対して適用)
  const [tableFilter, setTableFilter] = useState('')
  const filteredRows = useMemo(() => {
    if (!tableFilter.trim()) return page$.content
    const needle = normalizeForSearch(tableFilter.toLowerCase())
    return page$.content.filter((r) =>
      Object.values(r).some((v) =>
        normalizeForSearch(String(v ?? '').toLowerCase()).includes(needle)
      )
    )
  }, [page$.content, tableFilter])

  // 絞込後の税込/税抜/請求書金額の合計
  const tableTotals = useMemo(() => {
    let inc = 0, exc = 0, invoice = 0
    for (const r of filteredRows) {
      inc += Number(r.taxIncludedAmountChange ?? 0)
      exc += Number(r.taxExcludedAmountChange ?? 0)
      invoice += Number(r.invoiceAmount ?? 0)
    }
    return { inc, exc, invoice }
  }, [filteredRows])

  const columns: Column<AccountsReceivable>[] = useMemo(() => [
    { key: 'verificationResult', header: '検証', render: (r) => <VerificationBadge row={r} /> },
    { key: 'shopNo', header: '店舗' },
    { key: 'partnerCode', header: '得意先Code' },
    { key: 'partnerName', header: '得意先名', render: (r) => r.partnerName ?? '-' },
    { key: 'cutoffDate', header: '締め日', render: (r) => cutoffDateLabel(r.cutoffDate) },
    { key: 'transactionMonth', header: '取引日' },
    { key: 'taxRate', header: '税率', render: (r) => `${Number(r.taxRate)}%` },
    {
      key: 'taxIncludedAmountChange',
      header: '税込金額',
      render: (r) => <span className="tabular-nums">{formatCurrency(r.taxIncludedAmountChange ?? 0)}</span>,
    },
    {
      key: 'taxExcludedAmountChange',
      header: '税抜金額',
      render: (r) => <span className="tabular-nums">{formatCurrency(r.taxExcludedAmountChange ?? 0)}</span>,
    },
    {
      key: 'invoiceAmount',
      header: '請求書金額',
      render: (r) => r.invoiceAmount == null ? '-' : <span className="tabular-nums">{formatCurrency(r.invoiceAmount)}</span>,
    },
    {
      key: 'verificationDifference',
      header: '差額',
      render: (r) => {
        if (r.verificationDifference == null) return '-'
        const cls = r.verificationResult === 0 ? 'text-red-600 tabular-nums' : 'tabular-nums'
        return <span className={cls}>{formatCurrency(r.verificationDifference)}</span>
      },
    },
    {
      key: 'verifiedManually',
      header: '手動',
      render: (r) => r.verifiedManually ? <CheckCircle2 className="inline h-4 w-4 text-green-600" /> : '-',
    },
    {
      key: 'mfExportEnabled',
      header: 'MF',
      render: (r) => (
        <Switch
          checked={r.mfExportEnabled ?? false}
          onCheckedChange={(checked) => {
            mfExportToggleMutation.mutate({ row: r, enabled: checked })
          }}
          onClick={(e) => e.stopPropagation()}
        />
      ),
    },
    {
      key: 'verificationNote',
      header: '備考',
      render: (r) => r.verificationNote ? (
        <span className="block truncate max-w-[150px]" title={r.verificationNote}>{r.verificationNote}</span>
      ) : '-',
    },
    { key: 'invoiceNo', header: '請求書No', render: (r) => r.invoiceNo ?? '-' },
  ], [mfExportToggleMutation])

  if (listQuery.isLoading && !listQuery.data) {
    return <LoadingSpinner />
  }
  if (listQuery.error) {
    return <ErrorMessage message={(listQuery.error as Error).message} />
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="売掛金一覧"
        actions={
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="outline" onClick={() => setAggregateOpen(true)} disabled={!isAdmin}>
              <RefreshCw className="mr-1 h-4 w-4" />再集計
            </Button>
            <Button size="sm" variant="outline" onClick={() => setInvoiceImportOpen(true)}>
              <Upload className="mr-1 h-4 w-4" />請求書取込
            </Button>
            <Button size="sm" variant="outline" onClick={() => setConfirmBulkVerify(true)}>
              <ShieldCheck className="mr-1 h-4 w-4" />一括検証
            </Button>
            <Button size="sm" onClick={() => setConfirmExport(true)}>
              <FileDown className="mr-1 h-4 w-4" />MF CSV出力
            </Button>
          </div>
        }
      />

      {/* 検索フォーム */}
      <div className="rounded border p-3 space-y-2">
        <div className="grid grid-cols-1 md:grid-cols-5 gap-2">
          {isAdmin && (
            <div>
              <Label>店舗</Label>
              <SearchableSelect
                options={(shopsData ?? []).map((s) => ({ value: String(s.shopNo), label: `${s.shopNo}: ${s.shopName}` }))}
                value={search.shopNo != null ? String(search.shopNo) : ''}
                onValueChange={(v) => setSearch({ ...search, shopNo: v ? Number(v) : undefined, partnerNo: undefined })}
                placeholder="すべて"
                clearable
              />
            </div>
          )}
          <div>
            <Label htmlFor="from-date">期間(開始)</Label>
            <Input
              id="from-date"
              type="date"
              value={search.fromDate}
              onChange={(e) => setSearch({ ...search, fromDate: e.target.value })}
            />
          </div>
          <div>
            <Label htmlFor="to-date">期間(終了)</Label>
            <Input
              id="to-date"
              type="date"
              value={search.toDate}
              onChange={(e) => setSearch({ ...search, toDate: e.target.value })}
            />
          </div>
          <div>
            <Label>得意先</Label>
            <SearchableSelect
              value={search.partnerNo != null ? String(search.partnerNo) : ''}
              onValueChange={(v) => setSearch({ ...search, partnerNo: v ? Number(v) : undefined })}
              options={partnerOptions}
              placeholder={search.shopNo == null && isAdmin ? '店舗を先に選択' : 'すべて'}
              clearable
              disabled={search.shopNo == null && isAdmin}
            />
          </div>
          <div>
            <Label htmlFor="verification-filter">検証</Label>
            <Select
              value={search.verificationFilter}
              onValueChange={(v) => setSearch({ ...search, verificationFilter: v as VerificationFilter })}
            >
              <SelectTrigger id="verification-filter">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(VERIFICATION_FILTER_LABELS) as VerificationFilter[]).map((k) => (
                  <SelectItem key={k} value={k}>{VERIFICATION_FILTER_LABELS[k]}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
        <div className="flex justify-end">
          <Button size="sm" onClick={() => { setPage(0); setAppliedSearch(search) }}>検索</Button>
        </div>
      </div>

      {/* サマリアラート: 一致も含めて常に表示（未検証 or 不一致があるときだけ警告色、
          全件一致なら成功色） */}
      {summary && (summary.matchedCount + summary.unverifiedCount + summary.unmatchedCount) > 0 && (
        (() => {
          const hasIssue = summary.unverifiedCount > 0 || summary.unmatchedCount > 0
          const cls = hasIssue
            ? 'border-yellow-300 bg-yellow-50 text-yellow-900'
            : 'border-green-300 bg-green-50 text-green-900'
          const Icon = hasIssue ? AlertCircle : CheckCircle2
          return (
            <div className={`rounded border p-2 text-sm flex items-center gap-2 ${cls}`}>
              <Icon className="h-4 w-4" />
              一致 {summary.matchedCount}件 / 未検証 {summary.unverifiedCount}件 / 不一致 {summary.unmatchedCount}件
              {summary.unmatchedCount > 0 && (
                <> （差額合計 {formatCurrency(summary.unmatchedDifferenceSum)}）</>
              )}
            </div>
          )
        })()
      )}

      {/* テーブル内絞込 + 合計 + 全件表示 */}
      <div className="flex flex-wrap items-center justify-between gap-2 rounded border p-2 text-sm">
        <div className="flex items-center gap-3 flex-1 min-w-[320px] max-w-2xl">
          <Input
            placeholder={showAll ? '全件の中から絞込...' : '表示中のページ内で絞込...'}
            value={tableFilter}
            onChange={(e) => setTableFilter(e.target.value)}
            className="h-9 text-sm max-w-sm"
          />
          <label className="flex items-center gap-2 whitespace-nowrap cursor-pointer select-none">
            <Switch
              checked={showAll}
              onCheckedChange={(v) => { setShowAll(v); setPage(0) }}
            />
            <span className="text-xs">全件表示（合計検算用）</span>
          </label>
        </div>
        <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
          <span>
            表示中 <b className="tabular-nums">{filteredRows.length}</b>件
            {(tableFilter || !showAll) && (
              <span className="text-muted-foreground">
                {' '}/ {showAll ? '全件' : 'ページ内'} {page$.content.length}件
                {!showAll && <> / 総件数 {page$.totalElements}件</>}
              </span>
            )}
          </span>
          <span>税込合計 <b className="tabular-nums">{formatCurrency(tableTotals.inc)}</b></span>
          <span className="text-muted-foreground">税抜 {formatCurrency(tableTotals.exc)}</span>
          <span className="text-muted-foreground">請求書 {formatCurrency(tableTotals.invoice)}</span>
        </div>
      </div>

      {/* テーブル */}
      <DataTable
        data={filteredRows}
        columns={columns}
        rowKey={(r) => `${r.shopNo}-${r.partnerNo}-${r.transactionMonth}-${String(r.taxRate)}-${r.isOtakeGarbageBag}`}
        onRowClick={(r) => setVerifyRow(r)}
        serverPagination={{
          page: effectivePage,
          pageSize: effectivePageSize,
          totalElements: page$.totalElements,
          totalPages: showAll ? 1 : page$.totalPages,
          onPageChange: setPage,
        }}
      />

      <AccountsReceivableVerifyDialog
        row={verifyRow}
        onClose={() => setVerifyRow(null)}
        onSubmit={(inc, exc, note, mfExport) => {
          if (!verifyRow) return
          verifyMutation.mutate({ row: verifyRow, taxIncludedAmount: inc, taxExcludedAmount: exc, note, mfExportEnabled: mfExport })
        }}
        onReleaseManualLock={() => {
          if (!verifyRow) return
          releaseMutation.mutate(verifyRow)
        }}
        submitting={verifyMutation.isPending}
        releasing={releaseMutation.isPending}
        isAdmin={isAdmin}
      />

      <AccountsReceivableAggregateDialog
        open={aggregateOpen}
        onOpenChange={setAggregateOpen}
        defaultTargetDate={search.toDate}
        onSubmit={(targetDate, cutoffType) => aggregateMutation.mutate({ targetDate, cutoffType })}
        submitting={aggregateMutation.isPending}
      />

      <InvoiceImportDialog
        open={invoiceImportOpen}
        onOpenChange={(o) => {
          setInvoiceImportOpen(o)
          if (!o) invalidate()
        }}
      />

      <ConfirmDialog
        open={confirmBulkVerify}
        onOpenChange={setConfirmBulkVerify}
        title="一括検証"
        description={`期間 ${search.fromDate} ～ ${search.toDate} の売掛金を請求書と一括検証します。手動確定済みの行はスキップされます。`}
        confirmLabel={bulkVerifyMutation.isPending ? '実行中...' : '実行'}
        onConfirm={() => bulkVerifyMutation.mutate()}
      />

      <ConfirmDialog
        open={confirmExport}
        onOpenChange={setConfirmExport}
        title="MF CSV出力"
        description={`期間 ${search.fromDate} ～ ${search.toDate} の検証済み売掛金をMF CSVで出力します。`}
        confirmLabel="ダウンロード"
        onConfirm={handleExportCsv}
      />
    </div>
  )
}

function VerificationBadge({ row }: { row: AccountsReceivable }) {
  if (row.verificationResult === 1) {
    return <Badge className="bg-green-100 text-green-800 hover:bg-green-100"><CheckCircle2 className="mr-1 h-3 w-3" />一致</Badge>
  }
  if (row.verificationResult === 0) {
    return <Badge variant="destructive"><XCircle className="mr-1 h-3 w-3" />不一致</Badge>
  }
  return <Badge variant="outline">未検証</Badge>
}

function cutoffDateLabel(cutoff: number | null | undefined): string {
  if (cutoff == null) return '-'
  if (cutoff === -1) return '都度現金'
  if (cutoff === 0) return '月末'
  return `${cutoff}日`
}
