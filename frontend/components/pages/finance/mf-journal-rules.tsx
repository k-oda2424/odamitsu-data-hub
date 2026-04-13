'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PageHeader } from '@/components/features/common/PageHeader'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Checkbox } from '@/components/ui/checkbox'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import type { MfJournalRule, MfJournalRuleRequest } from '@/types/mf-cashbook'
import { TAX_RESOLVERS } from '@/types/mf-cashbook'

const initForm = (): MfJournalRuleRequest => ({
  descriptionC: '',
  descriptionDKeyword: '',
  priority: 100,
  amountSource: 'PAYMENT',
  debitAccount: '',
  debitSubAccount: '',
  debitDepartment: '',
  debitTaxResolver: 'OUTSIDE',
  creditAccount: '',
  creditSubAccount: '',
  creditSubAccountTemplate: '',
  creditDepartment: '',
  creditTaxResolver: 'OUTSIDE',
  summaryTemplate: '{d}',
  requiresClientMapping: false,
})

export default function MfJournalRulesPage() {
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const [editingId, setEditingId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState<MfJournalRuleRequest>(initForm())
  const [deleteTarget, setDeleteTarget] = useState<MfJournalRule | null>(null)

  const { data: rules = [], isLoading } = useQuery({
    queryKey: ['mf-journal-rules'],
    queryFn: () => api.get<MfJournalRule[]>('/finance/mf-journal-rules'),
  })

  const mutation = useMutation({
    mutationFn: async (payload: { id?: number; req: MfJournalRuleRequest }) => {
      if (payload.id) return api.put<MfJournalRule>(`/finance/mf-journal-rules/${payload.id}`, payload.req)
      return api.post<MfJournalRule>('/finance/mf-journal-rules', payload.req)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mf-journal-rules'] })
      toast.success(editingId ? '更新しました' : '追加しました')
      close()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/finance/mf-journal-rules/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mf-journal-rules'] })
      toast.success('削除しました')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const openCreate = () => { setCreating(true); setEditingId(null); setForm(initForm()) }
  const openEdit = (r: MfJournalRule) => {
    setEditingId(r.id)
    setCreating(false)
    setForm({
      descriptionC: r.descriptionC,
      descriptionDKeyword: r.descriptionDKeyword ?? '',
      priority: r.priority,
      amountSource: r.amountSource,
      debitAccount: r.debitAccount,
      debitSubAccount: r.debitSubAccount,
      debitDepartment: r.debitDepartment,
      debitTaxResolver: r.debitTaxResolver,
      creditAccount: r.creditAccount,
      creditSubAccount: r.creditSubAccount,
      creditSubAccountTemplate: r.creditSubAccountTemplate,
      creditDepartment: r.creditDepartment,
      creditTaxResolver: r.creditTaxResolver,
      summaryTemplate: r.summaryTemplate,
      requiresClientMapping: r.requiresClientMapping,
    })
  }
  const close = () => { setEditingId(null); setCreating(false) }

  return (
    <div className="space-y-4">
      <PageHeader
        title="MF仕訳ルール"
        actions={
          isAdmin && (
            <Button onClick={openCreate}>
              <Plus className="mr-1 h-4 w-4" />
              新規追加
            </Button>
          )
        }
      />

      {!isAdmin && (
        <div className="rounded border bg-amber-50 p-3 text-sm text-amber-800">
          編集・追加・削除は管理者（shop_no=0）のみ可能です
        </div>
      )}

      <div className="overflow-auto rounded border">
        <table className="min-w-full text-xs">
          <thead className="bg-muted">
            <tr>
              <th className="p-2 text-left">摘要C</th>
              <th className="p-2 text-left">D条件</th>
              <th className="p-2 text-center">優先度</th>
              <th className="p-2 text-center">金額</th>
              <th className="p-2 text-left">借方</th>
              <th className="p-2 text-left">借方税</th>
              <th className="p-2 text-left">貸方</th>
              <th className="p-2 text-left">貸方税</th>
              <th className="p-2 text-left">摘要テンプレ</th>
              <th className="p-2 text-center">要マップ</th>
              <th className="p-2 w-24"></th>
            </tr>
          </thead>
          <tbody>
            {isLoading && <tr><td colSpan={11} className="p-4 text-center">読み込み中...</td></tr>}
            {rules.map((r) => (
              <tr key={r.id} className="border-t">
                <td className="p-2">{r.descriptionC}</td>
                <td className="p-2">{r.descriptionDKeyword ?? '-'}</td>
                <td className="p-2 text-center">{r.priority}</td>
                <td className="p-2 text-center">{r.amountSource === 'INCOME' ? '収入' : '支払'}</td>
                <td className="p-2">{r.debitAccount}{r.debitSubAccount ? `/${r.debitSubAccount}` : ''}</td>
                <td className="p-2">{r.debitTaxResolver}</td>
                <td className="p-2">{r.creditAccount}{r.creditSubAccount ? `/${r.creditSubAccount}` : ''}{r.creditSubAccountTemplate ? `/${r.creditSubAccountTemplate}` : ''}</td>
                <td className="p-2">{r.creditTaxResolver}</td>
                <td className="p-2">{r.summaryTemplate}</td>
                <td className="p-2 text-center">{r.requiresClientMapping ? '✓' : ''}</td>
                <td className="p-2">
                  {isAdmin && (
                    <div className="flex gap-1">
                      <Button size="sm" variant="ghost" onClick={() => openEdit(r)}>
                        <Pencil className="h-3.5 w-3.5" />
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setDeleteTarget(r)}
                      >
                        <Trash2 className="h-3.5 w-3.5 text-red-500" />
                      </Button>
                    </div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Dialog open={creating || editingId !== null} onOpenChange={(o) => { if (!o) close() }}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>{editingId ? 'ルール編集' : 'ルール追加'}</DialogTitle>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-3 text-sm">
            <Field label="摘要C"><Input value={form.descriptionC} onChange={(e) => setForm({ ...form, descriptionC: e.target.value })} /></Field>
            <Field label="摘要D キーワード"><Input value={form.descriptionDKeyword ?? ''} onChange={(e) => setForm({ ...form, descriptionDKeyword: e.target.value })} /></Field>
            <Field label="優先度"><Input type="number" value={form.priority} onChange={(e) => setForm({ ...form, priority: Number(e.target.value) })} /></Field>
            <Field label="金額ソース">
              <Select value={form.amountSource} onValueChange={(v) => setForm({ ...form, amountSource: v as 'INCOME' | 'PAYMENT' })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  <SelectItem value="INCOME">収入</SelectItem>
                  <SelectItem value="PAYMENT">支払</SelectItem>
                </SelectContent>
              </Select>
            </Field>
            <Field label="借方勘定科目"><Input value={form.debitAccount} onChange={(e) => setForm({ ...form, debitAccount: e.target.value })} /></Field>
            <Field label="借方補助"><Input value={form.debitSubAccount ?? ''} onChange={(e) => setForm({ ...form, debitSubAccount: e.target.value })} /></Field>
            <Field label="借方部門"><Input value={form.debitDepartment ?? ''} onChange={(e) => setForm({ ...form, debitDepartment: e.target.value })} /></Field>
            <Field label="借方税リゾルバ"><TaxSelect value={form.debitTaxResolver} onChange={(v) => setForm({ ...form, debitTaxResolver: v })} /></Field>
            <Field label="貸方勘定科目"><Input value={form.creditAccount} onChange={(e) => setForm({ ...form, creditAccount: e.target.value })} /></Field>
            <Field label="貸方補助"><Input value={form.creditSubAccount ?? ''} onChange={(e) => setForm({ ...form, creditSubAccount: e.target.value })} /></Field>
            <Field label="貸方補助テンプレ"><Input value={form.creditSubAccountTemplate ?? ''} onChange={(e) => setForm({ ...form, creditSubAccountTemplate: e.target.value })} placeholder="例: ゴミ袋／{client}" /></Field>
            <Field label="貸方部門"><Input value={form.creditDepartment ?? ''} onChange={(e) => setForm({ ...form, creditDepartment: e.target.value })} /></Field>
            <Field label="貸方税リゾルバ"><TaxSelect value={form.creditTaxResolver} onChange={(v) => setForm({ ...form, creditTaxResolver: v })} /></Field>
            <Field label="摘要テンプレ"><Input value={form.summaryTemplate} onChange={(e) => setForm({ ...form, summaryTemplate: e.target.value })} placeholder="例: {d} 印紙税" /></Field>
            <div className="col-span-2 flex items-center gap-2">
              <Checkbox checked={form.requiresClientMapping} onCheckedChange={(v) => setForm({ ...form, requiresClientMapping: Boolean(v) })} id="rcm" />
              <Label htmlFor="rcm">得意先マッピング必須</Label>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={close}>キャンセル</Button>
            <Button
              disabled={!form.descriptionC || !form.debitAccount || !form.creditAccount || mutation.isPending}
              onClick={() => mutation.mutate({ id: editingId ?? undefined, req: form })}
            >
              {editingId ? '更新' : '追加'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(o) => { if (!o) setDeleteTarget(null) }}
        title="削除確認"
        description={deleteTarget ? `${deleteTarget.descriptionC} を削除しますか？` : ''}
        confirmLabel="削除"
        variant="destructive"
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
      />
    </div>
  )
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <Label className="text-xs">{label}</Label>
      {children}
    </div>
  )
}

function TaxSelect({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <Select value={value} onValueChange={onChange}>
      <SelectTrigger><SelectValue /></SelectTrigger>
      <SelectContent>
        {TAX_RESOLVERS.map((r) => <SelectItem key={r} value={r}>{r}</SelectItem>)}
      </SelectContent>
    </Select>
  )
}
