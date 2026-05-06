'use client'

import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import Link from 'next/link'
import { api, ApiError } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { CheckCircle2, AlertTriangle, XCircle, RefreshCw, Trash2, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import {
  HEALTH_COLOR,
  HEALTH_LABEL,
  judgeHealth,
  type HealthLevel,
  type MfHealthResponse,
} from '@/types/mf-health'

export function MfHealthPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const [shopNo, setShopNo] = useState<number | undefined>(isAdmin ? 1 : user?.shopNo)

  const shopsQuery = useShops(isAdmin)
  const shopOptions = (shopsQuery.data ?? []).map((s) => ({
    value: String(s.shopNo),
    label: `${s.shopNo}: ${s.shopName}`,
  }))

  const healthQuery = useQuery({
    queryKey: ['mf-health', shopNo],
    queryFn: async () => {
      if (shopNo === undefined) throw new Error('ショップ未選択')
      return api.get<MfHealthResponse>(`/finance/mf-health?shopNo=${shopNo}`)
    },
    enabled: shopNo !== undefined,
  })

  const invalidateCache = useMutation({
    mutationFn: async () => {
      if (shopNo === undefined) throw new Error('ショップ未選択')
      await api.post(`/finance/mf-health/cache/invalidate?shopNo=${shopNo}`, {})
    },
    onSuccess: () => {
      toast.success('MF journals キャッシュを破棄しました')
      healthQuery.refetch()
    },
    onError: (e: Error) => {
      toast.error(e instanceof ApiError ? e.message : String(e))
    },
  })

  const data = healthQuery.data
  const level: HealthLevel = data ? judgeHealth(data) : 'yellow'

  return (
    <div className="space-y-4">
      <PageHeader title="MF 連携ヘルスチェック" />

      <Card>
        <CardContent className="pt-4 flex flex-wrap items-center gap-3">
          {isAdmin && (
            <div className="flex items-center gap-2">
              <Label>ショップ</Label>
              <div className="w-60">
                <SearchableSelect
                  value={shopNo !== undefined ? String(shopNo) : ''}
                  onValueChange={(v) => setShopNo(v ? Number(v) : undefined)}
                  options={shopOptions}
                  placeholder="選択してください"
                  clearable
                />
              </div>
            </div>
          )}
          <Button variant="outline" size="sm"
                  onClick={() => healthQuery.refetch()}
                  disabled={healthQuery.isFetching}>
            {healthQuery.isFetching ? <Loader2 className="mr-1 h-3 w-3 animate-spin" /> : <RefreshCw className="mr-1 h-3 w-3" />}
            再読み込み
          </Button>
          {data && (
            <div className="ml-auto flex items-center gap-2">
              <HealthBadge level={level} />
              <span className="text-xs text-muted-foreground">
                checked: {new Date(data.checkedAt).toLocaleString('ja-JP')}
              </span>
            </div>
          )}
        </CardContent>
      </Card>

      {data && (
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <Card>
            <CardHeader><CardTitle className="text-sm">MF OAuth 状態</CardTitle></CardHeader>
            <CardContent className="space-y-2 text-sm">
              <Row label="接続">
                <ConnectionBadge
                  connected={data.mfOauth.connected}
                  apiReachable={data.mfOauth.apiReachable ?? null}
                />
              </Row>
              <Row label="Scope">
                <span className="font-mono text-xs">{data.mfOauth.scope ?? '-'}</span>
              </Row>
              <Row label="有効期限">
                {data.mfOauth.tokenExpiresAt
                  ? new Date(data.mfOauth.tokenExpiresAt).toLocaleString('ja-JP')
                  : '-'}
                {data.mfOauth.expiresInHours !== null && (
                  <span className={`ml-2 text-xs ${data.mfOauth.expiresInHours < 24 ? 'text-red-600' : 'text-muted-foreground'}`}>
                    (残り {data.mfOauth.expiresInHours}h)
                  </span>
                )}
              </Row>
              <Link href="/finance/mf-integration" className="text-xs text-blue-600 hover:underline">
                MF 連携状況を開く →
              </Link>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="text-sm">買掛金 summary ({data.summary.latestMonth ?? 'データなし'})</CardTitle></CardHeader>
            <CardContent className="space-y-2 text-sm">
              <Row label="合計行数">{data.summary.totalCount}</Row>
              <Row label="検証済み">{data.summary.verifiedCount}</Row>
              <Row label="未検証"><span className={data.summary.unverifiedCount > 0 ? 'text-amber-600' : ''}>{data.summary.unverifiedCount}</span></Row>
              <Row label="MF 出力 ON">{data.summary.mfExportEnabledCount}</Row>
              <Link href="/finance/accounts-payable" className="text-xs text-blue-600 hover:underline">
                買掛金一覧を開く →
              </Link>
            </CardContent>
          </Card>

          <Card>
            <CardHeader><CardTitle className="text-sm">アノマリー</CardTitle></CardHeader>
            <CardContent className="space-y-2 text-sm">
              <Row label="負 closing"><span className={data.anomalies.negativeClosingCount > 0 ? 'text-red-600 font-medium' : ''}>{data.anomalies.negativeClosingCount}</span></Row>
              {/* SF-18: 未検証 (当月) は summary カード側に統一表示。anomaly 側からは削除。 */}
              <Row label="検証差 (予定)">{data.anomalies.verifyDiffCount}</Row>
              <Row label="連続性断絶 (予定)">{data.anomalies.continuityBreakCount}</Row>
              <Row label="月ギャップ (予定)">{data.anomalies.monthGapCount}</Row>
              <p className="text-xs text-muted-foreground">※ 一部は supplier 単位検出で買掛帳画面のみ (shop 集計は次フェーズ予定)</p>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm">MF journals キャッシュ</CardTitle>
                <Button variant="ghost" size="sm"
                        onClick={() => {
                          if (window.confirm(`shop=${shopNo} の MF journals キャッシュを全破棄します。次回は MF API 再取得。`)) {
                            invalidateCache.mutate()
                          }
                        }}
                        disabled={invalidateCache.isPending}>
                  <Trash2 className="mr-1 h-3 w-3" /> 破棄
                </Button>
              </div>
            </CardHeader>
            <CardContent className="space-y-2 text-sm">
              {data.cache.cachedShops.length === 0 && (
                <p className="text-muted-foreground">キャッシュなし</p>
              )}
              {data.cache.cachedShops.map((s) => (
                <div key={s.shopNo} className="space-y-1 rounded border p-2">
                  <Row label="shop">{s.shopNo}</Row>
                  <Row label="保持月数">{s.monthsCount} 月</Row>
                  <Row label="最古">
                    {s.oldestFetchedAt ? new Date(s.oldestFetchedAt).toLocaleString('ja-JP') : '-'}
                  </Row>
                  <Row label="最新">
                    {s.newestFetchedAt ? new Date(s.newestFetchedAt).toLocaleString('ja-JP') : '-'}
                  </Row>
                </div>
              ))}
            </CardContent>
          </Card>
        </div>
      )}
    </div>
  )
}

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-sm">{children}</span>
    </div>
  )
}

