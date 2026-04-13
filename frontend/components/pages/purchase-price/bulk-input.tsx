'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, useSuppliers } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ArrowLeft, Plus, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { CHANGE_REASON_OPTIONS } from '@/types/purchase-price'

interface DetailRow {
  id: number
  goodsCode: string
  goodsName: string
  beforePrice: string
  afterPrice: string
}

let nextId = 1

export function PurchasePriceBulkInputPage() {
  const router = useRouter()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const [selectedShopNo, setSelectedShopNo] = useState<string>(
    isAdmin ? '' : String(user?.shopNo ?? '')
  )
  const [supplierNo, setSupplierNo] = useState<string>('')
  const [changePlanDate, setChangePlanDate] = useState('')
  const [changeReason, setChangeReason] = useState('')
  const [details, setDetails] = useState<DetailRow[]>([
    { id: nextId++, goodsCode: '', goodsName: '', beforePrice: '', afterPrice: '' },
  ])

  const effectiveShopNo = isAdmin ? selectedShopNo : String(user?.shopNo ?? '')
  const shopsQuery = useShops(isAdmin)
  const suppliersQuery = useSuppliers(effectiveShopNo)

  const selectedSupplier = (suppliersQuery.data ?? []).find(
    (s) => String(s.supplierNo) === supplierNo
  )

  const addRow = () => {
    setDetails([
      ...details,
      { id: nextId++, goodsCode: '', goodsName: '', beforePrice: '', afterPrice: '' },
    ])
  }

  const removeRow = (id: number) => {
    if (details.length <= 1) return
    setDetails(details.filter((d) => d.id !== id))
  }

  const updateDetail = (id: number, field: keyof DetailRow, value: string) => {
    setDetails(details.map((d) => (d.id === id ? { ...d, [field]: value } : d)))
  }

  const handleGoodsCodeBlur = async (id: number, goodsCode: string) => {
    if (!goodsCode || !effectiveShopNo || !supplierNo) return
    try {
      const prices = await api.get<Array<{ goodsCode: string; goodsName: string; goodsPrice: number }>>(
        `/purchase-prices?shopNo=${effectiveShopNo}&goodsCode=${goodsCode}&supplierNo=${supplierNo}`
      )
      if (prices.length > 0) {
        const price = prices[0]
        setDetails((prev) =>
          prev.map((d) =>
            d.id === id
              ? {
                  ...d,
                  goodsName: price.goodsName ?? '',
                  beforePrice: price.goodsPrice != null ? String(price.goodsPrice) : '',
                }
              : d
          )
        )
      }
    } catch {
      // API error - ignore, user can manually enter
    }
  }

  const bulkMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) =>
      api.post('/purchase-price-changes/bulk', data),
    onSuccess: () => {
      toast.success('仕入価格変更予定を一括登録しました')
      router.push('/purchase-prices/changes')
    },
    onError: () => {
      toast.error('一括登録に失敗しました')
    },
  })

  const handleSubmit = () => {
    if (!effectiveShopNo) {
      toast.error('店舗を選択してください')
      return
    }
    if (!supplierNo || !selectedSupplier) {
      toast.error('仕入先を選択してください')
      return
    }
    if (!changePlanDate) {
      toast.error('変更予定日を入力してください')
      return
    }
    if (!changeReason) {
      toast.error('変更理由を選択してください')
      return
    }
    const validDetails = details.filter((d) => d.goodsCode && d.afterPrice)
    if (validDetails.length === 0) {
      toast.error('明細を1件以上入力してください')
      return
    }

    bulkMutation.mutate({
      shopNo: Number(effectiveShopNo),
      supplierCode: selectedSupplier.supplierCode ?? '',
      changePlanDate,
      changeReason,
      details: validDetails.map((d) => ({
        goodsCode: d.goodsCode,
        goodsName: d.goodsName || null,
        beforePrice: d.beforePrice ? Number(d.beforePrice) : null,
        afterPrice: Number(d.afterPrice),
      })),
    })
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="仕入価格変更一括入力"
        actions={
          <Button variant="outline" onClick={() => router.back()}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            戻る
          </Button>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>ヘッダー情報</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 max-w-2xl md:grid-cols-2">
            {isAdmin && (
              <div className="space-y-2">
                <Label>店舗 <span className="text-destructive">*</span></Label>
                <Select value={selectedShopNo} onValueChange={setSelectedShopNo}>
                  <SelectTrigger>
                    <SelectValue placeholder="店舗を選択" />
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
              <Label>仕入先 <span className="text-destructive">*</span></Label>
              <SearchableSelect
                value={supplierNo}
                onValueChange={setSupplierNo}
                options={(suppliersQuery.data ?? []).map((s) => ({
                  value: String(s.supplierNo),
                  label: s.supplierName,
                }))}
                searchPlaceholder="仕入先を検索..."
                clearable={false}
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
                  {CHANGE_REASON_OPTIONS.map((opt) => (
                    <SelectItem key={opt.value} value={opt.value}>
                      {opt.label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>明細入力</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b">
                  <th className="px-2 py-2 text-left font-medium">商品コード</th>
                  <th className="px-2 py-2 text-left font-medium">商品名</th>
                  <th className="px-2 py-2 text-right font-medium">変更前価格</th>
                  <th className="px-2 py-2 text-right font-medium">変更後価格</th>
                  <th className="px-2 py-2 w-10"></th>
                </tr>
              </thead>
              <tbody>
                {details.map((row) => (
                  <tr key={row.id} className="border-b">
                    <td className="px-2 py-1">
                      <Input
                        placeholder="商品コード"
                        value={row.goodsCode}
                        onChange={(e) => updateDetail(row.id, 'goodsCode', e.target.value)}
                        onBlur={(e) => handleGoodsCodeBlur(row.id, e.target.value)}
                        className="h-8"
                      />
                    </td>
                    <td className="px-2 py-1">
                      <span className="text-muted-foreground">{row.goodsName || '-'}</span>
                    </td>
                    <td className="px-2 py-1 text-right">
                      <span className="text-muted-foreground">
                        {row.beforePrice ? Number(row.beforePrice).toLocaleString() : '-'}
                      </span>
                    </td>
                    <td className="px-2 py-1">
                      <Input
                        type="number"
                        placeholder="変更後価格"
                        value={row.afterPrice}
                        onChange={(e) => updateDetail(row.id, 'afterPrice', e.target.value)}
                        className="h-8 text-right"
                      />
                    </td>
                    <td className="px-2 py-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8"
                        onClick={() => removeRow(row.id)}
                        disabled={details.length <= 1}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="mt-4 flex gap-2">
            <Button variant="outline" onClick={addRow}>
              <Plus className="mr-2 h-4 w-4" />
              行追加
            </Button>
            <Button onClick={handleSubmit} disabled={bulkMutation.isPending}>
              {bulkMutation.isPending ? '登録中...' : '一括登録'}
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
