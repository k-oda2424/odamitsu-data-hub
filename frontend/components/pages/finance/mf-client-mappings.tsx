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
import type { MfClientMapping, MfClientMappingRequest } from '@/types/mf-cashbook'

export default function MfClientMappingsPage() {
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const [search, setSearch] = useState('')
  const [editing, setEditing] = useState<MfClientMapping | null>(null)
  const [creating, setCreating] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<MfClientMapping | null>(null)
  const [alias, setAlias] = useState('')
  const [mfName, setMfName] = useState('')

  const { data: mappings = [], isLoading } = useQuery({
    queryKey: ['mf-client-mappings'],
    queryFn: () => api.get<MfClientMapping[]>('/finance/mf-client-mappings'),
  })

  const createMutation = useMutation({
    mutationFn: (req: MfClientMappingRequest) => api.post<MfClientMapping>('/finance/mf-client-mappings', req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mf-client-mappings'] })
      toast.success('追加しました')
      closeDialog()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, req }: { id: number; req: MfClientMappingRequest }) =>
      api.put<MfClientMapping>(`/finance/mf-client-mappings/${id}`, req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mf-client-mappings'] })
      toast.success('更新しました')
      closeDialog()
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/finance/mf-client-mappings/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mf-client-mappings'] })
      toast.success('削除しました')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const openCreate = () => {
    setCreating(true)
    setEditing(null)
    setAlias('')
    setMfName('')
  }
  const openEdit = (m: MfClientMapping) => {
    setEditing(m)
    setCreating(false)
    setAlias(m.alias)
    setMfName(m.mfClientName)
  }
  const closeDialog = () => {
    setEditing(null)
    setCreating(false)
    setAlias('')
    setMfName('')
  }
  const submit = () => {
    const req = { alias, mfClientName: mfName }
    if (editing) updateMutation.mutate({ id: editing.id, req })
    else createMutation.mutate(req)
  }

  const filtered = mappings.filter(
    (m) => !search || m.alias.includes(search) || m.mfClientName.includes(search)
  )

  return (
    <div className="space-y-4">
      <PageHeader
        title="MF得意先マッピング"
        actions={
          <Button onClick={openCreate}>
            <Plus className="mr-1 h-4 w-4" />
            新規追加
          </Button>
        }
      />

      <div className="flex items-center gap-2">
        <Input
          placeholder="alias / MF名で検索"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="max-w-md"
        />
        <span className="text-sm text-muted-foreground">{filtered.length} / {mappings.length}件</span>
      </div>

      <div className="overflow-auto rounded border">
        <table className="min-w-full text-sm">
          <thead className="bg-muted">
            <tr>
              <th className="p-2 text-left">Alias（Excel表記）</th>
              <th className="p-2 text-left">MoneyForward正式名</th>
              <th className="p-2 w-32"></th>
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr><td colSpan={3} className="p-4 text-center text-muted-foreground">読み込み中...</td></tr>
            )}
            {!isLoading && filtered.length === 0 && (
              <tr><td colSpan={3} className="p-4 text-center text-muted-foreground">データがありません</td></tr>
            )}
            {filtered.map((m) => (
              <tr key={m.id} className="border-t">
                <td className="p-2"><code>{m.alias}</code></td>
                <td className="p-2">{m.mfClientName}</td>
                <td className="p-2">
                  <div className="flex gap-1">
                    <Button size="sm" variant="ghost" onClick={() => openEdit(m)}>
                      <Pencil className="h-3.5 w-3.5" />
                    </Button>
                    {isAdmin && (
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setDeleteTarget(m)}
                      >
                        <Trash2 className="h-3.5 w-3.5 text-red-500" />
                      </Button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <Dialog open={creating || editing !== null} onOpenChange={(o) => { if (!o) closeDialog() }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editing ? 'マッピング編集' : 'マッピング追加'}</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div>
              <Label>Alias（Excel表記）</Label>
              <Input value={alias} onChange={(e) => setAlias(e.target.value)} />
            </div>
            <div>
              <Label>MoneyForward正式名</Label>
              <Input value={mfName} onChange={(e) => setMfName(e.target.value)} />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={closeDialog}>キャンセル</Button>
            <Button
              disabled={!alias || !mfName || createMutation.isPending || updateMutation.isPending}
              onClick={submit}
            >
              {editing ? '更新' : '追加'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(o) => { if (!o) setDeleteTarget(null) }}
        title="削除確認"
        description={deleteTarget ? `${deleteTarget.alias} を削除しますか？` : ''}
        confirmLabel="削除"
        variant="destructive"
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
      />
    </div>
  )
}
