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
import { Badge } from '@/components/ui/badge'
import { Checkbox } from '@/components/ui/checkbox'
import { Separator } from '@/components/ui/separator'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { ArrowLeft, Pencil, Save, X, ArrowRightCircle, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { formatNumber } from '@/lib/utils'
import type { SalesGoodsDetailResponse, SalesGoodsUpdateRequest, Supplier } from '@/types/goods'

interface SalesGoodsDetailPageProps {
  shopNo: number
  goodsNo: number
  isWork: boolean
}

function ReadOnlyField({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div className="space-y-1">
      <dt className="text-sm font-medium text-muted-foreground">{label}</dt>
      <dd className={`text-base ${mono ? 'font-mono' : ''}`}>{value || '-'}</dd>
    </div>
  )
}

function PriceDisplay({ label, value, highlight }: { label: string; value: number | null; highlight?: boolean }) {
  const formatted = value != null ? `${formatNumber(value)} 円` : '-'
  return (
    <div className="space-y-1">
      <dt className="text-sm font-medium text-muted-foreground">{label}</dt>
      <dd className={`text-xl tabular-nums ${highlight ? 'font-bold' : 'font-medium'}`}>{formatted}</dd>
    </div>
  )
}

function EditableField({
  label,
  value,
  onChange,
  required,
  type = 'text',
  placeholder,
}: {
  label: string
  value: string
  onChange: (v: string) => void
  required?: boolean
  type?: string
  placeholder?: string
}) {
  return (
    <div className="space-y-1.5">
      <Label className="text-sm">
        {label} {required && <span className="text-destructive">*</span>}
      </Label>
      <Input type={type} value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
    </div>
  )
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
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false)
  const [deleteMasterChecked, setDeleteMasterChecked] = useState(false)

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
      if (window.history.length > 1) {
        router.back()
      } else {
        router.push('/sales-goods')
      }
    },
    onError: () => {
      toast.error('マスタへの反映に失敗しました')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: (params: { deleteMaster: boolean }) =>
      api.delete(`/sales-goods/work/${shopNo}/${goodsNo}?deleteMaster=${params.deleteMaster}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['sales-goods'] })
      toast.success('削除しました')
      if (window.history.length > 1) {
        router.back()
      } else {
        router.push(isWork ? '/sales-goods/work' : '/sales-goods')
      }
    },
    onError: () => {
      toast.error('削除に失敗しました')
    },
  })

  const handleDelete = () => {
    deleteMutation.mutate({ deleteMaster: deleteMasterChecked })
    setDeleteDialogOpen(false)
  }

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
    if (!goodsCode.trim()) { toast.error('商品コードは必須です'); return }
    if (!goodsName.trim()) { toast.error('商品名は必須です'); return }
    if (!supplierNo) { toast.error('仕入先は必須です'); return }
    if (!purchasePrice) { toast.error('標準仕入単価は必須です'); return }
    if (!goodsPrice) { toast.error('標準売単価は必須です'); return }
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

  const fallbackPath = isWork ? '/sales-goods/work' : '/sales-goods'
  const margin = data.goodsPrice != null && data.purchasePrice != null
    ? data.goodsPrice - data.purchasePrice
    : null
  const marginRate = data.goodsPrice != null && data.purchasePrice != null && data.goodsPrice > 0
    ? ((data.goodsPrice - data.purchasePrice) / data.goodsPrice * 100)
    : null

  return (
    <div className="space-y-6">
      <PageHeader
        title={
          <div className="flex items-center gap-3">
            <span>{isWork ? '販売商品ワーク詳細' : '販売商品マスタ詳細'}</span>
            <Badge variant={isWork ? 'outline' : 'secondary'}>
              {isWork ? 'ワーク' : 'マスタ'}
            </Badge>
          </div>
        }
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
                  <>
                    <Button
                      onClick={() => reflectMutation.mutate()}
                      disabled={reflectMutation.isPending}
                    >
                      <ArrowRightCircle className="mr-2 h-4 w-4" />
                      {reflectMutation.isPending ? '反映中...' : 'マスタに反映'}
                    </Button>
                    <Button
                      variant="destructive"
                      onClick={() => {
                        setDeleteMasterChecked(false)
                        setDeleteDialogOpen(true)
                      }}
                    >
                      <Trash2 className="mr-2 h-4 w-4" />
                      削除
                    </Button>
                  </>
                )}
              </>
            )}
            <Button variant="outline" onClick={() => {
              if (window.history.length > 1) {
                router.back()
              } else {
                router.push(fallbackPath)
              }
            }}>
              <ArrowLeft className="mr-2 h-4 w-4" />
              一覧に戻る
            </Button>
          </div>
        }
      />

      {/* 商品識別 + 商品マスタ情報（読み取り専用） */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">商品情報</CardTitle>
        </CardHeader>
        <CardContent>
          <dl className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            <ReadOnlyField label="商品番号" value={String(data.goodsNo)} mono />
            <ReadOnlyField label="JANコード" value={data.janCode} mono />
            <ReadOnlyField label="メーカー" value={data.makerName} />
            <ReadOnlyField label="規格" value={data.specification} />
          </dl>
        </CardContent>
      </Card>

      <div className="grid gap-6 lg:grid-cols-3">
        {/* 販売商品情報（編集可能） */}
        <Card className="lg:col-span-2">
          <CardHeader className="pb-3">
            <CardTitle className="text-base">販売商品情報</CardTitle>
          </CardHeader>
          <CardContent className="space-y-5">
            {isEditing ? (
              <div className="grid gap-4 sm:grid-cols-2">
                <EditableField label="商品コード" value={goodsCode} onChange={setGoodsCode} required placeholder="商品コードを入力" />
                <EditableField label="商品SKUコード" value={goodsSkuCode} onChange={setGoodsSkuCode} placeholder="SKUコードを入力" />
                <div className="sm:col-span-2">
                  <EditableField label="商品名" value={goodsName} onChange={setGoodsName} required placeholder="商品名を入力" />
                </div>
                <div className="sm:col-span-2">
                  <EditableField label="キーワード" value={keyword} onChange={setKeyword} placeholder="検索用キーワード" />
                </div>
                <div className="sm:col-span-2 space-y-1.5">
                  <Label className="text-sm">
                    仕入先 <span className="text-destructive">*</span>
                  </Label>
                  <SearchableSelect
                    value={supplierNo}
                    onValueChange={setSupplierNo}
                    options={(suppliersQuery.data ?? []).map((supplier) => ({
                      value: String(supplier.supplierNo),
                      label: `${supplier.supplierCode ?? ''} ${supplier.supplierName}`,
                    }))}
                    searchPlaceholder="仕入先を検索..."
                    clearable={false}
                  />
                </div>
              </div>
            ) : (
              <dl className="grid gap-4 sm:grid-cols-2">
                <ReadOnlyField label="商品コード" value={data.goodsCode} mono />
                <ReadOnlyField label="商品SKUコード" value={data.goodsSkuCode} mono />
                <div className="sm:col-span-2">
                  <ReadOnlyField label="商品名" value={data.goodsName} />
                </div>
                <ReadOnlyField label="キーワード" value={data.keyword} />
                <ReadOnlyField label="仕入先" value={data.supplierName} />
              </dl>
            )}
          </CardContent>
        </Card>

        {/* 価格情報 */}
        <Card>
          <CardHeader className="pb-3">
            <CardTitle className="text-base">価格情報</CardTitle>
          </CardHeader>
          <CardContent>
            {isEditing ? (
              <div className="space-y-4">
                <EditableField label="参考価格" value={referencePrice} onChange={setReferencePrice} type="number" />
                <EditableField label="標準仕入単価" value={purchasePrice} onChange={setPurchasePrice} type="number" required />
                <EditableField label="標準売単価" value={goodsPrice} onChange={setGoodsPrice} type="number" required />
              </div>
            ) : (
              <dl className="space-y-4">
                <PriceDisplay label="参考価格" value={data.referencePrice} />
                <Separator />
                <PriceDisplay label="標準仕入単価" value={data.purchasePrice} highlight />
                <PriceDisplay label="標準売単価" value={data.goodsPrice} highlight />
                <Separator />
                <div className="space-y-1">
                  <dt className="text-sm font-medium text-muted-foreground">粗利</dt>
                  <dd className="flex items-baseline gap-2">
                    <span className={`text-xl tabular-nums font-bold ${margin != null && margin >= 0 ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
                      {margin != null ? `${formatNumber(margin)} 円` : '-'}
                    </span>
                    {marginRate != null && (
                      <span className="text-sm text-muted-foreground tabular-nums">
                        ({marginRate.toFixed(1)}%)
                      </span>
                    )}
                  </dd>
                </div>
              </dl>
            )}
          </CardContent>
        </Card>
      </div>

      {/* 商品説明 */}
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">商品説明</CardTitle>
        </CardHeader>
        <CardContent>
          {isEditing ? (
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <EditableField label="キャッチフレーズ" value={catchphrase} onChange={setCatchphrase} placeholder="キャッチフレーズを入力" />
              </div>
              <div className="sm:col-span-2">
                <EditableField label="商品概要" value={goodsIntroduction} onChange={setGoodsIntroduction} placeholder="商品概要を入力" />
              </div>
              <EditableField label="商品説明1" value={goodsDescription1} onChange={setGoodsDescription1} placeholder="商品説明1を入力" />
              <EditableField label="商品説明2" value={goodsDescription2} onChange={setGoodsDescription2} placeholder="商品説明2を入力" />
            </div>
          ) : (
            <dl className="grid gap-4 sm:grid-cols-2">
              <div className="sm:col-span-2">
                <ReadOnlyField label="キャッチフレーズ" value={data.catchphrase} />
              </div>
              <div className="sm:col-span-2">
                <ReadOnlyField label="商品概要" value={data.goodsIntroduction} />
              </div>
              <ReadOnlyField label="商品説明1" value={data.goodsDescription1} />
              <ReadOnlyField label="商品説明2" value={data.goodsDescription2} />
            </dl>
          )}
        </CardContent>
      </Card>

      {/* 削除確認ダイアログ */}
      <AlertDialog open={deleteDialogOpen} onOpenChange={setDeleteDialogOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>販売商品ワークの削除</AlertDialogTitle>
            <AlertDialogDescription>
              「{data.goodsName}」（{data.goodsCode}）を削除しますか？この操作は取り消せません。
            </AlertDialogDescription>
          </AlertDialogHeader>
          {data.hasMaster && (
            <div className="flex items-center space-x-2 py-2">
              <Checkbox
                id="deleteMaster"
                checked={deleteMasterChecked}
                onCheckedChange={(c) => setDeleteMasterChecked(c === true)}
              />
              <Label htmlFor="deleteMaster">
                連動して販売商品マスタも削除する
              </Label>
            </div>
          )}
          <AlertDialogFooter>
            <AlertDialogCancel>キャンセル</AlertDialogCancel>
            <AlertDialogAction
              onClick={handleDelete}
              disabled={deleteMutation.isPending}
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
            >
              {deleteMutation.isPending ? '削除中...' : '削除'}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
