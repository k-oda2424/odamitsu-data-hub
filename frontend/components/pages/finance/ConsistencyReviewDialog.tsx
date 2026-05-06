'use client'

import { useEffect, useState } from 'react'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'

const NOTE_MAX_LENGTH = 500

export type ConsistencyReviewActionType = 'IGNORE' | 'MF_APPLY'

export interface ConsistencyReviewDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  /** ダイアログのタイトル (アクション種別の見出し)。 */
  actionLabel: string
  /** 補足説明 (副作用の有無、対象 supplier 名 等)。 */
  description?: string
  submitting?: boolean
  /** OK 押下時のコールバック。note は trim 後の文字列 (空可)。 */
  onConfirm: (note: string) => void
}

/**
 * 整合性レポート画面で「確認済」「MF確定」を実行する際の備考入力ダイアログ。
 * shadcn/ui Dialog + Textarea + 文字数カウンタ (max 500)。
 * `InvoiceImportDialog` と同じく Controlled Dialog として親が `open` を管理する。
 */
export function ConsistencyReviewDialog({
  open,
  onOpenChange,
  actionLabel,
  description,
  submitting = false,
  onConfirm,
}: ConsistencyReviewDialogProps) {
  const [note, setNote] = useState('')

  // ダイアログを開く度に note をリセット (前回入力の流用を防ぐ)
  useEffect(() => {
    if (open) {
      setNote('')
    }
  }, [open])

  const handleClose = () => {
    if (submitting) return
    onOpenChange(false)
  }

  const handleConfirm = () => {
    onConfirm(note.trim())
  }

  const remaining = NOTE_MAX_LENGTH - note.length
  const overLimit = note.length > NOTE_MAX_LENGTH

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) handleClose() }}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>{actionLabel}</DialogTitle>
          {description && <DialogDescription>{description}</DialogDescription>}
        </DialogHeader>
        <div className="space-y-2">
          <Label htmlFor="consistency-review-note">備考 (任意)</Label>
          <Textarea
            id="consistency-review-note"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="確認内容や補足を入力してください"
            rows={4}
            maxLength={NOTE_MAX_LENGTH}
            disabled={submitting}
          />
          <div className={`text-right text-xs ${overLimit ? 'text-destructive' : 'text-muted-foreground'}`}>
            {note.length} / {NOTE_MAX_LENGTH}
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={handleClose} disabled={submitting}>
            キャンセル
          </Button>
          <Button onClick={handleConfirm} disabled={submitting || overLimit}>
            {submitting ? '保存中...' : '確定'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
