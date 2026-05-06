'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { AlertTriangle } from 'lucide-react'
import { api } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { toast } from 'sonner'

interface InvoiceImportResult {
  closingDate: string
  shopNo: number
  totalRows: number
  insertedRows: number
  updatedRows: number
  skippedRows: number
  errors: string[]
}

interface InvoiceImportDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
}

// SF-23: サーバ message を業務向け補足文にマッピング
// マッチしない message はそのまま表示（後方互換）
const ERROR_MESSAGE_HINTS: Array<{ pattern: RegExp; hint: string }> = [
  {
    pattern: /締日|closingDate|closing_date/i,
    hint: 'Excel の 2 行目（A 列）に「YYYY/MM/末」または「YYYY/MM/DD」形式で締日が記載されているかご確認ください。',
  },
  {
    pattern: /partnerCode|partner_code|得意先コード/i,
    hint: '得意先コード列（B 列）に空白や全角文字、6 桁を超える値が混入していないかご確認ください。',
  },
  {
    pattern: /sheet|シート/i,
    hint: 'Sheet1 が存在しない、またはシート名が変更されている可能性があります。',
  },
  {
    pattern: /xlsx|excel|format/i,
    hint: '.xlsx 形式の Excel ファイルをご利用ください（.xls / .csv は未対応）。',
  },
  {
    pattern: /shopNo|shop_no|事業部/i,
    hint: 'ファイル名に「松山」を含む場合のみ第 2 事業部として取り込みます。命名規則をご確認ください。',
  },
]

function buildErrorMessage(rawMessage: string | undefined): string {
  if (!rawMessage) return 'インポートに失敗しました。Excel の内容をご確認ください。'
  const matched = ERROR_MESSAGE_HINTS.find((entry) => entry.pattern.test(rawMessage))
  if (matched) return `${matched.hint}\n（詳細: ${rawMessage}）`
  return `インポートに失敗しました: ${rawMessage}`
}

export function InvoiceImportDialog({ open, onOpenChange }: InvoiceImportDialogProps) {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [result, setResult] = useState<InvoiceImportResult | null>(null)

  const mutation = useMutation({
    mutationFn: async (f: File) => {
      const formData = new FormData()
      formData.append('file', f)
      return api.postForm<InvoiceImportResult>('/finance/invoices/import', formData)
    },
    onSuccess: (r) => {
      setResult(r)
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      toast.success(`インポート完了: ${r.insertedRows}件追加, ${r.updatedRows}件更新`)
    },
    onError: (error: Error) => {
      toast.error(buildErrorMessage(error.message))
    },
  })

  const handleClose = () => {
    onOpenChange(false)
    setFile(null)
    setResult(null)
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) handleClose() }}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>請求実績インポート</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          {!result ? (
            <>
              <div className="space-y-2">
                <Label>Excelファイル（.xlsx）</Label>
                <Input
                  type="file"
                  accept=".xlsx"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)}
                />
                <p className="text-xs text-muted-foreground">
                  SMILEの請求実績Excel（Sheet1）を選択してください。ファイル名に「松山」を含む場合は第2事業部として取り込みます。
                </p>
              </div>
              <div className="flex justify-end gap-2">
                <Button variant="outline" onClick={handleClose}>キャンセル</Button>
                <Button onClick={() => file && mutation.mutate(file)} disabled={!file || mutation.isPending}>
                  {mutation.isPending ? '取込中...' : '取込実行'}
                </Button>
              </div>
            </>
          ) : (
            <>
              <div className="rounded-lg border p-4 space-y-2 text-sm">
                <div className="flex justify-between"><span className="text-muted-foreground">締日</span><span className="font-medium">{result.closingDate}</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">事業部</span><span className="font-medium">{result.shopNo === 1 ? '第1事業部' : '第2事業部（松山）'}</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">処理行数</span><span className="font-medium">{result.totalRows}件</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">新規追加</span><span className="font-medium text-green-600">{result.insertedRows}件</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">更新</span><span className="font-medium text-blue-600">{result.updatedRows}件</span></div>
                <div className="flex justify-between"><span className="text-muted-foreground">スキップ</span><span className="font-medium text-gray-500">{result.skippedRows}件</span></div>
              </div>
              {/* M-N6: SF-13 で集約した errors をユーザーに可視化（スキップ理由 / silent zero 補正等） */}
              {result.errors && result.errors.length > 0 && (
                <div className="rounded-lg border border-amber-200 bg-amber-50 p-3 space-y-2">
                  <div className="flex items-center gap-2 text-sm font-medium text-amber-900">
                    <AlertTriangle className="h-4 w-4" />
                    取込時に検出された問題 ({result.errors.length} 件)
                  </div>
                  <p className="text-xs text-amber-800">
                    以下の行はスキップ、または 0 円で取り込まれました。Excel の該当行を確認してください。
                  </p>
                  <ul className="space-y-1 text-xs text-amber-800 max-h-40 overflow-y-auto font-mono">
                    {result.errors.map((err, i) => (
                      <li key={i} className="break-all">{err}</li>
                    ))}
                  </ul>
                </div>
              )}
              <div className="flex justify-end">
                <Button onClick={handleClose}>閉じる</Button>
              </div>
            </>
          )}
        </div>
      </DialogContent>
    </Dialog>
  )
}