/**
 * MA-02: OAuth 接続 + MF /accounts ping (apiReachable) の状態を 1 つの Badge で表示。
 * - connected=true && apiReachable=true  → 緑 「接続中 (API 疎通 OK)」
 * - connected=true && apiReachable=false → 黄 「接続中 (API 疎通 NG)」
 * - connected=true && apiReachable=null  → グレー 「接続中 (ping 未実行)」
 * - connected=false                       → 赤 「未接続」
 */
function ConnectionBadge({
  connected,
  apiReachable,
}: {
  connected: boolean
  apiReachable: boolean | null
}) {
  if (!connected) {
    return <Badge variant="destructive">未接続</Badge>
  }
  if (apiReachable === true) {
    return <Badge className="bg-green-600 text-white">接続中 (API 疎通 OK)</Badge>
  }
  if (apiReachable === false) {
    return <Badge className="bg-amber-500 text-white">接続中 (API 疎通 NG)</Badge>
  }
  return <Badge variant="secondary">接続中 (ping 未実行)</Badge>
}

function HealthBadge({ level }: { level: HealthLevel }) {
  const Icon = level === 'green' ? CheckCircle2 : level === 'yellow' ? AlertTriangle : XCircle
  return (
    <span className={`flex items-center gap-1 text-lg font-semibold ${HEALTH_COLOR[level]}`}>
      <Icon className="h-5 w-5" />
      {HEALTH_LABEL[level]}
    </span>
  )
}
