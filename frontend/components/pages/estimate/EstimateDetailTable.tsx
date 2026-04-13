'use client'

import type { UseQueryResult } from '@tanstack/react-query'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Plus, Trash2, Search } from 'lucide-react'
import { calcProfit, calcProfitRate, calcCaseProfit, fmt, fmtRate } from '@/lib/estimate-calc'
import type { EstimateDetailRow } from './form'

interface SupplierOption {
  supplierNo: number
  supplierCode?: string | null
  supplierName: string
}

interface Props {
  isAdmin: boolean
  shopNo: string
  rows: EstimateDetailRow[]
  suppliersQuery: UseQueryResult<SupplierOption[]>
  onUpdateRow: (rowId: string, field: keyof EstimateDetailRow, value: unknown) => void
  onAddRow: () => void
  onRemoveRow: (rowId: string) => void
  onSearchByCode: (rowId: string, code: string) => void
  onOpenGoodsDialog: (rowId: string) => void
}

export function EstimateDetailTable({
  isAdmin,
  shopNo,
  rows,
  suppliersQuery,
  onUpdateRow,
  onAddRow,
  onRemoveRow,
  onSearchByCode,
  onOpenGoodsDialog,
}: Props) {
  return (
    <Card>
      <CardContent className="pt-4">
        <div className="mb-3 flex items-center justify-between">
          <h3 className="text-sm font-medium">明細</h3>
          <Button variant="outline" size="sm" onClick={onAddRow}>
            <Plus className="mr-1 h-3 w-3" />
            行追加
          </Button>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/50">
                <th className="px-2 py-2 text-left font-medium w-36">商品コード</th>
                <th className="px-2 py-2 text-left font-medium">商品名</th>
                {isAdmin && (
                  <>
                    <th className="px-2 py-2 text-right font-medium w-24">原価</th>
                    <th className="px-2 py-2 text-left font-medium w-40">原価改訂予定</th>
                  </>
                )}
                <th className="px-2 py-2 text-right font-medium w-28">見積単価</th>
                <th className="px-2 py-2 text-right font-medium w-20">入数</th>
                {isAdmin && (
                  <>
                    <th className="px-2 py-2 text-right font-medium w-20">粗利</th>
                    <th className="px-2 py-2 text-right font-medium w-20">粗利率</th>
                    <th className="px-2 py-2 text-right font-medium w-24">ケース粗利</th>
                  </>
                )}
                <th className="px-2 py-2 text-left font-medium w-32">備考</th>
                <th className="px-2 py-2 text-left font-medium w-32">仕入先</th>
                <th className="px-2 py-2 text-center font-medium w-16">順</th>
                <th className="px-2 py-2 w-10"></th>
              </tr>
            </thead>
            <tbody>
              {rows.map((row) => (
                <tr key={row.id} className="border-b hover:bg-muted/30">
                  <td className="px-2 py-1">
                    <div className="flex gap-1">
                      <Input
                        value={row.goodsCode}
                        onChange={(e) => onUpdateRow(row.id, 'goodsCode', e.target.value)}
                        onBlur={(e) => onSearchByCode(row.id, e.target.value)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter') {
                            e.preventDefault()
                            onSearchByCode(row.id, row.goodsCode)
                          }
                        }}
                        className="h-8 font-mono text-xs"
                        placeholder="コード/JAN"
                        disabled={!shopNo}
                      />
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-8 w-8 p-0 shrink-0"
                        onClick={() => onOpenGoodsDialog(row.id)}
                        title="商品検索"
                        disabled={!shopNo}
                      >
                        <Search className="h-3 w-3" />
                      </Button>
                    </div>
                  </td>
                  <td className="px-2 py-1">
                    <div className="flex items-center gap-1">
                      <Input
                        value={row.goodsName}
                        onChange={(e) => onUpdateRow(row.id, 'goodsName', e.target.value)}
                        className="h-8 text-xs"
                        placeholder="商品名"
                      />
                      {!row.goodsNo && row.goodsName && (
                        <Badge variant="outline" className="shrink-0 text-[10px] px-1 py-0 border-orange-400 text-orange-600">
                          新規
                        </Badge>
                      )}
                    </div>
                    {row.specification && (
                      <span className="ml-1 text-xs text-muted-foreground">
                        {row.specification}
                      </span>
                    )}
                  </td>
                  {isAdmin && (
                    <>
                      <td className="px-2 py-1">
                        <Input
                          type="number"
                          value={row.purchasePrice ?? ''}
                          onChange={(e) =>
                            onUpdateRow(
                              row.id,
                              'purchasePrice',
                              e.target.value ? Number(e.target.value) : null,
                            )
                          }
                          className="h-8 text-right tabular-nums"
                          min={0}
                        />
                      </td>
                      <td className="px-2 py-1 text-xs text-muted-foreground">
                        {row.pricePlanInfo}
                      </td>
                    </>
                  )}
                  <td className="px-2 py-1">
                    <Input
                      type="number"
                      value={row.goodsPrice ?? ''}
                      onChange={(e) =>
                        onUpdateRow(
                          row.id,
                          'goodsPrice',
                          e.target.value ? Number(e.target.value) : null,
                        )
                      }
                      className="h-8 text-right tabular-nums"
                      min={0}
                    />
                  </td>
                  <td className="px-2 py-1">
                    <Input
                      type="number"
                      value={row.containNum ?? ''}
                      onChange={(e) =>
                        onUpdateRow(
                          row.id,
                          'containNum',
                          e.target.value ? Number(e.target.value) : null,
                        )
                      }
                      className="h-8 w-20 text-right tabular-nums"
                      min={1}
                    />
                  </td>
                  {isAdmin && (
                    <>
                      <td className="px-2 py-1 text-right tabular-nums">
                        {fmt(calcProfit(row.goodsPrice, row.purchasePrice))}
                      </td>
                      <td className="px-2 py-1 text-right tabular-nums">
                        {fmtRate(calcProfitRate(row.goodsPrice, row.purchasePrice))}
                      </td>
                      <td className="px-2 py-1 text-right tabular-nums font-medium">
                        {fmt(calcCaseProfit(row.goodsPrice, row.purchasePrice, row.containNum))}
                      </td>
                    </>
                  )}
                  <td className="px-2 py-1">
                    <Input
                      value={row.detailNote}
                      onChange={(e) => onUpdateRow(row.id, 'detailNote', e.target.value)}
                      className="h-8 text-xs"
                      placeholder="備考"
                    />
                  </td>
                  <td className="px-2 py-1">
                    {!row.goodsNo ? (
                      <SearchableSelect
                        value={row.supplierNo != null ? String(row.supplierNo) : ''}
                        onValueChange={(v) => onUpdateRow(row.id, 'supplierNo', v ? Number(v) : null)}
                        options={(suppliersQuery.data ?? []).map((s) => ({
                          value: String(s.supplierNo),
                          label: `${s.supplierCode ?? ''} ${s.supplierName}`.trim(),
                        }))}
                        placeholder="仕入先"
                        searchPlaceholder="仕入先を検索..."
                        clearable
                      />
                    ) : (
                      <span className="text-xs text-muted-foreground">
                        {row.supplierNo
                          ? (suppliersQuery.data ?? []).find((s) => s.supplierNo === row.supplierNo)?.supplierName ?? '-'
                          : '-'}
                      </span>
                    )}
                  </td>
                  <td className="px-2 py-1">
                    <Input
                      type="number"
                      value={row.displayOrder}
                      onChange={(e) =>
                        onUpdateRow(row.id, 'displayOrder', Number(e.target.value) || 1)
                      }
                      className="h-8 w-14 text-center"
                      min={1}
                    />
                  </td>
                  <td className="px-2 py-1 text-center">
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-8 w-8 p-0 text-destructive hover:text-destructive"
                      onClick={() => onRemoveRow(row.id)}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  )
}
