'use client'

import { useMemo, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'

import { api, ApiError } from '@/lib/api-client'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
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
  type BCartShippingSaveResponse,
  type BCartShippingUpdateRequest,
} from '@/types/bcart-shipping'
import { isRowLocked, type MergedRow, type RowPatch } from './shipping-types'
import { ShippingTable } from './ShippingTable'

function isKnownStatus(value: string | null): value is BCartShipmentStatus {
  return value !== null && (BCART_SHIPMENT_STATUSES as readonly string[]).includes(value)
}

function mergeRow(
  server: BCartShippingInputResponse,
  patch: RowPatch | undefined,
  adminMessageDirty: boolean,
): MergedRow {
  let baseStatus: BCartShipmentStatus
  if (isKnownStatus(server.shipmentStatus)) {
    baseStatus = server.shipmentStatus
  } else {
    // データ不整合（enum 定義外の値）を可視化する。フォールバックで握りつぶさない。
    console.warn(
      `[bcart-shipping] 未知のshipmentStatus "${server.shipmentStatus}" (logisticsId=${server.bCartLogisticsId}). '未発送' にフォールバックします。BE enum との整合性を確認してください。`,
    )
    baseStatus = '未発送'
  }
  return {
    bCartLogisticsId: server.bCartLogisticsId,
    partnerCode: server.partnerCode,
    partnerName: server.partnerName,
    deliveryCompName: server.deliveryCompName,
    goodsInfo: server.goodsInfo,
    smileSerialNoList: server.smileSerialNoList,
    bCartCsvExported: server.bCartCsvExported,
    deliveryCode: patch?.deliveryCode ?? server.deliveryCode ?? '',
    shipmentDate: patch?.shipmentDate ?? server.shipmentDate ?? '',
    memo: patch?.memo ?? server.memo ?? '',
    adminMessage: patch?.adminMessage ?? server.adminMessage ?? '',
    shipmentStatus: patch?.shipmentStatus ?? baseStatus,
    dirty: patch !== undefined && Object.keys(patch).length > 0,
    adminMessageDirty,
  }
}

function toRequest(row: MergedRow): BCartShippingUpdateRequest {
  return {
    bCartLogisticsId: row.bCartLogisticsId,
    deliveryCode: row.deliveryCode,
    shipmentDate: row.shipmentDate.length > 0 ? row.shipmentDate : null,
    memo: row.memo,
    adminMessage: row.adminMessageDirty ? row.adminMessage : null,
    adminMessageDirty: row.adminMessageDirty,
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
  const [partnerCodeInput, setPartnerCodeInput] = useState('')
  const [statusInput, setStatusInput] = useState<BCartShipmentStatus | ''>('')
  const [searchParams, setSearchParams] = useState<{ partnerCode: string; status: BCartShipmentStatus | '' }>({
    partnerCode: '',
    status: '',
  })

  // サーバーデータを複製せず、dirty差分のみローカル保持する
  const [dirtyMap, setDirtyMap] = useState<Map<number, RowPatch>>(new Map())
  const [adminDirtyIds, setAdminDirtyIds] = useState<Set<number>>(new Set())
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [bulkStatus, setBulkStatus] = useState<BCartShipmentStatus | ''>('')
  const [bulkDialogOpen, setBulkDialogOpen] = useState(false)
  const [saveDialogOpen, setSaveDialogOpen] = useState(false)
  const [pendingSave, setPendingSave] = useState<BCartShippingUpdateRequest[]>([])

  const listQuery = useQuery({
    queryKey: ['bcart-shipping', searchParams.status, searchParams.partnerCode],
    queryFn: async () => {
      const params = new URLSearchParams()
      if (searchParams.status) params.append('statuses', searchParams.status)
      if (searchParams.partnerCode) params.set('partnerCode', searchParams.partnerCode)
      const qs = params.toString()
      return await api.get<BCartShippingInputResponse[]>(`/bcart/shipping${qs ? `?${qs}` : ''}`)
    },
    refetchOnWindowFocus: false,
    staleTime: 30_000,
  })

  const serverRows = listQuery.data ?? []

  const rows = useMemo<MergedRow[]>(
    () => serverRows.map((r) => mergeRow(r, dirtyMap.get(r.bCartLogisticsId), adminDirtyIds.has(r.bCartLogisticsId))),
    [serverRows, dirtyMap, adminDirtyIds],
  )

  const dirtyRows = useMemo(() => rows.filter((r) => r.dirty && !isRowLocked(r)), [rows])

  const saveAllMutation = useMutation({
    mutationFn: (payload: BCartShippingUpdateRequest[]) =>
      api.put<BCartShippingSaveResponse>('/bcart/shipping', payload),
    onSuccess: (res) => {
      toast.success(`出荷情報を更新しました (${res?.updatedCount ?? 0}件)`)
      if (res?.skippedIds && res.skippedIds.length > 0) {
        toast.warning(
          `B-CART連携済・発送済のため ${res.skippedIds.length} 件は更新をスキップしました (ID: ${res.skippedIds.join(', ')})`,
        )
      }
      setDirtyMap(new Map())
      setAdminDirtyIds(new Set())
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
    setDirtyMap(new Map())
    setAdminDirtyIds(new Set())
    setSelected(new Set())
  }

  const handleRowChange = (id: number, patch: RowPatch) => {
    setDirtyMap((prev) => {
      const next = new Map(prev)
      const existing = next.get(id) ?? {}
      next.set(id, { ...existing, ...patch })
      return next
    })
    if (Object.prototype.hasOwnProperty.call(patch, 'adminMessage')) {
      setAdminDirtyIds((prev) => {
        if (prev.has(id)) return prev
        const next = new Set(prev)
        next.add(id)
        return next
      })
    }
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

  const confirmSave = () => {
    if (dirtyRows.length === 0) {
      toast.info('変更はありません')
      return
    }
    // スナップショットを固定してからダイアログを開く（保存確認中の編集で送信内容が変わらないように）
    setPendingSave(dirtyRows.map(toRequest))
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
          <ShippingTable
            rows={rows}
            selected={selected}
            allSelected={allSelected}
            onToggleSelect={toggleSelect}
            onToggleSelectAll={toggleSelectAll}
            onRowChange={handleRowChange}
          />

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
        description={`${pendingSave.length} 件の変更を更新します。よろしいですか？`}
        onConfirm={() => saveAllMutation.mutate(pendingSave)}
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
