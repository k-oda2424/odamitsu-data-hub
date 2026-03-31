'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useMakers, useSuppliers } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Checkbox } from '@/components/ui/checkbox'
import { ArrowLeft } from 'lucide-react'
import { toast } from 'sonner'
import type { GoodsResponse } from '@/types/goods'
import {
  goodsFormSchema,
  salesGoodsFormSchema,
  type GoodsFormValues,
  type SalesGoodsFormValues,
} from '@/types/goods-schemas'

export function GoodsCreatePage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const { user } = useAuth()

  const [createdGoods, setCreatedGoods] = useState<GoodsResponse | null>(null)

  const goodsForm = useForm<GoodsFormValues>({
    resolver: zodResolver(goodsFormSchema),
    defaultValues: {
      goodsName: '',
      janCode: '',
      makerNo: '',
      caseContainNum: '',
      specification: '',
      keyword: '',
      applyReducedTaxRate: false,
    },
  })

  const salesForm = useForm<SalesGoodsFormValues>({
    resolver: zodResolver(salesGoodsFormSchema),
    defaultValues: {
      goodsCode: '',
      goodsSkuCode: '',
      goodsName: '',
      keyword: '',
      supplierNo: '',
      referencePrice: '',
      purchasePrice: '',
      goodsPrice: '',
      catchphrase: '',
      goodsIntroduction: '',
      goodsDescription1: '',
      goodsDescription2: '',
    },
  })

  const makersQuery = useMakers()
  const suppliersQuery = useSuppliers(createdGoods ? user?.shopNo : undefined)

  const createGoodsMutation = useMutation({
    mutationFn: (values: GoodsFormValues) =>
      api.post<GoodsResponse>('/goods', {
        goodsName: values.goodsName,
        janCode: values.janCode || null,
        makerNo: values.makerNo ? Number(values.makerNo) : null,
        caseContainNum: values.caseContainNum ? Number(values.caseContainNum) : null,
        specification: values.specification || null,
        keyword: values.keyword || null,
        applyReducedTaxRate: values.applyReducedTaxRate,
      }),
    onSuccess: (data) => {
      setCreatedGoods(data)
      salesForm.setValue('goodsName', data.goodsName)
      salesForm.setValue('keyword', data.keyword ?? '')
      queryClient.invalidateQueries({ queryKey: ['goods'] })
      toast.success('商品マスタに登録しました')
    },
    onError: () => {
      toast.error('商品マスタの登録に失敗しました')
    },
  })

  const createSalesGoodsMutation = useMutation({
    mutationFn: (values: SalesGoodsFormValues) => {
      if (!createdGoods) throw new Error('商品マスタが未作成です')
      return api.post(`/goods/${createdGoods.goodsNo}/sales-goods`, {
        shopNo: user?.shopNo,
        goodsNo: createdGoods.goodsNo,
        goodsCode: values.goodsCode,
        goodsSkuCode: values.goodsSkuCode || null,
        goodsName: values.goodsName,
        keyword: values.keyword || null,
        supplierNo: Number(values.supplierNo),
        referencePrice: values.referencePrice ? Number(values.referencePrice) : null,
        purchasePrice: Number(values.purchasePrice),
        goodsPrice: Number(values.goodsPrice),
        catchphrase: values.catchphrase || null,
        goodsIntroduction: values.goodsIntroduction || null,
        goodsDescription1: values.goodsDescription1 || null,
        goodsDescription2: values.goodsDescription2 || null,
      })
    },
    onSuccess: () => {
      toast.success('販売商品マスタに登録しました')
    },
    onError: () => {
      toast.error('販売商品マスタの登録に失敗しました')
    },
  })

  const handleStep1Submit = goodsForm.handleSubmit((values) => {
    createGoodsMutation.mutate(values)
  })

  const handleStep2Submit = salesForm.handleSubmit((values) => {
    createSalesGoodsMutation.mutate(values)
  })

  const gErr = goodsForm.formState.errors
  const sErr = salesForm.formState.errors

  return (
    <div className="space-y-6">
      <PageHeader
        title="商品マスタ登録"
        actions={
          <Button variant="outline" onClick={() => router.push('/goods')}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            一覧に戻る
          </Button>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>商品マスタ情報</CardTitle>
        </CardHeader>
        <CardContent>
          <form onSubmit={handleStep1Submit} className="grid gap-4 max-w-lg">
            {createdGoods && (
              <div className="space-y-2">
                <Label>商品番号</Label>
                <div className="text-sm font-medium text-muted-foreground">{createdGoods.goodsNo}</div>
              </div>
            )}
            <div className="space-y-2">
              <Label>商品名 <span className="text-destructive">*</span></Label>
              <Input
                placeholder="商品名を入力してください"
                {...goodsForm.register('goodsName')}
                disabled={!!createdGoods}
              />
              {gErr.goodsName && <p className="text-sm text-destructive">{gErr.goodsName.message}</p>}
            </div>
            <div className="space-y-2">
              <Label>JANコード</Label>
              <Input
                placeholder="JANコードを入力してください"
                {...goodsForm.register('janCode')}
                disabled={!!createdGoods}
              />
            </div>
            <div className="space-y-2">
              <Label>メーカー</Label>
              <SearchableSelect
                value={goodsForm.watch('makerNo')}
                onValueChange={(v) => goodsForm.setValue('makerNo', v)}
                options={(makersQuery.data ?? []).map((maker) => ({
                  value: String(maker.makerNo),
                  label: maker.makerName,
                }))}
                searchPlaceholder="メーカーを検索..."
                disabled={!!createdGoods}
              />
            </div>
            <div className="space-y-2">
              <Label>入数</Label>
              <Input
                type="number"
                placeholder="入数を入力してください"
                {...goodsForm.register('caseContainNum')}
                disabled={!!createdGoods}
              />
            </div>
            <div className="space-y-2">
              <Label>仕様</Label>
              <Input
                placeholder="仕様を入力してください"
                {...goodsForm.register('specification')}
                disabled={!!createdGoods}
              />
            </div>
            <div className="space-y-2">
              <Label>キーワード</Label>
              <Input
                placeholder="キーワードを入力してください"
                {...goodsForm.register('keyword')}
                disabled={!!createdGoods}
              />
            </div>
            <div className="flex items-center space-x-2">
              <Checkbox
                id="reducedTax"
                checked={goodsForm.watch('applyReducedTaxRate')}
                onCheckedChange={(checked) => goodsForm.setValue('applyReducedTaxRate', checked === true)}
                disabled={!!createdGoods}
              />
              <Label htmlFor="reducedTax">軽減税率適用する</Label>
            </div>
            {!createdGoods && (
              <div>
                <Button type="submit" disabled={createGoodsMutation.isPending}>
                  {createGoodsMutation.isPending ? '登録中...' : '登録'}
                </Button>
              </div>
            )}
          </form>
        </CardContent>
      </Card>

      {createdGoods && (
        <Card>
          <CardHeader>
            <CardTitle>販売商品マスタ登録（任意）</CardTitle>
          </CardHeader>
          <CardContent>
            <form onSubmit={handleStep2Submit}>
              <Tabs defaultValue="basic" className="max-w-lg">
                <TabsList>
                  <TabsTrigger value="basic">商品基本情報</TabsTrigger>
                  <TabsTrigger value="price">価格情報</TabsTrigger>
                  <TabsTrigger value="description">商品説明</TabsTrigger>
                </TabsList>

                <TabsContent value="basic" className="space-y-4">
                  <div className="space-y-2">
                    <Label>商品名 <span className="text-destructive">*</span></Label>
                    <Input placeholder="商品名を入力してください" {...salesForm.register('goodsName')} />
                    {sErr.goodsName && <p className="text-sm text-destructive">{sErr.goodsName.message}</p>}
                  </div>
                  <div className="space-y-2">
                    <Label>商品コード <span className="text-destructive">*</span></Label>
                    <Input placeholder="商品コードを入力してください" {...salesForm.register('goodsCode')} />
                    {sErr.goodsCode && <p className="text-sm text-destructive">{sErr.goodsCode.message}</p>}
                  </div>
                  <div className="space-y-2">
                    <Label>商品SKUコード</Label>
                    <Input placeholder="商品SKUコードを入力してください" {...salesForm.register('goodsSkuCode')} />
                  </div>
                  <div className="space-y-2">
                    <Label>キーワード</Label>
                    <Input placeholder="キーワードを入力してください" {...salesForm.register('keyword')} />
                  </div>
                  <div className="space-y-2">
                    <Label>仕入先 <span className="text-destructive">*</span></Label>
                    <SearchableSelect
                      value={salesForm.watch('supplierNo')}
                      onValueChange={(v) => salesForm.setValue('supplierNo', v)}
                      options={(suppliersQuery.data ?? []).map((supplier) => ({
                        value: String(supplier.supplierNo),
                        label: supplier.supplierName,
                      }))}
                      searchPlaceholder="仕入先を検索..."
                      clearable={false}
                    />
                    {sErr.supplierNo && <p className="text-sm text-destructive">{sErr.supplierNo.message}</p>}
                  </div>
                </TabsContent>

                <TabsContent value="price" className="space-y-4">
                  <div className="space-y-2">
                    <Label>参考価格</Label>
                    <Input type="number" placeholder="参考価格を入力してください" {...salesForm.register('referencePrice')} />
                  </div>
                  <div className="space-y-2">
                    <Label>標準仕入単価 <span className="text-destructive">*</span></Label>
                    <Input type="number" placeholder="標準仕入単価を入力してください" {...salesForm.register('purchasePrice')} />
                    {sErr.purchasePrice && <p className="text-sm text-destructive">{sErr.purchasePrice.message}</p>}
                  </div>
                  <div className="space-y-2">
                    <Label>標準売単価 <span className="text-destructive">*</span></Label>
                    <Input type="number" placeholder="標準売単価を入力してください" {...salesForm.register('goodsPrice')} />
                    {sErr.goodsPrice && <p className="text-sm text-destructive">{sErr.goodsPrice.message}</p>}
                  </div>
                </TabsContent>

                <TabsContent value="description" className="space-y-4">
                  <div className="space-y-2">
                    <Label>キャッチフレーズ</Label>
                    <Input placeholder="キャッチフレーズを入力してください" {...salesForm.register('catchphrase')} />
                  </div>
                  <div className="space-y-2">
                    <Label>商品概要</Label>
                    <Input placeholder="商品概要を入力してください" {...salesForm.register('goodsIntroduction')} />
                  </div>
                  <div className="space-y-2">
                    <Label>商品説明1</Label>
                    <Input placeholder="商品説明1を入力してください" {...salesForm.register('goodsDescription1')} />
                  </div>
                  <div className="space-y-2">
                    <Label>商品説明2</Label>
                    <Input placeholder="商品説明2を入力してください" {...salesForm.register('goodsDescription2')} />
                  </div>
                </TabsContent>
              </Tabs>

              <div className="mt-4 flex gap-2">
                <Button type="submit" disabled={createSalesGoodsMutation.isPending || createSalesGoodsMutation.isSuccess}>
                  {createSalesGoodsMutation.isPending ? '登録中...' : '販売商品マスタに登録'}
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => router.push(`/goods/${createdGoods.goodsNo}`)}
                >
                  商品マスタ詳細を見る
                </Button>
              </div>
            </form>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
