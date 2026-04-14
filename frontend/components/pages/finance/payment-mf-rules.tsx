'use client'

import { useMemo, useState } from 'react'
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
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import type { PaymentMfRule, PaymentMfRuleRequest, RuleKind } from '@/types/payment-mf'
import { RULE_KINDS, TAX_CATEGORIES } from '@/types/payment-mf'

export default function PaymentMfRulesPage() {
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<PaymentMfRule | null>(null)
  const [creating, setCreating] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<PaymentMfRule | null>(null)
  const [form, setForm] = useState<PaymentMfRuleRequest>(blank())

  const { data: rules = [], isLoading } = useQuery({
    queryKey: ['payment-mf-rules'],
    queryFn: () => api.get<PaymentMfRule[]>('/finance/payment-mf/rules'),
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

  const filtered = useMemo(() => {
    const q = search.toLowerCase()
    return rules.filter((r) =>
      !q || r.sourceName.toLowerCase().includes(q)
          || (r.debitSubAccount ?? '').toLowerCase().includes(q)
          || (r.paymentSupplierCode ?? '').includes(q)
    )
  }, [rules, search])

  function close() {
    setCreating(false); setEditing(null); setForm(blank())
  }
  function openCreate() { setForm(blank()); setCreating(true) }
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
          <Button size="sm" onClick={openCreate}>
            <Plus className="mr-1 h-4 w-4" />新規
          </Button>
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
                        <Button size="sm" variant="ghost" onClick={() => openEdit(r)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button size="sm" variant="ghost"
                          onClick={() => setDeleteTarget(r)}>
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
