'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { PageHeader } from '@/components/features/common/PageHeader'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Loader2, Search, RefreshCw } from 'lucide-react'
import type { Paginated } from '@/types/paginated'
import type { AuditLogResponse } from '@/types/audit-log'

interface SearchParams {
  operation: string
  targetTable: string
  fromDate: string
  toDate: string
  page: number
  size: number
}

const PAGE_SIZE = 50

function buildQuery(p: SearchParams): string {
  const usp = new URLSearchParams()
  if (p.operation) usp.set('operation', p.operation)
  if (p.targetTable) usp.set('targetTable', p.targetTable)
  if (p.fromDate) usp.set('fromDate', `${p.fromDate}T00:00:00`)
  if (p.toDate) usp.set('toDate', `${p.toDate}T23:59:59`)
  usp.set('page', String(p.page))
  usp.set('size', String(p.size))
  return usp.toString()
}

function actorTypeBadge(actorType: string): React.ReactNode {
  if (actorType === 'USER') return <Badge variant="secondary">USER</Badge>
  if (actorType === 'SYSTEM') return <Badge variant="outline">SYSTEM</Badge>
  if (actorType === 'BATCH') return <Badge variant="outline">BATCH</Badge>
  return <Badge variant="outline">{actorType}</Badge>
}

