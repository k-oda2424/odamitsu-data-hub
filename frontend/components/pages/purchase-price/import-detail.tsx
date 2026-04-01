'use client'

import { useState } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, useSuppliers } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Label } from '@/components/ui/label'
import { ArrowLeft, Plus, SkipForward } from 'lucide-react'
import { toast } from 'sonner'
import { GoodsSearchPopover } from './GoodsSearchPopover'
import { NewGoodsDialog } from './NewGoodsDialog'
import { getChangeReasonLabel } from '@/types/purchase-price'
import type { QuoteImportDetailData, QuoteImportDetailResponse } from '@/types/quote-import'

interface QuoteImportDetailPageProps {
  importId: number
}

export function QuoteImportDetailPage({ importId }: QuoteImportDetailPageProps) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const { user } = useAuth()

  const isAdmin = user?.shopNo === 0
  const [selectedShopNo, setSelectedShopNo] = useState<string>(
    isAdmin ? '' : String(user?.shopNo ?? '1')
  )
  const [supplierNo, setSupplierNo] = useState<string>('')
  const [editingSupplier, setEditingSupplier] = useState(false)
  const [newGoodsDetail, setNewGoodsDetail] = useState<QuoteImportDetailResponse | null>(null)
  const [newGoodsDialogOpen, setNewGoodsDialogOpen] = useState(false)

  const shopsQuery = useShops(isAdmin)
  const suppliersQuery = useSuppliers(selectedShopNo || undefined)

  const detailQuery = useQuery({
    queryKey: ['quote-import', importId],
    queryFn: () => api.get<QuoteImportDetailData>(`/quote-imports/${importId}`),
  })

  const supplierMatchMutation = useMutation({
    mutationFn: (data: { supplierCode: string; supplierNo: number }) =>
      api.put(`/quote-imports/${importId}/supplier`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['quote-import', importId] })
      toast.success('仕入先を確定しました')
    },
    onError: () => toast.error('仕入先の確定に失敗しました'),
  })

  const skipMutation = useMutation({
    mutationFn: (detailId: number) =>
      api.delete(`/quote-imports/${importId}/details/${detailId}`),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['quote-import', importId] })
    },
  })

  const matchMutation = useMutation({
    mutationFn: ({ detailId, goodsCode, goodsNo }: { detailId: number; goodsCode: string; goodsNo: number }) =>
      api.post(`/quote-imports/${importId}/details/${detailId}/match`, { goodsCode, goodsNo }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['quote-import', importId] })
      toast.success('商品を突合しました')
    },
    onError: () => toast.error('突合に失敗しました'),
  })

  const handleSupplierConfirm = () => {
    const selected = (suppliersQuery.data ?? []).find((s) => String(s.supplierNo) === supplierNo)
    if (!selected) { toast.error('仕入先を選択してください'); return }
    supplierMatchMutation.mutate(
      { supplierCode: selected.supplierCode ?? '', supplierNo: selected.supplierNo },
      { onSuccess: () => setEditingSupplier(false) },
    )
  }

  const handleMatch = (detailId: number, goods: { goodsCode: string; goodsNo: number }) => {
    matchMutation.mutate({ detailId, goodsCode: goods.goodsCode, goodsNo: goods.goodsNo })
  }

  const handleSkip = (detailId: number) => {
    skipMutation.mutate(detailId)
  }

  const handleNewGoods = (detail: QuoteImportDetailResponse) => {
    setNewGoodsDetail(detail)
    setNewGoodsDialogOpen(true)
  }

  if (detailQuery.isLoading) return <LoadingSpinner />
  if (detailQuery.isError) return <ErrorMessage onRetry={() => detailQuery.refetch()} />
  if (!detailQuery.data) return <ErrorMessage onRetry={() => detailQuery.refetch()} />

  const { header, details } = detailQuery.data
  const processed = header.totalCount - header.remainingCount
  const progressPct = header.totalCount > 0 ? Math.round((processed / header.totalCount) * 100) : 0
  const isSupplierMatched = !!header.supplierCode

  return (
    <div className="space-y-6">
      <PageHeader
        title="商品突合"
        actions={
          <Button variant="outline" onClick={() => router.push('/purchase-prices/imports')}>
            <ArrowLeft className="mr-2 h-4 w-4" />
            一覧に戻る
          </Button>
        }
      />

      {/* ヘッダー情報 */}
      <Card>
        <CardContent className="pt-4">
          <div className="grid gap-2 text-sm md:grid-cols-4">
            <div><span className="text-muted-foreground">見積仕入先:</span> {header.supplierName ?? '-'}</div>
            <div><span className="text-muted-foreground">ファイル:</span> {header.fileName ?? '-'}</div>
            <div><span className="text-muted-foreground">適用日:</span> {header.effectiveDate ?? '-'}</div>
            <div><span className="text-muted-foreground">変更理由:</span> {header.changeReason ? getChangeReasonLabel(header.changeReason) : '-'}</div>
          </div>
          <div className="mt-3 flex items-center gap-3">
            <div className="w-32 h-2 bg-muted rounded-full overflow-hidden">
              <div className="h-full bg-primary rounded-full" style={{ width: `${progressPct}%` }} />
            </div>
            <span className="text-sm text-muted-foreground">
              {processed}/{header.totalCount} 処理済み ({progressPct}%)
            </span>
          </div>
        </CardContent>
      </Card>

      {/* Step 0: 仕入先突合 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">仕入先の突合</CardTitle>
        </CardHeader>
        <CardContent>
          {isSupplierMatched && !editingSupplier ? (
            <div className="flex items-center gap-2">
              <Badge variant="secondary">確定済み</Badge>
              <span className="text-sm">
                {header.supplierName} → {header.supplierCode}（No.{header.supplierNo}）
              </span>
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  setSupplierNo(String(header.supplierNo ?? ''))
                  setEditingSupplier(true)
                }}
              >
                変更
              </Button>
            </div>
          ) : (
            <div className="space-y-3 max-w-lg">
              <div className="text-sm text-muted-foreground">
                見積記載: <strong>{header.supplierName}</strong>
              </div>

              {/* ショップ選択 */}
              <div className="space-y-1">
                <Label className="text-sm">ショップ</Label>
                {isAdmin ? (
                  <SearchableSelect
                    value={selectedShopNo}
                    onValueChange={(v) => {
                      setSelectedShopNo(v)
                      setSupplierNo('')
                    }}
                    options={(shopsQuery.data ?? []).map((s) => ({
                      value: String(s.shopNo),
                      label: s.shopName,
                    }))}
                    placeholder="ショップを選択..."
                    searchPlaceholder="ショップを検索..."
                    clearable={false}
                  />
                ) : (
                  <div className="text-sm py-1">
                    {shopsQuery.data?.find(s => s.shopNo === user?.shopNo)?.shopName ?? `Shop ${user?.shopNo}`}
                  </div>
                )}
              </div>

              {/* 仕入先選択 */}
              <div className="space-y-1">
                <Label className="text-sm">仕入先</Label>
                <div className="flex gap-2">
                  <div className="flex-1">
                    <SearchableSelect
                      value={supplierNo}
                      onValueChange={setSupplierNo}
                      options={(suppliersQuery.data ?? []).map((s) => ({
                        value: String(s.supplierNo),
                        label: `${s.supplierCode ?? ''} ${s.supplierName}`,
                      }))}
                      placeholder={selectedShopNo ? 'システムの仕入先を選択...' : 'ショップを先に選択してください'}
                      searchPlaceholder="仕入先を検索..."
                      clearable={false}
                      disabled={!selectedShopNo}
                    />
                  </div>
                  <Button onClick={handleSupplierConfirm} disabled={!supplierNo || supplierMatchMutation.isPending}>
                    確定
                  </Button>
                  {editingSupplier && (
                    <Button variant="ghost" onClick={() => setEditingSupplier(false)}>
                      キャンセル
                    </Button>
                  )}
                </div>
              </div>
            </div>
          )}
        </CardContent>
      </Card>

      {/* Step 1: 商品突合 */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">
            商品の突合（残り {details.length} 件）
          </CardTitle>
        </CardHeader>
        <CardContent>
          {!isSupplierMatched ? (
            <div className="py-4 text-center text-muted-foreground">
              仕入先を確定してから商品の突合を行ってください
            </div>
          ) : details.length === 0 ? (
            <div className="py-4 text-center text-muted-foreground">
              全件処理完了しました
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-sm">
                <thead>
                  <tr className="border-b bg-muted/50">
                    <th className="px-2 py-2 text-left font-medium w-12">PDF行</th>
                    <th className="px-2 py-2 text-left font-medium">JAN</th>
                    <th className="px-2 py-2 text-left font-medium">見積商品名</th>
                    <th className="px-2 py-2 text-left font-medium">規格</th>
                    <th className="px-2 py-2 text-right font-medium">旧単価</th>
                    <th className="px-2 py-2 text-right font-medium">新単価</th>
                    <th className="px-2 py-2 text-left font-medium">操作</th>
                  </tr>
                </thead>
                <tbody>
                  {details.map((d) => (
                    <tr key={d.quoteImportDetailId} className="border-b hover:bg-muted/30">
                      <td className="px-2 py-2 text-muted-foreground">{d.rowNo ?? '-'}</td>
                      <td className="px-2 py-2 font-mono text-xs">{d.janCode || '(なし)'}</td>
                      <td className="px-2 py-2">{d.quoteGoodsName}</td>
                      <td className="px-2 py-2 text-muted-foreground">{d.specification}</td>
                      <td className="px-2 py-2 text-right">{d.oldPrice?.toLocaleString() ?? '-'}</td>
                      <td className="px-2 py-2 text-right font-medium">{d.newPrice?.toLocaleString() ?? '-'}</td>
                      <td className="px-2 py-2">
                        <div className="flex gap-1">
                          <GoodsSearchPopover
                            shopNo={header.shopNo}
                            supplierNo={header.supplierNo}
                            initialKeyword={d.quoteGoodsName.split(/\s+/).slice(0, 2).join(' ')}
                            onSelect={(goods) => handleMatch(d.quoteImportDetailId, goods)}
                            onSkip={() => handleSkip(d.quoteImportDetailId)}
                          />
                          <Button
                            variant="outline"
                            size="sm"
                            onClick={() => handleNewGoods(d)}
                          >
                            <Plus className="mr-1 h-3 w-3" />
                            新規作成
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            onClick={() => handleSkip(d.quoteImportDetailId)}
                          >
                            <SkipForward className="mr-1 h-3 w-3" />
                            スキップ
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </CardContent>
      </Card>

      <NewGoodsDialog
        importId={importId}
        detail={newGoodsDetail}
        supplierNo={header.supplierNo}
        supplierName={header.supplierName}
        open={newGoodsDialogOpen}
        onOpenChange={setNewGoodsDialogOpen}
        onSuccess={() => queryClient.invalidateQueries({ queryKey: ['quote-import', importId] })}
      />
    </div>
  )
}
