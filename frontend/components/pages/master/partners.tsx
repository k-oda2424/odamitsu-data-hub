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

interface Partner {
  partnerNo: number
  partnerCode: string
  partnerName: string
  shopNo: number
  companyNo: number
}

export function PartnerListPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const queryClient = useQueryClient()
  const [shopNo, setShopNo] = useState<string>(isAdmin ? '' : String(user?.shopNo ?? ''))
  const shopsQuery = useShops(isAdmin)
  const [createOpen, setCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<Partner | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Partner | null>(null)
  const [formCode, setFormCode] = useState('')
  const [formName, setFormName] = useState('')

  const partnersQuery = useQuery({
    queryKey: ['masters', 'partners', shopNo],
    queryFn: () => api.get<Partner[]>(`/masters/partners?shopNo=${shopNo}`),
    enabled: !!shopNo,
  })

  const createMutation = useMutation({
    mutationFn: (data: { partnerCode: string; partnerName: string; shopNo: number }) =>
      api.post<Partner>('/masters/partners', data),
    onSuccess: () => { toast.success('得意先を登録しました'); queryClient.invalidateQueries({ queryKey: ['masters', 'partners'] }); setCreateOpen(false) },
    onError: () => toast.error('登録に失敗しました'),
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: { partnerCode: string; partnerName: string; shopNo: number } }) =>
      api.put<Partner>(`/masters/partners/${id}`, data),
    onSuccess: () => { toast.success('得意先を更新しました'); queryClient.invalidateQueries({ queryKey: ['masters', 'partners'] }); setEditTarget(null) },
    onError: () => toast.error('更新に失敗しました'),
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/masters/partners/${id}`),
    onSuccess: () => { toast.success('得意先を削除しました'); queryClient.invalidateQueries({ queryKey: ['masters', 'partners'] }); setDeleteTarget(null) },
    onError: () => toast.error('削除に失敗しました'),
  })

  const columns: Column<Partner>[] = [
    { key: 'partnerCode', header: '得意先コード', sortable: true },
    { key: 'partnerName', header: '得意先名', sortable: true },
    ...(isAdmin ? [{
      key: '_actions',
      header: '操作',
      render: (item: Partner) => (
        <div className="flex gap-1">
          <Button size="sm" variant="ghost" onClick={(e) => { e.stopPropagation(); setEditTarget(item); setFormCode(item.partnerCode); setFormName(item.partnerName) }}><Pencil className="h-4 w-4" /></Button>
          <Button size="sm" variant="ghost" className="text-destructive hover:text-destructive" onClick={(e) => { e.stopPropagation(); setDeleteTarget(item) }}><Trash2 className="h-4 w-4" /></Button>
        </div>
      ),
    }] : []),
  ]

  return (
    <div className="space-y-6">
      <PageHeader
        title="得意先一覧"
        actions={isAdmin && shopNo ? (
          <Button onClick={() => { setFormCode(''); setFormName(''); setCreateOpen(true) }}>
            <Plus className="mr-2 h-4 w-4" />新規登録
          </Button>
        ) : undefined}
      />

      {isAdmin && (
        <div className="flex items-center gap-4">
          <div className="space-y-1">
            <Label>店舗</Label>
            <Select value={shopNo} onValueChange={setShopNo}>
              <SelectTrigger className="w-48"><SelectValue placeholder="店舗を選択" /></SelectTrigger>
              <SelectContent>
                {(shopsQuery.data ?? []).map((s) => (
                  <SelectItem key={s.shopNo} value={String(s.shopNo)}>{s.shopName}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>
      )}

      {!shopNo ? (
        <p className="text-muted-foreground">店舗を選択してください</p>
      ) : partnersQuery.isLoading ? <LoadingSpinner /> :
        partnersQuery.isError ? <ErrorMessage onRetry={() => partnersQuery.refetch()} /> :
        <DataTable data={partnersQuery.data ?? []} columns={columns} searchPlaceholder="得意先を検索..." rowKey={(item) => item.partnerNo} />}

      <Dialog key={editTarget ? `edit-${editTarget.partnerNo}` : 'create'} open={createOpen || editTarget !== null} onOpenChange={(open) => { if (!open) { setCreateOpen(false); setEditTarget(null) } }}>
        <DialogContent>
          <DialogHeader><DialogTitle>{editTarget ? '得意先編集' : '得意先登録'}</DialogTitle></DialogHeader>
          <form onSubmit={(e) => {
            e.preventDefault()
            if (editTarget) {
              updateMutation.mutate({ id: editTarget.partnerNo, data: { partnerCode: formCode, partnerName: formName, shopNo: editTarget.shopNo } })
            } else {
              createMutation.mutate({ partnerCode: formCode, partnerName: formName, shopNo: Number(shopNo) })
            }
          }} className="space-y-4">
            <div className="space-y-1"><Label>得意先コード *</Label><Input value={formCode} onChange={(e) => setFormCode(e.target.value)} required /></div>
            <div className="space-y-1"><Label>得意先名 *</Label><Input value={formName} onChange={(e) => setFormName(e.target.value)} required /></div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => { setCreateOpen(false); setEditTarget(null) }}>キャンセル</Button>
              <Button type="submit" disabled={createMutation.isPending || updateMutation.isPending}>保存</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader><AlertDialogTitle>得意先の削除</AlertDialogTitle>
            <AlertDialogDescription>「{deleteTarget?.partnerName}」（{deleteTarget?.partnerCode}）を削除しますか？</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>キャンセル</AlertDialogCancel>
            <AlertDialogAction onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.partnerNo)}
              disabled={deleteMutation.isPending} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">削除</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
