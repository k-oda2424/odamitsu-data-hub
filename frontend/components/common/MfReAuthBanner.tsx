'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { AlertCircle, AlertTriangle } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import type { MfTokenStatus } from '@/types/mf-integration'

/**
 * MF refresh_token 540 日寿命の予兆 banner (P1-04 案 α)。
 * <p>
 * グローバル top header (AppLayout) に常設し、`daysUntilReauth` に応じて
 * 4 段階の severity (黄/橙/赤/赤強調) で再認可を促す。≤ 0 は失効済 = エラー表示。
 * <p>
 * - `staleTime` 5 分 + `refetchInterval` 1 時間で API 負荷を最小化
 * - 未接続 (`connected: false`) は banner 非表示 (mf-integration 画面で別 UI 担当)
 * - `daysUntilReauth > 60` は非表示 (まだ早すぎ)
 */

const THRESHOLD_60 = 60
const THRESHOLD_30 = 30
const THRESHOLD_14 = 14
const THRESHOLD_7 = 7

export function MfReAuthBanner() {
  const { isAuthenticated } = useAuth()

  const { data, isError } = useQuery<MfTokenStatus>({
    queryKey: ['mf-oauth-status'],
    queryFn: () => api.get<MfTokenStatus>('/finance/mf-integration/oauth/status'),
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5 分間 cache
    refetchInterval: 60 * 60 * 1000, // 1 時間ごとに refetch
    retry: false, // 401/403 等で再試行しない
  })

  if (!isAuthenticated) return null
  if (isError || !data) return null
  if (!data.connected) return null
  // G1-M4: 期限超過 (540 日寿命を超過済) は最上位 severity で先に判定する。
  // 通常の severity 分岐は daysUntilReauth ≤ 60 の範囲のみ扱う。
  if (!data.reAuthExpired && (data.daysUntilReauth == null || data.daysUntilReauth > THRESHOLD_60)) {
    return null
  }

  const days = data.daysUntilReauth ?? 0

  // severity 判定
  let variant: 'default' | 'destructive' = 'default'
  let className = ''
  let title = ''
  let message = ''

  if (data.reAuthExpired) {
    // G1-M4: 540 日寿命を超過済。最上位 severity (destructive + pulse) で再認可を強制する。
    variant = 'destructive'
    className = 'animate-pulse'
    title = 'MF refresh_token 期限超過、再認可必須'
    message =
      'refresh_token の 540 日寿命を超過しました。MF 連携が機能しません。直ちに再認可してください。'
  } else if (days <= 0) {
    variant = 'destructive'
    title = 'MF 連携停止中'
    message = 'MF refresh_token が失効しました。再認可してください。'
  } else if (days <= THRESHOLD_7) {
    variant = 'destructive'
    className = 'animate-pulse'
    title = `緊急: あと ${days} 日で MF 連携停止`
    message = '直ちに再認可してください。'
  } else if (days <= THRESHOLD_14) {
    variant = 'destructive'
    title = `MF 再認可必須 (あと ${days} 日)`
    message = '2 週間以内に再認可しないと連携が停止します。'
  } else if (days <= THRESHOLD_30) {
    variant = 'default'
    className = 'border-amber-500 bg-amber-50 text-amber-900'
    title = `MF 再認可推奨 (あと ${days} 日)`
    message = '今月中に再認可してください。'
  } else {
    // 31-60
    variant = 'default'
    className = 'border-yellow-500 bg-yellow-50 text-yellow-900'
    title = `MF 再認可予定 (あと ${days} 日)`
    message = '計画的に再認可作業を準備してください。'
  }

  const Icon = variant === 'destructive' ? AlertCircle : AlertTriangle

  return (
    <Alert variant={variant} className={className}>
      <Icon className="h-4 w-4" />
      <AlertTitle>{title}</AlertTitle>
      <AlertDescription className="flex w-full items-center justify-between gap-3">
        <span>{message}</span>
        <Button asChild variant="outline" size="sm">
          <Link href="/finance/mf-integration">再認可画面へ</Link>
        </Button>
      </AlertDescription>
    </Alert>
  )
}
