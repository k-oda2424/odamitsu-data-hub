'use client'

import { useEffect, useRef, useState } from 'react'
import { toast } from 'sonner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Switch } from '@/components/ui/switch'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog'
import { formatCurrency } from '@/lib/utils'
import { Unlock } from 'lucide-react'
import type { AccountsReceivable } from '@/types/accounts-receivable'

interface Props {
  row: AccountsReceivable | null
  onClose: () => void
  onSubmit: (taxIncludedAmount: number, taxExcludedAmount: number, note: string, mfExportEnabled: boolean) => void
  onReleaseManualLock: () => void
  submitting: boolean
  releasing: boolean
  isAdmin: boolean
}

/**
 * 売掛金の手動確定ダイアログ。買掛側 VerifyDialog と対称。
 * 税込・税抜を個別に入力できる（売掛は税率別に行が分かれているため）。
 */
export function AccountsReceivableVerifyDialog({
  row,
  onClose,
  onSubmit,
  onReleaseManualLock,
  submitting,
  releasing,
  isAdmin,
}: Props) {
  const rowKey = row
    ? `${row.shopNo}-${row.partnerNo}-${row.transactionMonth}-${String(row.taxRate)}-${row.isOtakeGarbageBag}`
    : null
  const prevKeyRef = useRef<string | null>(null)
  const [taxIncluded, setTaxIncluded] = useState<string>('')
  const [taxExcluded, setTaxExcluded] = useState<string>('')
  const [note, setNote] = useState<string>('')
  const [mfExport, setMfExport] = useState<boolean>(true)

  useEffect(() => {
    if (rowKey !== prevKeyRef.current) {
      prevKeyRef.current = rowKey
      if (row) {
        // 請求書金額があればそれを初期値に（差額を吸収する定番操作）
        const defaultInc = row.invoiceAmount ?? row.taxIncludedAmount ?? row.taxIncludedAmountChange ?? 0
        const defaultExc = row.taxExcludedAmount ?? row.taxExcludedAmountChange ?? 0
        setTaxIncluded(String(defaultInc))
        setTaxExcluded(String(defaultExc))
        setNote(row.verificationNote ?? '')
        // F-W9: 現行値を尊重。既存行が代引等で OFF ならそのまま、未設定は true デフォルト。
        setMfExport(row.mfExportEnabled ?? true)
        // NOTE: `?? true` を維持 (完全 false デフォルトにすると通常ケースで checkbox OFF になり UX 悪化)。
        // row.mfExportEnabled が false で保存されている行は確実に OFF で開く。
      } else {
        setTaxIncluded('')
        setTaxExcluded('')
        setNote('')
        setMfExport(true)
      }
    }
  }, [rowKey, row])

  const open = row !== null

  const handleSubmit = () => {
    const inc = Number(taxIncluded)
    const exc = Number(taxExcluded)
    if (!Number.isFinite(inc) || !Number.isFinite(exc)) {
      toast.error('金額が不正です')
      return
    }
    if (note.length > 500) {
      toast.error('備考は500字以内で入力してください')
      return
    }
    onSubmit(inc, exc, note, mfExport)
  }

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) onClose() }}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle>売掛金 検証確定</DialogTitle>
        </DialogHeader>
        {row && (
          <div className="space-y-3 text-sm">
            <div className="grid grid-cols-2 gap-2">
              <div><span className="text-muted-foreground">得意先: </span>{row.partnerCode} {row.partnerName ?? ''}</div>
              <div><span className="text-muted-foreground">税率: </span>{Number(row.taxRate)}%</div>
              <div><span className="text-muted-foreground">取引月: </span>{row.transactionMonth}</div>
              <div><span className="text-muted-foreground">ショップ: </span>{row.shopNo}</div>
              {row.isOtakeGarbageBag && (
                <div className="col-span-2"><span className="text-muted-foreground">種別: </span>大竹市ゴミ袋</div>
              )}
              {row.orderNo != null && (
                <div><span className="text-muted-foreground">注文No: </span>{row.orderNo}</div>
              )}
            </div>
            <div className="rounded border p-2 space-y-1">
              <div className="flex justify-between"><span>集計金額(税込)</span><span className="tabular-nums">{formatCurrency(row.taxIncludedAmountChange ?? 0)}</span></div>
              <div className="flex justify-between"><span>集計金額(税抜)</span><span className="tabular-nums">{formatCurrency(row.taxExcludedAmountChange ?? 0)}</span></div>
              <div className="flex justify-between"><span>請求書金額(税込)</span><span className="tabular-nums">{row.invoiceAmount == null ? '-' : formatCurrency(row.invoiceAmount)}</span></div>
              <div className="flex justify-between">
                <span>差額</span>
                <span className={row.verificationResult === 0 ? 'text-red-600 tabular-nums' : 'tabular-nums'}>
                  {row.verificationDifference == null ? '-' : formatCurrency(row.verificationDifference)}
                </span>
              </div>
            </div>
            {row.verifiedManually && (
              <div className="rounded border border-green-300 bg-green-50 p-2 text-xs text-green-800">
                このレコードは手動確定済みです。次回 再集計・再検証で上書きされません。
              </div>
            )}
            <div>
              <Label htmlFor="tax-included">確定金額(税込)</Label>
              <Input
                id="tax-included"
                type="number"
                value={taxIncluded}
                onChange={(e) => setTaxIncluded(e.target.value)}
              />
            </div>
            <div>
              <Label htmlFor="tax-excluded">確定金額(税抜)</Label>
              <Input
                id="tax-excluded"
                type="number"
                value={taxExcluded}
                onChange={(e) => setTaxExcluded(e.target.value)}
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
            <div className="flex items-center gap-2">
              <Switch id="mf-export" checked={mfExport} onCheckedChange={setMfExport} />
              <Label htmlFor="mf-export" className="cursor-pointer">MF CSV出力対象にする</Label>
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
          <Button onClick={handleSubmit} disabled={submitting || !taxIncluded || !taxExcluded}>
            {submitting ? '更新中...' : '確定'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}
