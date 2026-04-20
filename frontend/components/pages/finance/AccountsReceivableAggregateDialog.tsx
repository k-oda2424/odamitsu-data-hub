'use client'

import { useState, useEffect } from 'react'
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { RefreshCw } from 'lucide-react'
import { toast } from 'sonner'
import { type CutoffType, CUTOFF_TYPE_LABELS, toYyyyMmDd } from '@/types/accounts-receivable'

interface Props {
  open: boolean
  onOpenChange: (open: boolean) => void
  defaultTargetDate: string // yyyy-MM-dd
  onSubmit: (targetDate: string, cutoffType: CutoffType) => void
  submitting: boolean
}

/**
 * 売掛金集計バッチ起動ダイアログ。
 * 締め日タイプをラジオボタンで選択（15日/20日/月末/すべて）。
 */
export function AccountsReceivableAggregateDialog({
  open, onOpenChange, defaultTargetDate, onSubmit, submitting,
}: Props) {
  const [targetDate, setTargetDate] = useState(defaultTargetDate)
  const [cutoffType, setCutoffType] = useState<CutoffType>('all')

  useEffect(() => {
    if (open) {
      setTargetDate(defaultTargetDate)
      setCutoffType('all')
    }
  }, [open, defaultTargetDate])

  const handleSubmit = () => {
    if (!targetDate) {
      toast.error('対象日を指定してください')
      return
    }
    onSubmit(toYyyyMmDd(targetDate), cutoffType)
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>売掛金 再集計</DialogTitle>
        </DialogHeader>
        <div className="space-y-4 text-sm">
          <div>
            <Label htmlFor="target-date">対象日 (yyyy-MM-dd)</Label>
            <Input
              id="target-date"
              type="date"
              value={targetDate}
              onChange={(e) => setTargetDate(e.target.value)}
            />
            <p className="mt-1 text-xs text-muted-foreground">
              指定した日を基準に、選択した締め日タイプの期間を集計します
            </p>
          </div>
          <div>
            <Label htmlFor="cutoff-type">締め日タイプ</Label>
            <Select value={cutoffType} onValueChange={(v) => setCutoffType(v as CutoffType)}>
              <SelectTrigger id="cutoff-type" className="mt-1">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(Object.keys(CUTOFF_TYPE_LABELS) as CutoffType[]).map((t) => (
                  <SelectItem key={t} value={t}>
                    {CUTOFF_TYPE_LABELS[t]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="rounded border border-yellow-200 bg-yellow-50 p-2 text-xs text-yellow-900">
            ※ 手動確定済み（verified_manually=true）の行は上書きされません
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>キャンセル</Button>
          <Button onClick={handleSubmit} disabled={submitting}>
            <RefreshCw className="mr-1 h-4 w-4" />
            {submitting ? '実行中...' : '再集計実行'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
