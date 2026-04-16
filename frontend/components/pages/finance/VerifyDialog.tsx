'use client'

import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { formatCurrency } from '@/lib/utils'
import { Unlock } from 'lucide-react'
import type { AccountsPayable } from '@/types/accounts-payable'

interface Props {
  row: AccountsPayable | null
  onClose: () => void
  onSubmit: (verifiedAmount: number, note: string) => void
  onReleaseManualLock: () => void
  submitting: boolean
  releasing: boolean
  isAdmin: boolean
}

/**
 * 買掛金の手動検証ダイアログ。
 * <p>親の key 付与に依存せず、自身で {@code row} の変化を検知して amount/note を同期する
 * （以前は親の {@code key={rowKey ?? 'closed'}} による再マウントに暗黙依存していた）。
 */
export function VerifyDialog({
  row,
  onClose,
  onSubmit,
  onReleaseManualLock,
  submitting,
  releasing,
  isAdmin,
}: Props) {
  const rowKey = row
    ? `${row.shopNo}-${row.supplierNo}-${row.transactionMonth}-${String(row.taxRate)}`
    : null
  const prevKeyRef = useRef<string | null>(null)
  const [amount, setAmount] = useState<string>('')
  const [note, setNote] = useState<string>('')

  useEffect(() => {
    // row が切り替わる・初めて開かれる場合のみ初期値を反映する
    // （ユーザーが編集中の入力を不用意に上書きしないため、rowKey の変化のみをトリガーにする）。
    if (rowKey !== prevKeyRef.current) {
      prevKeyRef.current = rowKey
      setAmount(row
        ? String(row.taxIncludedAmount ?? row.taxIncludedAmountChange ?? '')
        : '')
      setNote(row?.verificationNote ?? '')
    }
  }, [rowKey, row])

  const open = row !== null
  const handleSubmit = () => {
    const v = Number(amount)
    if (!Number.isFinite(v)) {
      toast.error('金額が不正です')
      return
    }
    if (note.length > 500) {
      toast.error('備考は500字以内で入力してください')
      return
    }
    onSubmit(v, note)
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose() }}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>買掛金検証</DialogTitle>
        </DialogHeader>
        {row && (
          <div className="space-y-3 text-sm">
            <div className="grid grid-cols-2 gap-2">
              <div><span className="text-muted-foreground">仕入先: </span>{row.supplierCode} {row.supplierName ?? ''}</div>
              <div><span className="text-muted-foreground">税率: </span>{row.taxRate}%</div>
              <div><span className="text-muted-foreground">取引月: </span>{row.transactionMonth}</div>
              <div><span className="text-muted-foreground">ショップ: </span>{row.shopNo}</div>
            </div>
            <div className="rounded border p-2 space-y-1">
              <div className="flex justify-between"><span>買掛金額(税込)</span><span className="tabular-nums">{formatCurrency(row.taxIncludedAmountChange ?? 0)}</span></div>
              <div className="flex justify-between"><span>買掛金額(税抜)</span><span className="tabular-nums">{formatCurrency(row.taxExcludedAmountChange ?? 0)}</span></div>
              <div className="flex justify-between"><span>SMILE支払額(税込)</span><span className="tabular-nums">{row.taxIncludedAmount == null ? '-' : formatCurrency(row.taxIncludedAmount)}</span></div>
              <div className="flex justify-between">
                <span>差額</span>
                <span className={row.verificationResult === 0 ? 'text-red-600 tabular-nums' : 'tabular-nums'}>
                  {row.paymentDifference == null ? '-' : formatCurrency(row.paymentDifference)}
                </span>
              </div>
            </div>
            {row.verifiedManually && (
              <div className="rounded border border-green-300 bg-green-50 p-2 text-xs text-green-800">
                このレコードは手動確定済みです。次回 SMILE 再検証バッチで上書きされません。
              </div>
            )}
            <div>
              <Label htmlFor="verified-amount">検証済み支払額(税込)</Label>
              <Input
                id="verified-amount"
                type="number"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="verification-note">備考（最大500字）</Label>
              <Textarea
                id="verification-note"
                rows={3}
                maxLength={500}
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="例: 請求書No.A-001で確認"
              />
            </div>
          </div>
        )}
        <DialogFooter className="gap-2">
          {row?.verifiedManually && isAdmin && (
            <Button
              variant="outline"
              onClick={onReleaseManualLock}
              disabled={releasing || submitting}
              className="mr-auto"
            >
              <Unlock className="mr-1 h-4 w-4" />
              {releasing ? '解除中...' : '手動確定解除'}
            </Button>
          )}
          <Button variant="outline" onClick={onClose}>キャンセル</Button>
          <Button onClick={handleSubmit} disabled={submitting || !amount}>
            {submitting ? '更新中...' : '更新'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
