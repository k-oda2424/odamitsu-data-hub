'use client'

import { useMemo, useState, useEffect } from 'react'
import { useSearchParams } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PageHeader } from '@/components/features/common/PageHeader'
import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import {
  Select, SelectTrigger, SelectValue, SelectContent, SelectItem,
} from '@/components/ui/select'
import { Plus, Pencil, Trash2, Copy, Wand2, CheckCircle2, AlertTriangle, XCircle } from 'lucide-react'
import { toast } from 'sonner'
import type { PaymentMfRule, PaymentMfRuleRequest, RuleKind, BackfillResult } from '@/types/payment-mf'
import { RULE_KINDS, TAX_CATEGORIES } from '@/types/payment-mf'

export default function PaymentMfRulesPage() {
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const searchParams = useSearchParams()
  const qParam = searchParams.get('q') ?? ''
  // 検索欄は ?q= を初期値として受け取り、その後は片方向 (URL→input) でのみ同期する。
  // ユーザーが手入力で編集した内容は URL には反映されない（永続化不要な検索体験のため）。
  // 別タブから `?q=XYZ` で再アクセスした場合は新しい qParam で初期化される
  // （target="_blank" 開きのため毎回マウントされる前提）。
  const [search, setSearch] = useState(qParam)
  useEffect(() => {
    // 同一タブで URL だけ変わるケース(履歴操作など)に備えて追従する。
    // ユーザー編集を尊重したい場合は "search !== qParam" 判定を変えること。
    setSearch(qParam)
  }, [qParam])
  const [editing, setEditing] = useState<PaymentMfRule | null>(null)
  const [creating, setCreating] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<PaymentMfRule | null>(null)
  const [form, setForm] = useState<PaymentMfRuleRequest>(blank())
  const [backfill, setBackfill] = useState<BackfillResult | null>(null)

  const { data: rules = [], isLoading } = useQuery({
    queryKey: ['payment-mf-rules'],
    queryFn: () => api.get<PaymentMfRule[]>('/finance/payment-mf/rules'),
    staleTime: 60_000,
  })

  const createMut = useMutation({
    mutationFn: (req: PaymentMfRuleRequest) =>
      api.post<PaymentMfRule>('/finance/payment-mf/rules', req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payment-mf-rules'] })
      toast.success('追加しました'); close()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const updateMut = useMutation({
    mutationFn: ({ id, req }: { id: number; req: PaymentMfRuleRequest }) =>
      api.put<PaymentMfRule>(`/finance/payment-mf/rules/${id}`, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payment-mf-rules'] })
      toast.success('更新しました'); close()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMut = useMutation({
    mutationFn: (id: number) => api.delete(`/finance/payment-mf/rules/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payment-mf-rules'] })
      toast.success('削除しました'); setDeleteTarget(null)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const backfillMut = useMutation({
    mutationFn: (dryRun: boolean) =>
      api.post<BackfillResult>(`/finance/payment-mf/rules/backfill-codes?dryRun=${dryRun}`),
    onSuccess: (r) => {
      setBackfill(r)
      if (!r.dryRun) {
        queryClient.invalidateQueries({ queryKey: ['payment-mf-rules'] })
        toast.success(`一括補完しました: 一致 ${r.matchedCount} / 未確定 ${r.ambiguousCount} / 該当なし ${r.notFoundCount}`)
      }
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const filtered = useMemo(() => {
    const raw = search.toLowerCase()
    const q = normalizeName(raw)
    return rules.filter((r) => {
      if (!raw) return true
      const name = normalizeName(r.sourceName.toLowerCase())
      return name.includes(q)
          || (r.debitSubAccount ?? '').toLowerCase().includes(raw)
          || (r.paymentSupplierCode ?? '').includes(raw)
    })
  }, [rules, search])

  function close() {
    setCreating(false); setEditing(null); setForm(blank())
  }
  function openCreate() { setForm(blank()); setCreating(true) }
  function openDuplicate(r: PaymentMfRule) {
    setEditing(null)
    setForm({
      sourceName: `${r.sourceName}（複製）`,
      paymentSupplierCode: r.paymentSupplierCode ?? '',
      ruleKind: r.ruleKind,
      debitAccount: r.debitAccount,
      debitSubAccount: r.debitSubAccount ?? '',
      debitDepartment: r.debitDepartment ?? '',
      debitTaxCategory: r.debitTaxCategory,
      creditAccount: r.creditAccount,
      creditSubAccount: r.creditSubAccount ?? '',
      creditDepartment: r.creditDepartment ?? '',
      creditTaxCategory: r.creditTaxCategory,
      summaryTemplate: r.summaryTemplate,
      tag: r.tag ?? '',
      priority: r.priority,
    })
    setCreating(true)
  }
  function openEdit(r: PaymentMfRule) {
    setEditing(r)
    setForm({
      sourceName: r.sourceName,
      paymentSupplierCode: r.paymentSupplierCode ?? '',
      ruleKind: r.ruleKind,
      debitAccount: r.debitAccount,
      debitSubAccount: r.debitSubAccount ?? '',
      debitDepartment: r.debitDepartment ?? '',
      debitTaxCategory: r.debitTaxCategory,
      creditAccount: r.creditAccount,
      creditSubAccount: r.creditSubAccount ?? '',
      creditDepartment: r.creditDepartment ?? '',
      creditTaxCategory: r.creditTaxCategory,
      summaryTemplate: r.summaryTemplate,
      tag: r.tag ?? '',
      priority: r.priority,
    })
  }
  function submit() {
    if (editing) updateMut.mutate({ id: editing.id, req: form })
    else createMut.mutate(form)
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="買掛仕入MFルール マスタ"
        actions={
          <>
            {isAdmin && (
              <Button size="sm" variant="outline"
                onClick={() => backfillMut.mutate(true)}
                disabled={backfillMut.isPending}>
                <Wand2 className="mr-1 h-4 w-4" />
                {backfillMut.isPending ? '解析中...' : '支払先コード自動補完'}
              </Button>
            )}
            <Button size="sm" onClick={openCreate}>
              <Plus className="mr-1 h-4 w-4" />新規
            </Button>
          </>
        }
      />
      <Input
        placeholder="送り先名 / 補助科目 / 支払先コードで検索"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        className="max-w-md"
      />

      {isLoading ? <div>読込中...</div> : (
        <div className="overflow-auto rounded border">
          <table className="min-w-full text-sm">
            <thead className="bg-muted">
              <tr>
                <th className="p-2 text-left">送り先</th>
                <th className="p-2 text-left">コード</th>
                <th className="p-2 text-left">種別</th>
                <th className="p-2 text-left">借方</th>
                <th className="p-2 text-left">借方補助</th>
                <th className="p-2 text-left">部門</th>
                <th className="p-2 text-left">税区分</th>
                <th className="p-2 text-left">摘要</th>
                <th className="p-2 text-left">タグ</th>
                <th className="p-2"></th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((r) => (
                <tr key={r.id} className="border-t">
                  <td className="p-2">{r.sourceName}</td>
                  <td className="p-2">{r.paymentSupplierCode ?? ''}</td>
                  <td className="p-2">{r.ruleKind}</td>
                  <td className="p-2">{r.debitAccount}</td>
                  <td className="p-2">{r.debitSubAccount ?? ''}</td>
                  <td className="p-2">{r.debitDepartment ?? ''}</td>
                  <td className="p-2">{r.debitTaxCategory}</td>
                  <td className="p-2">{r.summaryTemplate}</td>
                  <td className="p-2">{r.tag ?? ''}</td>
                  <td className="p-2 text-right">
                    {isAdmin && (
                      <>
                        <Button size="sm" variant="ghost" onClick={() => openEdit(r)} title="編集">
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button size="sm" variant="ghost" onClick={() => openDuplicate(r)} title="複製">
                          <Copy className="h-4 w-4" />
                        </Button>
                        <Button size="sm" variant="ghost"
                          onClick={() => setDeleteTarget(r)} title="削除">
                          <Trash2 className="h-4 w-4 text-red-600" />
                        </Button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
              {filtered.length === 0 && (
                <tr><td colSpan={10} className="p-4 text-center text-muted-foreground">該当なし</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <Dialog open={creating || editing !== null} onOpenChange={(o) => { if (!o) close() }}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>{editing ? 'ルール編集' : 'ルール新規登録'}</DialogTitle>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-3">
            <F label="送り先名（必須）"><Input value={form.sourceName}
              onChange={(e) => setForm({ ...form, sourceName: e.target.value })} /></F>
            <F label="支払先コード">
              <Input value={form.paymentSupplierCode ?? ''}
                onChange={(e) => setForm({ ...form, paymentSupplierCode: e.target.value })} />
            </F>
            <F label="種別">
              <Select value={form.ruleKind}
                onValueChange={(v) => setForm({ ...form, ruleKind: v as RuleKind })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {RULE_KINDS.map((k) => <SelectItem key={k} value={k}>{k}</SelectItem>)}
                </SelectContent>
              </Select>
            </F>
            <F label="優先度">
              <Input type="number" value={form.priority ?? 100}
                onChange={(e) => setForm({ ...form, priority: Number(e.target.value) })} />
            </F>
            <F label="借方勘定"><Input value={form.debitAccount}
              onChange={(e) => setForm({ ...form, debitAccount: e.target.value })} /></F>
            <F label="借方補助"><Input value={form.debitSubAccount ?? ''}
              onChange={(e) => setForm({ ...form, debitSubAccount: e.target.value })} /></F>
            <F label="借方部門"><Input value={form.debitDepartment ?? ''}
              onChange={(e) => setForm({ ...form, debitDepartment: e.target.value })} /></F>
            <F label="借方税区分">
              <Select value={form.debitTaxCategory}
                onValueChange={(v) => setForm({ ...form, debitTaxCategory: v })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {TAX_CATEGORIES.map((k) => <SelectItem key={k} value={k}>{k}</SelectItem>)}
                </SelectContent>
              </Select>
            </F>
            <F label="貸方勘定"><Input value={form.creditAccount ?? '資金複合'}
              onChange={(e) => setForm({ ...form, creditAccount: e.target.value })} /></F>
            <F label="貸方補助"><Input value={form.creditSubAccount ?? ''}
              onChange={(e) => setForm({ ...form, creditSubAccount: e.target.value })} /></F>
            <F label="貸方部門"><Input value={form.creditDepartment ?? ''}
              onChange={(e) => setForm({ ...form, creditDepartment: e.target.value })} /></F>
            <F label="貸方税区分">
              <Select value={form.creditTaxCategory ?? '対象外'}
                onValueChange={(v) => setForm({ ...form, creditTaxCategory: v })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {TAX_CATEGORIES.map((k) => <SelectItem key={k} value={k}>{k}</SelectItem>)}
                </SelectContent>
              </Select>
            </F>
            <F label="摘要テンプレ"><Input value={form.summaryTemplate}
              onChange={(e) => setForm({ ...form, summaryTemplate: e.target.value })} /></F>
            <F label="タグ"><Input value={form.tag ?? ''}
              onChange={(e) => setForm({ ...form, tag: e.target.value })} /></F>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={close}>キャンセル</Button>
            <Button disabled={!form.sourceName || !form.debitAccount || createMut.isPending || updateMut.isPending}
              onClick={submit}>
              {editing ? '更新' : '登録'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(o) => { if (!o) setDeleteTarget(null) }}
        title="ルールを削除しますか？"
        description={deleteTarget ? `「${deleteTarget.sourceName}」を削除します` : ''}
        onConfirm={() => deleteTarget && deleteMut.mutate(deleteTarget.id)}
      />

      <Dialog open={backfill !== null} onOpenChange={(o) => { if (!o) setBackfill(null) }}>
        <DialogContent className="max-w-4xl">
          <DialogHeader>
            <DialogTitle>
              支払先コード自動補完 {backfill?.dryRun ? '（プレビュー）' : '（結果）'}
            </DialogTitle>
          </DialogHeader>
          {backfill && (
            <div className="space-y-3">
              <div className="flex gap-4 text-sm">
                <span className="text-green-700">一致 {backfill.matchedCount}</span>
                <span className="text-amber-700">候補複数 {backfill.ambiguousCount}</span>
                <span className="text-red-600">該当なし {backfill.notFoundCount}</span>
                <span className="text-muted-foreground">対象外 {backfill.skippedCount}</span>
              </div>
              <p className="text-xs text-muted-foreground">
                対象: 種別=PAYABLE かつ支払先コード未設定のルール。
                m_payment_supplier の支払先名と正規化して一致照合します。
                候補複数／該当なしのルールは個別に編集してください。
              </p>
              <div className="max-h-[50vh] overflow-auto rounded border">
                <table className="min-w-full text-xs">
                  <thead className="sticky top-0 bg-muted">
                    <tr>
                      <th className="p-1 text-left">ルールID</th>
                      <th className="p-1 text-left">ルール送り先名</th>
                      <th className="p-1 text-left">状態</th>
                      <th className="p-1 text-left">候補コード</th>
                      <th className="p-1 text-left">候補名</th>
                    </tr>
                  </thead>
                  <tbody>
                    {backfill.items.map((it) => (
                      <tr key={it.ruleId} className="border-t">
                        <td className="p-1">{it.ruleId}</td>
                        <td className="p-1">{it.sourceName}</td>
                        <td className="p-1">
                          {it.status === 'MATCHED' && (
                            <span className="inline-flex items-center gap-1 text-green-700">
                              <CheckCircle2 className="h-3.5 w-3.5" />一致
                            </span>
                          )}
                          {it.status === 'AMBIGUOUS' && (
                            <span className="inline-flex items-center gap-1 text-amber-700">
                              <AlertTriangle className="h-3.5 w-3.5" />候補複数
                            </span>
                          )}
                          {it.status === 'NOT_FOUND' && (
                            <span className="inline-flex items-center gap-1 text-red-600">
                              <XCircle className="h-3.5 w-3.5" />該当なし
                            </span>
                          )}
                        </td>
                        <td className="p-1 font-mono">{it.candidateCode ?? ''}</td>
                        <td className="p-1">
                          {it.candidateName ?? (it.alternatives ? it.alternatives.join(' / ') : '')}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setBackfill(null)}>閉じる</Button>
            {backfill?.dryRun && isAdmin && (backfill.matchedCount > 0) && (
              <Button
                disabled={backfillMut.isPending}
                onClick={() => backfillMut.mutate(false)}>
                {backfillMut.isPending ? '適用中...' : `${backfill.matchedCount} 件を一括適用`}
              </Button>
            )}
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function F({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <Label className="text-xs">{label}</Label>
      {children}
    </div>
  )
}

/**
 * 会社表記ゆれ（㈱/株式会社/空白など）を吸収した正規化文字列を返す。
 *
 * 除去順序が重要: 「(株)」「(有)」は丸括弧を含むため、先に除去しないと
 * 括弧のみ除去されて「株」「有」が本文として残り、別エントリと誤マッチする。
 * 例: 「サラヤ(株)」→ `(株)` を先に除去 →「サラヤ」(正)
 *   従来の順序では `株式会社` 除去 →「サラヤ(株)」→ 括弧除去 →「サラヤ株」
 */
function normalizeName(s: string): string {
  return s
    .replace(/[\s\u3000]/g, '')
    .replace(/\(株\)|\(有\)|（株）|（有）/g, '')
    .replace(/株式会社|有限会社|合同会社|合資会社|合名会社/g, '')
    .replace(/[㈱㈲]/g, '')
    .replace(/[()（）]/g, '')
}

function blank(): PaymentMfRuleRequest {
  return {
    sourceName: '', paymentSupplierCode: '', ruleKind: 'PAYABLE',
    debitAccount: '買掛金', debitSubAccount: '', debitDepartment: '',
    debitTaxCategory: '対象外',
    creditAccount: '資金複合', creditSubAccount: '', creditDepartment: '',
    creditTaxCategory: '対象外',
    summaryTemplate: '{source_name}', tag: '', priority: 100,
  }
}
