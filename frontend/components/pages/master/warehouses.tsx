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

interface Warehouse {
  warehouseNo: number
  warehouseName: string
  companyNo: number
  [key: string]: unknown
}

export function WarehouseListPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const queryClient = useQueryClient()
  const [createOpen, setCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<Warehouse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Warehouse | null>(null)
  const [formName, setFormName] = useState('')
  const [formCompanyNo, setFormCompanyNo] = useState(1)

  const warehousesQuery = useQuery({
    queryKey: ['masters', 'warehouses'],
    queryFn: () => api.get<Warehouse[]>('/masters/warehouses'),
  })

  const createMutation = useMutation({
    mutationFn: (data: { warehouseName: string; companyNo: number }) => api.post<Warehouse>('/masters/warehouses', data),
    onSuccess: () => { toast.success('倉庫を登録しました'); queryClient.invalidateQueries({ queryKey: ['masters', 'warehouses'] }); setCreateOpen(false) },
    onError: () => toast.error('登録に失敗しました'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: { warehouseName: string; companyNo: number } }) => api.put<Warehouse>(`/masters/warehouses/${id}`, data),
    onSuccess: () => { toast.success('倉庫を更新しました'); queryClient.invalidateQueries({ queryKey: ['masters', 'warehouses'] }); setEditTarget(null) },
    onError: () => toast.error('更新に失敗しました'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/masters/warehouses/${id}`),
    onSuccess: () => { toast.success('倉庫を削除しました'); queryClient.invalidateQueries({ queryKey: ['masters', 'warehouses'] }); setDeleteTarget(null) },
    onError: () => toast.error('削除に失敗しました'),
  })

  const columns: Column<Warehouse>[] = [
    { key: 'warehouseNo', header: '倉庫No', sortable: true },
    { key: 'warehouseName', header: '倉庫名', sortable: true },
    { key: 'companyNo', header: '会社No' },
    ...(isAdmin ? [{
      key: '_actions',
      header: '操作',
      render: (item: Warehouse) => (
        <div className="flex gap-1">
          <Button size="sm" variant="ghost" onClick={(e) => { e.stopPropagation(); setEditTarget(item); setFormName(item.warehouseName); setFormCompanyNo(item.companyNo) }}><Pencil className="h-4 w-4" /></Button>
          <Button size="sm" variant="ghost" className="text-destructive hover:text-destructive" onClick={(e) => { e.stopPropagation(); setDeleteTarget(item) }}><Trash2 className="h-4 w-4" /></Button>
        </div>
      ),
    }] : []),
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="倉庫一覧"
        actions={isAdmin ? (
          <Button onClick={() => { setFormName(''); setFormCompanyNo(1); setCreateOpen(true) }}>
            <Plus className="mr-2 h-4 w-4" />新規登録
          </Button>
        ) : undefined}
      />
      {warehousesQuery.isLoading ? <LoadingSpinner /> :
       warehousesQuery.isError ? <ErrorMessage onRetry={() => warehousesQuery.refetch()} /> :
       <DataTable data={warehousesQuery.data ?? []} columns={columns} searchPlaceholder="倉庫を検索..." />}

      <Dialog key={editTarget ? `edit-${editTarget.warehouseNo}` : 'create'} open={createOpen || editTarget !== null} onOpenChange={(open) => { if (!open) { setCreateOpen(false); setEditTarget(null) } }}>
        <DialogContent>
          <DialogHeader><DialogTitle>{editTarget ? '倉庫編集' : '倉庫登録'}</DialogTitle></DialogHeader>
          <form onSubmit={(e) => {
            e.preventDefault()
            const data = { warehouseName: formName, companyNo: formCompanyNo }
            editTarget ? updateMutation.mutate({ id: editTarget.warehouseNo, data }) : createMutation.mutate(data)
          }} className="space-y-4">
            <div className="space-y-1"><Label>倉庫名 *</Label><Input value={formName} onChange={(e) => setFormName(e.target.value)} required /></div>
            <div className="space-y-1"><Label>会社No *</Label><Input type="number" value={formCompanyNo} onChange={(e) => setFormCompanyNo(Number(e.target.value))} required /></div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setCreateOpen(false); setEditTarget(null) }}>キャンセル</Button>
              <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>保存</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader><AlertDialogTitle>倉庫の削除</AlertDialogTitle>
            <AlertDialogDescription>「{deleteTarget?.warehouseName}」を削除しますか？</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>キャンセル</AlertDialogCancel>
            <AlertDialogAction onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.warehouseNo)}
              disabled={deleteMutation.isPending} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">削除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
