'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { toast } from 'sonner'
import { api, ApiError } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { Switch } from '@/components/ui/switch'
import { FileDown } from 'lucide-react'

interface Props {
  open: boolean
  onOpenChange: (o: boolean) => void
  transactionMonth: string // yyyy-MM-dd
}

interface PurchaseJournalExportPreview {
  transactionMonth: string
  rowCount: number
  payableCount: number
  totalAmount: number
  nonExportableCount: number
  skippedSuppliers: string[]
}

/**
 * 買掛金サマリから MF 仕入仕訳 CSV（借方「仕入高」/ 貸方「買掛金」）を直接ダウンロードするダイアログ。
 * バッチ purchaseJournalIntegration と同じロジックだが、ファイル書き出しではなくブラウザ DL。
 */
export function PurchaseJournalCsvExportDialog({ open, onOpenChange, transactionMonth }: Props) {
  const [downloading, setDownloading] = useState(false)
  const [forceExport, setForceExport] = useState(false)

  const previewQuery = useQuery({
    queryKey: ['purchase-journal-export-preview', transactionMonth, forceExport],
    queryFn: () => {
      const sp = new URLSearchParams()
      sp.set('transactionMonth', transactionMonth)
      if (forceExport) sp.set('forceExport', 'true')
      return api.get<PurchaseJournalExportPreview>(
        `/finance/accounts-payable/export-purchase-journal/preview?${sp.toString()}`,
      )
    },
    enabled: open,
    staleTime: 10_000,
  })

  const download = async () => {
    setDownloading(true)
    try {
      const sp = new URLSearchParams()
      sp.set('transactionMonth', transactionMonth)
      if (forceExport) sp.set('forceExport', 'true')
      const { blob, filename, headers } = await api.download(
        `/finance/accounts-payable/export-purchase-journal?${sp.toString()}`,
      )
      const yyyymmdd = transactionMonth.replaceAll('-', '')
      const suffix = forceExport ? '_UNCHECKED' : ''
      const suggest = filename ?? `accounts_payable_to_purchase_journal_${yyyymmdd}${suffix}.csv`
      const a = document.createElement('a')
      const url = URL.createObjectURL(blob)
      a.href = url
      a.download = suggest
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      setTimeout(() => URL.revokeObjectURL(url), 1000)

      const rowCount = headers.get('X-Row-Count') ?? '?'
      const totalAmountNum = Number(headers.get('X-Total-Amount'))
      const totalAmountStr = Number.isFinite(totalAmountNum) ? totalAmountNum.toLocaleString() : '?'
      const skippedCount = Number(headers.get('X-Skipped-Count') ?? 0)
      const skippedRaw = headers.get('X-Skipped-Suppliers')
      let skipped = ''
      if (skippedRaw) {
        skipped = skippedRaw
          .split('|')
          .map((seg) => {
            try {
              return decodeURIComponent(seg)
            } catch {
              return seg
            }
          })
          .join(', ')
      }
      toast.success(`仕入仕訳CSV出力: ${rowCount}件 / 合計 ${totalAmountStr}円`)
      if (skippedCount > 0) {
        toast.warning(`MF勘定科目マスタ未登録のため除外: ${skippedCount}件\n${skipped}`)
      }
      onOpenChange(false)
    } catch (e) {
      const msg = e instanceof ApiError ? e.message : (e as Error).message
      toast.error(`ダウンロード失敗: ${msg}`)
    } finally {
      setDownloading(false)
    }
  }

  const preview = previewQuery.data

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>買掛金から仕入仕訳CSV出力（MF）</DialogTitle>
        </DialogHeader>
        <div className="space-y-3 text-sm">
          <p className="text-xs text-muted-foreground">
            指定取引月の買掛金サマリから、マネーフォワードクラウド会計向けの
            <b>仕入仕訳CSV（借方「仕入高」/ 貸方「買掛金」）</b>
            を生成します。supplier × 税率 で集約して 1 行ずつ出力します。
          </p>
          <div>
            <Label>対象取引月（CSV 取引日列）</Label>
            <Input value={transactionMonth} readOnly disabled className="bg-muted" />
            <p className="text-[11px] text-muted-foreground mt-1">
              20日締めの当月分（例: 2026-03-20 なら 2026-02-21〜2026-03-20 の買掛金）。
            </p>
          </div>

          <div className="flex items-center gap-3 rounded border p-3">
            <Switch id="force-export" checked={forceExport} onCheckedChange={setForceExport} />
            <div className="flex-1">
              <Label htmlFor="force-export" className="cursor-pointer">
                MF出力OFF の行も含める（強制出力）
              </Label>
              <p className="text-[11px] text-muted-foreground">
                チェック未完了・不一致のまま出力したい時だけ ON。通常は OFF のままにしてください。
              </p>
            </div>
          </div>

          {previewQuery.isLoading && (
            <div className="text-xs text-muted-foreground">内訳を集計中...</div>
          )}
          {preview && (
            <div className="rounded border p-3 space-y-2 text-xs">
              <div className="font-medium">内訳</div>
              <div className="flex justify-between">
                <span>出力対象（集約後の仕訳行）</span>
                <span className="tabular-nums">
                  {preview.rowCount}件 / {preview.totalAmount.toLocaleString()}円
                </span>
              </div>
              <div className="flex justify-between text-muted-foreground">
                <span>対象買掛金（集約前）</span>
                <span className="tabular-nums">{preview.payableCount}件</span>
              </div>
              {preview.nonExportableCount > 0 && (
                <div className="flex justify-between text-amber-700">
                  <span>MF出力OFF で除外</span>
                  <span className="tabular-nums">{preview.nonExportableCount}件</span>
                </div>
              )}
              {preview.skippedSuppliers.length > 0 && (
                <details className="mt-2 text-[11px] text-red-800">
                  <summary className="cursor-pointer select-none">
                    MF勘定科目マスタ未登録で除外: {preview.skippedSuppliers.length}件
                    <span className="ml-1 text-muted-foreground">（クリックで内訳表示）</span>
                  </summary>
                  <ul className="mt-1 ml-4 list-disc space-y-0.5">
                    {preview.skippedSuppliers.map((s, i) => (
                      <li key={`${s}-${i}`} className="font-mono">
                        {s}
                      </li>
                    ))}
                  </ul>
                  <p className="mt-1 text-[10px] text-muted-foreground">
                    対応: 財務諸表項目「買掛金」/ 勘定科目「買掛金」の MF勘定科目マスタに
                    検索キー（supplier_code）を登録してください。
                  </p>
                </details>
              )}
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={downloading}>
            キャンセル
          </Button>
          <Button
            onClick={download}
            disabled={downloading || !preview || preview.rowCount === 0}
          >
            <FileDown className="mr-1 h-4 w-4" />
            {downloading ? '生成中...' : 'CSVダウンロード'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
