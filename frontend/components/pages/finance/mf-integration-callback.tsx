'use client'

import { useEffect, useRef, useState } from 'react'
import { useRouter, useSearchParams } from 'next/navigation'
import { api, ApiError } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { CheckCircle2, Loader2, XCircle } from 'lucide-react'

type Phase = 'processing' | 'success' | 'error'

/**
 * MF からのリダイレクト後、URL の code/state を backend に POST して token 交換する。
 * 成功したら親画面 (/finance/mf-integration) に戻る。
 */
export function MfIntegrationCallbackPage() {
  const router = useRouter()
  const sp = useSearchParams()
  const [phase, setPhase] = useState<Phase>('processing')
  const [message, setMessage] = useState<string>('')
  // SF-03: StrictMode の二重マウントで callback POST が 2 回飛ぶのを防止する once-only ガード。
  const calledRef = useRef(false)

  useEffect(() => {
    if (calledRef.current) return
    calledRef.current = true

    const code = sp.get('code')
    const state = sp.get('state')
    const error = sp.get('error')
    const errorDesc = sp.get('error_description')

    if (error) {
      setPhase('error')
      setMessage(`MF から拒否されました: ${error}${errorDesc ? ' - ' + errorDesc : ''}`)
      return
    }
    if (!code || !state) {
      setPhase('error')
      setMessage('code/state が URL に含まれていません。')
      return
    }

    api
      .post<{ connected: boolean }>('/finance/mf-integration/oauth/callback', { code, state })
      .then(() => {
        setPhase('success')
        setMessage('MF との接続に成功しました。')
        // SF-05: 親タブ (mf-integration 画面) に BroadcastChannel で通知して statusQuery を即時 invalidate させる。
        // window.opener を使わない (noopener,noreferrer で開いているため null)。
        try {
          const ch = new BroadcastChannel('mf-oauth')
          ch.postMessage({ type: 'connected', source: 'odamitsu-data-hub' })
          ch.close()
        } catch { /* BroadcastChannel 非対応環境では無視 */ }
      })
      .catch((e: unknown) => {
        setPhase('error')
        const msg = e instanceof ApiError ? e.message : (e as Error).message
        setMessage(`接続に失敗しました: ${msg}`)
        try {
          const ch = new BroadcastChannel('mf-oauth')
          ch.postMessage({ type: 'failed', source: 'odamitsu-data-hub', message: msg })
          ch.close()
        } catch { /* ignore */ }
      })
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  return (
    <div className="mx-auto max-w-lg space-y-4 py-12">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">
            {phase === 'processing' && (
              <span className="flex items-center gap-2">
                <Loader2 className="h-5 w-5 animate-spin" />
                MF 連携を処理中...
              </span>
            )}
            {phase === 'success' && (
              <span className="flex items-center gap-2 text-emerald-700">
                <CheckCircle2 className="h-5 w-5" />
                接続成功
              </span>
            )}
            {phase === 'error' && (
              <span className="flex items-center gap-2 text-destructive">
                <XCircle className="h-5 w-5" />
                接続失敗
              </span>
            )}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 text-sm">
          {message && <p>{message}</p>}
          {phase !== 'processing' && (
            <Button size="sm" onClick={() => router.push('/finance/mf-integration')}>
              連携状況画面に戻る
            </Button>
          )}
          {phase === 'processing' && (
            <p className="text-xs text-muted-foreground">
              このタブは処理完了後、自動的に親画面に反映されます。
              完了後このタブは閉じて問題ありません。
            </p>
          )}
          {phase === 'success' && (
            <p className="text-xs text-muted-foreground">
              親画面 (MF 連携状況) に通知済み。このタブは閉じて構いません。
            </p>
          )}
        </CardContent>
      </Card>
    </div>
  )
}
