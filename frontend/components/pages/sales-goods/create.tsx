'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { SearchForm } from '@/components/features/common/SearchForm'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { ArrowLeft, ArrowRightCircle } from 'lucide-react'
import { toast } from 'sonner'
import type { GoodsResponse, Maker, Supplier, SalesGoodsDetailResponse } from '@/types/goods'

const goodsColumns: Column<GoodsResponse>[] = [
  { key: 'goodsNo', header: '商品No', sortable: true },
  { key: 'goodsName', header: '商品名', sortable: true },
  { key: 'janCode', header: 'JANコード' },
  { key: 'makerName', header: 'メーカー' },
  { key: 'keyword', header: 'キーワード' },
  { key: 'specification', header: '規格' },
]

export function SalesGoodsCreatePage() {
  const router = useRouter()
  const { user } = useAuth()

  // Step control
  const [selectedGoods, setSelectedGoods] = useState<GoodsResponse | null>(null)

  // Step 1 search state
  const [searchGoodsName, setSearchGoodsName] = useState('')
  const [searchKeyword, setSearchKeyword] = useState('')
  const [searchJanCode, setSearchJanCode] = useState('')
  const [searchMakerNo, setSearchMakerNo] = useState<string>('')
  const [searchParams, setSearchParams] = useState<Record<string, string>>({})

  // Step 2 form state
  const [goodsCode, setGoodsCode] = useState('')
  const [goodsSkuCode, setGoodsSkuCode] = useState('')
  const [salesGoodsName, setSalesGoodsName] = useState('')
  const [salesKeyword, setSalesKeyword] = useState('')
  const [supplierNo, setSupplierNo] = useState<string>('')
  const [referencePrice, setReferencePrice] = useState('')
  const [purchasePrice, setPurchasePrice] = useState('')
  const [goodsPrice, setGoodsPrice] = useState('')
  const [catchphrase, setCatchphrase] = useState('')
  const [goodsIntroduction, setGoodsIntroduction] = useState('')
  const [goodsDescription1, setGoodsDescription1] = useState('')
  const [goodsDescription2, setGoodsDescription2] = useState('')

  const makersQuery = useQuery({
    queryKey: ['makers'],
    queryFn: () => api.get<Maker[]>('/masters/makers'),
  })

  const suppliersQuery = useQuery({
    queryKey: ['suppliers', user?.shopNo],
    queryFn: () => api.get<Supplier[]>(`/masters/suppliers?shopNo=${user?.shopNo}`),
    enabled: !!user?.shopNo && !!selectedGoods,
  })

  const availableGoodsQuery = useQuery({
    queryKey: ['goods-available-for-sales', user?.shopNo, searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      params.append('shopNo', String(user!.shopNo))
      if (searchParams.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams.keyword) params.append('keyword', searchParams.keyword)
      if (searchParams.janCode) params.append('janCode', searchParams.janCode)
      if (searchParams.makerNo) params.append('makerNo', searchParams.makerNo)
      return api.get<GoodsResponse[]>(`/goods/available-for-sales?${params.toString()}`)
    },
    enabled: !!user?.shopNo && !selectedGoods,
  })

  const createWorkMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) =>
      api.post<SalesGoodsDetailResponse>('/sales-goods/work', data),
  })

  const reflectMutation = useMutation({
    mutationFn: ({ shopNo, goodsNo }: { shopNo: number; goodsNo: number }) =>
      api.post<SalesGoodsDetailResponse>(`/sales-goods/work/${shopNo}/${goodsNo}/reflect`),
  })

  const handleSearch = () => {
    setSearchParams({
      goodsName: searchGoodsName,
      keyword: searchKeyword,
      janCode: searchJanCode,
      makerNo: searchMakerNo,
    })
  }

  const handleReset = () => {
    setSearchGoodsName('')
    setSearchKeyword('')
    setSearchJanCode('')
    setSearchMakerNo('')
    setSearchParams({})
  }

  const handleSelectGoods = (goods: GoodsResponse) => {
    setSelectedGoods(goods)
    setSalesGoodsName(goods.goodsName ?? '')
    setSalesKeyword(goods.keyword ?? '')
  }

  const buildFormData = () => ({
    shopNo: user!.shopNo,
    goodsNo: selectedGoods!.goodsNo,
    goodsCode,
    goodsSkuCode: goodsSkuCode || null,
    goodsName: salesGoodsName,
    keyword: salesKeyword || null,
    supplierNo: Number(supplierNo),
    referencePrice: referencePrice ? Number(referencePrice) : null,
    purchasePrice: Number(purchasePrice),
    goodsPrice: Number(goodsPrice),
    catchphrase: catchphrase || null,
    goodsIntroduction: goodsIntroduction || null,
    goodsDescription1: goodsDescription1 || null,
    goodsDescription2: goodsDescription2 || null,
  })

  const validateForm = () => {
    if (!goodsCode.trim()) {
      toast.error('商品コードは必須です')
      return false
    }
    if (!salesGoodsName.trim()) {
      toast.error('商品名は必須です')
      return false
    }
    if (!supplierNo) {
      toast.error('仕入先は必須です')
      return false
    }
    if (!purchasePrice) {
      toast.error('標準仕入単価は必須です')
      return false
    }
    if (!goodsPrice) {
      toast.error('標準売単価は必須です')
      return false
    }
    return true
  }

  const handleSaveAsWork = async () => {
    if (!validateForm()) return
    try {
      await createWorkMutation.mutateAsync(buildFormData())
      toast.success('ワークに保存しました')
      router.push('/sales-goods/work')
    } catch {
      toast.error('ワークへの保存に失敗しました')
    }
  }

  const handleReflectToMaster = async () => {
    if (!validateForm()) return
    try {
      const work = await createWorkMutation.mutateAsync(buildFormData())
      await reflectMutation.mutateAsync({
        shopNo: work.shopNo,
        goodsNo: work.goodsNo,
      })
      toast.success('マスタに反映しました')
      router.push('/sales-goods')
    } catch {
      toast.error('マスタへの反映に失敗しました')
    }
  }

  const isPending = createWorkMutation.isPending || reflectMutation.isPending

  return (
    <div className="space-y-6">
      <PageHeader
        title="販売商品新規作成"
        actions={
          <div className="flex items-center gap-2">
            {selectedGoods && (
              <Button variant="outline" onClick={() => setSelectedGoods(null)}>
                商品マスタ選択に戻る
              </Button>
            )}
            <Button variant="outline" onClick={() => router.push('/sales-goods/work')}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              一覧に戻る
            </Button>
          </div>
        }
      />

      {!selectedGoods ? (
        <>
          <Card>
            <CardHeader>
              <CardTitle>販売商品の元となる商品マスタを選択</CardTitle>
            </CardHeader>
            <CardContent>
              <SearchForm onSearch={handleSearch} onReset={handleReset}>
                <div className="space-y-2">
                  <Label>商品名</Label>
                  <Input
                    placeholder="商品名を入力"
                    value={searchGoodsName}
                    onChange={(e) => setSearchGoodsName(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label>キーワード</Label>
                  <Input
                    placeholder="キーワードを入力"
                    value={searchKeyword}
                    onChange={(e) => setSearchKeyword(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label>JANコード</Label>
                  <Input
                    placeholder="JANコードを入力"
                    value={searchJanCode}
                    onChange={(e) => setSearchJanCode(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label>メーカー</Label>
                  <SearchableSelect
                    value={searchMakerNo}
                    onValueChange={setSearchMakerNo}
                    options={(makersQuery.data ?? []).map((maker) => ({
                      value: String(maker.makerNo),
                      label: maker.makerName,
                    }))}
                    searchPlaceholder="メーカーを検索..."
                  />
                </div>
              </SearchForm>
            </CardContent>
          </Card>

          {availableGoodsQuery.isLoading ? (
            <LoadingSpinner />
          ) : availableGoodsQuery.isError ? (
            <ErrorMessage onRetry={() => availableGoodsQuery.refetch()} />
          ) : (
            <DataTable
              data={availableGoodsQuery.data ?? []}
              columns={goodsColumns}
              searchPlaceholder="テーブル内を検索..."
              onRowClick={handleSelectGoods}
            />
          )}
        </>
      ) : (
        <>
          <Card>
            <CardHeader>
              <CardTitle>選択した商品マスタ</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="grid gap-2 max-w-lg text-sm">
                <div className="flex gap-2">
                  <span className="font-medium w-24">商品番号:</span>
                  <span>{selectedGoods.goodsNo}</span>
                </div>
                <div className="flex gap-2">
                  <span className="font-medium w-24">商品名:</span>
                  <span>{selectedGoods.goodsName}</span>
                </div>
                <div className="flex gap-2">
                  <span className="font-medium w-24">JANコード:</span>
                  <span>{selectedGoods.janCode || '-'}</span>
                </div>
                <div className="flex gap-2">
                  <span className="font-medium w-24">メーカー:</span>
                  <span>{selectedGoods.makerName || '-'}</span>
                </div>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>販売商品情報入力</CardTitle>
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
                    <Label>商品コード <span className="text-destructive">*</span></Label>
                    <Input
                      placeholder="商品コードを入力してください"
                      value={goodsCode}
                      onChange={(e) => setGoodsCode(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>商品SKUコード</Label>
                    <Input
                      placeholder="商品SKUコードを入力してください"
                      value={goodsSkuCode}
                      onChange={(e) => setGoodsSkuCode(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>商品名 <span className="text-destructive">*</span></Label>
                    <Input
                      placeholder="商品名を入力してください"
                      value={salesGoodsName}
                      onChange={(e) => setSalesGoodsName(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>キーワード</Label>
                    <Input
                      placeholder="キーワードを入力してください"
                      value={salesKeyword}
                      onChange={(e) => setSalesKeyword(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>仕入先 <span className="text-destructive">*</span></Label>
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
                  </div>
                </TabsContent>

                <TabsContent value="price" className="space-y-4">
                  <div className="space-y-2">
                    <Label>参考価格</Label>
                    <Input
                      type="number"
                      placeholder="参考価格を入力してください"
                      value={referencePrice}
                      onChange={(e) => setReferencePrice(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>標準仕入単価 <span className="text-destructive">*</span></Label>
                    <Input
                      type="number"
                      placeholder="標準仕入単価を入力してください"
                      value={purchasePrice}
                      onChange={(e) => setPurchasePrice(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>標準売単価 <span className="text-destructive">*</span></Label>
                    <Input
                      type="number"
                      placeholder="標準売単価を入力してください"
                      value={goodsPrice}
                      onChange={(e) => setGoodsPrice(e.target.value)}
                    />
                  </div>
                </TabsContent>

                <TabsContent value="description" className="space-y-4">
                  <div className="space-y-2">
                    <Label>キャッチフレーズ</Label>
                    <Input
                      placeholder="キャッチフレーズを入力してください"
                      value={catchphrase}
                      onChange={(e) => setCatchphrase(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>商品概要</Label>
                    <Input
                      placeholder="商品概要を入力してください"
                      value={goodsIntroduction}
                      onChange={(e) => setGoodsIntroduction(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>商品説明1</Label>
                    <Input
                      placeholder="商品説明1を入力してください"
                      value={goodsDescription1}
                      onChange={(e) => setGoodsDescription1(e.target.value)}
                    />
                  </div>
                  <div className="space-y-2">
                    <Label>商品説明2</Label>
                    <Input
                      placeholder="商品説明2を入力してください"
                      value={goodsDescription2}
                      onChange={(e) => setGoodsDescription2(e.target.value)}
                    />
                  </div>
                </TabsContent>
              </Tabs>

              <div className="mt-4 flex gap-2">
                <Button onClick={handleSaveAsWork} disabled={isPending}>
                  {createWorkMutation.isPending ? '保存中...' : 'ワークに保存'}
                </Button>
                <Button
                  variant="outline"
                  onClick={handleReflectToMaster}
                  disabled={isPending}
                >
                  <ArrowRightCircle className="mr-2 h-4 w-4" />
                  {reflectMutation.isPending ? '反映中...' : 'マスタに直接反映'}
                </Button>
              </div>
            </CardContent>
          </Card>
        </>
      )}
    </div>
  )
}
