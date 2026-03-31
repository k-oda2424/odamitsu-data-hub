'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import { z } from 'zod'
import { api } from '@/lib/api-client'
import { PageHeader } from '@/components/features/common/PageHeader'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { ArrowLeft, Pencil, Save, X } from 'lucide-react'
import { toast } from 'sonner'
import type { PartnerGoodsDetailResponse, OrderHistoryResponse } from '@/types/partner-goods'

const partnerGoodsFormSchema = z.object({
  goodsName: z.string().min(1, '商品名は必須です'),
  keyword: z.string(),
  goodsPrice: z.string().refine((v) => !v || Number(v) > 0, '売価は正の値を入力してください'),
})

type PartnerGoodsFormValues = z.infer<typeof partnerGoodsFormSchema>

interface PartnerGoodsDetailPageProps {
  partnerNo: number
  destinationNo: number
  goodsNo: number
}

const orderHistoryColumns: Column<OrderHistoryResponse>[] = [
  {
    key: 'orderDateTime',
    header: '注文日',
    sortable: true,
    render: (item) => {
      if (!item.orderDateTime) return ''
      const d = new Date(item.orderDateTime)
      return d.toLocaleDateString('ja-JP')
    },
  },
  { key: 'goodsCode', header: '商品コード' },
  { key: 'goodsName', header: '商品名' },
  {
    key: 'goodsPrice',
    header: '売価',
    render: (item) => (
      <span className="text-right block">
        {item.goodsPrice != null ? item.goodsPrice.toLocaleString() : '未設定'}
      </span>
    ),
  },
  {
    key: 'goodsNum',
    header: '数量',
    render: (item) => (
      <span className={item.goodsNum < 0 ? 'text-red-600' : ''}>
        {item.goodsNum}
      </span>
    ),
  },
]

export function PartnerGoodsDetailPage({ partnerNo, destinationNo, goodsNo }: PartnerGoodsDetailPageProps) {
  const router = useRouter()
  const queryClient = useQueryClient()

  const [isEditing, setIsEditing] = useState(false)

  const form = useForm<PartnerGoodsFormValues>({
    resolver: zodResolver(partnerGoodsFormSchema),
    defaultValues: {
      goodsName: '',
      keyword: '',
      goodsPrice: '',
    },
  })

  const detailQuery = useQuery({
    queryKey: ['partner-goods-detail', partnerNo, destinationNo, goodsNo],
    queryFn: () =>
      api.get<PartnerGoodsDetailResponse>(
        `/partner-goods/${partnerNo}/${destinationNo}/${goodsNo}`,
      ),
    enabled: partnerNo != null && destinationNo != null && goodsNo != null,
  })

  const updateMutation = useMutation({
    mutationFn: (values: PartnerGoodsFormValues) =>
      api.put(`/partner-goods/${partnerNo}/${destinationNo}/${goodsNo}`, {
        goodsName: values.goodsName,
        keyword: values.keyword || null,
        goodsPrice: values.goodsPrice ? Number(values.goodsPrice) : null,
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({
        queryKey: ['partner-goods-detail', partnerNo, destinationNo, goodsNo],
      })
      setIsEditing(false)
      toast.success('得意先商品を更新しました')
    },
    onError: () => {
      toast.error('得意先商品の更新に失敗しました')
    },
  })

  const startEditing = () => {
    const data = detailQuery.data
    if (!data) return
    form.reset({
      goodsName: data.goodsName ?? '',
      keyword: data.keyword ?? '',
      goodsPrice: data.goodsPrice != null ? String(data.goodsPrice) : '',
    })
    setIsEditing(true)
  }

  const cancelEditing = () => {
    setIsEditing(false)
  }

  const handleSave = form.handleSubmit((values) => {
    updateMutation.mutate(values)
  })

  if (detailQuery.isLoading) return <LoadingSpinner />
  if (detailQuery.isError) return <ErrorMessage onRetry={() => detailQuery.refetch()} />

  const data = detailQuery.data
  if (!data) return <ErrorMessage onRetry={() => detailQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="得意先商品詳細"
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
            <Button variant="outline" onClick={() => router.push('/partner-goods')}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              一覧に戻る
            </Button>
          </div>
        }
      />

      <Card>
        <CardHeader>
          <CardTitle>得意先商品情報</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 max-w-lg">
            <div className="space-y-2">
              <Label>得意先</Label>
              <div className="text-sm font-medium text-muted-foreground">{data.companyName}</div>
            </div>

            <div className="space-y-2">
              <Label>商品番号</Label>
              <div className="text-sm font-medium text-muted-foreground">{data.goodsNo}</div>
            </div>

            <div className="space-y-2">
              <Label>商品コード</Label>
              <div className="text-sm">{data.goodsCode || '-'}</div>
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
              <Label>売価</Label>
              {isEditing ? (
                <>
                  <Input type="number" {...form.register('goodsPrice')} />
                  {form.formState.errors.goodsPrice && (
                    <p className="text-sm text-destructive">{form.formState.errors.goodsPrice.message}</p>
                  )}
                </>
              ) : (
                <div className="text-sm">{data.goodsPrice?.toLocaleString() ?? '-'}</div>
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
              <Label>最終売上日</Label>
              <div className="text-sm">{data.lastSalesDate ?? '-'}</div>
            </div>

            <div className="space-y-2">
              <Label>最終単価更新日</Label>
              <div className="text-sm">{data.lastPriceUpdateDate ?? '-'}</div>
            </div>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>注文履歴</CardTitle>
        </CardHeader>
        <CardContent>
          <DataTable
            data={data.orderHistory ?? []}
            columns={orderHistoryColumns}
            searchPlaceholder="注文履歴を検索..."
          />
        </CardContent>
      </Card>
    </div>
  )
}
