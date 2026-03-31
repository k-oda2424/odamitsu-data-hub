'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { toast } from 'sonner'
import type { PurchasePriceResponse } from '@/types/purchase-price'
import { CHANGE_REASON_OPTIONS } from '@/types/purchase-price'

interface PriceChangeDialogProps {
  purchasePrice: PurchasePriceResponse | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess: () => void
}

export function PriceChangeDialog({
  purchasePrice,
  open,
  onOpenChange,
  onSuccess,
}: PriceChangeDialogProps) {
  const [afterPrice, setAfterPrice] = useState('')
  const [changePlanDate, setChangePlanDate] = useState('')
  const [changeReason, setChangeReason] = useState('')

  const createMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) =>
      api.post('/purchase-price-changes', data),
    onSuccess: () => {
      toast.success('仕入価格変更予定を登録しました')
      onOpenChange(false)
      resetForm()
      onSuccess()
    },
    onError: () => {
      toast.error('登録に失敗しました')
    },
  })

  const resetForm = () => {
    setAfterPrice('')
    setChangePlanDate('')
    setChangeReason('')
  }

  const handleSubmit = () => {
    if (!afterPrice || !changePlanDate || !changeReason) {
      toast.error('変更後価格・変更予定日・変更理由は必須です')
      return
    }
    if (!purchasePrice) return
    createMutation.mutate({
      shopNo: purchasePrice.shopNo,
      goodsCode: purchasePrice.goodsCode,
      goodsName: purchasePrice.goodsName,
      supplierCode: purchasePrice.supplierCode,
      beforePrice: purchasePrice.goodsPrice,
      afterPrice: Number(afterPrice),
      changePlanDate,
      changeReason,
    })
  }

  if (!purchasePrice) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle>仕入価格変更予定入力</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="space-y-2">
            <Label>商品コード</Label>
            <div className="text-sm text-muted-foreground">{purchasePrice.goodsCode ?? '-'}</div>
          </div>
          <div className="space-y-2">
            <Label>商品名</Label>
            <div className="text-sm text-muted-foreground">{purchasePrice.goodsName ?? '-'}</div>
          </div>
          <div className="space-y-2">
            <Label>仕入先</Label>
            <div className="text-sm text-muted-foreground">{purchasePrice.supplierName ?? '-'}</div>
          </div>
          <div className="space-y-2">
            <Label>変更前価格</Label>
            <div className="text-sm font-medium">
              {purchasePrice.goodsPrice != null ? purchasePrice.goodsPrice.toLocaleString() : '-'}
            </div>
          </div>
          <div className="space-y-2">
            <Label>変更後価格 <span className="text-destructive">*</span></Label>
            <Input
              type="number"
              placeholder="変更後価格を入力"
              value={afterPrice}
              onChange={(e) => setAfterPrice(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label>変更予定日 <span className="text-destructive">*</span></Label>
            <Input
              type="date"
              value={changePlanDate}
              onChange={(e) => setChangePlanDate(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label>変更理由 <span className="text-destructive">*</span></Label>
            <Select value={changeReason} onValueChange={setChangeReason}>
              <SelectTrigger>
                <SelectValue placeholder="選択してください" />
              </SelectTrigger>
              <SelectContent>
                {CHANGE_REASON_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => onOpenChange(false)}>
              キャンセル
            </Button>
            <Button onClick={handleSubmit} disabled={createMutation.isPending}>
              {createMutation.isPending ? '登録中...' : '登録'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
