'use client'

import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Link2, Link2Off, RefreshCw, AlertCircle, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import type {
  MfOauthClientRequest,
  MfOauthClientResponse,
  MfTokenStatus,
} from '@/types/mf-integration'
import { MF_DEFAULT_CONFIG } from '@/types/mf-integration'

export function MfIntegrationPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const queryClient = useQueryClient()

  const [form, setForm] = useState<MfOauthClientRequest>({
    clientId: '',
    clientSecret: '',
    ...MF_DEFAULT_CONFIG,
  })

  const clientQuery = useQuery({
    queryKey: ['mf-oauth-client'],
    queryFn: () => api.get<MfOauthClientResponse>('/finance/mf-integration/oauth/client'),
    enabled: isAdmin,
  })

  const statusQuery = useQuery({
    queryKey: ['mf-oauth-status'],
    queryFn: () => api.get<MfTokenStatus>('/finance/mf-integration/oauth/status'),
    enabled: isAdmin,
    refetchInterval: 60_000,
  })

  // 初回ロード時: 既存設定があれば form にマージ (F-4 render-time setState 回避)
  // clientQuery.data.id をキーにして、レコード差し替え時のみ反映する。
  useEffect(() => {
    const c = clientQuery.data
    if (!c || !c.clientId) return
    setForm((prev) => ({
      ...prev,
      clientId: c.clientId ?? prev.clientId,
      clientSecret: '', // secret は返却されない
      redirectUri: c.redirectUri ?? MF_DEFAULT_CONFIG.redirectUri,
      scope: c.scope ?? MF_DEFAULT_CONFIG.scope,
      authorizeUrl: c.authorizeUrl ?? MF_DEFAULT_CONFIG.authorizeUrl,
      tokenUrl: c.tokenUrl ?? MF_DEFAULT_CONFIG.tokenUrl,
      apiBaseUrl: c.apiBaseUrl ?? MF_DEFAULT_CONFIG.apiBaseUrl,
    }))
  }, [clientQuery.data])

  // F-3: callback タブからの post-message を受け取り、status を即時 invalidate して画面反映する。
  useEffect(() => {
    const handler = (event: MessageEvent) => {
      if (event.origin !== window.location.origin) return
      const data = event.data as { type?: string; source?: string; message?: string } | null
      if (!data || data.source !== 'odamitsu-data-hub') return
      if (data.type === 'mf-connected') {
        toast.success('MF 連携が完了しました')
        queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })
      } else if (data.type === 'mf-connection-failed') {
        toast.error(`MF 連携に失敗: ${data.message ?? 'unknown'}`)
      }
    }
    window.addEventListener('message', handler)
    return () => window.removeEventListener('message', handler)
  }, [queryClient])

  const saveMutation = useMutation({
    mutationFn: (body: MfOauthClientRequest) =>
      api.put<MfOauthClientResponse>('/finance/mf-integration/oauth/client', body),
    onSuccess: () => {
      toast.success('OAuth クライアント設定を保存しました')
      queryClient.invalidateQueries({ queryKey: ['mf-oauth-client'] })
      queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })
      setForm((prev) => ({ ...prev, clientSecret: '' })) // 保存後は UI 側の secret をクリア
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const connectMutation = useMutation({
    mutationFn: () => api.get<{ url: string }>('/finance/mf-integration/oauth/authorize-url'),
    onSuccess: (res) => {
      // 新タブで MF 認可ページを開く
      // 親タブから opener アクセスできるよう noopener は付けない（post-message 通知のため）。
      window.open(res.url, '_blank', 'noreferrer')
      toast.info('MF 認可ページを新しいタブで開きました。完了後、このタブに自動反映されます。')
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 422) {
        toast.error('クライアント設定が未登録です。先に Client ID/Secret を保存してください。')
      } else {
        toast.error(e.message)
      }
    },
  })

  const revokeMutation = useMutation({
    mutationFn: () => api.post<MfTokenStatus>('/finance/mf-integration/oauth/revoke'),
    onSuccess: () => {
      toast.success('MF 連携を切断しました')
      queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  if (!isAdmin) {
    return (
      <div className="rounded border border-amber-300 bg-amber-50 p-4 text-sm text-amber-800">
        <AlertCircle className="mr-1 inline h-4 w-4" />
        このページは管理者のみ利用可能です。
      </div>
    )
  }

  if (clientQuery.isLoading || statusQuery.isLoading) return <LoadingSpinner />

  const status = statusQuery.data
  const client = clientQuery.data

  const handleSave = () => {
    if (!form.clientId.trim()) {
      toast.error('Client ID は必須です')
      return
    }
    if (!client?.clientSecretConfigured && !form.clientSecret?.trim()) {
      toast.error('新規登録時は Client Secret が必須です')
      return
    }
    // secret が空なら送信しない（既存値維持）
    const body: MfOauthClientRequest = { ...form }
    if (!body.clientSecret?.trim()) {
      delete (body as Partial<MfOauthClientRequest>).clientSecret
    }
    saveMutation.mutate(body)
  }

  const formatTime = (iso: string | null) =>
    iso ? new Date(iso).toLocaleString('ja-JP') : '-'

  return (
    <div className="space-y-4">
      <PageHeader title="MF 連携状況" description="マネーフォワードクラウド会計 API 連携設定" />

      {/* 接続ステータスカード */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-base">接続ステータス</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {status?.connected ? (
            <div className="flex items-center gap-2">
              <Badge className="bg-emerald-600 hover:bg-emerald-700">
                <CheckCircle2 className="mr-1 h-3.5 w-3.5" />
                接続中
              </Badge>
              <span className="text-xs text-muted-foreground">
                有効期限: {formatTime(status.expiresAt)}
                {status.scope && ` / scope: ${status.scope}`}
              </span>
            </div>
          ) : status?.configured ? (
            <div className="flex items-center gap-2">
              <Badge variant="secondary">未接続</Badge>
              <span className="text-xs text-muted-foreground">
                クライアント設定済み。下の「接続」ボタンで認可を開始できます。
              </span>
            </div>
          ) : (
            <div className="flex items-center gap-2">
              <Badge variant="outline">未設定</Badge>
              <span className="text-xs text-muted-foreground">
                下のフォームで Client ID / Client Secret を登録してください。
              </span>
            </div>
          )}
          <div className="flex gap-2">
            <Button
              size="sm"
              onClick={() => connectMutation.mutate()}
              disabled={!status?.configured || connectMutation.isPending}
            >
              <Link2 className="mr-1 h-4 w-4" />
              {status?.connected ? '再認証' : '接続'}
            </Button>
            <Button
              size="sm"
              variant="outline"
              onClick={() => revokeMutation.mutate()}
              disabled={!status?.connected || revokeMutation.isPending}
            >
              <Link2Off className="mr-1 h-4 w-4" />
              切断
            </Button>
            <Button
              size="sm"
              variant="ghost"
              onClick={() => queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })}
            >
              <RefreshCw className="mr-1 h-4 w-4" />
              再読込
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* OAuth クライアント設定フォーム */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-base">OAuth クライアント設定</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <p className="text-xs text-muted-foreground">
            MF アプリポータル (
            <a
              className="text-blue-600 hover:underline"
              href="https://app-portal.moneyforward.com/"
              target="_blank"
              rel="noopener noreferrer"
            >
              app-portal.moneyforward.com
            </a>
            ) で発行した Client ID / Client Secret を登録します。Secret は暗号化して保存され、画面には再表示されません。
          </p>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <Label htmlFor="clientId">Client ID</Label>
              <Input
                id="clientId"
                value={form.clientId}
                onChange={(e) => setForm({ ...form, clientId: e.target.value })}
                placeholder="MF アプリポータルで発行された Client ID"
                autoComplete="off"
              />
            </div>
            <div>
              <Label htmlFor="clientSecret">
                Client Secret
                {client?.clientSecretConfigured && (
                  <span className="ml-2 text-xs text-emerald-600">（登録済み・空のままで維持）</span>
                )}
              </Label>
              <Input
                id="clientSecret"
                type="password"
                value={form.clientSecret ?? ''}
                onChange={(e) => setForm({ ...form, clientSecret: e.target.value })}
                placeholder={
                  client?.clientSecretConfigured ? '変更する場合のみ入力' : '新規登録時は必須'
                }
                autoComplete="new-password"
              />
            </div>
            <div className="md:col-span-2">
              <Label htmlFor="redirectUri">Redirect URI</Label>
              <Input
                id="redirectUri"
                value={form.redirectUri}
                onChange={(e) => setForm({ ...form, redirectUri: e.target.value })}
              />
              <p className="mt-1 text-[11px] text-muted-foreground">
                アプリポータルに登録した redirect URI と完全に一致している必要があります。
              </p>
            </div>
            <div>
              <Label htmlFor="scope">Scope</Label>
              <Input
                id="scope"
                value={form.scope}
                onChange={(e) => setForm({ ...form, scope: e.target.value })}
              />
            </div>
            <div>
              <Label htmlFor="apiBaseUrl">API Base URL</Label>
              <Input
                id="apiBaseUrl"
                value={form.apiBaseUrl}
                onChange={(e) => setForm({ ...form, apiBaseUrl: e.target.value })}
              />
            </div>
            <div>
              <Label htmlFor="authorizeUrl">Authorize URL</Label>
              <Input
                id="authorizeUrl"
                value={form.authorizeUrl}
                onChange={(e) => setForm({ ...form, authorizeUrl: e.target.value })}
              />
            </div>
            <div>
              <Label htmlFor="tokenUrl">Token URL</Label>
              <Input
                id="tokenUrl"
                value={form.tokenUrl}
                onChange={(e) => setForm({ ...form, tokenUrl: e.target.value })}
              />
            </div>
          </div>

          <div className="flex gap-2">
            <Button onClick={handleSave} disabled={saveMutation.isPending}>
              {saveMutation.isPending ? '保存中...' : '保存'}
            </Button>
          </div>
        </CardContent>
      </Card>

      {/* 最終更新情報 */}
      {status?.connected && (
        <Card>
          <CardContent className="pt-4 text-xs text-muted-foreground">
            トークン最終更新: {formatTime(status.lastRefreshedAt)}
          </CardContent>
        </Card>
      )}
    </div>
  )
}
