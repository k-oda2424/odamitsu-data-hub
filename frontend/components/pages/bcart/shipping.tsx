'use client'

import { Fragment, useEffect, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { api, ApiError } from '@/lib/api-client'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { Label } from '@/components/ui/label'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import {
  BCART_SHIPMENT_STATUSES,
  type BCartShipmentStatus,
  type BCartShippingBulkStatusRequest,
  type BCartShippingInputResponse,
  type BCartShippingUpdateRequest,
} from '@/types/bcart-shipping'

interface EditableRow extends BCartShippingInputResponse {
  dirty: boolean
  adminMessageDirty: boolean
}

function isKnownStatus(value: string | null): value is BCartShipmentStatus {
  return value !== null && (BCART_SHIPMENT_STATUSES as readonly string[]).includes(value)
}

function toRequest(row: EditableRow): BCartShippingUpdateRequest {
  return {
    bCartLogisticsId: row.bCartLogisticsId,
    deliveryCode: row.deliveryCode ?? '',
    // 空文字はバックエンドで LocalDate パース不可のため null を送る
    shipmentDate: row.shipmentDate && row.shipmentDate.length > 0 ? row.shipmentDate : null,
    memo: row.memo ?? '',
    // 編集されていない場合は null。バックエンドで adminMessage の上書きをスキップする契約。
    adminMessage: row.adminMessageDirty ? (row.adminMessage ?? '') : null,
    shipmentStatus: row.shipmentStatus,
  }
}

function formatApiError(e: unknown, fallback: string): string {
  if (e instanceof ApiError) {
    if (e.status === 400) {
      try {
        const body = JSON.parse(e.message) as { message?: string; errors?: Array<{ field?: string; defaultMessage?: string }> }
        if (body.errors && body.errors.length > 0) {
          return body.errors
            .map((err) => `${err.field ?? ''}: ${err.defaultMessage ?? ''}`.trim())
            .filter(Boolean)
            .join(' / ')
        }
        if (body.message) return body.message
      } catch {
        if (e.message) return e.message
      }
    }
    return `${fallback} (${e.status})`
  }
  return fallback
}

export function BCartShippingPage() {
  const queryClient = useQueryClient()
  // 検索条件
  const [partnerCodeInput, setPartnerCodeInput] = useState('')
  const [statusInput, setStatusInput] = useState<BCartShipmentStatus | ''>('')
  const [searchParams, setSearchParams] = useState<{ partnerCode: string; status: BCartShipmentStatus | '' }>({
    partnerCode: '',
    status: '',
  })

  const [rows, setRows] = useState<EditableRow[]>([])
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [bulkStatus, setBulkStatus] = useState<BCartShipmentStatus | ''>('')
  const [bulkDialogOpen, setBulkDialogOpen] = useState(false)
  const [saveDialogOpen, setSaveDialogOpen] = useState(false)

  const listQuery = useQuery({
    queryKey: ['bcart-shipping', searchParams.status, searchParams.partnerCode],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (searchParams.status) {
        params.append('statuses', searchParams.status)
      }
      if (searchParams.partnerCode) {
        params.set('partnerCode', searchParams.partnerCode)
      }
      const qs = params.toString()
      return await api.get<BCartShippingInputResponse[]>(`/bcart/shipping${qs ? `?${qs}` : ''}`)
    },
    refetchOnWindowFocus: false,
    staleTime: 30_000,
  })

  // listQuery 更新時にローカル編集状態をマージ
  // dirty 行は保持し、サーバーの新規/変更行のみ反映する
  useEffect(() => {
    if (!listQuery.data) return
    setRows((prev) => {
      const prevDirtyMap = new Map(prev.filter((r) => r.dirty).map((r) => [r.bCartLogisticsId, r]))
      const nextRows = listQuery.data.map((r) => {
        const dirty = prevDirtyMap.get(r.bCartLogisticsId)
        if (dirty) return dirty
        return {
          ...r,
          deliveryCode: r.deliveryCode ?? '',
          shipmentDate: r.shipmentDate ?? '',
          memo: r.memo ?? '',
          adminMessage: r.adminMessage ?? '',
          shipmentStatus: isKnownStatus(r.shipmentStatus) ? r.shipmentStatus : ('未発送' as BCartShipmentStatus),
          dirty: false,
          adminMessageDirty: false,
        }
      })
      return nextRows
    })
    // 選択状態は新しい行集合に絞る
    setSelected((prev) => {
      const validIds = new Set(listQuery.data!.map((r) => r.bCartLogisticsId))
      const next = new Set<number>()
      prev.forEach((id) => {
        if (validIds.has(id)) next.add(id)
      })
      return next
    })
  }, [listQuery.data])

  const saveAllMutation = useMutation({
    mutationFn: (payload: BCartShippingUpdateRequest[]) => api.put<void>('/bcart/shipping', payload),
    onSuccess: () => {
      toast.success('出荷情報を更新しました')
      queryClient.invalidateQueries({ queryKey: ['bcart-shipping'] })
    },
    onError: (e) => toast.error(formatApiError(e, '更新に失敗しました')),
  })

  const bulkStatusMutation = useMutation({
    mutationFn: (payload: BCartShippingBulkStatusRequest) =>
      api.put<void>('/bcart/shipping/bulk-status', payload),
    onSuccess: () => {
      toast.success('選択した項目のステータスを更新しました')
      queryClient.invalidateQueries({ queryKey: ['bcart-shipping'] })
    },
    onError: (e) => toast.error(formatApiError(e, '一括更新に失敗しました')),
  })

  const handleSearch = () => {
    setSearchParams({ partnerCode: partnerCodeInput.trim(), status: statusInput })
  }

  const handleRowChange = (id: number, patch: Partial<EditableRow>) => {
    setRows((prev) =>
      prev.map((r) => {
        if (r.bCartLogisticsId !== id) return r
        const next: EditableRow = { ...r, ...patch, dirty: true }
        if (Object.prototype.hasOwnProperty.call(patch, 'adminMessage')) {
          next.adminMessageDirty = true
        }
        return next
      }),
    )
  }

  const toggleSelect = (id: number, checked: boolean) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (checked) next.add(id)
      else next.delete(id)
      return next
    })
  }

  const allSelectable = rows.filter((r) => !isRowLocked(r))
  const allSelected = allSelectable.length > 0 && allSelectable.every((r) => selected.has(r.bCartLogisticsId))

  const toggleSelectAll = (checked: boolean) => {
    if (checked) setSelected(new Set(allSelectable.map((r) => r.bCartLogisticsId)))
    else setSelected(new Set())
  }

  const dirtyRows = rows.filter((r) => r.dirty && !isRowLocked(r))

  const confirmSave = () => {
    if (dirtyRows.length === 0) {
      toast.info('変更はありません')
      return
    }
    setSaveDialogOpen(true)
  }

  const confirmBulk = () => {
    if (selected.size === 0) {
      toast.info('対象行を選択してください')
      return
    }
    if (!bulkStatus) {
      toast.info('一括更新するステータスを選択してください')
      return
    }
    if (dirtyRows.length > 0) {
      toast.warning('未保存の編集があります。先に出荷情報更新してください')
      return
    }
    // SMILE 未連携の行を「発送済」に一括更新することは不可
    if (bulkStatus === '発送済') {
      const unlinked = rows.filter((r) => selected.has(r.bCartLogisticsId) && r.goodsInfo.length === 0)
      if (unlinked.length > 0) {
        toast.error(`SMILE 未連携の行が ${unlinked.length} 件含まれています。発送済には変更できません`)
        return
      }
    }
    setBulkDialogOpen(true)
  }

  return (
    <div className="space-y-4">
      <PageHeader title="B-CART出荷情報入力" />

      {/* 検索フォーム */}
      <div className="rounded-md border bg-card p-4">
        <div className="grid gap-4 md:grid-cols-[1fr_1fr_auto] md:items-end">
          <div className="space-y-1.5">
            <Label htmlFor="partnerCode">得意先</Label>
            <Input
              id="partnerCode"
              value={partnerCodeInput}
              onChange={(e) => setPartnerCodeInput(e.target.value)}
              placeholder="得意先コードを入力してください"
            />
          </div>
          <div className="space-y-1.5">
            <Label htmlFor="status">出荷ステータス</Label>
            <Select value={statusInput || '__none__'} onValueChange={(v) => setStatusInput(v === '__none__' ? '' : (v as BCartShipmentStatus))}>
              <SelectTrigger id="status">
                <SelectValue placeholder="選択して下さい" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__none__">選択して下さい</SelectItem>
                {BCART_SHIPMENT_STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {s}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <Button onClick={handleSearch}>検索</Button>
        </div>
      </div>

      {listQuery.isLoading && <LoadingSpinner />}
      {listQuery.isError && <ErrorMessage onRetry={() => listQuery.refetch()} />}

      {!listQuery.isLoading && !listQuery.isError && (
        <>
          {/* 出荷情報入力テーブル */}
          <div className="rounded-md border bg-card">
            <div className="border-b px-4 py-2 text-sm font-medium">出荷情報入力</div>
            <div className="overflow-x-auto">
              <Table className="text-xs">
                <TableHeader>
                  <TableRow>
                    <TableHead rowSpan={2} className="w-10 text-center align-middle border-r">
                      <div className="flex flex-col items-center gap-0.5">
                        <Checkbox
                          checked={allSelected}
                          onCheckedChange={(v) => toggleSelectAll(Boolean(v))}
                          aria-label="全選択"
                        />
                        <span className="text-[10px]">選択</span>
                      </div>
                    </TableHead>
                    <TableHead className="text-center align-middle border-r">得意先コード</TableHead>
                    <TableHead className="text-center align-middle border-r">b-cart.LogisticsID</TableHead>
                    <TableHead rowSpan={2} className="text-center align-middle border-r">届け先</TableHead>
                    <TableHead rowSpan={2} className="text-center align-middle border-r">商品コード：商品名：数量</TableHead>
                    <TableHead className="text-center align-middle border-r">送り状番号</TableHead>
                    <TableHead rowSpan={2} className="text-center align-middle border-r min-w-[110px]">出荷ステータス</TableHead>
                    <TableHead className="text-center align-middle">メモ</TableHead>
                  </TableRow>
                  <TableRow>
                    <TableHead className="text-center align-middle border-r">得意先名</TableHead>
                    <TableHead className="text-center align-middle border-r">smile連番</TableHead>
                    <TableHead className="text-center align-middle border-r">出荷日</TableHead>
                    <TableHead className="text-center align-middle">得意先へ連絡事項</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {rows.length === 0 ? (
                    <TableRow>
                      <TableCell colSpan={8} className="text-center text-muted-foreground py-6">
                        表示対象のデータがありません
                      </TableCell>
                    </TableRow>
                  ) : (
                    rows.map((row) => {
                      const locked = isRowLocked(row)
                      const rowSelected = selected.has(row.bCartLogisticsId)
                      const smileUnlinked = row.goodsInfo.length === 0
                      const availableStatuses = BCART_SHIPMENT_STATUSES.filter(
                        (s) => !(smileUnlinked && s === '発送済'),
                      )
                      return (
                        <Fragment key={row.bCartLogisticsId}>
                          {/* 1 行目 */}
                          <TableRow className="border-b-0">
                            <TableCell rowSpan={2} className="text-center align-middle border-r border-b">
                              <Checkbox
                                checked={rowSelected}
                                onCheckedChange={(v) => toggleSelect(row.bCartLogisticsId, Boolean(v))}
                                disabled={locked}
                                aria-label={`row-${row.bCartLogisticsId}`}
                              />
                            </TableCell>
                            <TableCell className="text-center align-middle border-r">
                              {row.partnerCode ?? ''}
                            </TableCell>
                            <TableCell className="text-center align-middle border-r">
                              {row.bCartLogisticsId}
                            </TableCell>
                            <TableCell rowSpan={2} className="align-middle border-r border-b">
                              {row.deliveryCompName ?? ''}
                            </TableCell>
                            <TableCell rowSpan={2} className="align-middle border-r border-b">
                              {row.goodsInfo.length === 0 ? (
                                <span className="text-muted-foreground">SMILE未連携</span>
                              ) : (
                                <ul className="list-none space-y-0.5">
                                  {row.goodsInfo.map((g, i) => (
                                    <li key={`${row.bCartLogisticsId}-g-${i}`}>{g}</li>
                                  ))}
                                </ul>
                              )}
                            </TableCell>
                            <TableCell className="align-middle border-r">
                              <Input
                                value={row.deliveryCode ?? ''}
                                onChange={(e) => handleRowChange(row.bCartLogisticsId, { deliveryCode: e.target.value })}
                                disabled={locked}
                                placeholder="送り状番号"
                                className="h-8"
                                data-testid={`delivery-code-${row.bCartLogisticsId}`}
                              />
                            </TableCell>
                            <TableCell rowSpan={2} className="align-middle border-r border-b min-w-[110px]">
                              <Select
                                value={row.shipmentStatus}
                                onValueChange={(v) => handleRowChange(row.bCartLogisticsId, { shipmentStatus: v as BCartShipmentStatus })}
                                disabled={locked}
                              >
                                <SelectTrigger className="h-8">
                                  <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                  {availableStatuses.map((s) => (
                                    <SelectItem key={s} value={s}>
                                      {s}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                              {locked && (
                                <div className="mt-1 text-[10px] text-muted-foreground">B-CART連携済</div>
                              )}
                              {smileUnlinked && !locked && (
                                <div className="mt-1 text-[10px] text-muted-foreground">SMILE未連携のため発送済不可</div>
                              )}
                            </TableCell>
                            <TableCell className="align-middle">
                              <Textarea
                                value={row.memo ?? ''}
                                onChange={(e) => handleRowChange(row.bCartLogisticsId, { memo: e.target.value })}
                                disabled={locked}
                                rows={2}
                                className="min-h-[40px] text-xs"
                              />
                            </TableCell>
                          </TableRow>
                          {/* 2 行目 */}
                          <TableRow>
                            <TableCell className="text-center align-middle border-r">
                              {row.partnerName ?? ''}
                            </TableCell>
                            <TableCell className="text-center align-middle border-r">
                              {row.smileSerialNoList.length === 0 ? '' : row.smileSerialNoList.join(', ')}
                            </TableCell>
                            <TableCell className="align-middle border-r">
                              <Input
                                type="date"
                                value={row.shipmentDate ?? ''}
                                onChange={(e) => handleRowChange(row.bCartLogisticsId, { shipmentDate: e.target.value })}
                                disabled={locked}
                                className="h-8"
                              />
                            </TableCell>
                            <TableCell className="align-middle">
                              <Textarea
                                value={row.adminMessage ?? ''}
                                onChange={(e) => handleRowChange(row.bCartLogisticsId, { adminMessage: e.target.value })}
                                disabled={locked}
                                rows={2}
                                className="min-h-[40px] text-xs"
                              />
                            </TableCell>
                          </TableRow>
                        </Fragment>
                      )
                    })
                  )}
                </TableBody>
              </Table>
            </div>
          </div>

          {/* 一括更新 */}
          <div className="flex flex-wrap items-center gap-2 rounded-md border bg-card p-4">
            <Label>一括更新:</Label>
            <Select value={bulkStatus || '__none__'} onValueChange={(v) => setBulkStatus(v === '__none__' ? '' : (v as BCartShipmentStatus))}>
              <SelectTrigger className="w-[200px]" data-testid="bulk-status-select">
                <SelectValue placeholder="選択して下さい" />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="__none__">選択して下さい</SelectItem>
                {BCART_SHIPMENT_STATUSES.map((s) => (
                  <SelectItem key={s} value={s}>
                    {s}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Button onClick={confirmBulk} disabled={bulkStatusMutation.isPending} data-testid="bulk-update-btn">
              選択した項目を一括更新
            </Button>
          </div>

          {/* 出荷情報更新ボタン */}
          <div>
            <Button onClick={confirmSave} disabled={saveAllMutation.isPending}>
              出荷情報更新
            </Button>
          </div>
        </>
      )}

      <ConfirmDialog
        open={saveDialogOpen}
        onOpenChange={setSaveDialogOpen}
        title="出荷情報更新"
        description={`${dirtyRows.length} 件の変更を更新します。よろしいですか？`}
        onConfirm={() => saveAllMutation.mutate(dirtyRows.map(toRequest))}
        confirmLabel="更新"
      />
      <ConfirmDialog
        open={bulkDialogOpen}
        onOpenChange={setBulkDialogOpen}
        title="一括ステータス更新"
        description={`選択した ${selected.size} 件のステータスを「${bulkStatus}」に変更します。よろしいですか？`}
        onConfirm={() =>
          bulkStatusMutation.mutate({
            bCartLogisticsIds: Array.from(selected),
            shipmentStatus: bulkStatus as BCartShipmentStatus,
          })
        }
        confirmLabel="更新"
      />
    </div>
  )
}

function isRowLocked(row: BCartShippingInputResponse): boolean {
  return row.bCartCsvExported && row.shipmentStatus === '発送済'
}
