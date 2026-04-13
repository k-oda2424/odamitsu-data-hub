'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
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
      toast.error(error.message)
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
