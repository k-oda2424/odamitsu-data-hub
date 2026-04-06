'use client'

import { useState, useCallback } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, useSuppliers, useWarehouses, useSalesGoodsBySupplier } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Plus, Trash2, ArrowLeft, Check } from 'lucide-react'
import { toast } from 'sonner'
import { useRouter } from 'next/navigation'
import { formatNumber } from '@/lib/utils'
import type { SendOrderResponse, SendOrderCreateRequest } from '@/types/send-order'

interface DetailRow {
  id: string
  goodsNo: number | null
  goodsCode: string
  goodsName: string
  goodsPrice: number
  sendOrderNum: number
  containNum: number | null
}

function createDetailRow(): DetailRow {
  return { id: crypto.randomUUID(), goodsNo: null, goodsCode: '', goodsName: '', goodsPrice: 0, sendOrderNum: 0, containNum: null }
}

function getNowDateTimeLocal(): string {
  const d = new Date()
  d.setMinutes(d.getMinutes() - d.getTimezoneOffset())
  return d.toISOString().slice(0, 16)
}

export function SendOrderCreatePage() {
  const router = useRouter()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const [step, setStep] = useState<'input' | 'confirm' | 'complete'>('input')
  const [createdOrder, setCreatedOrder] = useState<SendOrderResponse | null>(null)

  // Header fields
  const [shopNo, setShopNo] = useState<string>(isAdmin ? '' : String(user?.shopNo ?? ''))
  const [warehouseNo, setWarehouseNo] = useState<string>('')
  const [supplierNo, setSupplierNo] = useState<string>('')
  const [sendOrderDateTime, setSendOrderDateTime] = useState(getNowDateTimeLocal())
  const [desiredDeliveryDate, setDesiredDeliveryDate] = useState('')

  // Detail rows
  const [details, setDetails] = useState<DetailRow[]>([
    createDetailRow(),
  ])

  // Master data queries
  const shopsQuery = useShops(isAdmin)
  const suppliersQuery = useSuppliers(shopNo)
  const warehousesQuery = useWarehouses(undefined)
  const salesGoodsQuery = useSalesGoodsBySupplier(shopNo, supplierNo)

  const selectedSupplier = (suppliersQuery.data ?? []).find((s) => String(s.supplierNo) === supplierNo)
  const selectedWarehouse = (warehousesQuery.data ?? []).find((w) => String(w.warehouseNo) === warehouseNo)

  const handleShopChange = (value: string) => {
    setShopNo(value)
    setSupplierNo('')
    setWarehouseNo('')
    setDetails([createDetailRow()])
  }

  const handleSupplierChange = (value: string) => {
    setSupplierNo(value)
    setDetails([createDetailRow()])
  }

  const handleGoodsSelect = (index: number, goodsNoStr: string) => {
    const goods = (salesGoodsQuery.data ?? []).find((g) => String(g.goodsNo) === goodsNoStr)
    if (!goods) return
    setDetails((prev) => {
      const updated = [...prev]
      updated[index] = {
        ...updated[index],
        goodsNo: goods.goodsNo,
        goodsCode: goods.goodsCode,
        goodsName: goods.goodsName,
        goodsPrice: goods.purchasePrice ?? 0,
        sendOrderNum: updated[index].sendOrderNum || 1,
        containNum: null,
      }
      return updated
    })
  }

  const handleDetailChange = useCallback((index: number, field: keyof DetailRow, value: string | number) => {
    setDetails((prev) => {
      const updated = [...prev]
      updated[index] = { ...updated[index], [field]: value }
      return updated
    })
  }, [])

  const addDetailRow = () => {
    setDetails([...details, createDetailRow()])
  }

  const removeDetailRow = (index: number) => {
    if (details.length <= 1) return
    setDetails(details.filter((_, i) => i !== index))
  }

  const validDetails = details.filter((d) => d.goodsNo !== null && d.sendOrderNum > 0)
  const totalAmount = validDetails.reduce((sum, d) => sum + d.goodsPrice * d.sendOrderNum, 0)

  const canConfirm = shopNo && warehouseNo && supplierNo && sendOrderDateTime && validDetails.length > 0

  const createMutation = useMutation({
    mutationFn: (req: SendOrderCreateRequest) => api.post<SendOrderResponse>('/send-orders', req),
    onSuccess: (data) => {
      setCreatedOrder(data)
      setStep('complete')
      toast.success('発注を登録しました')
    },
    onError: () => {
      toast.error('発注の登録に失敗しました')
    },
  })

  const handleConfirm = () => {
    if (!canConfirm) return
    setStep('confirm')
  }

  const handleSubmit = () => {
    const request: SendOrderCreateRequest = {
      shopNo: Number(shopNo),
      warehouseNo: Number(warehouseNo),
      supplierNo: Number(supplierNo),
      sendOrderDateTime: sendOrderDateTime + ':00',
      desiredDeliveryDate: desiredDeliveryDate || null,
      details: validDetails.map((d) => ({
        goodsNo: d.goodsNo!,
        goodsCode: d.goodsCode,
        goodsName: d.goodsName,
        goodsPrice: d.goodsPrice,
        sendOrderNum: d.sendOrderNum,
        containNum: d.containNum,
      })),
    }
    createMutation.mutate(request)
  }

  const handleReset = () => {
    setStep('input')
    setCreatedOrder(null)
    setWarehouseNo('')
    setSupplierNo('')
    setSendOrderDateTime(getNowDateTimeLocal())
    setDesiredDeliveryDate('')
    setDetails([createDetailRow()])
  }

  // ===== STEP: INPUT =====
  if (step === 'input') {
    return (
      <div className="space-y-6">
        <PageHeader
          title="発注入力"
          actions={
            <Button variant="outline" onClick={() => router.push('/send-orders')}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              発注一覧
            </Button>
          }
        />

        <Card>
          <CardHeader>
            <CardTitle>発注情報</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid gap-4 max-w-2xl">
              {isAdmin && (
                <div className="space-y-2">
                  <Label>ショップ <span className="text-destructive">*</span></Label>
                  <Select value={shopNo} onValueChange={handleShopChange}>
                    <SelectTrigger>
                      <SelectValue placeholder="選択してください" />
                    </SelectTrigger>
                    <SelectContent>
                      {(shopsQuery.data ?? []).map((shop) => (
                        <SelectItem key={shop.shopNo} value={String(shop.shopNo)}>
                          {shop.shopName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
              )}
              <div className="space-y-2">
                <Label>倉庫 <span className="text-destructive">*</span></Label>
                <Select value={warehouseNo} onValueChange={setWarehouseNo}>
                  <SelectTrigger>
                    <SelectValue placeholder="選択してください" />
                  </SelectTrigger>
                  <SelectContent>
                    {(warehousesQuery.data ?? []).map((w) => (
                      <SelectItem key={w.warehouseNo} value={String(w.warehouseNo)}>
                        {w.warehouseName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label>仕入先 <span className="text-destructive">*</span></Label>
                <SearchableSelect
                  value={supplierNo}
                  onValueChange={handleSupplierChange}
                  options={(suppliersQuery.data ?? []).map((s) => ({
                    value: String(s.supplierNo),
                    label: `${s.supplierCode ?? ''} ${s.supplierName}`.trim(),
                  }))}
                  searchPlaceholder="仕入先を検索..."
                  disabled={!shopNo}
                />
              </div>
              <div className="grid grid-cols-2 gap-4">
                <div className="space-y-2">
                  <Label>発注日時 <span className="text-destructive">*</span></Label>
                  <Input
                    type="datetime-local"
                    value={sendOrderDateTime}
                    onChange={(e) => setSendOrderDateTime(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label>希望納期</Label>
                  <Input
                    type="date"
                    value={desiredDeliveryDate}
                    onChange={(e) => setDesiredDeliveryDate(e.target.value)}
                  />
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle className="flex items-center justify-between">
              発注明細
              <Button variant="outline" size="sm" onClick={addDetailRow}>
                <Plus className="mr-1 h-4 w-4" />
                行追加
              </Button>
            </CardTitle>
          </CardHeader>
          <CardContent>
            {!supplierNo ? (
              <div className="text-center text-muted-foreground py-4">
                仕入先を選択してください
              </div>
            ) : (
              <div className="space-y-3">
                {details.map((row, index) => (
                  <div key={row.id} className="flex items-end gap-3 border-b pb-3">
                    <div className="flex-1 space-y-1">
                      <Label className="text-xs">商品</Label>
                      <SearchableSelect
                        value={row.goodsNo ? String(row.goodsNo) : ''}
                        onValueChange={(v) => handleGoodsSelect(index, v)}
                        options={(salesGoodsQuery.data ?? []).map((g) => ({
                          value: String(g.goodsNo),
                          label: `${g.goodsCode} ${g.goodsName}`,
                        }))}
                        searchPlaceholder="商品を検索..."
                        clearable={false}
                      />
                    </div>
                    <div className="w-28 space-y-1">
                      <Label className="text-xs">仕入単価</Label>
                      <Input
                        type="number"
                        value={row.goodsPrice || ''}
                        onChange={(e) => handleDetailChange(index, 'goodsPrice', Number(e.target.value))}
                      />
                    </div>
                    <div className="w-24 space-y-1">
                      <Label className="text-xs">数量</Label>
                      <Input
                        type="number"
                        min={1}
                        value={row.sendOrderNum || ''}
                        onChange={(e) => handleDetailChange(index, 'sendOrderNum', Number(e.target.value))}
                      />
                    </div>
                    <div className="w-28 space-y-1">
                      <Label className="text-xs">小計</Label>
                      <div className="text-sm font-medium py-2">
                        {formatNumber(row.goodsPrice * row.sendOrderNum)}
                      </div>
                    </div>
                    <Button
                      variant="ghost"
                      size="icon"
                      onClick={() => removeDetailRow(index)}
                      disabled={details.length <= 1}
                    >
                      <Trash2 className="h-4 w-4" />
                    </Button>
                  </div>
                ))}
                <div className="text-right text-lg font-bold">
                  合計: {formatNumber(totalAmount)}円
                </div>
              </div>
            )}
          </CardContent>
        </Card>

        <div className="flex justify-end">
          <Button onClick={handleConfirm} disabled={!canConfirm}>
            確認画面へ
          </Button>
        </div>
      </div>
    )
  }

  // ===== STEP: CONFIRM =====
  if (step === 'confirm') {
    return (
      <div className="space-y-6">
        <PageHeader title="発注確認" />

        <Card>
          <CardHeader>
            <CardTitle>発注情報</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="grid gap-3 max-w-lg">
              <div className="flex justify-between">
                <span className="text-muted-foreground">仕入先</span>
                <span>{selectedSupplier?.supplierName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">倉庫</span>
                <span>{selectedWarehouse?.warehouseName}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-muted-foreground">発注日時</span>
                <span>{sendOrderDateTime.replace('T', ' ')}</span>
              </div>
              {desiredDeliveryDate && (
                <div className="flex justify-between">
                  <span className="text-muted-foreground">希望納期</span>
                  <span>{desiredDeliveryDate}</span>
                </div>
              )}
            </div>
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>発注明細</CardTitle>
          </CardHeader>
          <CardContent>
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b">
                  <th className="text-left py-2">商品コード</th>
                  <th className="text-left py-2">商品名</th>
                  <th className="text-right py-2">仕入単価</th>
                  <th className="text-right py-2">数量</th>
                  <th className="text-right py-2">小計</th>
                </tr>
              </thead>
              <tbody>
                {validDetails.map((d, i) => (
                  <tr key={i} className="border-b">
                    <td className="py-2">{d.goodsCode}</td>
                    <td className="py-2">{d.goodsName}</td>
                    <td className="text-right py-2">{formatNumber(d.goodsPrice)}</td>
                    <td className="text-right py-2">{formatNumber(d.sendOrderNum)}</td>
                    <td className="text-right py-2">{formatNumber(d.goodsPrice * d.sendOrderNum)}</td>
                  </tr>
                ))}
              </tbody>
              <tfoot>
                <tr>
                  <td colSpan={4} className="text-right py-2 font-bold">合計</td>
                  <td className="text-right py-2 font-bold">{formatNumber(totalAmount)}円</td>
                </tr>
              </tfoot>
            </table>
          </CardContent>
        </Card>

        <div className="flex justify-end gap-3">
          <Button variant="outline" onClick={() => setStep('input')}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            戻る
          </Button>
          <Button onClick={handleSubmit} disabled={createMutation.isPending}>
            <Check className="mr-2 h-4 w-4" />
            {createMutation.isPending ? '登録中...' : '発注登録'}
          </Button>
        </div>
      </div>
    )
  }

  // ===== STEP: COMPLETE =====
  return (
    <div className="space-y-6">
      <PageHeader title="発注完了" />

      <Card>
        <CardContent className="py-8 text-center space-y-4">
          <div className="text-green-600 text-xl font-bold">発注を登録しました</div>
          {createdOrder && (
            <div className="text-muted-foreground">
              発注番号: <span className="font-mono font-bold text-foreground">{createdOrder.sendOrderNo}</span>
            </div>
          )}
        </CardContent>
      </Card>

      <div className="flex justify-center gap-3">
        <Button variant="outline" onClick={() => router.push('/send-orders')}>
          発注一覧へ
        </Button>
        <Button onClick={handleReset}>
          続けて発注
        </Button>
      </div>
    </div>
  )
}
