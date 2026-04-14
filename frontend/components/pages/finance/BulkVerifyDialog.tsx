'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { toast } from 'sonner'

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
      a.href = URL.createObjectURL(blob)
      a.download = suggest
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(a.href)
      toast.success('CSVをダウンロードしました')
    } catch (e) {
      toast.error(`ダウンロード失敗: ${(e as Error).message}`)
    }
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader>
          <DialogTitle>振込明細アップロードによる一括検証</DialogTitle>
        </DialogHeader>
        <div className="space-y-3">
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
              {preview.unregisteredSources.length > 0 && (
                <div className="text-xs text-red-600">
                  マスタ未登録: {preview.unregisteredSources.join(', ')}
                </div>
              )}
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
