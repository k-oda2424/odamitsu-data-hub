'use client'

import { Fragment } from 'react'
import { Checkbox } from '@/components/ui/checkbox'
import { Input } from '@/components/ui/input'
import { Textarea } from '@/components/ui/textarea'
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
import { BCART_SHIPMENT_STATUSES, type BCartShipmentStatus } from '@/types/bcart-shipping'
import { isRowLocked, type MergedRow, type RowPatch } from './shipping-types'

interface Props {
  rows: MergedRow[]
  selected: Set<number>
  allSelected: boolean
  onToggleSelect: (id: number, checked: boolean) => void
  onToggleSelectAll: (checked: boolean) => void
  onRowChange: (id: number, patch: RowPatch) => void
}

export function ShippingTable({
  rows,
  selected,
  allSelected,
  onToggleSelect,
  onToggleSelectAll,
  onRowChange,
}: Props) {
  return (
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
                    onCheckedChange={(v) => onToggleSelectAll(Boolean(v))}
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
                    <TableRow className="border-b-0">
                      <TableCell rowSpan={2} className="text-center align-middle border-r border-b">
                        <Checkbox
                          checked={rowSelected}
                          onCheckedChange={(v) => onToggleSelect(row.bCartLogisticsId, Boolean(v))}
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
                          value={row.deliveryCode}
                          onChange={(e) => onRowChange(row.bCartLogisticsId, { deliveryCode: e.target.value })}
                          disabled={locked}
                          placeholder="送り状番号"
                          className="h-8"
                          data-testid={`delivery-code-${row.bCartLogisticsId}`}
                        />
                      </TableCell>
                      <TableCell rowSpan={2} className="align-middle border-r border-b min-w-[110px]">
                        <Select
                          value={row.shipmentStatus}
                          onValueChange={(v) => onRowChange(row.bCartLogisticsId, { shipmentStatus: v as BCartShipmentStatus })}
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
                          value={row.memo}
                          onChange={(e) => onRowChange(row.bCartLogisticsId, { memo: e.target.value })}
                          disabled={locked}
                          rows={2}
                          className="min-h-[40px] text-xs"
                        />
                      </TableCell>
                    </TableRow>
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
                          value={row.shipmentDate}
                          onChange={(e) => onRowChange(row.bCartLogisticsId, { shipmentDate: e.target.value })}
                          disabled={locked}
                          className="h-8"
                        />
                      </TableCell>
                      <TableCell className="align-middle">
                        <Textarea
                          value={row.adminMessage}
                          onChange={(e) => onRowChange(row.bCartLogisticsId, { adminMessage: e.target.value })}
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
  )
}
