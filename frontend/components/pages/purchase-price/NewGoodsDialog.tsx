'use client'

import { useEffect, useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useMakers } from '@/hooks/use-master-data'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { toast } from 'sonner'
import type { QuoteImportDetailResponse } from '@/types/quote-import'

interface NewGoodsDialogProps {
  importId: number
  detail: QuoteImportDetailResponse | null
  supplierNo: number | null
  supplierName: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
  onSuccess: () => void
}

export function NewGoodsDialog({
  importId,
  detail,
  supplierNo,
  supplierName,
  open,
  onOpenChange,
  onSuccess,
}: NewGoodsDialogProps) {
  const [goodsName, setGoodsName] = useState('')
  const [janCode, setJanCode] = useState('')
  const [makerNo, setMakerNo] = useState('')
  const [specification, setSpecification] = useState('')
  const [caseContainNum, setCaseContainNum] = useState('')
  const [applyReducedTaxRate, setApplyReducedTaxRate] = useState(false)
  const [goodsCode, setGoodsCode] = useState('')
  const [salesGoodsName, setSalesGoodsName] = useState('')
  const [purchasePrice, setPurchasePrice] = useState('')
  const [goodsPrice, setGoodsPrice] = useState('')

  const makersQuery = useMakers()

  // ダイアログが開いたとき、見積明細の情報でフォームを初期化
  useEffect(() => {
    if (open && detail) {
      setGoodsName(detail.quoteGoodsName)
      setJanCode(detail.janCode ?? '')
      setSpecification(detail.specification ?? '')
      setCaseContainNum(detail.quantityPerCase != null ? String(detail.quantityPerCase) : '')
      setGoodsCode(detail.janCode ?? '')
      setSalesGoodsName(detail.quoteGoodsName)
      setPurchasePrice(detail.newPrice != null ? String(detail.newPrice) : '')
      setGoodsPrice('')
      setApplyReducedTaxRate(false)

      // 仕入先名からメーカーを自動選択
      if (supplierName && makersQuery.data) {
        const matched = makersQuery.data.find((m) =>
          supplierName.includes(m.makerName) || m.makerName.includes(supplierName)
        )
        setMakerNo(matched ? String(matched.makerNo) : '')
      } else {
        setMakerNo('')
      }
    }
  }, [open, detail, supplierName, makersQuery.data])

  const createMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) =>
      api.post(`/quote-imports/${importId}/details/${detail?.quoteImportDetailId}/create-new`, data),
    onSuccess: () => {
      toast.success('新規商品を登録しました')
      onOpenChange(false)
      onSuccess()
    },
    onError: () => {
      toast.error('登録に失敗しました')
    },
  })

  const handleSubmit = () => {
    if (!goodsName.trim()) { toast.error('商品名は必須です'); return }
    if (!goodsCode.trim()) { toast.error('商品コードは必須です'); return }
    if (!purchasePrice) { toast.error('標準仕入単価は必須です'); return }
    if (!goodsPrice) { toast.error('標準売単価は必須です'); return }

    createMutation.mutate({
      goods: {
        goodsName,
        janCode: janCode || null,
        makerNo: makerNo ? Number(makerNo) : null,
        specification: specification || null,
        caseContainNum: caseContainNum ? Number(caseContainNum) : null,
        applyReducedTaxRate,
      },
      salesGoods: {
        goodsCode,
        goodsName: salesGoodsName || goodsName,
        purchasePrice: Number(purchasePrice),
        goodsPrice: Number(goodsPrice),
      },
    })
  }

  if (!detail) return null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-lg max-h-[80vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>新規商品登録</DialogTitle>
        </DialogHeader>
        <div className="space-y-4">
          <div className="text-sm text-muted-foreground bg-muted/50 rounded p-2 space-y-1">
            <div>見積商品: {detail.quoteGoodsName} ({detail.specification})</div>
            {detail.newPrice != null && <div>新価格: ¥{detail.newPrice.toLocaleString()}</div>}
            {supplierName && <div>仕入先: {supplierName}{supplierNo != null && ` (No.${supplierNo})`}</div>}
          </div>

          <h4 className="font-medium text-sm border-b pb-1">商品マスタ情報</h4>
          <div className="space-y-2">
            <Label>商品名 <span className="text-destructive">*</span></Label>
            <Input value={goodsName} onChange={(e) => setGoodsName(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>JANコード</Label>
            <Input value={janCode} onChange={(e) => setJanCode(e.target.value)} />
          </div>
          <div className="space-y-2">
            <Label>メーカー</Label>
            <SearchableSelect
              value={makerNo}
              onValueChange={setMakerNo}
              options={(makersQuery.data ?? []).map((m) => ({
                value: String(m.makerNo),
                label: m.makerName,
              }))}
              searchPlaceholder="メーカーを検索..."
            />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div className="space-y-2">
              <Label>規格</Label>
              <Input value={specification} onChange={(e) => setSpecification(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>入数</Label>
              <Input type="number" value={caseContainNum} onChange={(e) => setCaseContainNum(e.target.value)} />
            </div>
          </div>
          <div className="flex items-center space-x-2">
            <Checkbox
              id="reducedTax"
              checked={applyReducedTaxRate}
              onCheckedChange={(c) => setApplyReducedTaxRate(c === true)}
            />
            <Label htmlFor="reducedTax">軽減税率適用</Label>
          </div>

          <h4 className="font-medium text-sm border-b pb-1 pt-2">販売商品情報</h4>
          <div className="space-y-2">
            <Label>商品コード <span className="text-destructive">*</span></Label>
            <Input
              value={goodsCode}
              onChange={(e) => setGoodsCode(e.target.value)}
              placeholder="商品コードを入力（JANコードで仮登録可）"
            />
          </div>
          <div className="space-y-2">
            <Label>商品名</Label>
            <Input value={salesGoodsName} onChange={(e) => setSalesGoodsName(e.target.value)} />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <div className="space-y-2">
              <Label>標準仕入単価 <span className="text-destructive">*</span></Label>
              <Input type="number" value={purchasePrice} onChange={(e) => setPurchasePrice(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>標準売単価 <span className="text-destructive">*</span></Label>
              <Input type="number" value={goodsPrice} onChange={(e) => setGoodsPrice(e.target.value)} />
            </div>
          </div>

          <div className="flex justify-end gap-2 pt-2">
            <Button variant="outline" onClick={() => onOpenChange(false)}>キャンセル</Button>
            <Button onClick={handleSubmit} disabled={createMutation.isPending}>
              {createMutation.isPending ? '登録中...' : '登録'}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  )
}
