'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select, SelectContent, SelectItem, SelectTrigger, SelectValue,
} from '@/components/ui/select'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import {
  AlertDialog, AlertDialogAction, AlertDialogCancel, AlertDialogContent,
  AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import type { Supplier } from '@/types/goods'

export function SupplierListPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const queryClient = useQueryClient()
  const [shopNo, setShopNo] = useState<string>(isAdmin ? '' : String(user?.shopNo ?? ''))
  const shopsQuery = useShops(isAdmin)

  const [createOpen, setCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<Supplier | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Supplier | null>(null)

  const suppliersQuery = useQuery({
    queryKey: ['masters', 'suppliers', shopNo],
    queryFn: () => api.get<Supplier[]>(`/masters/suppliers?shopNo=${shopNo}`),
    enabled: !!shopNo,
  })

  const createMutation = useMutation({
    mutationFn: (data: { supplierCode: string; supplierName: string; supplierNameDisplay: string; shopNo: number; standardLeadTime: number | null }) =>
      api.post('/masters/suppliers', data),
    onSuccess: () => {
      toast.success('仕入先を登録しました')
      queryClient.invalidateQueries({ queryKey: ['masters', 'suppliers'] })
      setCreateOpen(false)
    },
    onError: () => toast.error('登録に失敗しました'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: { supplierCode: string; supplierName: string; supplierNameDisplay: string; shopNo: number; standardLeadTime: number | null } }) =>
      api.put(`/masters/suppliers/${id}`, data),
    onSuccess: () => {
      toast.success('仕入先を更新しました')
      queryClient.invalidateQueries({ queryKey: ['masters', 'suppliers'] })
      setEditTarget(null)
    },
    onError: () => toast.error('更新に失敗しました'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/masters/suppliers/${id}`),
    onSuccess: () => {
      toast.success('仕入先を削除しました')
      queryClient.invalidateQueries({ queryKey: ['masters', 'suppliers'] })
      setDeleteTarget(null)
    },
    onError: () => toast.error('削除に失敗しました'),
  })

  const columns: Column<Supplier>[] = [
    { key: 'supplierCode', header: '仕入先コード', sortable: true },
    { key: 'supplierName', header: '仕入先名', sortable: true },
    { key: 'supplierNameDisplay', header: '表示名', sortable: true },
    { key: 'standardLeadTime', header: 'リードタイム(日)', sortable: true },
    { key: 'paymentSupplierName', header: '仕入支払先', sortable: true },
    ...(isAdmin ? [{
      key: '_actions' as keyof Supplier & string,
      header: '操作',
      render: (item: Supplier) => (
        <div className="flex gap-1">
          <Button size="sm" variant="ghost" onClick={(e) => { e.stopPropagation(); setEditTarget(item) }}><Pencil className="h-4 w-4" /></Button>
          <Button size="sm" variant="ghost" className="text-destructive hover:text-destructive" onClick={(e) => { e.stopPropagation(); setDeleteTarget(item) }}><Trash2 className="h-4 w-4" /></Button>
        </div>
      ),
    }] : []),
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="仕入先一覧"
        actions={isAdmin && shopNo ? (
          <Button size="sm" onClick={() => setCreateOpen(true)}>
            <Plus className="mr-1 h-4 w-4" />新規登録
          </Button>
        ) : undefined}
      />

      {isAdmin && (
        <div className="flex items-center gap-4">
          <div className="space-y-1">
            <Label>店舗</Label>
            <Select value={shopNo} onValueChange={setShopNo}>
              <SelectTrigger className="w-48">
                <SelectValue placeholder="店舗を選択" />
              </SelectTrigger>
              <SelectContent>
                {(shopsQuery.data ?? []).map((s) => (
                  <SelectItem key={s.shopNo} value={String(s.shopNo)}>
                    {s.shopName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      )}

      {!shopNo ? (
        <p className="text-muted-foreground">店舗を選択してください</p>
      ) : suppliersQuery.isLoading ? (
        <LoadingSpinner />
      ) : suppliersQuery.isError ? (
        <ErrorMessage onRetry={() => suppliersQuery.refetch()} />
      ) : (
        <DataTable data={suppliersQuery.data ?? []} columns={columns} searchPlaceholder="仕入先を検索..." rowKey={(item) => item.supplierNo} />
      )}

      {/* Create Dialog */}
      <SupplierFormDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        title="仕入先登録"
        initialValues={{ supplierCode: '', supplierName: '', supplierNameDisplay: '', standardLeadTime: '' }}
        onSubmit={(v) => createMutation.mutate({ ...v, shopNo: Number(shopNo), standardLeadTime: v.standardLeadTime ? Number(v.standardLeadTime) : null })}
        isPending={createMutation.isPending}
      />

      {/* Edit Dialog */}
      {editTarget && (
        <SupplierFormDialog
          key={editTarget.supplierNo}
          open={true}
          onOpenChange={(open) => { if (!open) setEditTarget(null) }}
          title="仕入先編集"
          initialValues={{
            supplierCode: editTarget.supplierCode ?? '',
            supplierName: editTarget.supplierName ?? '',
            supplierNameDisplay: editTarget.supplierNameDisplay ?? '',
            standardLeadTime: editTarget.standardLeadTime != null ? String(editTarget.standardLeadTime) : '',
          }}
          onSubmit={(v) => updateMutation.mutate({
            id: editTarget.supplierNo,
            data: { ...v, shopNo: editTarget.shopNo, standardLeadTime: v.standardLeadTime ? Number(v.standardLeadTime) : null },
          })}
          isPending={updateMutation.isPending}
        />
      )}

      {/* Delete Dialog */}
      <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>仕入先の削除</AlertDialogTitle>
            <AlertDialogDescription>
              「{deleteTarget?.supplierName}」（{deleteTarget?.supplierCode}）を削除しますか？
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>キャンセル</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.supplierNo)}
              disabled={deleteMutation.isPending}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {deleteMutation.isPending ? '削除中...' : '削除'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}

function SupplierFormDialog({
  open, onOpenChange, title, initialValues, onSubmit, isPending,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  initialValues: { supplierCode: string; supplierName: string; supplierNameDisplay: string; standardLeadTime: string }
  onSubmit: (v: { supplierCode: string; supplierName: string; supplierNameDisplay: string; standardLeadTime: string }) => void
  isPending: boolean
}) {
  const [values, setValues] = useState(initialValues)
  const handleOpen = (isOpen: boolean) => {
    if (isOpen) setValues(initialValues)
    onOpenChange(isOpen)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpen}>
      <DialogContent>
        <DialogHeader><DialogTitle>{title}</DialogTitle></DialogHeader>
        <form onSubmit={(e) => { e.preventDefault(); onSubmit(values) }} className="space-y-4">
          <div className="space-y-1">
            <Label>仕入先コード *</Label>
            <Input value={values.supplierCode} onChange={(e) => setValues({ ...values, supplierCode: e.target.value })} required />
          </div>
          <div className="space-y-1">
            <Label>仕入先名 *</Label>
            <Input value={values.supplierName} onChange={(e) => setValues({ ...values, supplierName: e.target.value })} required />
          </div>
          <div className="space-y-1">
            <Label>表示名</Label>
            <Input value={values.supplierNameDisplay} onChange={(e) => setValues({ ...values, supplierNameDisplay: e.target.value })} />
          </div>
          <div className="space-y-1">
            <Label>リードタイム（日）</Label>
            <Input type="number" value={values.standardLeadTime} onChange={(e) => setValues({ ...values, standardLeadTime: e.target.value })} />
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>キャンセル</Button>
            <Button type="submit" disabled={isPending}>{isPending ? '保存中...' : '保存'}</Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
