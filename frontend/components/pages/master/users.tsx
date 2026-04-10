'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { z } from 'zod/v4'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
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
import { Pencil, Trash2, Plus, Search } from 'lucide-react'
import { toast } from 'sonner'
import type { UserResponse } from '@/types/user'

const createSchema = z.object({
  loginId: z.string().min(1, 'ログインIDは必須です'),
  userName: z.string().min(1, 'ユーザー名は必須です'),
  password: z.string().min(5, 'パスワードは5文字以上').max(16, 'パスワードは16文字以下'),
  confirmPassword: z.string(),
}).check(
  ctx => {
    if (ctx.value.password !== ctx.value.confirmPassword) {
      ctx.issues.push({ code: 'custom', input: ctx.value, message: 'パスワードが一致しません', path: ['confirmPassword'] })
    }
  }
)

const updateSchema = z.object({
  loginId: z.string().min(1, 'ログインIDは必須です'),
  userName: z.string().min(1, 'ユーザー名は必須です'),
  password: z.string().max(16, 'パスワードは16文字以下').optional().or(z.literal('')),
  confirmPassword: z.string().optional().or(z.literal('')),
}).check(
  ctx => {
    const { password, confirmPassword } = ctx.value
    if (password && password.length > 0 && password.length < 5) {
      ctx.issues.push({ code: 'custom', input: ctx.value, message: 'パスワードは5文字以上', path: ['password'] })
    }
    if (password && password !== confirmPassword) {
      ctx.issues.push({ code: 'custom', input: ctx.value, message: 'パスワードが一致しません', path: ['confirmPassword'] })
    }
  }
)

type CreateForm = z.infer<typeof createSchema>
type UpdateForm = z.infer<typeof updateSchema>

