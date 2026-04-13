'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, useSuppliers, usePartners, useDestinations } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Checkbox } from '@/components/ui/checkbox'
import { Card, CardContent } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ArrowLeft, Save } from 'lucide-react'
import { toast } from 'sonner'
import type { PurchasePriceResponse, PurchasePriceCreateRequest } from '@/types/purchase-price'
import type { EstimateGoodsSearchResponse } from '@/types/estimate'

interface Props {
  purchasePriceNo?: number
}

export function PurchasePriceFormPage({ purchasePriceNo }: Props) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const isEditMode = purchasePriceNo != null

  const [shopNo, setShopNo] = useState(isAdmin ? '' : String(user?.shopNo ?? ''))
  useEffect(() => {
    if (!isAdmin && user?.shopNo && shopNo === '') {
      setShopNo(String(user.shopNo))
    }
  }, [isAdmin, user?.shopNo, shopNo])
  const [goodsNo, setGoodsNo] = useState<number | null>(null)
  const [goodsCode, setGoodsCode] = useState('')
  const [goodsName, setGoodsName] = useState('')
  const [supplierNo, setSupplierNo] = useState('')
  const [partnerNo, setPartnerNo] = useState('')
  const [destinationNo, setDestinationNo] = useState('')
  const [goodsPrice, setGoodsPrice] = useState('')
  const [taxRate, setTaxRate] = useState('10')
  const [includeTaxFlg, setIncludeTaxFlg] = useState(false)
  const [periodFrom, setPeriodFrom] = useState('')
  const [periodTo, setPeriodTo] = useState('')
  const [note, setNote] = useState('')
  const [initialized, setInitialized] = useState(!isEditMode)

  const shopsQuery = useShops(isAdmin)
  const suppliersQuery = useSuppliers(shopNo)
  const partnersQuery = usePartners(shopNo)
  const destinationsQuery = useDestinations(partnerNo)

  const detailQuery = useQuery({
    queryKey: ['purchase-price', purchasePriceNo],
    queryFn: () => api.get<PurchasePriceResponse>(`/purchase-prices/${purchasePriceNo}`),
    enabled: isEditMode,
  })

  useEffect(() => {
    if (!isEditMode || !detailQuery.data || initialized) return
    const d = detailQuery.data
    setShopNo(String(d.shopNo))
    setGoodsNo(d.goodsNo)
    setGoodsCode(d.goodsCode ?? '')
    setGoodsName(d.goodsName ?? '')
    setSupplierNo(String(d.supplierNo))
    setPartnerNo(d.partnerNo && d.partnerNo !== 0 ? String(d.partnerNo) : '')
    setDestinationNo(d.destinationNo && d.destinationNo !== 0 ? String(d.destinationNo) : '')
    setGoodsPrice(d.includeTaxFlg === '1' ? String(d.includeTaxGoodsPrice ?? '') : String(d.goodsPrice ?? ''))
    setTaxRate(String(d.taxRate ?? '10'))
    setIncludeTaxFlg(d.includeTaxFlg === '1')
    setPeriodFrom(d.periodFrom ?? '')
    setPeriodTo(d.periodTo ?? '')
    setNote(d.note ?? '')
    setInitialized(true)
  }, [isEditMode, detailQuery.data, initialized])

  const saveMutation = useMutation({
    mutationFn: (request: PurchasePriceCreateRequest) => {
      if (isEditMode) {
        return api.put<PurchasePriceResponse>(`/purchase-prices/${purchasePriceNo}`, request)
      }
      return api.post<PurchasePriceResponse>('/purchase-prices', request)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['purchase-prices'] })
      toast.success(isEditMode ? '仕入価格を更新しました' : '仕入価格を登録しました')
      router.push('/purchase-prices')
    },
    onError: (error) => {
      const message = error instanceof Error ? error.message : '保存に失敗しました'
      toast.error(message)
    },
  })

  const handleSave = () => {
    if (!shopNo) { toast.error('店舗を選択してください'); return }
    if (!goodsNo) { toast.error('商品を選択してください'); return }
    if (!supplierNo) { toast.error('仕入先を選択してください'); return }
    if (!goodsPrice) { toast.error('仕入単価を入力してください'); return }

    const request: PurchasePriceCreateRequest = {
      shopNo: Number(shopNo),
      goodsNo,
      supplierNo: Number(supplierNo),
      partnerNo: partnerNo ? Number(partnerNo) : null,
      destinationNo: destinationNo ? Number(destinationNo) : null,
      goodsPrice: Number(goodsPrice),
      taxRate: taxRate ? Number(taxRate) : null,
      includeTaxFlg,
      periodFrom: periodFrom || null,
      periodTo: periodTo || null,
      note: note || null,
    }
    saveMutation.mutate(request)
  }

  // 商品コードで商品検索
  const searchGoods = async (code: string) => {
    if (!code || !shopNo) return
    try {
      const result = await api.get<EstimateGoodsSearchResponse>(
        `/estimates/goods-search?shopNo=${shopNo}&code=${encodeURIComponent(code)}`
      )
      if (result) {
        setGoodsNo(result.goodsNo)
        setGoodsCode(result.goodsCode)
        setGoodsName(`${result.goodsName}${result.specification ? ' ' + result.specification : ''}`)
      }
    } catch {
      toast.error('商品が見つかりません')
    }
  }

  if (isEditMode && detailQuery.isLoading) return <LoadingSpinner />
  if (isEditMode && detailQuery.isError) return <ErrorMessage onRetry={() => detailQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title={isEditMode ? `仕入価格 #${purchasePriceNo} 編集` : '仕入価格 新規登録'}
        actions={
          <div className="flex gap-2">
            <Button onClick={handleSave} disabled={saveMutation.isPending}>
              <Save className="mr-2 h-4 w-4" />
              {saveMutation.isPending ? '保存中...' : '保存'}
            </Button>
            <Button variant="outline" onClick={() => router.back()}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              戻る
            </Button>
          </div>
        }
      />

      <Card>
        <CardContent className="pt-4">
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {isAdmin && (
              <div className="space-y-2">
                <Label>店舗 *</Label>
                <Select value={shopNo} onValueChange={setShopNo}>
                  <SelectTrigger>
                    <SelectValue placeholder="店舗を選択" />
                  </SelectTrigger>
                  <SelectContent>
                    {(shopsQuery.data ?? []).map((s) => (
                      <SelectItem key={s.shopNo} value={String(s.shopNo)}>{s.shopName}</SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}

            <div className="space-y-2">
              <Label>商品コード *</Label>
              <Input
                value={goodsCode}
                onChange={(e) => setGoodsCode(e.target.value)}
                onBlur={(e) => searchGoods(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter') searchGoods(goodsCode) }}
                placeholder="商品コード入力→自動検索"
              />
              {goodsName && (
                <p className="text-sm text-muted-foreground">{goodsName}</p>
              )}
            </div>

            <div className="space-y-2">
              <Label>仕入先 *</Label>
              <SearchableSelect
                value={supplierNo}
                onValueChange={setSupplierNo}
                options={(suppliersQuery.data ?? []).map((s) => ({
                  value: String(s.supplierNo),
                  label: `${s.supplierName}`,
                }))}
                placeholder="仕入先を���択"
                searchPlaceholder="仕入先を検索..."
              />
            </div>

            <div className="space-y-2">
              <Label>得意先（空欄=標準価格）</Label>
              <SearchableSelect
                value={partnerNo}
                onValueChange={(v) => {
                  setPartnerNo(v)
                  setDestinationNo('')
                }}
                options={(partnersQuery.data ?? []).map((p) => ({
                  value: String(p.partnerNo),
                  label: `${p.partnerCode} ${p.partnerName}`,
                }))}
                placeholder="標準価格"
                searchPlaceholder="得意先を検索..."
                clearable
              />
            </div>

            <div className="space-y-2">
              <Label>納品先</Label>
              <SearchableSelect
                value={destinationNo}
                onValueChange={setDestinationNo}
                options={(destinationsQuery.data ?? []).map((d) => ({
                  value: String(d.destinationNo),
                  label: d.destinationName,
                }))}
                placeholder="指定なし"
                searchPlaceholder="納品先を検索..."
                disabled={!partnerNo}
                clearable
              />
            </div>

            <div className="space-y-2">
              <Label>仕入単価 *</Label>
              <Input
                type="number"
                value={goodsPrice}
                onChange={(e) => setGoodsPrice(e.target.value)}
                placeholder="0"
              />
            </div>

            <div className="space-y-2">
              <Label>税率 (%)</Label>
              <Input
                type="number"
                value={taxRate}
                onChange={(e) => setTaxRate(e.target.value)}
                placeholder="10"
              />
            </div>

            <div className="space-y-2 flex items-end gap-2">
              <label className="flex items-center gap-2 h-9">
                <Checkbox
                  checked={includeTaxFlg}
                  onCheckedChange={(checked) => setIncludeTaxFlg(checked === true)}
                />
                <span className="text-sm">税込入力</span>
              </label>
            </div>

            <div className="space-y-2">
              <Label>適用開始日</Label>
              <Input
                type="date"
                value={periodFrom}
                onChange={(e) => setPeriodFrom(e.target.value)}
              />
            </div>

            <div className="space-y-2">
              <Label>適用終了日</Label>
              <Input
                type="date"
                value={periodTo}
                onChange={(e) => setPeriodTo(e.target.value)}
              />
            </div>

            <div className="space-y-2 md:col-span-2 lg:col-span-3">
              <Label>備考</Label>
              <Textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="備考"
                rows={2}
              />
            </div>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
