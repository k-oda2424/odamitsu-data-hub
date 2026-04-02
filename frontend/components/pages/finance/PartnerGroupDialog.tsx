'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { Trash2, Pencil } from 'lucide-react'
import { toast } from 'sonner'

interface PartnerGroup {
  partnerGroupId: number
  groupName: string
  shopNo: number
  partnerCodes: string[]
}

interface PartnerGroupDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  groups: PartnerGroup[]
  shopNo: number
  editingGroup?: PartnerGroup | null
}

export function PartnerGroupDialog({ open, onOpenChange, groups, shopNo, editingGroup: initialEditingGroup }: PartnerGroupDialogProps) {
  const queryClient = useQueryClient()
  const [editingGroup, setEditingGroup] = useState<PartnerGroup | null>(initialEditingGroup ?? null)
  const [groupName, setGroupName] = useState(initialEditingGroup?.groupName ?? '')
  const [groupPartnerCodes, setGroupPartnerCodes] = useState(initialEditingGroup?.partnerCodes.join('\n') ?? '')
  const [deleteTarget, setDeleteTarget] = useState<PartnerGroup | null>(null)

  const saveMutation = useMutation({
    mutationFn: (data: { id?: number; groupName: string; shopNo: number; partnerCodes: string[] }) => {
      const body = { groupName: data.groupName, shopNo: data.shopNo, partnerCodes: data.partnerCodes }
      return data.id
        ? api.put<PartnerGroup>(`/finance/partner-groups/${data.id}`, body)
        : api.post<PartnerGroup>('/finance/partner-groups', body)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['partner-groups'] })
      onOpenChange(false)
      toast.success('グループを保存しました')
    },
    onError: () => {
      toast.error('グループの保存に失敗しました')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/finance/partner-groups/${id}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['partner-groups'] })
      setDeleteTarget(null)
      toast.success('グループを削除しました')
    },
  })

  const handleEdit = (group: PartnerGroup) => {
    setEditingGroup(group)
    setGroupName(group.groupName)
    setGroupPartnerCodes(group.partnerCodes.join('\n'))
  }

  const handleNewGroup = () => {
    setEditingGroup(null)
    setGroupName('')
    setGroupPartnerCodes('')
  }

  const handleSave = () => {
    if (!groupName.trim()) return
    const codes = groupPartnerCodes
      .split(/[\n,\s]+/)
      .map((c) => c.trim())
      .filter((c) => c.length > 0)
    saveMutation.mutate({
      id: editingGroup?.partnerGroupId,
      groupName: groupName.trim(),
      shopNo,
      partnerCodes: codes,
    })
  }

  const handleClose = () => {
    onOpenChange(false)
    setEditingGroup(null)
    setGroupName('')
    setGroupPartnerCodes('')
  }

  return (
    <>
      <Dialog open={open} onOpenChange={(o) => { if (!o) handleClose() }}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>入金グループ管理</DialogTitle>
          </DialogHeader>
          <div className="space-y-4">
            {groups.length > 0 && (
              <div className="space-y-2">
                <Label>登録済みグループ</Label>
                <div className="max-h-40 overflow-auto rounded border divide-y">
                  {groups.map((g) => (
                    <div key={g.partnerGroupId} className="flex items-center justify-between px-3 py-2 text-sm">
                      <span>{g.groupName}（{g.partnerCodes.length}件）</span>
                      <div className="flex gap-1">
                        <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => handleEdit(g)}>
                          <Pencil className="h-3 w-3" />
                        </Button>
                        <Button variant="ghost" size="icon" className="h-7 w-7 text-destructive" onClick={() => setDeleteTarget(g)}>
                          <Trash2 className="h-3 w-3" />
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="border-t pt-4 space-y-3">
              <div className="flex items-center justify-between">
                <Label>{editingGroup ? 'グループ編集' : '新規グループ'}</Label>
                {editingGroup && (
                  <Button variant="ghost" size="sm" onClick={handleNewGroup}>新規作成に切替</Button>
                )}
              </div>
              <Input
                placeholder="グループ名（例: イズミグループ）"
                value={groupName}
                onChange={(e) => setGroupName(e.target.value)}
              />
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground">得意先コード（1行に1コード、またはカンマ区切り）</Label>
                <textarea
                  className="flex w-full rounded-md border bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  rows={5}
                  placeholder={"000231\n000232\n000233"}
                  value={groupPartnerCodes}
                  onChange={(e) => setGroupPartnerCodes(e.target.value)}
                />
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={handleClose}>キャンセル</Button>
                <Button onClick={handleSave} disabled={!groupName.trim() || saveMutation.isPending}>
                  {saveMutation.isPending ? '保存中...' : '保存'}
                </Button>
              </div>
            </div>
          </div>
        </DialogContent>
      </Dialog>

      <AlertDialog open={!!deleteTarget} onOpenChange={(o) => { if (!o) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>グループ削除の確認</AlertDialogTitle>
            <AlertDialogDescription>
              「{deleteTarget?.groupName}」を削除しますか？この操作は取り消せません。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>キャンセル</AlertDialogCancel>
            <AlertDialogAction onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.partnerGroupId)}>
              削除
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  )
}
