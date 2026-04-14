'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, usePaymentSuppliers } from '@/hooks/use-master-data'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Switch } from '@/components/ui/switch'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatCurrency } from '@/lib/utils'
import { emptyPage, type Paginated } from '@/types/paginated'
import { AlertCircle, RefreshCw, ShieldCheck, Unlock, Upload } from 'lucide-react'
import { toast } from 'sonner'
import { BulkVerifyDialog } from './BulkVerifyDialog'
import {
  type AccountsPayable,
  type AccountsPayableSummary,
  type VerificationFilter,
  VERIFICATION_FILTER_LABELS,
  defaultTransactionMonth,
  fromMonthInput,
  toMonthInput,
} from '@/types/accounts-payable'

const PAGE_SIZE = 50

/**
 * 20日締め取引月から対応する仕入期間を算出する。
 * transactionMonth=2026-02-20 → fromDate=2026-01-21, toDate=2026-02-20
 */
function purchaseDateRange(transactionMonth: string): { fromDate: string; toDate: string } {
  const [y, m, d] = transactionMonth.split('-').map(Number)
  const to = new Date(y, m - 1, d)
  const from = new Date(y, m - 2, d + 1)
  const fmt = (x: Date) =>
    `${x.getFullYear()}-${String(x.getMonth() + 1).padStart(2, '0')}-${String(x.getDate()).padStart(2, '0')}`
  return { fromDate: fmt(from), toDate: fmt(to) }
}

interface SearchParams {
  shopNo?: number
  supplierNo?: number
  transactionMonth: string // yyyy-MM-dd
  verificationFilter: VerificationFilter
}

function VerificationBadge({ row }: { row: AccountsPayable }) {
  if (row.verificationResult === 1) {
    return (
      <div className="flex items-center gap-1">
        <Badge className="bg-green-600 hover:bg-green-700">一致</Badge>
        {row.verifiedManually && <Badge variant="outline" className="text-xs">手動</Badge>}
      </div>
    )
  }
  if (row.verificationResult === 0) {
    return (
      <div className="flex items-center gap-1">
        <Badge variant="destructive">不一致</Badge>
        {row.verifiedManually && <Badge variant="outline" className="text-xs">手動</Badge>}
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
  }))
  const [page, setPage] = useState(0)
  const [dialogRow, setDialogRow] = useState<AccountsPayable | null>(null)
  const [bulkDialog, setBulkDialog] = useState(false)

  const shopsQuery = useShops(isAdmin)
  const paymentSuppliersQuery = usePaymentSuppliers(params.shopNo ?? (isAdmin ? undefined : user?.shopNo))

  const queryString = (() => {
    const sp = new URLSearchParams()
    sp.set('page', String(page))
    sp.set('size', String(PAGE_SIZE))
    sp.set('transactionMonth', params.transactionMonth)
    if (params.shopNo !== undefined) sp.set('shopNo', String(params.shopNo))
    if (params.supplierNo !== undefined) sp.set('supplierNo', String(params.supplierNo))
    if (params.verificationFilter !== 'all') sp.set('verificationFilter', params.verificationFilter)
    return sp.toString()
  })()

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

  const invalidate = () => {
    queryClient.invalidateQueries({ queryKey: ['accounts-payable'] })
    queryClient.invalidateQueries({ queryKey: ['accounts-payable-summary'] })
  }

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
    onError: (e: Error) => toast.error(e.message),
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
    onError: (e: Error) => toast.error(e.message),
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
    onError: (e: Error) => toast.error(e.message),
  })

  const runBatchMutation = useMutation({
    mutationFn: async (jobName: 'accountsPayableSummary' | 'accountsPayableVerification') => {
      return api.post<{ message: string }>(`/batch/execute/${jobName}`)
    },
    onSuccess: (res) => {
      toast.success(res?.message ?? 'バッチを起動しました')
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 429) {
        toast.error('他のバッチが実行中です。しばらく待ってから再実行してください')
      } else {
        toast.error(e.message)
      }
    },
  })

  const columns: Column<AccountsPayable>[] = [
    { key: 'supplierCode', header: '仕入先コード', sortable: true },
    {
      key: 'supplierName',
      header: '仕入先名',
      sortable: true,
      render: (r) => {
        // 支払先(m_payment_supplier)は shop_no=1 配下に集約されているため、shop_no=1 固定で遷移
        // 20日締めの取引月から仕入期間(前月21日〜当月20日)を算出
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
            {r.supplierName ?? '不明'}
          </a>
        )
      },
    },
    { key: 'taxRate', header: '税率', render: (r) => `${r.taxRate}%` },
    {
      key: 'taxIncludedAmountChange',
      header: '買掛金額(税込)',
      render: (r) => <span className="tabular-nums">{formatCurrency(r.taxIncludedAmountChange ?? 0)}</span>,
    },
    {
      key: 'taxIncludedAmount',
      header: 'SMILE支払額',
      render: (r) => <span className="tabular-nums">{r.taxIncludedAmount == null ? '-' : formatCurrency(r.taxIncludedAmount)}</span>,
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

  if (apQuery.isLoading && !apQuery.data) return <LoadingSpinner />
  if (apQuery.isError) return <ErrorMessage onRetry={() => apQuery.refetch()} />

  const p = apQuery.data ?? emptyPage<AccountsPayable>(PAGE_SIZE)
  const summary = summaryQuery.data

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
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => runBatchMutation.mutate('accountsPayableSummary')}
                  disabled={runBatchMutation.isPending}
                >
                  <RefreshCw className="mr-1 h-4 w-4" />
                  再集計
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => runBatchMutation.mutate('accountsPayableVerification')}
                  disabled={runBatchMutation.isPending}
                >
                  <ShieldCheck className="mr-1 h-4 w-4" />
                  再検証(SMILE)
                </Button>
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

      <DataTable
        data={p.content}
        columns={columns}
        serverPagination={{
          page: p.number,
          pageSize: p.size,
          totalElements: p.totalElements,
          totalPages: p.totalPages,
          onPageChange: setPage,
        }}
      />

      <VerifyDialog
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
    </div>
  )
}

