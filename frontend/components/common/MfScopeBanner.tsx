'use client'

import { useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { AlertCircle } from 'lucide-react'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import type { MfTokenStatus } from '@/types/mf-integration'

/**
 * MF OAuth scope 不足検知 banner (T6, 2026-05-04)。
 * <p>
 * グローバル top header (AppLayout) に常設し、admin が画面上で
 * 必須 scope を削除した場合に警告を出す。{@link MfReAuthBanner}
 * (refresh_token 寿命) と独立した責務で、banner ロジックを分離する。
 * <ul>
 *   <li>severity 固定 = destructive (赤): 関連 API が 403 で失敗する可能性あり</li>
 *   <li>未接続時は表示しない (mf-integration 画面で別途対応)</li>
 *   <li>scopeOk=true (= missingScopes 空) なら表示しない</li>
 *   <li>queryKey は {@link MfReAuthBanner} と共有 ('mf-oauth-status')。
 *       TanStack Query の cache 共有で 1 度の fetch で両 banner が判定する。</li>
 * </ul>
 */
export function MfScopeBanner() {
  const { isAuthenticated } = useAuth()

  const { data, isError } = useQuery<MfTokenStatus>({
    queryKey: ['mf-oauth-status'],
    queryFn: () => api.get<MfTokenStatus>('/finance/mf-integration/oauth/status'),
    enabled: isAuthenticated,
    staleTime: 5 * 60 * 1000, // 5 分間 cache
    refetchInterval: 60 * 60 * 1000, // 1 時間ごとに refetch
    retry: false,
  })

  if (!isAuthenticated) return null
  if (isError || !data) return null
  // 未接続なら mf-integration 画面で別 UI を出すため banner 非表示
  if (!data.connected) return null
  // 必須 scope が揃っていれば表示しない
  if (data.scopeOk) return null

  return (
    <Alert variant="destructive">
      <AlertCircle className="h-4 w-4" />
      <AlertTitle>MF 連携 scope 不足</AlertTitle>
      <AlertDescription className="flex w-full items-start justify-between gap-3">
        <div className="flex-1">
          <p>以下の必須 scope が現設定に含まれていません。再認可するまで関連 API が 403 で失敗する可能性があります。</p>
          <ul className="ml-4 mt-1 list-disc text-xs">
            {data.missingScopes.map((s) => (
              <li key={s}>
                <code>{s}</code>
              </li>
            ))}
          </ul>
          <p className="mt-2 text-xs">
            対応: 「クライアント設定」で scope を修正し、「再認証」を実行してください。
          </p>
        </div>
        <Button asChild variant="outline" size="sm">
          <Link href="/finance/mf-integration">再認可画面へ</Link>
        </Button>
      </AlertDescription>
    </Alert>
  )
}
