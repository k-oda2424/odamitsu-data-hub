/**
 * T2: 監査ログ閲覧 型定義。
 * 設計書: claudedocs/design-finance-audit-log.md
 */

export type AuditActorType = 'USER' | 'SYSTEM' | 'BATCH'

export interface AuditLogResponse {
  id: number
  occurredAt: string
  actorUserNo: number | null
  actorUserName: string | null
  actorType: AuditActorType
  operation: string
  targetTable: string
  /** 一覧 (summaryFrom) では PK のみ。詳細 (from) では before/after も付く。 */
  targetPk: unknown
  beforeValues?: unknown
  afterValues?: unknown
  reason?: string | null
  sourceIp?: string | null
  userAgent?: string | null
}
