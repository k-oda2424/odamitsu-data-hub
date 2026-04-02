'use client'

import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Badge } from '@/components/ui/badge'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { getChangeReasonLabel } from '@/types/purchase-price'
import type { SupplierQuoteDataResponse, SupplierQuoteHistoryResponse } from '@/types/supplier-quote-data'

interface SupplierQuoteHistoryDialogProps {
  item: SupplierQuoteDataResponse | null
  shopNo: string
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function SupplierQuoteHistoryDialog({
  item,
  shopNo,
  open,
  onOpenChange,
}: SupplierQuoteHistoryDialogProps) {
  const historyQuery = useQuery({
    queryKey: ['supplier-quote-history', shopNo, item?.janCode],
    queryFn: () =>
      api.get<SupplierQuoteHistoryResponse[]>(
        `/supplier-quote-data/history?shopNo=${shopNo}&janCode=${item!.janCode}`
      ),
    enabled: open && !!item,
  })

  if (!item) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>見積価格履歴</DialogTitle>
          <div className="text-sm text-muted-foreground">
            {item.quoteGoodsName} / {item.janCode}
            {item.supplierName && ` / ${item.supplierName}`}
          </div>
        </DialogHeader>

        {historyQuery.isLoading ? (
          <LoadingSpinner />
        ) : historyQuery.isError ? (
          <div className="text-center text-destructive py-4">
            データの取得に失敗しました
          </div>
        ) : (
          <div className="rounded-lg border">
            <Table>
              <TableHeader>
                <TableRow className="bg-muted/40 hover:bg-muted/40">
                  <TableHead className="text-xs font-semibold">見積日</TableHead>
                  <TableHead className="text-xs font-semibold">適用日</TableHead>
                  <TableHead className="text-xs font-semibold">旧単価</TableHead>
                  <TableHead className="text-xs font-semibold">新単価</TableHead>
                  <TableHead className="text-xs font-semibold">ファイル名</TableHead>
                  <TableHead className="text-xs font-semibold">変更理由</TableHead>
                  <TableHead className="text-xs font-semibold"></TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(historyQuery.data ?? []).length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={7} className="text-center text-muted-foreground py-8">
                      履歴データがありません
                    </TableCell>
                  </TableRow>
                ) : (
                  (historyQuery.data ?? []).map((row) => (
                    <TableRow
                      key={row.quoteImportDetailId}
                      className={row.latest ? 'bg-primary/5' : ''}
                    >
                      <TableCell className="text-sm">{row.quoteDate ?? '-'}</TableCell>
                      <TableCell className="text-sm">{row.effectiveDate ?? '-'}</TableCell>
                      <TableCell className="text-sm">
                        {row.oldPrice?.toLocaleString() ?? '-'}
                      </TableCell>
                      <TableCell className="text-sm">
                        {row.newPrice?.toLocaleString() ?? '-'}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {row.fileName ?? '-'}
                      </TableCell>
                      <TableCell className="text-sm">
                        {row.changeReason ? getChangeReasonLabel(row.changeReason) : '-'}
                      </TableCell>
                      <TableCell>
                        {row.latest && <Badge variant="secondary">最新</Badge>}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
