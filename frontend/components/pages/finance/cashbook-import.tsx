'use client'

import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PageHeader } from '@/components/features/common/PageHeader'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { Download, Upload, AlertCircle } from 'lucide-react'
import { toast } from 'sonner'
import type {
  CashBookPreviewResponse,
  MfClientMapping,
  MfClientMappingRequest,
} from '@/types/mf-cashbook'

export default function CashBookImportPage() {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<CashBookPreviewResponse | null>(null)
  const [mappingDialog, setMappingDialog] = useState<{ alias: string } | null>(null)
  const [mfName, setMfName] = useState('')

  const previewMutation = useMutation({
    mutationFn: async (f: File) => {
      const fd = new FormData()
      fd.append('file', f)
      return api.postForm<CashBookPreviewResponse>('/finance/cashbook/preview', fd)
    },
    onSuccess: (r) => {
      setPreview(r)
      if (r.errorCount === 0) toast.success(`プレビュー成功: ${r.totalRows}件`)
      else toast.warning(`エラー ${r.errorCount} 件あります。マッピングを追加してください`)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const rePreviewMutation = useMutation({
    mutationFn: async (uploadId: string) => {
      return api.post<CashBookPreviewResponse>(`/finance/cashbook/preview/${uploadId}`)
    },
    onSuccess: (r) => {
      setPreview(r)
      if (r.errorCount === 0) toast.success('すべてのエラーが解消されました')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const addMappingMutation = useMutation({
    mutationFn: async (req: MfClientMappingRequest) => {
      return api.post<MfClientMapping>('/finance/mf-client-mappings', req)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['mf-client-mappings'] })
      toast.success('マッピングを追加しました')
      setMappingDialog(null)
      setMfName('')
      if (preview) rePreviewMutation.mutate(preview.uploadId)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const downloadCsv = async () => {
    if (!preview) return
    try {
      const { blob, filename } = await api.downloadPost(`/finance/cashbook/convert/${preview.uploadId}`)
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      const suggest = filename ?? preview.fileName?.replace(/\.xlsx$/i, '.csv') ?? 'cashbook.csv'
      a.download = suggest
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)
      toast.success('CSVをダウンロードしました')
    } catch (e) {
      toast.error(`ダウンロード失敗: ${(e as Error).message}`)
    }
  }

  return (
    <div className="space-y-4">
      <PageHeader title="現金出納帳 → MoneyForward CSV変換" />

      <div className="rounded border p-4 space-y-3">
        <Label>Excelファイル（.xlsx）</Label>
        <div className="flex items-center gap-2">
          <Input
            type="file"
            accept=".xlsx"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className="max-w-md"
          />
          <Button
            onClick={() => file && previewMutation.mutate(file)}
            disabled={!file || previewMutation.isPending}
          >
            <Upload className="mr-1 h-4 w-4" />
            {previewMutation.isPending ? '解析中...' : 'プレビュー'}
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          「記入」シートを読み込みます。shop_no=1 固定。
        </p>
      </div>

      {preview && (
        <>
          <div className="flex items-center justify-between rounded border bg-card p-4">
            <div className="space-y-1 text-sm">
              <div>ファイル: <span className="font-medium">{preview.fileName}</span></div>
              <div>取引件数: <span className="font-medium">{preview.totalRows}</span></div>
              <div>エラー件数: <span className={preview.errorCount > 0 ? 'font-medium text-red-600' : 'font-medium text-green-600'}>{preview.errorCount}</span></div>
            </div>
            <Button
              onClick={downloadCsv}
              disabled={preview.errorCount > 0}
              variant="default"
            >
              <Download className="mr-1 h-4 w-4" />
              CSVダウンロード
            </Button>
          </div>

          {preview.unmappedClients.length > 0 && (
            <div className="rounded border border-orange-300 bg-orange-50 p-4">
              <div className="mb-2 flex items-center gap-2 text-sm font-medium text-orange-700">
                <AlertCircle className="h-4 w-4" />
                未マッピング得意先 ({preview.unmappedClients.length})
              </div>
              <div className="space-y-1">
                {preview.unmappedClients.map((c) => (
                  <div key={c} className="flex items-center justify-between gap-2 text-sm">
                    <code className="rounded bg-white px-2 py-0.5">{c}</code>
                    <Button size="sm" variant="outline" onClick={() => { setMappingDialog({ alias: c }); setMfName('') }}>
                      マッピング追加
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}

          {preview.unknownDescriptions.length > 0 && (
            <div className="rounded border border-red-300 bg-red-50 p-4">
              <div className="mb-2 flex items-center gap-2 text-sm font-medium text-red-700">
                <AlertCircle className="h-4 w-4" />
                未定義の摘要C列 ({preview.unknownDescriptions.length})
              </div>
              <div className="space-y-1 text-sm">
                {preview.unknownDescriptions.map((c) => (
                  <div key={c}><code>{c}</code> — 管理者に仕訳ルール追加を依頼してください</div>
                ))}
              </div>
            </div>
          )}

          <div className="overflow-auto rounded border">
            <table className="min-w-full text-xs">
              <thead className="sticky top-0 bg-muted">
                <tr>
                  <th className="p-1">Excel行</th>
                  <th className="p-1">No</th>
                  <th className="p-1">日付</th>
                  <th className="p-1">借方勘定</th>
                  <th className="p-1">借方補助</th>
                  <th className="p-1">借方税区分</th>
                  <th className="p-1 text-right">借方金額</th>
                  <th className="p-1">貸方勘定</th>
                  <th className="p-1">貸方補助</th>
                  <th className="p-1">貸方税区分</th>
                  <th className="p-1 text-right">貸方金額</th>
                  <th className="p-1">摘要</th>
                </tr>
              </thead>
              <tbody>
                {preview.rows.map((r, idx) => (
                  <tr key={idx} className={r.errorType ? 'bg-red-50' : ''}>
                    <td className="p-1 text-center">{r.excelRowIndex}</td>
                    <td className="p-1 text-center">{r.transactionNo ?? ''}</td>
                    <td className="p-1">{r.transactionDate ?? ''}</td>
                    <td className="p-1">{r.debitAccount ?? ''}</td>
                    <td className="p-1">{r.debitSubAccount ?? ''}</td>
                    <td className="p-1">{r.debitTax ?? ''}</td>
                    <td className="p-1 text-right">{r.debitAmount?.toLocaleString() ?? ''}</td>
                    <td className="p-1">{r.creditAccount ?? ''}</td>
                    <td className="p-1">{r.creditSubAccount ?? ''}</td>
                    <td className="p-1">{r.creditTax ?? ''}</td>
                    <td className="p-1 text-right">{r.creditAmount?.toLocaleString() ?? ''}</td>
                    <td className="p-1">
                      {r.errorType ? (
                        <span className="text-red-600">{r.errorMessage}</span>
                      ) : (
                        r.summary ?? ''
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <Dialog open={mappingDialog !== null} onOpenChange={(o) => { if (!o) setMappingDialog(null) }}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>得意先マッピング追加</DialogTitle>
          </DialogHeader>
          <div className="space-y-3">
            <div>
              <Label>Excel表記（alias）</Label>
              <Input value={mappingDialog?.alias ?? ''} disabled />
            </div>
            <div>
              <Label>MoneyForward正式名</Label>
              <Input value={mfName} onChange={(e) => setMfName(e.target.value)} placeholder="例: （株） ぬまご" />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setMappingDialog(null)}>キャンセル</Button>
            <Button
              disabled={!mfName || addMappingMutation.isPending}
              onClick={() => mappingDialog && addMappingMutation.mutate({ alias: mappingDialog.alias, mfClientName: mfName })}
            >
              {addMappingMutation.isPending ? '登録中...' : '登録＆再プレビュー'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}