function VerifyDialog({
  row,
  onClose,
  onSubmit,
  onReleaseManualLock,
  submitting,
  releasing,
  isAdmin,
}: {
  row: AccountsPayable | null
  onClose: () => void
  onSubmit: (verifiedAmount: number, note: string) => void
  onReleaseManualLock: () => void
  submitting: boolean
  releasing: boolean
  isAdmin: boolean
}) {
  const [amount, setAmount] = useState<string>('')
  const [note, setNote] = useState<string>('')

  // Reset when a new row is opened
  const rowKey = row ? `${row.shopNo}-${row.supplierNo}-${row.transactionMonth}-${row.taxRate}` : null
  const [currentKey, setCurrentKey] = useState<string | null>(null)
  if (rowKey !== currentKey) {
    setCurrentKey(rowKey)
    if (row) {
      const init = row.taxIncludedAmount ?? row.taxIncludedAmountChange ?? 0
      setAmount(String(init ?? ''))
      setNote(row.verificationNote ?? '')
    }
  }

  const open = row !== null
  const handleSubmit = () => {
    const v = Number(amount)
    if (!Number.isFinite(v)) {
      toast.error('金額が不正です')
      return
    }
    onSubmit(v, note)
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose() }}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>買掛金検証</DialogTitle>
        </DialogHeader>
        {row && (
          <div className="space-y-3 text-sm">
            <div className="grid grid-cols-2 gap-2">
              <div><span className="text-muted-foreground">仕入先: </span>{row.supplierCode} {row.supplierName ?? ''}</div>
              <div><span className="text-muted-foreground">税率: </span>{row.taxRate}%</div>
              <div><span className="text-muted-foreground">取引月: </span>{row.transactionMonth}</div>
              <div><span className="text-muted-foreground">ショップ: </span>{row.shopNo}</div>
            </div>
            <div className="rounded border p-2 space-y-1">
              <div className="flex justify-between"><span>買掛金額(税込)</span><span className="tabular-nums">{formatCurrency(row.taxIncludedAmountChange ?? 0)}</span></div>
              <div className="flex justify-between"><span>買掛金額(税抜)</span><span className="tabular-nums">{formatCurrency(row.taxExcludedAmountChange ?? 0)}</span></div>
              <div className="flex justify-between"><span>SMILE支払額(税込)</span><span className="tabular-nums">{row.taxIncludedAmount == null ? '-' : formatCurrency(row.taxIncludedAmount)}</span></div>
              <div className="flex justify-between">
                <span>差額</span>
                <span className={row.verificationResult === 0 ? 'text-red-600 tabular-nums' : 'tabular-nums'}>
                  {row.paymentDifference == null ? '-' : formatCurrency(row.paymentDifference)}
                </span>
              </div>
            </div>
            {row.verifiedManually && (
              <div className="rounded border border-green-300 bg-green-50 p-2 text-xs text-green-800">
                このレコードは手動確定済みです。次回 SMILE 再検証バッチで上書きされません。
              </div>
            )}
            <div>
              <Label htmlFor="verified-amount">検証済み支払額(税込)</Label>
              <Input
                id="verified-amount"
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="verification-note">備考（最大500字）</Label>
              <Textarea
                id="verification-note"
                rows={3}
                maxLength={500}
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="例: 請求書No.A-001で確認"
              />
            </div>
          </div>
        )}
        <DialogFooter className="gap-2">
          {row?.verifiedManually && isAdmin && (
            <Button
              variant="outline"
              onClick={onReleaseManualLock}
              disabled={releasing || submitting}
              className="mr-auto"
            >
              <Unlock className="mr-1 h-4 w-4" />
              {releasing ? '解除中...' : '手動確定解除'}
            </Button>
          )}
          <Button variant="outline" onClick={onClose}>キャンセル</Button>
          <Button onClick={handleSubmit} disabled={submitting || !amount}>
            {submitting ? '更新中...' : '更新'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