export function UserManagementPage() {
  const { user: currentUser } = useAuth()
  const isAdmin = currentUser?.shopNo === 0
  const queryClient = useQueryClient()

  if (!isAdmin) {
    return (
      <div className="flex items-center justify-center h-64 text-muted-foreground">
        アクセス権限がありません
      </div>
    )
  }
  const [searchUserName, setSearchUserName] = useState('')
  const [searchLoginId, setSearchLoginId] = useState('')
  const [searchParams, setSearchParams] = useState<{ userName?: string; loginId?: string } | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [editTarget, setEditTarget] = useState<UserResponse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<UserResponse | null>(null)

  const listQuery = useQuery({
    queryKey: ['users', searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      if (searchParams?.userName) params.set('userName', searchParams.userName)
      if (searchParams?.loginId) params.set('loginId', searchParams.loginId)
      const qs = params.toString()
      return api.get<UserResponse[]>(`/users${qs ? `?${qs}` : ''}`)
    },
  })

  const createMutation = useMutation({
    mutationFn: (data: { loginId: string; userName: string; password: string }) =>
      api.post<UserResponse>('/users', data),
    onSuccess: () => {
      toast.success('ユーザーを登録しました')
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setCreateOpen(false)
    },
    onError: (err: { message?: string }) => {
      toast.error(err.message || '登録に失敗しました')
    },
  })

  const updateMutation = useMutation({
    mutationFn: ({ id, data }: { id: number; data: { loginId: string; userName: string; password?: string } }) =>
      api.put<UserResponse>(`/users/${id}`, data),
    onSuccess: () => {
      toast.success('ユーザーを更新しました')
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setEditTarget(null)
    },
    onError: (err: { message?: string }) => {
      toast.error(err.message || '更新に失敗しました')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (id: number) => api.delete(`/users/${id}`),
    onSuccess: () => {
      toast.success('ユーザーを削除しました')
      queryClient.invalidateQueries({ queryKey: ['users'] })
      setDeleteTarget(null)
    },
    onError: (err: { message?: string }) => {
      toast.error(err.message || '削除に失敗しました')
    },
  })

  const handleSearch = () => {
    setSearchParams({
      userName: searchUserName || undefined,
      loginId: searchLoginId || undefined,
    })
  }

  const users = listQuery.data ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        title="ユーザー管理"
        actions={
          <Button onClick={() => setCreateOpen(true)}>
            <Plus className="mr-2 h-4 w-4" />
            新規登録
          </Button>
        }
      />

      {/* Search */}
      <Card>
        <CardContent className="pt-6">
          <div className="flex items-end gap-4">
            <div className="space-y-1">
              <Label className="text-xs">ユーザー名</Label>
              <Input
                value={searchUserName}
                onChange={(e) => setSearchUserName(e.target.value)}
                placeholder="部分一致"
                className="w-48"
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">ログインID</Label>
              <Input
                value={searchLoginId}
                onChange={(e) => setSearchLoginId(e.target.value)}
                placeholder="部分一致"
                className="w-48"
                onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
              />
            </div>
            <Button onClick={handleSearch} variant="secondary">
              <Search className="mr-2 h-4 w-4" />
              検索
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* List */}
      {listQuery.isLoading ? (
        <LoadingSpinner />
      ) : listQuery.isError ? (
        <ErrorMessage onRetry={() => listQuery.refetch()} />
      ) : (
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/50">
                <th className="px-3 py-2 text-left font-medium w-16">No</th>
                <th className="px-3 py-2 text-left font-medium">ユーザー名</th>
                <th className="px-3 py-2 text-left font-medium">ログインID</th>
                <th className="px-3 py-2 text-left font-medium w-24">権限種別</th>
                <th className="px-3 py-2 text-left font-medium w-40">登録日時</th>
                <th className="px-3 py-2 text-left font-medium w-24">操作</th>
              </tr>
            </thead>
            <tbody>
              {users.map((u) => (
                <tr key={u.loginUserNo} className="border-b hover:bg-muted/30">
                  <td className="px-3 py-2">{u.loginUserNo}</td>
                  <td className="px-3 py-2">{u.userName}</td>
                  <td className="px-3 py-2">{u.loginId}</td>
                  <td className="px-3 py-2">{u.companyType}</td>
                  <td className="px-3 py-2 text-xs text-muted-foreground">
                    {u.addDateTime ? new Date(u.addDateTime).toLocaleString('ja-JP') : '-'}
                  </td>
                  <td className="px-3 py-2">
                    <div className="flex items-center gap-1">
                      <Button
                        size="sm"
                        variant="ghost"
                        onClick={() => setEditTarget(u)}
                      >
                        <Pencil className="h-4 w-4" />
                      </Button>
                      <Button
                        size="sm"
                        variant="ghost"
                        className="text-destructive hover:text-destructive"
                        onClick={() => setDeleteTarget(u)}
                        disabled={u.loginUserNo === currentUser?.loginUserNo}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
              {users.length === 0 && (
                <tr>
                  <td colSpan={6} className="px-3 py-8 text-center text-muted-foreground">
                    ユーザーが見つかりません
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {/* Create Dialog */}
      <CreateUserDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        onSubmit={(data) => createMutation.mutate(data)}
        isPending={createMutation.isPending}
      />

      {/* Edit Dialog */}
      {editTarget && (
        <EditUserDialog
          open={true}
          onOpenChange={(open) => { if (!open) setEditTarget(null) }}
          user={editTarget}
          onSubmit={(data) => updateMutation.mutate({ id: editTarget.loginUserNo, data })}
          isPending={updateMutation.isPending}
        />
      )}

      {/* Delete Confirmation */}
      <AlertDialog open={deleteTarget !== null} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>ユーザーの削除</AlertDialogTitle>
            <AlertDialogDescription>
              「{deleteTarget?.userName}」（{deleteTarget?.loginId}）を削除しますか？この操作は取り消せません。
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>キャンセル</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => deleteTarget && deleteMutation.mutate(deleteTarget.loginUserNo)}
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

function CreateUserDialog({
  open,
  onOpenChange,
  onSubmit,
  isPending,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  onSubmit: (data: { loginId: string; userName: string; password: string }) => void
  isPending: boolean
}) {
  const { register, handleSubmit, reset, formState: { errors } } = useForm<CreateForm>({
    defaultValues: { loginId: '', userName: '', password: '', confirmPassword: '' },
  })

  const onValid = (data: CreateForm) => {
    const result = createSchema.safeParse(data)
    if (!result.success) {
      result.error.issues.forEach((issue) => toast.error(issue.message))
      return
    }
    onSubmit({ loginId: data.loginId, userName: data.userName, password: data.password })
  }

  return (
    <Dialog open={open} onOpenChange={(v) => { if (!v) reset(); onOpenChange(v) }}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>ユーザー新規登録</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onValid)} className="space-y-4">
          <div className="space-y-1">
            <Label>ログインID</Label>
            <Input {...register('loginId')} />
            {errors.loginId && <p className="text-xs text-destructive">{errors.loginId.message}</p>}
          </div>
          <div className="space-y-1">
            <Label>ユーザー名</Label>
            <Input {...register('userName')} />
            {errors.userName && <p className="text-xs text-destructive">{errors.userName.message}</p>}
          </div>
          <div className="space-y-1">
            <Label>パスワード（5〜16文字）</Label>
            <Input type="password" {...register('password')} />
            {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
          </div>
          <div className="space-y-1">
            <Label>パスワード確認</Label>
            <Input type="password" {...register('confirmPassword')} />
            {errors.confirmPassword && <p className="text-xs text-destructive">{errors.confirmPassword.message}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              キャンセル
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? '登録中...' : '登録'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}

function EditUserDialog({
  open,
  onOpenChange,
  user,
  onSubmit,
  isPending,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  user: UserResponse
  onSubmit: (data: { loginId: string; userName: string; password?: string }) => void
  isPending: boolean
}) {
  const { register, handleSubmit, formState: { errors } } = useForm<UpdateForm>({
    defaultValues: { loginId: user.loginId, userName: user.userName, password: '', confirmPassword: '' },
  })

  const onValid = (data: UpdateForm) => {
    const result = updateSchema.safeParse(data)
    if (!result.success) {
      result.error.issues.forEach((issue) => toast.error(issue.message))
      return
    }
    onSubmit({
      loginId: data.loginId,
      userName: data.userName,
      password: data.password || undefined,
    })
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>ユーザー編集</DialogTitle>
        </DialogHeader>
        <form onSubmit={handleSubmit(onValid)} className="space-y-4">
          <div className="space-y-1">
            <Label>ログインID</Label>
            <Input {...register('loginId')} />
            {errors.loginId && <p className="text-xs text-destructive">{errors.loginId.message}</p>}
          </div>
          <div className="space-y-1">
            <Label>ユーザー名</Label>
            <Input {...register('userName')} />
            {errors.userName && <p className="text-xs text-destructive">{errors.userName.message}</p>}
          </div>
          <div className="space-y-1">
            <Label>新しいパスワード（空欄なら変更しない）</Label>
            <Input type="password" {...register('password')} />
            {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
          </div>
          <div className="space-y-1">
            <Label>パスワード確認</Label>
            <Input type="password" {...register('confirmPassword')} />
            {errors.confirmPassword && <p className="text-xs text-destructive">{errors.confirmPassword.message}</p>}
          </div>
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              キャンセル
            </Button>
            <Button type="submit" disabled={isPending}>
              {isPending ? '更新中...' : '更新'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  )
}
