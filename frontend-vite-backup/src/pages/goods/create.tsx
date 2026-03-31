import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useQuery, useMutation } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { PageHeader } from '@/components/features/common/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Checkbox } from '@/components/ui/checkbox'
import { ArrowLeft } from 'lucide-react'
import { toast } from 'sonner'

interface Maker {
  makerNo: number
  makerName: string
}

interface Supplier {
  supplierNo: number
  supplierName: string
  shopNo: number
}

interface GoodsResponse {
  goodsNo: number
  goodsName: string
  janCode: string
  makerNo: number
  keyword: string
  specification: string
  caseContainNum: number
  applyReducedTaxRate: boolean
}

export function GoodsCreatePage() {
  const navigate = useNavigate()
  const { user } = useAuth()

  // Step 1 form state
  const [goodsName, setGoodsName] = useState('')
  const [janCode, setJanCode] = useState('')
  const [makerNo, setMakerNo] = useState<string>('')
  const [caseContainNum, setCaseContainNum] = useState('')
  const [specification, setSpecification] = useState('')
  const [keyword, setKeyword] = useState('')
  const [applyReducedTaxRate, setApplyReducedTaxRate] = useState(false)

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

  // Created goods state
  const [createdGoods, setCreatedGoods] = useState<GoodsResponse | null>(null)

  const makersQuery = useQuery({
    queryKey: ['makers'],
    queryFn: () => api.get<Maker[]>('/masters/makers'),
  })

  const suppliersQuery = useQuery({
    queryKey: ['suppliers', user?.shopNo],
    queryFn: () => api.get<Supplier[]>(`/masters/suppliers?shopNo=${user?.shopNo}`),
    enabled: !!user?.shopNo && !!createdGoods,
  })

  // Step 1: Create m_goods
  const createGoodsMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) =>
      api.post<GoodsResponse>('/goods', data),
    onSuccess: (data) => {
      setCreatedGoods(data)
      setSalesGoodsName(data.goodsName)
      setSalesKeyword(data.keyword ?? '')
      toast.success('商品マスタに登録しました')
    },
    onError: () => {
      toast.error('商品マスタの登録に失敗しました')
    },
  })

  // Step 2: Create m_sales_goods
  const createSalesGoodsMutation = useMutation({
    mutationFn: (data: Record<string, unknown>) =>
      api.post(`/goods/${createdGoods!.goodsNo}/sales-goods`, data),
    onSuccess: () => {
      toast.success('販売商品マスタに登録しました')
    },
    onError: () => {
      toast.error('販売商品マスタの登録に失敗しました')
    },
  })

  const handleStep1Submit = () => {
    if (!goodsName.trim()) {
      toast.error('商品名は必須です')
      return
    }
    createGoodsMutation.mutate({
      goodsName,
      janCode: janCode || null,
      makerNo: makerNo ? Number(makerNo) : null,
      caseContainNum: caseContainNum ? Number(caseContainNum) : null,
      specification: specification || null,
      keyword: keyword || null,
      applyReducedTaxRate,
    })
  }

  const handleStep2Submit = () => {
    if (!goodsCode.trim()) {
      toast.error('商品コードは必須です')
      return
    }
    if (!salesGoodsName.trim()) {
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
    createSalesGoodsMutation.mutate({
      shopNo: user?.shopNo,
      goodsNo: createdGoods!.goodsNo,
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
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="商品マスタ登録"
        actions={
          <Button variant="outline" onClick={() => navigate('/goods')}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            一覧に戻る
          </Button>
        }
      />

      {/* Step 1: 商品マスタ情報 */}
      <Card>
        <CardHeader>
          <CardTitle>商品マスタ情報</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 max-w-lg">
            {createdGoods && (
              <div className="space-y-2">
                <Label>商品番号</Label>
                <div className="text-sm font-medium text-muted-foreground">
                  {createdGoods.goodsNo}
                </div>
              </div>
            )}
            <div className="space-y-2">
              <Label>商品名 <span className="text-destructive">*</span></Label>
              <Input
                placeholder="商品名を入力してください"
                value={goodsName}
                onChange={(e) => setGoodsName(e.target.value)}
                disabled={!!createdGoods}
              />
            </div>
            <div className="space-y-2">
              <Label>JANコード</Label>
              <Input
                placeholder="JANコードを入力してください"
                value={janCode}
                onChange={(e) => setJanCode(e.target.value)}
                disabled={!!createdGoods}
              />
            </div>
            <div className="space-y-2">
              <Label>メーカー</Label>
              <Select value={makerNo} onValueChange={setMakerNo} disabled={!!createdGoods}>
                <SelectTrigger>
                  <SelectValue placeholder="選択してください" />
                </SelectTrigger>
                <SelectContent>
                  {(makersQuery.data ?? []).map((maker) => (
                    <SelectItem key={maker.makerNo} value={String(maker.makerNo)}>
                      {maker.makerName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-2">
              <Label>入数</Label>
              <Input
                type="number"
                placeholder="入数を入力してください"
                value={caseContainNum}
                onChange={(e) => setCaseContainNum(e.target.value)}
                disabled={!!createdGoods}
              />
            </div>
            <div className="space-y-2">
              <Label>仕様</Label>
              <Input
                placeholder="仕様を入力してください"
                value={specification}
                onChange={(e) => setSpecification(e.target.value)}
                disabled={!!createdGoods}
              />
            </div>
            <div className="space-y-2">
              <Label>キーワード</Label>
              <Input
                placeholder="キーワードを入力してください"
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                disabled={!!createdGoods}
              />
            </div>
            <div className="flex items-center space-x-2">
              <Checkbox
                id="reducedTax"
                checked={applyReducedTaxRate}
                onCheckedChange={(checked) => setApplyReducedTaxRate(checked === true)}
                disabled={!!createdGoods}
              />
              <Label htmlFor="reducedTax">軽減税率適用する</Label>
            </div>
            {!createdGoods && (
              <div>
                <Button
                  onClick={handleStep1Submit}
                  disabled={createGoodsMutation.isPending}
                >
                  {createGoodsMutation.isPending ? '登録中...' : '登録'}
                </Button>
              </div>
            )}
          </div>
        </CardContent>
      </Card>

      {/* Step 2: 販売商品マスタ登録 */}
      {createdGoods && (
        <Card>
          <CardHeader>
            <CardTitle>販売商品マスタ登録（任意）</CardTitle>
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
                  <div className="text-sm font-medium text-muted-foreground">
                    {createdGoods.goodsNo}
                  </div>
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
                  <Label>キーワード</Label>
                  <Input
                    placeholder="キーワードを入力してください"
                    value={salesKeyword}
                    onChange={(e) => setSalesKeyword(e.target.value)}
                  />
                </div>
                <div className="space-y-2">
                  <Label>仕入先 <span className="text-destructive">*</span></Label>
                  <Select value={supplierNo} onValueChange={setSupplierNo}>
                    <SelectTrigger>
                      <SelectValue placeholder="選択してください" />
                    </SelectTrigger>
                    <SelectContent>
                      {(suppliersQuery.data ?? []).map((supplier) => (
                        <SelectItem key={supplier.supplierNo} value={String(supplier.supplierNo)}>
                          {supplier.supplierName}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
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

            <div className="mt-4">
              <Button
                onClick={handleStep2Submit}
                disabled={createSalesGoodsMutation.isPending || createSalesGoodsMutation.isSuccess}
              >
                {createSalesGoodsMutation.isPending ? '登録中...' : '販売商品マスタに登録'}
              </Button>
            </div>
          </CardContent>
        </Card>
      )}
    </div>
  )
}
