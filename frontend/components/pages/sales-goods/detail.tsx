'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { ArrowLeft, Pencil, Save, X, ArrowRightCircle } from 'lucide-react'
import { toast } from 'sonner'
import type { SalesGoodsDetailResponse, SalesGoodsUpdateRequest, Supplier } from '@/types/goods'

interface SalesGoodsDetailPageProps {
  shopNo: number
  goodsNo: number
  isWork: boolean
}

export function SalesGoodsDetailPage({ shopNo, goodsNo, isWork }: SalesGoodsDetailPageProps) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const base = isWork ? 'work' : 'master'

  const [isEditing, setIsEditing] = useState(false)
  const [goodsCode, setGoodsCode] = useState('')
  const [goodsSkuCode, setGoodsSkuCode] = useState('')
  const [goodsName, setGoodsName] = useState('')
  const [keyword, setKeyword] = useState('')
  const [supplierNo, setSupplierNo] = useState<string>('')
  const [referencePrice, setReferencePrice] = useState('')
  const [purchasePrice, setPurchasePrice] = useState('')
  const [goodsPrice, setGoodsPrice] = useState('')
  const [catchphrase, setCatchphrase] = useState('')
  const [goodsIntroduction, setGoodsIntroduction] = useState('')
  const [goodsDescription1, setGoodsDescription1] = useState('')
  const [goodsDescription2, setGoodsDescription2] = useState('')

  const detailQuery = useQuery({
    queryKey: ['sales-goods', base, shopNo, goodsNo],
    queryFn: () =>
      api.get<SalesGoodsDetailResponse>(`/sales-goods/${base}/${shopNo}/${goodsNo}`),
  })

  const suppliersQuery = useQuery({
    queryKey: ['suppliers', shopNo],
    queryFn: () => api.get<Supplier[]>(`/masters/suppliers?shopNo=${shopNo}`),
  })

  const updateMutation = useMutation({
    mutationFn: (data: SalesGoodsUpdateRequest) =>
      api.put<SalesGoodsDetailResponse>(`/sales-goods/${base}/${shopNo}/${goodsNo}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sales-goods', base, shopNo, goodsNo] })
      setIsEditing(false)
      toast.success('販売商品を更新しました')
    },
    onError: () => {
      toast.error('販売商品の更新に失敗しました')
    },
  })

  const reflectMutation = useMutation({
    mutationFn: () =>
      api.post<SalesGoodsDetailResponse>(`/sales-goods/work/${shopNo}/${goodsNo}/reflect`),
    onSuccess: () => {
      toast.success('マスタに反映しました')
      router.push('/sales-goods')
    },
    onError: () => {
      toast.error('マスタへの反映に失敗しました')
    },
  })

  const startEditing = () => {
    const data = detailQuery.data
    if (!data) return
    setGoodsCode(data.goodsCode ?? '')
    setGoodsSkuCode(data.goodsSkuCode ?? '')
    setGoodsName(data.goodsName ?? '')
    setKeyword(data.keyword ?? '')
    setSupplierNo(data.supplierNo != null ? String(data.supplierNo) : '')
    setReferencePrice(data.referencePrice != null ? String(data.referencePrice) : '')
    setPurchasePrice(data.purchasePrice != null ? String(data.purchasePrice) : '')
    setGoodsPrice(data.goodsPrice != null ? String(data.goodsPrice) : '')
    setCatchphrase(data.catchphrase ?? '')
    setGoodsIntroduction(data.goodsIntroduction ?? '')
    setGoodsDescription1(data.goodsDescription1 ?? '')
    setGoodsDescription2(data.goodsDescription2 ?? '')
    setIsEditing(true)
  }

  const cancelEditing = () => {
    setIsEditing(false)
  }

  const handleSave = () => {
    if (!goodsCode.trim()) {
      toast.error('商品コードは必須です')
      return
    }
    if (!goodsName.trim()) {
      toast.error('商品名は必須です')
      return
    }
    if (!supplierNo) {
      toast.error('仕入先は必須です')
      return
    }
    if (!purchasePrice) {
      toast.error('標準仕入単価は必須です')
      return
    }
    if (!goodsPrice) {
      toast.error('標準売単価は必須です')
      return
    }
    updateMutation.mutate({
      goodsCode,
      goodsSkuCode: goodsSkuCode || null,
      goodsName,
      keyword: keyword || null,
      supplierNo: Number(supplierNo),
      purchasePrice: Number(purchasePrice),
      goodsPrice: Number(goodsPrice),
      referencePrice: referencePrice ? Number(referencePrice) : null,
      catchphrase: catchphrase || null,
      goodsIntroduction: goodsIntroduction || null,
      goodsDescription1: goodsDescription1 || null,
      goodsDescription2: goodsDescription2 || null,
    })
  }

  if (detailQuery.isLoading) return <LoadingSpinner />
  if (detailQuery.isError) return <ErrorMessage onRetry={() => detailQuery.refetch()} />

  const data = detailQuery.data
  if (!data) return <ErrorMessage onRetry={() => detailQuery.refetch()} />

  const backPath = isWork ? '/sales-goods/work' : '/sales-goods'

  return (
    <div className="space-y-6">
      <PageHeader
        title={isWork ? '販売商品ワーク詳細' : '販売商品マスタ詳細'}
        actions={
          <div className="flex items-center gap-2">
            {isEditing ? (
              <>
                <Button onClick={handleSave} disabled={updateMutation.isPending}>
                  <Save className="mr-2 h-4 w-4" />
                  {updateMutation.isPending ? '保存中...' : '保存'}
                </Button>
                <Button variant="outline" onClick={cancelEditing}>
                  <X className="mr-2 h-4 w-4" />
                  キャンセル
                </Button>
              </>
            ) : (
              <>
                <Button variant="outline" onClick={startEditing}>
                  <Pencil className="mr-2 h-4 w-4" />
                  編集
                </Button>
                {isWork && (
                  <Button
                    onClick={() => reflectMutation.mutate()}
                    disabled={reflectMutation.isPending}
                  >
                    <ArrowRightCircle className="mr-2 h-4 w-4" />
                    {reflectMutation.isPending ? '反映中...' : 'マスタに反映'}
                  </Button>
                )}
              </>
            )}
            <Button variant="outline" onClick={() => router.push(backPath)}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              一覧に戻る
            </Button>
          </div>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>販売商品情報</CardTitle>
        </CardHeader>
        <CardContent>
          <Tabs defaultValue="basic" className="max-w-lg">
            <TabsList>
              <TabsTrigger value="basic">商品基本情報</TabsTrigger>
              <TabsTrigger value="price">価格情報</TabsTrigger>
              <TabsTrigger value="description">商品説明</TabsTrigger>
            </TabsList>

            <TabsContent value="basic" className="space-y-4">
              <div className="space-y-2">
                <Label>商品番号</Label>
                <div className="text-sm font-medium text-muted-foreground">{data.goodsNo}</div>
              </div>

              <div className="space-y-2">
                <Label>商品コード {isEditing && <span className="text-destructive">*</span>}</Label>
                {isEditing ? (
                  <Input value={goodsCode} onChange={(e) => setGoodsCode(e.target.value)} />
                ) : (
                  <div className="text-sm">{data.goodsCode || '-'}</div>
                )}
              </div>

              <div className="space-y-2">
                <Label>商品SKUコード</Label>
                {isEditing ? (
                  <Input value={goodsSkuCode} onChange={(e) => setGoodsSkuCode(e.target.value)} />
                ) : (
                  <div className="text-sm">{data.goodsSkuCode || '-'}</div>
                )}
              </div>

              <div className="space-y-2">
                <Label>商品名 {isEditing && <span className="text-destructive">*</span>}</Label>
                {isEditing ? (
                  <Input value={goodsName} onChange={(e) => setGoodsName(e.target.value)} />
                ) : (
                  <div className="text-sm">{data.goodsName}</div>
                )}
              </div>

              <div className="space-y-2">
                <Label>キーワード</Label>
                {isEditing ? (
                  <Input value={keyword} onChange={(e) => setKeyword(e.target.value)} />
                ) : (
                  <div className="text-sm">{data.keyword || '-'}</div>
                )}
              </div>

              <div className="space-y-2">
                <Label>仕入先 {isEditing && <span className="text-destructive">*</span>}</Label>
                {isEditing ? (
                  <SearchableSelect
                    value={supplierNo}
                    onValueChange={setSupplierNo}
                    options={(suppliersQuery.data ?? []).map((supplier) => ({
                      value: String(supplier.supplierNo),
                      label: supplier.supplierName,
                    }))}
                    searchPlaceholder="仕入先を検索..."
                    clearable={false}
                  />
                ) : (
                  <div className="text-sm">{data.supplierName || '-'}</div>
                )}
              </div>

              <div className="space-y-2">
                <Label>JANコード</Label>
                <div className="text-sm text-muted-foreground">{data.janCode || '-'}</div>
              </div>

              <div className="space-y-2">
                <Label>メーカー</Label>
                <div className="text-sm text-muted-foreground">{data.makerName || '-'}</div>
              </div>
            </TabsContent>

            <TabsContent value="price" className="space-y-4">
              <div className="space-y-2">
                <Label>参考価格</Label>
                {isEditing ? (
                  <Input
                    type="number"
                    value={referencePrice}
                    onChange={(e) => setReferencePrice(e.target.value)}
                  />
                ) : (
                  <div className="text-sm">
                    {data.referencePrice != null ? data.referencePrice.toLocaleString() : '-'}
                  </div>
                )}
              </div>

              <div className="space-y-2">
                <Label>
                  標準仕入単価 {isEditing && <span className="text-destructive">*</span>}
                </Label>
                {isEditing ? (
                  <Input
                    type="number"
                    value={purchasePrice}
                    onChange={(e) => setPurchasePrice(e.target.value)}
                  />
                ) : (
                  <div className="text-sm">
                    {data.purchasePrice != null ? data.purchasePrice.toLocaleString() : '-'}
                  </div>
                )}
              </div>

              <div className="space-y-2">
                <Label>
                  標準売単価 {isEditing && <span className="text-destructive">*</span>}
                </Label>
                {isEditing ? (
                  <Input
                    type="number"
                    value={goodsPrice}
                    onChange={(e) => setGoodsPrice(e.target.value)}
                  />
                ) : (
                  <div className="text-sm">
                    {data.goodsPrice != null ? data.goodsPrice.toLocaleString() : '-'}
                  </div>
                )}
              </div>
            </TabsContent>

            <TabsContent value="description" className="space-y-4">
              <div className="space-y-2">
                <Label>キャッチフレーズ</Label>
                {isEditing ? (
                  <Input
                    value={catchphrase}
                    onChange={(e) => setCatchphrase(e.target.value)}
                  />
                ) : (
                  <div className="text-sm">{data.catchphrase || '-'}</div>
                )}
              </div>

              <div className="space-y-2">
                <Label>商品概要</Label>
                {isEditing ? (
                  <Input
                    value={goodsIntroduction}
                    onChange={(e) => setGoodsIntroduction(e.target.value)}
                  />
                ) : (
                  <div className="text-sm">{data.goodsIntroduction || '-'}</div>
                )}
              </div>

              <div className="space-y-2">
                <Label>商品説明1</Label>
                {isEditing ? (
                  <Input
                    value={goodsDescription1}
                    onChange={(e) => setGoodsDescription1(e.target.value)}
                  />
                ) : (
                  <div className="text-sm">{data.goodsDescription1 || '-'}</div>
                )}
              </div>

              <div className="space-y-2">
                <Label>商品説明2</Label>
                {isEditing ? (
                  <Input
                    value={goodsDescription2}
                    onChange={(e) => setGoodsDescription2(e.target.value)}
                  />
                ) : (
                  <div className="text-sm">{data.goodsDescription2 || '-'}</div>
                )}
              </div>
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </div>
  )
}
