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
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import type { OffsetJournalRule, OffsetJournalRuleRequest } from '@/types/offset-journal-rule'

const initForm = (): OffsetJournalRuleRequest => ({
  shopNo: 1,
  creditAccount: '仕入値引・戻し高',
  creditSubAccount: '',
  creditDepartment: '物販事業部',
  creditTaxCategory: '課税仕入-返還等 10%',
  summaryPrefix: '相殺／',
})

export default function OffsetJournalRulePage() {
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const [editingId, setEditingId] = useState<number | null>(null)
  const [creating, setCreating] = useState(false)
  const [form, setForm] = useState<OffsetJournalRuleRequest>(initForm())
  const [deleteTarget, setDeleteTarget] = useState<OffsetJournalRule | null>(null)

  const { data: rules = [], isLoading } = useQuery({
    queryKey: ['offset-journal-rules'],
    queryFn: () => api.get<OffsetJournalRule[]>('/finance/offset-journal-rules'),
  })

  const mutation = useMutation({
    mutationFn: async (payload: { id?: number; req: OffsetJournalRuleRequest }) => {
      if (payload.id) {
        return api.put<OffsetJournalRule>(`/finance/offset-journal-rules/${payload.id}`, payload.req)
      }
      return api.post<OffsetJournalRule>('/finance/offset-journal-rules', payload.req)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offset-journal-rules'] })
      toast.success(editingId ? '更新しました' : '追加しました')
      close()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/finance/offset-journal-rules/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['offset-journal-rules'] })
      toast.success('削除しました')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const openCreate = () => {
    setCreating(true)
    setEditingId(null)
    setForm(initForm())
  }
  const openEdit = (r: OffsetJournalRule) => {
    setEditingId(r.id)
    setCreating(false)
    setForm({
      shopNo: r.shopNo,
      creditAccount: r.creditAccount,
      creditSubAccount: r.creditSubAccount ?? '',
      creditDepartment: r.creditDepartment ?? '',
      creditTaxCategory: r.creditTaxCategory,
      summaryPrefix: r.summaryPrefix,
    })
  }
  const close = () => {
    setEditingId(null)
    setCreating(false)
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="OFFSET仕訳マスタ"
        description="買掛仕入MF変換の OFFSET 副行（相殺）の貸方科目を管理します。税理士確認後に値を変更できます。"
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
              <th className="p-2 text-center">店舗</th>
              <th className="p-2 text-left">貸方勘定</th>
              <th className="p-2 text-left">貸方補助</th>
              <th className="p-2 text-left">貸方部門</th>
              <th className="p-2 text-left">貸方税区分</th>
              <th className="p-2 text-left">摘要プレフィックス</th>
              <th className="p-2 w-24"></th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td colSpan={7} className="p-4 text-center">
                  読み込み中...
                </td>
              </tr>
            )}
            {!isLoading && rules.length === 0 && (
              <tr>
                <td colSpan={7} className="p-4 text-center text-muted-foreground">
                  登録なし
                </td>
              </tr>
            )}
            {rules.map((r) => (
              <tr key={r.id} className="border-t">
                <td className="p-2 text-center">{r.shopNo}</td>
                <td className="p-2">{r.creditAccount}</td>
                <td className="p-2">{r.creditSubAccount ?? '-'}</td>
                <td className="p-2">{r.creditDepartment ?? '-'}</td>
                <td className="p-2">{r.creditTaxCategory}</td>
                <td className="p-2">{r.summaryPrefix}</td>
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
        <DialogContent className="max-w-xl">
          <DialogHeader>
            <DialogTitle>{editingId ? 'OFFSET仕訳マスタ編集' : 'OFFSET仕訳マスタ追加'}</DialogTitle>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-3 text-sm">
            <Field label="店舗No">
              <Input
                type="number"
                value={form.shopNo}
                onChange={(e) => setForm({ ...form, shopNo: Number(e.target.value) })}
              />
            </Field>
            <Field label="貸方勘定科目">
              <Input
                value={form.creditAccount}
                onChange={(e) => setForm({ ...form, creditAccount: e.target.value })}
              />
            </Field>
            <Field label="貸方補助">
              <Input
                value={form.creditSubAccount ?? ''}
                onChange={(e) => setForm({ ...form, creditSubAccount: e.target.value })}
              />
            </Field>
            <Field label="貸方部門">
              <Input
                value={form.creditDepartment ?? ''}
                onChange={(e) => setForm({ ...form, creditDepartment: e.target.value })}
              />
            </Field>
            <Field label="貸方税区分">
              <Input
                value={form.creditTaxCategory}
                onChange={(e) => setForm({ ...form, creditTaxCategory: e.target.value })}
              />
            </Field>
            <Field label="摘要プレフィックス">
              <Input
                value={form.summaryPrefix}
                onChange={(e) => setForm({ ...form, summaryPrefix: e.target.value })}
                placeholder="例: 相殺／"
              />
            </Field>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={close}>
              キャンセル
            </Button>
            <Button
              disabled={
                !form.creditAccount ||
                !form.creditTaxCategory ||
                !form.summaryPrefix ||
                mutation.isPending
              }
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
        description={
          deleteTarget
            ? `店舗 ${deleteTarget.shopNo} の OFFSET 仕訳マスタを削除しますか？`
            : ''
        }
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
