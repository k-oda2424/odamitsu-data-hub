'use client'

import { useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { handleApiError } from '@/lib/api-error-handler'
import { useAuth } from '@/lib/auth'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Link2, Link2Off, RefreshCw, AlertCircle, CheckCircle2 } from 'lucide-react'
import { toast } from 'sonner'
import type {
  MfOauthClientRequest,
  MfOauthClientResponse,
  MfTokenStatus,
} from '@/types/mf-integration'
import { MF_DEFAULT_CONFIG } from '@/types/mf-integration'
import { MfEnumTranslationTab } from './MfEnumTranslationTab'
import { MfAccountSyncTab } from './MfAccountSyncTab'
import { MfReconcileTab } from './MfReconcileTab'
import { MfBalanceReconcileTab } from './MfBalanceReconcileTab'

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
  // SF-17: 再レンダリングのたびに発火しないよう、依存配列を「実体差し替え」を示す key field のみに絞る。
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientQuery.data?.id, clientQuery.data?.clientId])

  // SF-05: callback タブからの BroadcastChannel メッセージを受け取り、status を即時 invalidate して画面反映する。
  // window.open を noopener,noreferrer で開いているため、window.opener / postMessage は使えない。
  useEffect(() => {
    let ch: BroadcastChannel | null = null
    try {
      ch = new BroadcastChannel('mf-oauth')
      ch.onmessage = (event) => {
        const data = event.data as { type?: string; source?: string; message?: string } | null
        if (!data || data.source !== 'odamitsu-data-hub') return
        if (data.type === 'connected') {
          toast.success('MF 連携が完了しました')
          queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })
        } else if (data.type === 'failed') {
          toast.error(`MF 連携に失敗: ${data.message ?? 'unknown'}`)
        }
      }
    } catch { /* BroadcastChannel 非対応環境では無視 */ }
    return () => {
      try { ch?.close() } catch { /* ignore */ }
    }
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
    onError: (e) => handleApiError(e, { fallbackMessage: '保存に失敗しました' }),
  })

  const connectMutation = useMutation({
    mutationFn: () => api.get<{ url: string }>('/finance/mf-integration/oauth/authorize-url'),
    onSuccess: (res) => {
      // SF-05: 新タブで MF 認可ページを開く。
      // BroadcastChannel 経由で通知するため window.opener は不要 → noopener,noreferrer でセキュリティ強化。
      window.open(res.url, '_blank', 'noopener,noreferrer')
      toast.info('MF 認可ページを新しいタブで開きました。完了後、このタブに自動反映されます。')
    },
    onError: (e) => {
      // 422: クライアント設定が未登録 (= 業務メッセージとして固定文言で誘導)
      if (e instanceof ApiError && e.status === 422) {
        toast.error('クライアント設定が未登録です。先に Client ID/Secret を保存してください。')
        return
      }
      // それ以外は MF_HOST_NOT_ALLOWED 等を含めて handleApiError へ委譲
      handleApiError(e, { fallbackMessage: 'MF 認可 URL の取得に失敗しました' })
    },
  })

  const revokeMutation = useMutation({
    mutationFn: () => api.post<MfTokenStatus>('/finance/mf-integration/oauth/revoke'),
    onSuccess: () => {
      toast.success('MF 連携を切断しました')
      queryClient.invalidateQueries({ queryKey: ['mf-oauth-status'] })
    },
    onError: (e) => handleApiError(e, { fallbackMessage: '切断に失敗しました' }),
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

      <Tabs defaultValue="connection">
        <TabsList>
          <TabsTrigger value="connection">接続</TabsTrigger>
          <TabsTrigger value="translations" disabled={!status?.connected}>
            enum 翻訳辞書
          </TabsTrigger>
          <TabsTrigger value="account-sync" disabled={!status?.connected}>
            勘定科目同期
          </TabsTrigger>
          <TabsTrigger value="reconcile" disabled={!status?.connected}>
            仕訳突合
          </TabsTrigger>
          <TabsTrigger value="balance-reconcile" disabled={!status?.connected}>
            残高突合
          </TabsTrigger>
        </TabsList>

        <TabsContent value="connection" className="mt-3 space-y-4">
      {/* 接続ステータスカード */}
      <Card>
        <CardHeader className="pb-2">
          <CardTitle className="text-base">接続ステータス</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {status?.connected ? (
            <div className="space-y-2">
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
              {/* P1-01 (DD-F-04): バインド済 MF tenant 情報。別会社 MF 誤接続検知のため必ず表示。 */}
              {status.mfTenantId ? (
                <div className="rounded border border-emerald-200 bg-emerald-50 p-2 text-xs text-emerald-900">
                  <div>
                    連携先: <span className="font-medium">{status.mfTenantName ?? '(名称未取得)'}</span>{' '}
                    <span className="text-muted-foreground">
                      (tenant id: <span className="font-mono">{status.mfTenantId}</span>)
                    </span>
                  </div>
                  {status.tenantBoundAt && (
                    <div className="mt-0.5 text-[11px] text-emerald-800/80">
                      バインド日時: {formatTime(status.tenantBoundAt)}
                    </div>
                  )}
                  {/* P1-04 案 α: refresh_token 540 日寿命の残日数。banner と同じ source データ。 */}
                  {status.daysUntilReauth != null && (
                    <div className="mt-0.5 text-[11px] text-emerald-800/80">
                      refresh_token 残日数: {status.daysUntilReauth} 日
                      {status.refreshTokenIssuedAt && (
                        <> (発行: {formatTime(status.refreshTokenIssuedAt)} / 寿命 540 日)</>
                      )}
                    </div>
                  )}
                </div>
              ) : (
                <div className="rounded border border-amber-200 bg-amber-50 p-2 text-xs text-amber-900">
                  <AlertCircle className="mr-1 inline h-3.5 w-3.5" />
                  MF tenant が未バインドです (旧データ)。次回 token refresh 時に自動でバインドされます。
                </div>
              )}
            </div>
          ) : status?.configured ? (
            <div className="space-y-2">
              <div className="flex items-center gap-2">
                <Badge variant="secondary">未接続</Badge>
                <span className="text-xs text-muted-foreground">
                  クライアント設定済み。下の「接続」ボタンで認可を開始できます。
                </span>
              </div>
              {/* 過去にバインドされた tenant 情報 (revoke 後はクリアされる) */}
              {status.mfTenantId && (
                <div className="rounded border border-slate-200 bg-slate-50 p-2 text-xs text-slate-700">
                  前回連携先: <span className="font-medium">{status.mfTenantName ?? '(名称未取得)'}</span>{' '}
                  <span className="text-muted-foreground">
                    (tenant id: <span className="font-mono">{status.mfTenantId}</span>)
                  </span>
                </div>
              )}
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

      {/* 診断（dev のみ）: MF API の生レスポンスを取得して shape 確認 */}
      {status?.connected && <MfDiagnosticsCard />}
        </TabsContent>

        <TabsContent value="translations" className="mt-3">
          <MfEnumTranslationTab />
        </TabsContent>

        <TabsContent value="account-sync" className="mt-3">
          <MfAccountSyncTab />
        </TabsContent>

        <TabsContent value="reconcile" className="mt-3">
          <MfReconcileTab />
        </TabsContent>

        <TabsContent value="balance-reconcile" className="mt-3">
          <MfBalanceReconcileTab />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function MfDiagnosticsCard() {
  const [result, setResult] = useState<{ label: string; json: unknown } | null>(null)
  const [loading, setLoading] = useState<'accounts' | 'taxes' | 'journals' | null>(null)
  // journals 用の取引月。デフォルトは前月 20 日（20 日締めの典型値）。
  const [journalDate, setJournalDate] = useState<string>(() => {
    const now = new Date()
    const prev = new Date(now.getFullYear(), now.getMonth() - 1, 20)
    return `${prev.getFullYear()}-${String(prev.getMonth() + 1).padStart(2, '0')}-${String(prev.getDate()).padStart(2, '0')}`
  })

  const fetch = async (kind: 'accounts' | 'taxes' | 'journals') => {
    setLoading(kind)
    setResult(null)
    try {
      let path: string
      if (kind === 'accounts') path = 'accounts-raw'
      else if (kind === 'taxes') path = 'taxes-raw'
      else path = `journals-raw?transactionMonth=${journalDate}`
      const json = await api.get<unknown>(`/finance/mf-integration/debug/${path}`)
      setResult({ label: kind, json })
    } catch (e) {
      handleApiError(e, { fallbackMessage: `${kind} 取得失敗` })
    } finally {
      setLoading(null)
    }
  }

  const copyToClipboard = async () => {
    if (!result) return
    try {
      await navigator.clipboard.writeText(JSON.stringify(result.json, null, 2))
      toast.success('JSON をクリップボードにコピーしました')
    } catch {
      toast.error('コピー失敗')
    }
  }

  return (
    <Card>
      <CardHeader className="pb-2">
        <CardTitle className="text-base">診断（MF API 生レスポンス確認）</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3">
        <p className="text-xs text-muted-foreground">
          勘定科目同期機能の設計用に、MF API の生レスポンス shape を確認します。dev プロファイルのみ動作。
        </p>
        <div className="flex gap-2">
          <Button
            size="sm"
            variant="outline"
            onClick={() => fetch('accounts')}
            disabled={loading !== null}
          >
            {loading === 'accounts' ? '取得中...' : 'accounts 取得（3件）'}
          </Button>
          <Button
            size="sm"
            variant="outline"
            onClick={() => fetch('taxes')}
            disabled={loading !== null}
          >
            {loading === 'taxes' ? '取得中...' : 'taxes 取得（5件）'}
          </Button>
          {result && (
            <Button size="sm" variant="ghost" onClick={copyToClipboard}>
              JSON コピー
            </Button>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Label htmlFor="journal-date" className="text-xs">
            取引日:
          </Label>
          <Input
            id="journal-date"
            type="date"
            className="h-8 w-40 text-xs"
            value={journalDate}
            onChange={(e) => setJournalDate(e.target.value)}
          />
          <Button
            size="sm"
            variant="outline"
            onClick={() => fetch('journals')}
            disabled={loading !== null}
          >
            {loading === 'journals' ? '取得中...' : 'journals 取得（3件）'}
          </Button>
        </div>
        {result && (
          <pre className="max-h-96 overflow-auto rounded bg-muted p-3 text-[11px] font-mono">
            {JSON.stringify(result.json, null, 2)}
          </pre>
        )}
      </CardContent>
    </Card>
  )
}
