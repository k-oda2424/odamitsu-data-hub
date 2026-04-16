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
import { FileDown } from 'lucide-react'
import type { VerifiedExportPreview } from '@/types/payment-mf'

interface Props {
  open: boolean
  onOpenChange: (o: boolean) => void
  transactionMonth: string
}

/**
 * 検証済み買掛金データから直接 MF 仕訳 CSV を生成するダイアログ。
 * <p>PAYABLE + 補助行(EXPENSE/SUMMARY/DIRECT_PURCHASE) を結合した完全な MF CSV を出力。
 * CSV 取引日列は 小田光の締め日(前月20日) = transactionMonth 固定。
 * 支払日は MF の銀行データ連携で自動付与されるため CSV には含めない。
 */
export function VerifiedCsvExportDialog({
  open,
  onOpenChange,
  transactionMonth,
}: Props) {
  const [downloading, setDownloading] = useState(false)

  const previewQuery = useQuery({
    queryKey: ['verified-export-preview', transactionMonth],
    queryFn: () => {
      const sp = new URLSearchParams()
      sp.set('transactionMonth', transactionMonth)
      return api.get<VerifiedExportPreview>(`/finance/payment-mf/export-verified/preview?${sp.toString()}`)
    },
    enabled: open,
    staleTime: 10_000,
  })

  const download = async () => {
    setDownloading(true)
    try {
      const sp = new URLSearchParams()
      sp.set('transactionMonth', transactionMonth)
      const { blob, filename, headers } = await api.download(
        `/finance/payment-mf/export-verified?${sp.toString()}`,
      )
      const yyyymmdd = transactionMonth.replaceAll('-', '')
      const suggest = filename ?? `買掛仕入MFインポートファイル_${yyyymmdd}.csv`
      const a = document.createElement('a')
      const url = URL.createObjectURL(blob)
      a.href = url
      a.download = suggest
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      // revoke は非同期ダウンロード完了を待つ（同期 revoke は大容量 CSV で稀に失敗する）
      setTimeout(() => URL.revokeObjectURL(url), 1000)

      const rowCount = headers.get('X-Row-Count') ?? '?'
      const totalAmountNum = Number(headers.get('X-Total-Amount'))
      const totalAmountStr = Number.isFinite(totalAmountNum) ? totalAmountNum.toLocaleString() : '?'
      const skippedCount = Number(headers.get('X-Skipped-Count') ?? 0)
      const skippedRaw = headers.get('X-Skipped-Suppliers')
      // バックエンドは "|" 区切りで supplier 名に含まれるカンマとの衝突を回避し、
      // 各要素は encodeURIComponent 済み。デコード失敗時は生テキストを提示する。
      let skipped = ''
      if (skippedRaw) {
        skipped = skippedRaw.split('|')
          .map((seg) => { try { return decodeURIComponent(seg) } catch { return seg } })
          .join(', ')
      }
      toast.success(`CSV出力: ${rowCount}件 / 合計 ${totalAmountStr}円`)
      if (skippedCount > 0) {
        toast.warning(`ルール未登録のため除外: ${skippedCount}件\n${skipped}`)
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
  const auxTotalRows = preview?.auxBreakdown.reduce((sum, b) => sum + b.count, 0) ?? 0

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>検証済み買掛金からMF CSV出力</DialogTitle>
        </DialogHeader>
        <div className="space-y-3 text-sm">
          <p className="text-xs text-muted-foreground">
            <b>買掛金一覧（検証結果=一致 かつ MF出力=ON）</b>と
            <b>MF補助行（振込明細で一括検証した EXPENSE/SUMMARY/DIRECT_PURCHASE）</b>
            を結合して MF仕訳CSV を生成します。
          </p>
          <div>
            <Label>対象取引月（CSV 取引日列）</Label>
            <Input value={transactionMonth} readOnly disabled className="bg-muted" />
            <p className="text-[11px] text-muted-foreground mt-1">
              小田光の締め日(前月20日)。支払日は MF の銀行データ連携で自動付与されるため CSV には含みません。
            </p>
          </div>

          {previewQuery.isLoading && (
            <div className="text-xs text-muted-foreground">内訳を集計中...</div>
          )}
          {preview && (
            <div className="rounded border p-3 space-y-2 text-xs">
              <div className="font-medium">内訳</div>
              <div className="flex justify-between">
                <span>買掛金（一致 + MF出力ON）</span>
                <span className="tabular-nums">
                  {preview.payableCount}件 / {preview.payableTotalAmount.toLocaleString()}円
                </span>
              </div>
              {preview.auxBreakdown.length === 0 ? (
                <div className="text-amber-700">
                  補助行なし（振込明細Excelで一括検証するとここに内訳が表示されます）
                </div>
              ) : (
                <>
                  {preview.auxBreakdown.map((b) => (
                    <div key={`${b.transferDate}-${b.ruleKind}`} className="flex justify-between">
                      <span>
                        補助行 {b.transferDate} {b.ruleKind}
                      </span>
                      <span className="tabular-nums">
                        {b.count}件 / {b.totalAmount.toLocaleString()}円
                      </span>
                    </div>
                  ))}
                </>
              )}
              <div className="border-t pt-1 flex justify-between font-medium">
                <span>CSV 行数合計</span>
                <span className="tabular-nums">{preview.payableCount + auxTotalRows}件</span>
              </div>
              {preview.warnings.length > 0 && (
                <div className="mt-2 space-y-1">
                  {preview.warnings.map((w) => (
                    <div key={w} className="text-[11px] text-amber-800 bg-amber-50 rounded border border-amber-300 p-1.5">
                      ⚠️ {w}
                    </div>
                  ))}
                </div>
              )}
              {preview.skippedSuppliers.length > 0 && (
                <details className="mt-2 text-[11px] text-red-800">
                  <summary className="cursor-pointer select-none">
                    ルール未登録でCSV除外: {preview.skippedSuppliers.length}件
                    <span className="ml-1 text-muted-foreground">（クリックで内訳表示）</span>
                  </summary>
                  <ul className="mt-1 ml-4 list-disc space-y-0.5">
                    {preview.skippedSuppliers.map((s, i) => (
                      <li key={`${s}-${i}`} className="font-mono">{s}</li>
                    ))}
                  </ul>
                  <p className="mt-1 text-[10px] text-muted-foreground">
                    対応: <b>買掛仕入MFルール マスタ</b>（/finance/payment-mf-rules）で
                    supplier_code を指定した PAYABLE ルールを追加すると、次回から CSV に含まれます。
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
          <Button onClick={download} disabled={downloading || !preview || (preview.payableCount + auxTotalRows) === 0}>
            <FileDown className="mr-1 h-4 w-4" />
            {downloading ? '生成中...' : 'CSVダウンロード'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
