'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useMakers } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { ArrowLeft, Pencil, Save, X } from 'lucide-react'
import { toast } from 'sonner'
import type { GoodsDetailResponse, SalesGoodsDetailResponse } from '@/types/goods'
import { goodsFormSchema, type GoodsFormValues } from '@/types/goods-schemas'

interface GoodsDetailPageProps {
  goodsNo: number
}

const salesGoodsColumns: Column<SalesGoodsDetailResponse>[] = [
  { key: 'goodsCode', header: '商品コード', sortable: true },
  { key: 'goodsName', header: '商品名', sortable: true },
  { key: 'supplierName', header: '仕入先' },
  {
    key: 'purchasePrice',
    header: '仕入単価',
    render: (item) => item.purchasePrice?.toLocaleString() ?? '',
  },
  {
    key: 'goodsPrice',
    header: '売単価',
    render: (item) => item.goodsPrice?.toLocaleString() ?? '',
  },
  {
    key: 'isWork',
    header: '状態',
    render: (item) =>
      item.isWork ? (
        <Badge variant="outline">ワーク</Badge>
      ) : (
        <Badge variant="secondary">マスタ</Badge>
      ),
  },
]

export function GoodsDetailPage({ goodsNo }: GoodsDetailPageProps) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const { user } = useAuth()

  const [isEditing, setIsEditing] = useState(false)

  const form = useForm<GoodsFormValues>({
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

  const detailQuery = useQuery({
    queryKey: ['goods-detail', goodsNo, user?.shopNo],
    queryFn: () => {
      const params = new URLSearchParams()
      if (user?.shopNo) params.append('shopNo', String(user.shopNo))
      const qs = params.toString()
      return api.get<GoodsDetailResponse>(`/goods/${goodsNo}/detail${qs ? `?${qs}` : ''}`)
    },
    enabled: !!goodsNo,
  })

  const makersQuery = useMakers()

  const updateMutation = useMutation({
    mutationFn: (values: GoodsFormValues) =>
      api.put(`/goods/${goodsNo}`, {
        goodsName: values.goodsName,
        janCode: values.janCode || null,
        makerNo: values.makerNo ? Number(values.makerNo) : null,
        caseContainNum: values.caseContainNum ? Number(values.caseContainNum) : null,
        specification: values.specification || null,
        keyword: values.keyword || null,
        applyReducedTaxRate: values.applyReducedTaxRate,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['goods-detail', goodsNo] })
      setIsEditing(false)
      toast.success('商品マスタを更新しました')
    },
    onError: () => {
      toast.error('商品マスタの更新に失敗しました')
    },
  })

  const startEditing = () => {
    const data = detailQuery.data
    if (!data) return
    form.reset({
      goodsName: data.goodsName ?? '',
      janCode: data.janCode ?? '',
      makerNo: data.makerNo != null ? String(data.makerNo) : '',
      caseContainNum: data.caseContainNum != null ? String(data.caseContainNum) : '',
      specification: data.specification ?? '',
      keyword: data.keyword ?? '',
      applyReducedTaxRate: data.applyReducedTaxRate ?? false,
    })
    setIsEditing(true)
  }

  const cancelEditing = () => {
    setIsEditing(false)
  }

  const handleSave = form.handleSubmit((values) => {
    updateMutation.mutate(values)
  })

  const handleSalesGoodsRowClick = (item: SalesGoodsDetailResponse) => {
    if (item.isWork) {
      router.push(`/sales-goods/work/${item.shopNo}/${item.goodsNo}`)
    } else {
      router.push(`/sales-goods/${item.shopNo}/${item.goodsNo}`)
    }
  }

  if (detailQuery.isLoading) return <LoadingSpinner />
  if (detailQuery.isError) return <ErrorMessage onRetry={() => detailQuery.refetch()} />

  const data = detailQuery.data
  if (!data) return <ErrorMessage onRetry={() => detailQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="商品マスタ詳細"
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
              <Button variant="outline" onClick={startEditing}>
                <Pencil className="mr-2 h-4 w-4" />
                編集
              </Button>
            )}
            <Button variant="outline" onClick={() => router.push('/goods')}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              一覧に戻る
            </Button>
          </div>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>商品マスタ情報</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 max-w-lg">
            <div className="space-y-2">
              <Label>商品番号</Label>
              <div className="text-sm font-medium text-muted-foreground">{data.goodsNo}</div>
            </div>

            <div className="space-y-2">
              <Label>商品名 {isEditing && <span className="text-destructive">*</span>}</Label>
              {isEditing ? (
                <>
                  <Input {...form.register('goodsName')} />
                  {form.formState.errors.goodsName && (
                    <p className="text-sm text-destructive">{form.formState.errors.goodsName.message}</p>
                  )}
                </>
              ) : (
                <div className="text-sm">{data.goodsName}</div>
              )}
            </div>

            <div className="space-y-2">
              <Label>JANコード</Label>
              {isEditing ? (
                <Input {...form.register('janCode')} />
              ) : (
                <div className="text-sm">{data.janCode || '-'}</div>
              )}
            </div>

            <div className="space-y-2">
              <Label>メーカー</Label>
              {isEditing ? (
                <SearchableSelect
                  value={form.watch('makerNo')}
                  onValueChange={(v) => form.setValue('makerNo', v)}
                  options={(makersQuery.data ?? []).map((maker) => ({
                    value: String(maker.makerNo),
                    label: maker.makerName,
                  }))}
                  searchPlaceholder="メーカーを検索..."
                />
              ) : (
                <div className="text-sm">{data.makerName || '-'}</div>
              )}
            </div>

            <div className="space-y-2">
              <Label>入数</Label>
              {isEditing ? (
                <Input type="number" {...form.register('caseContainNum')} />
              ) : (
                <div className="text-sm">{data.caseContainNum ?? '-'}</div>
              )}
            </div>

            <div className="space-y-2">
              <Label>規格</Label>
              {isEditing ? (
                <Input {...form.register('specification')} />
              ) : (
                <div className="text-sm">{data.specification || '-'}</div>
              )}
            </div>

            <div className="space-y-2">
              <Label>キーワード</Label>
              {isEditing ? (
                <Input {...form.register('keyword')} />
              ) : (
                <div className="text-sm">{data.keyword || '-'}</div>
              )}
            </div>

            <div className="space-y-2">
              <Label>軽減税率</Label>
              {isEditing ? (
                <div className="flex items-center space-x-2">
                  <Checkbox
                    id="reducedTax"
                    checked={form.watch('applyReducedTaxRate')}
                    onCheckedChange={(checked) => form.setValue('applyReducedTaxRate', checked === true)}
                  />
                  <Label htmlFor="reducedTax">適用する</Label>
                </div>
              ) : (
                <div className="text-sm">{data.applyReducedTaxRate ? '適用' : '非適用'}</div>
              )}
            </div>

            <div className="space-y-2">
              <Label>廃番</Label>
              <div>
                {data.discontinuedFlg === '1' ? (
                  <Badge variant="destructive">廃番</Badge>
                ) : (
                  <Badge variant="secondary">有効</Badge>
                )}
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>関連販売商品一覧</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={data.salesGoodsList ?? []}
            columns={salesGoodsColumns}
            searchPlaceholder="販売商品を検索..."
            onRowClick={handleSalesGoodsRowClick}
          />
        </CardContent>
      </Card>
    </div>
  )
}
