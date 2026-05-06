'use client'

import { useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Textarea } from '@/components/ui/textarea'
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { formatCurrency, parseAmount } from '@/lib/utils'
import { AlertCircle, Download, Loader2, Pencil } from 'lucide-react'
import { toast } from 'sonner'
import {
  DEFAULT_OPENING_DATE,
  VALIDATION_BADGE_CLASS,
  VALIDATION_LABEL,
  type MfOpeningBalanceFetchResponse,
  type SupplierOpeningBalanceResponse,
  type SupplierOpeningBalanceRow,
  type SupplierOpeningBalanceUpdateRequest,
} from '@/types/supplier-opening-balance'
import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'

/**
 * supplier 毎の前期繰越 (期首残) 管理画面。
 * MF journal #1 から取得 + 手動補正で buying ledger / integrity / supplier-balances に期首残を注入する。
 *
 * 設計書: claudedocs/design-supplier-opening-balance.md
 */
export function SupplierOpeningBalancePage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const queryClient = useQueryClient()

  const [shopNo, setShopNo] = useState<number | undefined>(isAdmin ? 1 : user?.shopNo)
  const [openingDate, setOpeningDate] = useState<string>(DEFAULT_OPENING_DATE)
  const [editing, setEditing] = useState<SupplierOpeningBalanceRow | null>(null)
  const [form, setForm] = useState<{ adj: string; reason: string; note: string }>({
    adj: '0', reason: '', note: '',
  })

  const shopsQuery = useShops(isAdmin)
  const shopOptions = (shopsQuery.data ?? []).map((s) => ({
    value: String(s.shopNo), label: `${s.shopNo}: ${s.shopName}`,
  }))

  const listQuery = useQuery({
    queryKey: ['supplier-opening-balance', shopNo, openingDate],
    queryFn: () => {
      const sp = new URLSearchParams({ shopNo: String(shopNo), openingDate })
      return api.get<SupplierOpeningBalanceResponse>(`/finance/supplier-opening-balance?${sp.toString()}`)
    },
    enabled: shopNo !== undefined && !!openingDate,
  })

  const fetchMutation = useMutation({
    mutationFn: async () => {
      const sp = new URLSearchParams({ shopNo: String(shopNo), openingDate })
      return api.post<MfOpeningBalanceFetchResponse>(`/finance/supplier-opening-balance/fetch-from-mf?${sp.toString()}`)
    },
    onSuccess: (res) => {
      toast.success(
        `MF journal #${res.journalNumber ?? '?'} 取得完了: ${res.matchedCount}/${res.branchCount} 件 upsert` +
        (res.preservedManualCount > 0 ? ` (手動補正 ${res.preservedManualCount} 件保持)` : ''),
      )
      if (res.unmatchedBranches.length > 0) {
        toast.warning(`未解決 sub_account ${res.unmatchedBranches.length} 件あり。mf_account_master で search_key を設定してください。`)
      }
      queryClient.invalidateQueries({ queryKey: ['supplier-opening-balance'] })
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) {
        toast.error('MF 認証エラー。「MF連携状況」画面で再認証してください。')
      } else if (e instanceof ApiError && e.status === 403) {
        toast.error('admin 権限が必要です。')
      } else {
        toast.error(e.message)
      }
    },
  })

  const updateMutation = useMutation({
    mutationFn: (req: SupplierOpeningBalanceUpdateRequest) =>
      api.put('/finance/supplier-opening-balance/manual-adjustment', req),
    onSuccess: () => {
      toast.success('手動補正を更新しました')
      queryClient.invalidateQueries({ queryKey: ['supplier-opening-balance'] })
      setEditing(null)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const data = listQuery.data
  const summary = data?.summary

  const openEdit = (row: SupplierOpeningBalanceRow) => {
    setEditing(row)
    setForm({
      adj: String(row.manualAdjustment ?? 0),
      reason: row.adjustmentReason ?? '',
      note: row.note ?? '',
    })
  }

  const submitEdit = () => {
    if (!editing || shopNo === undefined) return
    const adjNum = parseAmount(form.adj)
    if (adjNum === null) {
      toast.error('補正額は数値で入力してください (カンマ・全角数字可)')
      return
    }
    if (adjNum !== 0 && !form.reason.trim()) {
      toast.error('補正額が 0 でない場合は補正理由を入力してください')
      return
    }
    updateMutation.mutate({
      shopNo,
      openingDate,
      supplierNo: editing.supplierNo,
      manualAdjustment: adjNum,
      adjustmentReason: form.reason.trim() || null,
      note: form.note.trim() || null,
    })
  }

  const adjPreview = parseAmount(form.adj) ?? 0

  const validation = useMemo(() => {
    if (!summary) return null
    return {
      level: summary.validationLevel,
      diff: summary.validationDiff,
      effective: summary.totalEffectiveBalance,
      mfClosing: summary.mfTrialBalanceClosing,
    }
  }, [summary])

  return (
    <div className="space-y-4">
      <PageHeader title="前期繰越 (supplier 期首残)" />

      <Card>
        <CardContent className="pt-4">
          <div className="grid grid-cols-1 gap-3 md:grid-cols-4">
            {isAdmin && (
              <div>
                <Label>ショップ</Label>
                <SearchableSelect
                  value={shopNo !== undefined ? String(shopNo) : ''}
                  onValueChange={(v) => setShopNo(v ? Number(v) : undefined)}
                  options={shopOptions}
                  placeholder="選択してください"
                />
              </div>
            )}
            <div>
              <Label htmlFor="opening-date">基準日 (20 日締め)</Label>
              <Input
                id="opening-date"
                type="date"
                value={openingDate}
                onChange={(e) => setOpeningDate(e.target.value)}
              />
              <p className="text-xs text-muted-foreground mt-1">
                通常は MF fiscal year 直前日 (例: 2025-06-20)。journal #{1} は翌日 ({openingDate && new Date(new Date(openingDate).getTime() + 86400000).toISOString().slice(0, 10)}) から取得。
              </p>
            </div>
            {isAdmin && (
              <div className="flex items-end">
                <Button
                  variant="outline"
                  onClick={() => fetchMutation.mutate()}
                  disabled={fetchMutation.isPending || shopNo === undefined}
                >
                  {fetchMutation.isPending ? <Loader2 className="mr-1 h-4 w-4 animate-spin" /> : <Download className="mr-1 h-4 w-4" />}
                  MF から取得
                </Button>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {listQuery.isLoading && <LoadingSpinner />}
      {listQuery.isError && <ErrorMessage onRetry={() => listQuery.refetch()} />}

      {data && summary && (
        <>
          <Card>
            <CardContent className="pt-4 grid grid-cols-2 md:grid-cols-5 gap-3 text-sm tabular-nums">
              <Tile label="登録件数" value={String(summary.totalRowCount)} subtext={`MF 取込 ${summary.mfSourcedCount} / 手動補正 ${summary.manuallyAdjustedCount}`} />
              <Tile label="MF 合計" value={formatCurrency(summary.totalMfBalance)} />
              <Tile label="手動補正 合計" value={formatCurrency(summary.totalManualAdjustment)} />
              <Tile label="実効 合計" value={formatCurrency(summary.totalEffectiveBalance)} emphasize />
              <div className="flex flex-col gap-1">
                <span className="text-xs text-muted-foreground">整合検証</span>
                {validation && (
                  <>
                    <Badge variant="outline" className={`w-fit text-xs ${VALIDATION_BADGE_CLASS[validation.level]}`}>
                      {VALIDATION_LABEL[validation.level]}
                    </Badge>
                    {validation.mfClosing !== null && (
                      <span className="text-xs text-muted-foreground">
                        MF trial_balance: {formatCurrency(validation.mfClosing)} / diff: {formatCurrency(validation.diff ?? 0)}
                      </span>
                    )}
                  </>
                )}
              </div>
            </CardContent>
          </Card>

          {summary.unmatchedCount > 0 && (
            <div className="flex items-start gap-2 rounded-md border border-amber-300 bg-amber-50 p-3 text-sm text-amber-900">
              <AlertCircle className="h-4 w-4 mt-0.5" />
              <div>
                <b>未解決 supplier が {summary.unmatchedCount} 件あります</b>。
                supplier マスタから削除された or 取込時に sub_account_name が見つからなかった可能性。
                {isAdmin && '手動補正で金額を 0 にするか、mf_account_master で search_key を設定して再取得してください。'}
              </div>
            </div>
          )}

          <Card>
            <CardContent className="pt-4 overflow-x-auto">
              <table className="w-full text-sm tabular-nums">
                <thead>
                  <tr className="border-b text-left">
                    <th className="py-2">仕入先</th>
                    <th className="py-2 text-right">MF 取得値<AmountSourceTooltip source="MF_JOURNAL" /></th>
                    <th className="py-2 text-right">手動補正</th>
                    <th className="py-2 text-right">実効値<AmountSourceTooltip source="OPENING_BALANCE" /></th>
                    <th className="py-2">出典</th>
                    <th className="py-2">備考</th>
                    {isAdmin && <th className="py-2 w-16"></th>}
                  </tr>
                </thead>
                <tbody>
                  {data.rows.length === 0 && (
                    <tr>
                      <td colSpan={isAdmin ? 7 : 6} className="py-6 text-center text-muted-foreground">
                        データがありません。「MF から取得」ボタンで取り込んでください。
                      </td>
                    </tr>
                  )}
                  {data.rows.map((row) => (
                    <tr key={row.supplierNo} className={`border-b ${row.unmatched ? 'bg-amber-50' : ''}`}>
                      <td className="py-2">
                        <div className="flex flex-col">
                          <span className={row.unmatched ? 'text-amber-700' : ''}>
                            {row.supplierCode ?? '?'} {row.supplierName ?? `(supplierNo=${row.supplierNo})`}
                          </span>
                          {row.sourceSubAccountName && row.supplierName !== row.sourceSubAccountName && (
                            <span className="text-xs text-muted-foreground">MF: {row.sourceSubAccountName}</span>
                          )}
                        </div>
                      </td>
                      <td className="py-2 text-right">
                        {row.mfBalance === null ? <span className="text-muted-foreground">—</span> : formatCurrency(row.mfBalance)}
                      </td>
                      <td className={`py-2 text-right ${row.manualAdjustment !== 0 ? 'text-amber-700 font-medium' : 'text-muted-foreground'}`}>
                        {row.manualAdjustment === 0 ? '—' : (row.manualAdjustment > 0 ? '+' : '') + formatCurrency(row.manualAdjustment)}
                      </td>
                      <td className="py-2 text-right font-medium">{formatCurrency(row.effectiveBalance)}</td>
                      <td className="py-2 text-xs">
                        {row.sourceJournalNumber && (
                          <Badge variant="outline" className="text-xs mr-1">MF #{row.sourceJournalNumber}</Badge>
                        )}
                        {row.manualAdjustment !== 0 && (
                          <Badge variant="outline" className="text-xs border-amber-400 text-amber-700">🖐️ 補正</Badge>
                        )}
                      </td>
                      <td className="py-2 text-xs text-muted-foreground truncate max-w-xs" title={row.adjustmentReason ?? row.note ?? ''}>
                        {row.adjustmentReason || row.note || ''}
                      </td>
                      {isAdmin && (
                        <td className="py-2">
                          <Button variant="ghost" size="sm" onClick={() => openEdit(row)}>
                            <Pencil className="h-3 w-3" />
                          </Button>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </CardContent>
          </Card>

          <p className="text-xs text-muted-foreground">
            <b>前期繰越</b>: MF の fiscal year 開始日 ({new Date(new Date(openingDate).getTime() + 86400000).toISOString().slice(0, 10)}) にある
            「期首残高仕訳」(journal #1) の各 credit branch から supplier 毎に取り込んだ値。
            buying ledger / supplier-balances / 整合性レポート / MF ヘルスチェックの累積初期値として使用される。
          </p>
          <p className="text-xs text-muted-foreground">
            <b>手動補正</b>: MF journal #1 に含まれない supplier (例: shop=2 太幸) や税理士確認差分を吸収するため。
            再取得しても手動補正は保持される。補正額が 0 でない場合は「補正理由」入力必須。
          </p>
        </>
      )}

      <Dialog open={editing !== null} onOpenChange={(o) => !o && setEditing(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>
              手動補正 — {editing?.supplierCode ?? ''} {editing?.supplierName ?? `supplierNo=${editing?.supplierNo}`}
            </DialogTitle>
          </DialogHeader>
          {editing && (
            <div className="space-y-3">
              <div className="grid grid-cols-3 gap-2 text-sm tabular-nums">
                <div>
                  <Label className="text-xs text-muted-foreground">MF 取得値</Label>
                  <div className="mt-1 font-mono">
                    {editing.mfBalance === null ? '—' : formatCurrency(editing.mfBalance)}
                  </div>
                </div>
                <div>
                  <Label htmlFor="adj-input" className="text-xs">手動補正 (signed)</Label>
                  <Input
                    id="adj-input"
                    type="text"
                    inputMode="decimal"
                    value={form.adj}
                    onChange={(e) => setForm({ ...form, adj: e.target.value })}
                    placeholder="例: 1,000,000 / -500000 / １２３４"
                  />
                </div>
                <div>
                  <Label className="text-xs text-muted-foreground">実効値 (自動)</Label>
                  <div className="mt-1 font-mono font-semibold">
                    {formatCurrency((editing.mfBalance ?? 0) + adjPreview)}
                  </div>
                </div>
              </div>
              <div>
                <Label htmlFor="reason-input">補正理由 {adjPreview !== 0 && <span className="text-red-600">*</span>}</Label>
                <Input
                  id="reason-input"
                  value={form.reason}
                  onChange={(e) => setForm({ ...form, reason: e.target.value })}
                  placeholder="例: 太幸 shop=2 期首残 (journal #1 未掲載)"
                />
              </div>
              <div>
                <Label htmlFor="note-input">備考</Label>
                <Textarea
                  id="note-input"
                  value={form.note}
                  onChange={(e) => setForm({ ...form, note: e.target.value })}
                  rows={2}
                />
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)}>キャンセル</Button>
            <Button onClick={submitEdit} disabled={updateMutation.isPending}>
              {updateMutation.isPending && <Loader2 className="mr-1 h-4 w-4 animate-spin" />}
              保存
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function Tile({ label, value, subtext, emphasize }: { label: string; value: string; subtext?: string; emphasize?: boolean }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className={emphasize ? 'font-semibold font-mono' : 'font-mono'}>{value}</span>
      {subtext && <span className="text-[10px] text-muted-foreground">{subtext}</span>}
    </div>
  )
}