function formatJson(value: unknown): string {
  if (value === null || value === undefined) return '(なし)'
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

export function AuditLogPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const [filterForm, setFilterForm] = useState<SearchParams>({
    operation: '',
    targetTable: '',
    fromDate: '',
    toDate: '',
    page: 0,
    size: PAGE_SIZE,
  })
  const [searchParams, setSearchParams] = useState<SearchParams | null>(null)
  const [detailId, setDetailId] = useState<number | null>(null)

  const tablesQuery = useQuery({
    queryKey: ['audit-log', 'tables'],
    queryFn: () => api.get<string[]>('/admin/audit-log/tables'),
    enabled: isAdmin,
  })

  const operationsQuery = useQuery({
    queryKey: ['audit-log', 'operations'],
    queryFn: () => api.get<string[]>('/admin/audit-log/operations'),
    enabled: isAdmin,
  })

  const searchQuery = useQuery({
    queryKey: ['audit-log', 'search', searchParams],
    queryFn: () => api.get<Paginated<AuditLogResponse>>(
      `/admin/audit-log/search?${buildQuery(searchParams!)}`,
    ),
    enabled: isAdmin && searchParams !== null,
  })

  const detailQuery = useQuery({
    queryKey: ['audit-log', 'detail', detailId],
    queryFn: () => api.get<AuditLogResponse>(`/admin/audit-log/${detailId}`),
    enabled: detailId !== null,
  })

  if (!isAdmin) {
    return (
      <div className="space-y-4">
        <PageHeader title="監査ログ" />
        <Card>
          <CardContent className="pt-4">
            <p className="text-sm text-muted-foreground">
              この画面は管理者のみが閲覧できます。
            </p>
          </CardContent>
        </Card>
      </div>
    )
  }

  const tableOptions = (tablesQuery.data ?? []).map((t) => ({ value: t, label: t }))
  const opOptions = (operationsQuery.data ?? []).map((o) => ({ value: o, label: o }))

  const onSearch = () => {
    setSearchParams({ ...filterForm, page: 0 })
  }

  const onPageChange = (next: number) => {
    if (!searchParams) return
    setSearchParams({ ...searchParams, page: next })
  }

  const detail = detailQuery.data

  return (
    <div className="space-y-4">
      <PageHeader
        title="監査ログ"
        description="finance Service 層の操作履歴 (T2 監査証跡基盤)"
      />

      <Card>
        <CardContent className="pt-4 grid grid-cols-1 md:grid-cols-5 gap-3 items-end">
          <div className="space-y-1">
            <Label className="text-xs">対象テーブル</Label>
            <SearchableSelect
              value={filterForm.targetTable}
              onValueChange={(v) => setFilterForm({ ...filterForm, targetTable: v ?? '' })}
              options={tableOptions}
              placeholder="(すべて)"
              clearable
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">操作</Label>
            <SearchableSelect
              value={filterForm.operation}
              onValueChange={(v) => setFilterForm({ ...filterForm, operation: v ?? '' })}
              options={opOptions}
              placeholder="(すべて)"
              clearable
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">From</Label>
            <Input
              type="date"
              value={filterForm.fromDate}
              onChange={(e) => setFilterForm({ ...filterForm, fromDate: e.target.value })}
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">To</Label>
            <Input
              type="date"
              value={filterForm.toDate}
              onChange={(e) => setFilterForm({ ...filterForm, toDate: e.target.value })}
            />
          </div>
          <div className="flex gap-2">
            <Button size="sm" onClick={onSearch} disabled={searchQuery.isFetching}>
              {searchQuery.isFetching
                ? <Loader2 className="mr-1 h-3 w-3 animate-spin" />
                : <Search className="mr-1 h-3 w-3" />}
              検索
            </Button>
            {searchParams && (
              <Button
                size="sm"
                variant="outline"
                onClick={() => searchQuery.refetch()}
                disabled={searchQuery.isFetching}
              >
                <RefreshCw className="mr-1 h-3 w-3" />
                再読込
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="pt-4">
          {searchParams === null ? (
            <p className="text-sm text-muted-foreground">
              フィルタを指定して検索ボタンを押してください。
            </p>
          ) : searchQuery.isLoading ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : searchQuery.data && searchQuery.data.content.length > 0 ? (
            <>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead className="w-[160px]">日時</TableHead>
                    <TableHead className="w-[80px]">種別</TableHead>
                    <TableHead className="w-[140px]">実行者</TableHead>
                    <TableHead className="w-[120px]">操作</TableHead>
                    <TableHead className="w-[200px]">対象テーブル</TableHead>
                    <TableHead>PK</TableHead>
                    <TableHead className="w-[100px]"></TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {searchQuery.data.content.map((row) => (
                    <TableRow key={row.id}>
                      <TableCell className="text-xs font-mono">
                        {new Date(row.occurredAt).toLocaleString('ja-JP')}
                      </TableCell>
                      <TableCell>{actorTypeBadge(row.actorType)}</TableCell>
                      <TableCell className="text-xs">
                        {row.actorUserName ?? (row.actorUserNo ? `#${row.actorUserNo}` : '-')}
                      </TableCell>
                      <TableCell className="text-xs font-mono">{row.operation}</TableCell>
                      <TableCell className="text-xs font-mono">{row.targetTable}</TableCell>
                      <TableCell className="text-xs font-mono truncate max-w-[300px]">
                        {row.targetPk ? JSON.stringify(row.targetPk) : '-'}
                      </TableCell>
                      <TableCell>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => setDetailId(row.id)}
                        >
                          詳細
                        </Button>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
              <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground">
                <span>
                  全 {searchQuery.data.totalElements} 件 / page {searchQuery.data.number + 1}/
                  {Math.max(1, searchQuery.data.totalPages)}
                </span>
                <div className="flex gap-2">
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={searchQuery.data.first}
                    onClick={() => onPageChange(searchQuery.data!.number - 1)}
                  >
                    前へ
                  </Button>
                  <Button
                    variant="outline"
                    size="sm"
                    disabled={searchQuery.data.last}
                    onClick={() => onPageChange(searchQuery.data!.number + 1)}
                  >
                    次へ
                  </Button>
                </div>
              </div>
            </>
          ) : (
            <p className="text-sm text-muted-foreground">該当する監査ログはありません。</p>
          )}
        </CardContent>
      </Card>

      <Dialog open={detailId !== null} onOpenChange={(open) => !open && setDetailId(null)}>
        <DialogContent className="max-w-5xl max-h-[90vh] overflow-y-auto">
          <DialogHeader>
            <DialogTitle>監査ログ詳細 #{detailId}</DialogTitle>
            <DialogDescription>
              {detail
                ? `${new Date(detail.occurredAt).toLocaleString('ja-JP')} ・ ${detail.targetTable} ・ ${detail.operation}`
                : 'loading...'}
            </DialogDescription>
          </DialogHeader>
          {detailQuery.isLoading || !detail ? (
            <div className="flex items-center justify-center py-8">
              <Loader2 className="h-5 w-5 animate-spin text-muted-foreground" />
            </div>
          ) : (
            <div className="space-y-4 text-sm">
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <Label className="text-xs">実行者</Label>
                  <div>
                    {actorTypeBadge(detail.actorType)}{' '}
                    <span className="ml-2">
                      {detail.actorUserName ?? (detail.actorUserNo ? `#${detail.actorUserNo}` : '-')}
                    </span>
                  </div>
                </div>
                <div>
                  <Label className="text-xs">送信元 IP / UA</Label>
                  <div className="text-xs font-mono break-all">
                    {detail.sourceIp ?? '-'} / {detail.userAgent ?? '-'}
                  </div>
                </div>
              </div>

              {detail.reason && (
                <div className="rounded border border-amber-300 bg-amber-50 px-3 py-2 text-xs text-amber-900">
                  <strong>reason:</strong> {detail.reason}
                </div>
              )}

              <div>
                <Label className="text-xs">PK</Label>
                <pre className="mt-1 rounded bg-muted p-2 text-[11px] font-mono overflow-x-auto">
                  {formatJson(detail.targetPk)}
                </pre>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
                <div>
                  <Label className="text-xs">Before</Label>
                  <pre className="mt-1 max-h-[40vh] overflow-auto rounded bg-muted p-2 text-[11px] font-mono">
                    {formatJson(detail.beforeValues)}
                  </pre>
                </div>
                <div>
                  <Label className="text-xs">After</Label>
                  <pre className="mt-1 max-h-[40vh] overflow-auto rounded bg-muted p-2 text-[11px] font-mono">
                    {formatJson(detail.afterValues)}
                  </pre>
                </div>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
