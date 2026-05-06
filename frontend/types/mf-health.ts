/**
 * 軸 E: MF 連携ヘルスチェック 型定義。
 * 設計書: claudedocs/design-supplier-balances-health.md §4
 */

export interface MfOauthStatus {
  connected: boolean
  tokenExpiresAt: string | null
  scope: string | null
  expiresInHours: number | null
  /**
   * MF /api/v3/accounts への ping 結果。
   * - true:  接続中かつ API 疎通 OK
   * - false: 接続中だが API 疎通 NG (401 / network error 等)
   * - null:  ping 未実行 (connected=false の時)
   * SF-22 (backend) + MA-02 (frontend 表示)
   */
  apiReachable?: boolean | null
}

export interface SummaryStats {
  latestMonth: string | null
  totalCount: number
  verifiedCount: number
  unverifiedCount: number
  mfExportEnabledCount: number
}

export interface AnomalyStats {
  negativeClosingCount: number
  unverifiedCount: number
  verifyDiffCount: number
  continuityBreakCount: number
  monthGapCount: number
}

export interface ShopCacheInfo {
  shopNo: number
  monthsCount: number
  oldestFetchedAt: string | null
  newestFetchedAt: string | null
}

export interface CacheStats {
  cachedShops: ShopCacheInfo[]
}

export interface MfHealthResponse {
  checkedAt: string
  shopNo: number
  mfOauth: MfOauthStatus
  summary: SummaryStats
  anomalies: AnomalyStats
  cache: CacheStats
}

export type HealthLevel = 'green' | 'yellow' | 'red'

/**
 * 設計書 §4.3 の判定ロジック:
 * - 🔴: token 期限切れ OR negativeClosing > 0 OR anomaly 合計 > 10
 * - 🟡: token 残り 24h 以内 OR unverifiedCount > 0 OR anomaly 合計 > 0
 * - 🟢: 上記以外
 */
export function judgeHealth(res: MfHealthResponse): HealthLevel {
  const a = res.anomalies
  // SF-19: verifyDiff/continuityBreak/monthGap は backend (PayableAnomalyCounter) 未実装で常に 0。
  // 0 固定値を判定に組み込むと将来実装後に挙動が静かに変わるため、現時点では negativeClosing のみで判定する。
  // TODO: backend 実装後 (PayableAnomalyCounter) に verifyDiff/continuityBreak/monthGap を anomalyTotal に組み込む
  const anomalyTotal = a.negativeClosingCount
  const tokenExpired = !res.mfOauth.connected || (res.mfOauth.expiresInHours ?? 0) <= 0
  if (tokenExpired || a.negativeClosingCount > 0 || anomalyTotal > 10) return 'red'
  const tokenNear = (res.mfOauth.expiresInHours ?? 9999) < 24
  if (tokenNear || res.summary.unverifiedCount > 0 || anomalyTotal > 0) return 'yellow'
  return 'green'
}

export const HEALTH_COLOR: Record<HealthLevel, string> = {
  green: 'text-green-600',
  yellow: 'text-amber-600',
  red: 'text-red-600',
}

export const HEALTH_LABEL: Record<HealthLevel, string> = {
  green: '健全',
  yellow: '注意',
  red: '要対応',
}
