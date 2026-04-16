'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'

import Link from 'next/link'
import { Scale } from 'lucide-react'

import { api } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import type { PaymentMfPreviewResponse, PaymentMfVerifyResult } from '@/types/payment-mf'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  onApplied: () => void
}

export function BulkVerifyDialog({ open, onOpenChange, onApplied }: Props) {
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<PaymentMfPreviewResponse | null>(null)
  const [result, setResult] = useState<PaymentMfVerifyResult | null>(null)

  const reset = () => {
    setFile(null)
    setPreview(null)
    setResult(null)
  }

  const handleOpenChange = (o: boolean) => {
    if (!o) reset()
    onOpenChange(o)
  }

  const previewMut = useMutation({
    mutationFn: async (f: File) => {
      const fd = new FormData()
      fd.append('file', f)
      return api.postForm<PaymentMfPreviewResponse>('/finance/payment-mf/preview', fd)
    },
    onSuccess: (r) => {
      setPreview(r)
      setResult(null)
      setFile(null) // プレビュー成功後はファイル選択状態をクリア（次回誤再送防止）
      if (r.errorCount > 0) {
        toast.warning(`マスタ未登録 ${r.errorCount} 件あります。先にマスタ整備してください`)
      }
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const verifyMut = useMutation({
    mutationFn: async (uploadId: string) =>
      api.post<PaymentMfVerifyResult>(`/finance/payment-mf/verify/${uploadId}`),
    onSuccess: (r) => {
      setResult(r)
      toast.success(
        `反映: 一致 ${r.matchedCount} / 差異 ${r.diffCount} / 買掛金なし ${r.notFoundCount}`,
      )
      onApplied()
    },
    onError: (e: Error) => toast.error(`反映失敗: ${e.message}`),
  })

  const download = async () => {
    if (!preview) return
    try {
      const { blob, filename } = await api.downloadPost(
        `/finance/payment-mf/convert/${preview.uploadId}`,
      )
      const date = preview.transferDate?.replaceAll('-', '') ?? 'unknown'
      const suggest = filename ?? `買掛仕入MFインポートファイル_${date}.csv`
      const a = document.createElement('a')
      const url = URL.createObjectURL(blob)
      a.href = url
      a.download = suggest
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      // revoke は非同期ダウンロード完了を待つ（同期 revoke は大容量 CSV で稀に失敗する）
      setTimeout(() => URL.revokeObjectURL(url), 1000)
      toast.success('CSVをダウンロードしました')
    } catch (e) {
      toast.error(`ダウンロード失敗: ${(e as Error).message}`)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-3xl max-h-[90vh] flex flex-col">
        <DialogHeader>
          <DialogTitle>振込明細アップロードによる一括検証</DialogTitle>
        </DialogHeader>
        <div className="space-y-3 flex-1 min-h-0 overflow-auto pr-1">
          <p className="text-xs text-muted-foreground">
            振込明細Excel（5日払い/20日払い）をアップロードし、買掛金一覧に <code>verified_manually=true</code> で一括反映します。
            差額100円以内なら「一致」、超えれば「不一致」。振込明細を正として上書きします。
          </p>
          {!preview && (
            <div className="flex items-center gap-2">
              <Input
                type="file"
                accept=".xlsx"
                onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                className="max-w-md"
              />
              <Button
                onClick={() => file && previewMut.mutate(file)}
                disabled={!file || previewMut.isPending}
              >
                {previewMut.isPending ? '解析中...' : 'プレビュー'}
              </Button>
            </div>
          )}

          {preview && (
            <div className="rounded border p-3 text-sm space-y-2">
              <div>ファイル: <span className="font-medium">{preview.fileName}</span></div>
              <div>送金日: <span className="font-medium">{preview.transferDate}</span>
                {preview.transactionMonth && (
                  <> / 対応取引月: <span className="font-medium">{preview.transactionMonth}</span></>
                )}
              </div>
              <div className="flex gap-3 text-xs">
                <span className="text-green-700">一致 {preview.matchedCount}</span>
                <span className="text-amber-700">差異 {preview.diffCount}</span>
                <span className="text-red-600">買掛金なし {preview.unmatchedCount}</span>
                {preview.errorCount > 0 && (
                  <span className="text-red-600 font-semibold">未登録 {preview.errorCount}</span>
                )}
              </div>
              {(() => {
                const unmatched = preview.rows.filter(
                  (r) => r.matchStatus === 'UNMATCHED' && r.errorType !== 'UNREGISTERED'
                )
                if (unmatched.length === 0) return null
                return (
                  <div className="rounded border border-red-300 bg-red-50 p-2 space-y-1">
                    <div className="text-xs font-medium text-red-700">
                      買掛金なし ({unmatched.length}件) — 振込明細にあるが買掛金集計に無い
                    </div>
                    <p className="text-[11px] text-red-800">
                      {preview.transactionMonth} の買掛金集計に該当レコードが見つかりません。
                      仕入データ未取込、集計未実行、または支払先コード不一致の可能性があります。
                    </p>
                    <div className="max-h-40 overflow-auto space-y-0.5">
                      {unmatched.map((r, i) => (
                        <div
                          key={`${r.paymentSupplierCode ?? 'x'}-${r.sourceName ?? 'x'}-${i}`}
                          className="flex items-center justify-between gap-2 text-xs"
                        >
                          <span className="flex gap-2">
                            <code className="rounded bg-white px-1.5 py-0.5 font-mono">
                              {r.paymentSupplierCode ?? '—'}
                            </code>
                            <span>{r.sourceName ?? ''}</span>
                          </span>
                          <span className="tabular-nums text-red-700">
                            {r.amount?.toLocaleString() ?? ''} 円
                          </span>
                        </div>
                      ))}
                    </div>
                  </div>
                )
              })()}
              {preview.unregisteredSources.length > 0 && (
                <div className="rounded border border-orange-300 bg-orange-50 p-2 space-y-2">
                  <div className="flex items-center justify-between gap-2">
                    <div className="text-xs font-medium text-orange-700">
                      マスタ未登録 ({preview.unregisteredSources.length}件)
                    </div>
                    <Button asChild size="sm" variant="outline">
                      <Link href="/finance/payment-mf-rules" target="_blank" rel="noopener noreferrer">
                        <Scale className="mr-1 h-3 w-3" />
                        マッピングマスタを確認
                      </Link>
                    </Button>
                  </div>
                  <p className="text-[11px] text-orange-800">
                    略称違い（例: 「カミイソ ㈱」⇔「カミイソ産商 ㈱」）で未登録扱いになる場合があります。
                    <Link href="/finance/payment-mf-rules" target="_blank" rel="noopener noreferrer" className="underline font-medium ml-1">
                      マッピングマスタ
                    </Link>
                    で類似名を検索し、必要ならルールを修正/追加してください。
                  </p>
                  <div className="space-y-1">
                    {preview.unregisteredSources.map((c) => (
                      <div key={c} className="flex items-center justify-between gap-2 text-xs">
                        <code className="rounded bg-white px-1.5 py-0.5">{c}</code>
                        <Button asChild size="sm" variant="ghost" className="h-6 px-2 text-xs">
                          <Link
                            href={`/finance/payment-mf-rules?q=${encodeURIComponent(c)}`}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            マスタで検索
                          </Link>
                        </Button>
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {preview.rulesMissingSupplierCode && preview.rulesMissingSupplierCode.length > 0 && (
                <div className="rounded border border-yellow-300 bg-yellow-50 p-2 space-y-2">
                  <div className="flex items-center justify-between gap-2">
                    <div className="text-xs font-medium text-yellow-800">
                      ルール未登録（CSV除外予備軍）({preview.rulesMissingSupplierCode.length}件)
                    </div>
                    <Button asChild size="sm" variant="outline">
                      <Link href="/finance/payment-mf-rules" target="_blank" rel="noopener noreferrer">
                        <Scale className="mr-1 h-3 w-3" />
                        自動補完
                      </Link>
                    </Button>
                  </div>
                  <p className="text-[11px] text-yellow-900">
                    PAYABLE ルールはヒットしていますが <b>支払先コード (payment_supplier_code)</b> が未設定のため、
                    このままだと <b>「検証済みCSV出力」で CSV から除外</b>されます。
                    マッピングマスタの <b>「支払先コード自動補完」</b> で一括補完するか、
                    個別に編集してから一括検証を実施してください。
                  </p>
                  <div className="max-h-40 overflow-auto space-y-0.5">
                    {preview.rulesMissingSupplierCode.map((c, i) => (
                      <div key={`${c}-${i}`} className="flex items-center justify-between gap-2 text-xs">
                        <code className="rounded bg-white px-1.5 py-0.5 font-mono">{c}</code>
                        <Button asChild size="sm" variant="ghost" className="h-6 px-2 text-xs">
                          <Link
                            href={`/finance/payment-mf-rules?q=${encodeURIComponent(c)}`}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            マスタで検索
                          </Link>
                        </Button>
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {preview.amountReconciliation && (() => {
                const r = preview.amountReconciliation
                const allOk = r.excelMatched && r.readMatched
                return (
                  <div className={`rounded border p-2 text-xs space-y-2 ${
                    allOk
                      ? 'border-emerald-300 bg-emerald-50 text-emerald-900'
                      : 'border-red-300 bg-red-50 text-red-900'
                  }`}>
                    {/* チェック1: 合計行の列間整合 */}
                    <div>
                      <div className="font-medium">
                        合計行整合性: {r.excelMatched
                          ? '✅ 一致'
                          : '❌ 不一致（差額 ' + r.excelDifference.toLocaleString() + ' 円）'}
                      </div>
                      <div className="flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] mt-0.5">
                        <span>C列 請求額: <span className="tabular-nums">{r.summaryInvoiceTotal.toLocaleString()}</span></span>
                        <span>- F列 手数料: <span className="tabular-nums">{r.summaryFee.toLocaleString()}</span></span>
                        <span>- H列 早払: <span className="tabular-nums">{r.summaryEarly.toLocaleString()}</span></span>
                        <span>= 期待振込額: <span className="tabular-nums">{r.expectedTransferAmount.toLocaleString()}</span></span>
                        <span>E列 振込金額: <span className="tabular-nums">{r.summaryTransferAmount.toLocaleString()}</span></span>
                      </div>
                    </div>
                    {/* チェック2: 明細行の読取り整合 */}
                    <div>
                      <div className="font-medium">
                        明細行読取り: {r.readMatched
                          ? '✅ 一致'
                          : '❌ 不一致（差額 ' + r.readDifference.toLocaleString() + ' 円）'}
                      </div>
                      <div className="flex flex-wrap gap-x-3 gap-y-0.5 text-[11px] mt-0.5">
                        <span>明細 請求額合計: <span className="tabular-nums">{r.preTotalInvoiceSum.toLocaleString()}</span></span>
                        <span>合計行 請求額: <span className="tabular-nums">{r.summaryInvoiceTotal.toLocaleString()}</span></span>
                      </div>
                    </div>
                    {r.directPurchaseTotal > 0 && (
                      <div className="text-[11px] text-muted-foreground">
                        別振込 / DIRECT_PURCHASE: <span className="tabular-nums">{r.directPurchaseTotal.toLocaleString()}円</span>
                      </div>
                    )}
                    {!allOk && (
                      <p className="text-[11px]">
                        Excel の数値に矛盾があります。Excel を確認してください。
                      </p>
                    )}
                  </div>
                )
              })()}
            </div>
          )}

          {result && (
            <div className="rounded border border-green-300 bg-green-50 p-3 text-sm">
              反映完了: 一致 <b>{result.matchedCount}</b> / 差異 <b>{result.diffCount}</b>
              {' '}/ 買掛金なし <b>{result.notFoundCount}</b>
              {result.unmatchedSuppliers.length > 0 && (
                <div className="mt-2 text-xs text-amber-700">
                  買掛金なし: {result.unmatchedSuppliers.join(', ')}
                </div>
              )}
            </div>
          )}
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => handleOpenChange(false)}>閉じる</Button>
          {preview && (
            <Button
              variant="outline"
              disabled={preview.errorCount > 0}
              onClick={download}
            >
              CSVダウンロード
            </Button>
          )}
          {preview && !result && (
            <Button
              disabled={preview.errorCount > 0 || verifyMut.isPending}
              onClick={() => verifyMut.mutate(preview.uploadId)}
            >
              {verifyMut.isPending ? '反映中...' : '買掛金一覧に反映'}
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
