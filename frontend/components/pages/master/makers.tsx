'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { toast } from 'sonner'

interface Maker {
  makerNo: number
  makerName: string
  [key: string]: unknown
}

export function MakerListPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const queryClient = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<Maker | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Maker | null>(null)
  const [formValue, setFormValue] = useState('')

  const makersQuery = useQuery({
    queryKey: ['masters', 'makers'],
    queryFn: () => api.get<Maker[]>('/masters/makers'),
  })

  const createMutation = useMutation({
    mutationFn: (data: { makerName: string }) => api.post<Maker>('/masters/makers', data),
    onSuccess: () => { toast.success('メーカーを登録しました'); queryClient.invalidateQueries({ queryKey: ['masters', 'makers'] }); setCreateOpen(false) },
    onError: () => toast.error('登録に失敗しました'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: { makerName: string } }) => api.put<Maker>(`/masters/makers/${id}`, data),
    onSuccess: () => { toast.success('メーカーを更新しました'); queryClient.invalidateQueries({ queryKey: ['masters', 'makers'] }); setEditTarget(null) },
    onError: () => toast.error('更新に失敗しました'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/masters/makers/${id}`),
    onSuccess: () => { toast.success('メーカーを削除しました'); queryClient.invalidateQueries({ queryKey: ['masters', 'makers'] }); setDeleteTarget(null) },
    onError: () => toast.error('削除に失敗しました'),
  })

  const columns: Column<Maker>[] = [
    { key: 'makerNo', header: 'メーカーNo', sortable: true },
    { key: 'makerName', header: 'メーカー名', sortable: true },
    ...(isAdmin ? [{
      key: '_actions',
      header: '操作',
      render: (item: Maker) => (
        <div className="flex gap-1">
          <Button size="sm" variant="ghost" onClick={(e) => { e.stopPropagation(); setEditTarget(item); setFormValue(item.makerName) }}><Pencil className="h-4 w-4" /></Button>
          <Button size="sm" variant="ghost" className="text-destructive hover:text-destructive" onClick={(e) => { e.stopPropagation(); setDeleteTarget(item) }}><Trash2 className="h-4 w-4" /></Button>
        </div>
      ),
    }] : []),
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="メーカー一覧"
        actions={isAdmin ? (
          <Button onClick={() => { setFormValue(''); setCreateOpen(true) }}>
            <Plus className="mr-2 h-4 w-4" />新規登録
          </Button>
        ) : undefined}
      />
      {makersQuery.isLoading ? (
        <LoadingSpinner />
      ) : makersQuery.isError ? (
        <ErrorMessage onRetry={() => makersQuery.refetch()} />
      ) : (
        <DataTable data={makersQuery.data ?? []} columns={columns} searchPlaceholder="メーカーを検索..." />
      )}

      {/* Create/Edit Dialog */}
      <Dialog
        key={editTarget ? `edit-${editTarget.makerNo}` : 'create'}
        open={createOpen || editTarget !== null}
        onOpenChange={(open) => { if (!open) { setCreateOpen(false); setEditTarget(null) } }}
      >
        <DialogContent>
          <DialogHeader>
            <DialogTitle>{editTarget ? 'メーカー編集' : 'メーカー登録'}</DialogTitle>
          </DialogHeader>
          <form onSubmit={(e) => {
            e.preventDefault()
            if (editTarget) {
              updateMutation.mutate({ id: editTarget.makerNo, data: { makerName: formValue } })
            } else {
              createMutation.mutate({ makerName: formValue })
            }
          }} className="space-y-4">
            <div className="space-y-1">
              <Label>メーカー名 *</Label>
              <Input value={formValue} onChange={(e) => setFormValue(e.target.value)} required />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setCreateOpen(false); setEditTarget(null) }}>キャンセル</Button>
              <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>
                {(createMutation.isPending || updateMutation.isPending) ? '保存中...' : '保存'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Dialog */}
      <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>メーカーの削除</AlertDialogTitle>
            <AlertDialogDescription>「{deleteTarget?.makerName}」を削除しますか？</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>キャンセル</AlertDialogCancel>
            <AlertDialogAction onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.makerNo)}
              disabled={deleteMutation.isPending} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              {deleteMutation.isPending ? '削除中...' : '削除'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
