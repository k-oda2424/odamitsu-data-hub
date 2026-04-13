'use client'

import { useState, useEffect, useCallback } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, usePartners, useDestinations } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Card, CardContent } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ArrowLeft, Plus, Save } from 'lucide-react'
import { toast } from 'sonner'
import type {
  ComparisonResponse,
  ComparisonCreateRequest,
  GroupRow,
} from '@/types/estimate-comparison'
import { createEmptyGroup } from '@/types/estimate-comparison'
import { ComparisonGroupForm } from './ComparisonGroupForm'

interface Props {
  comparisonNo?: number
}

export function ComparisonFormPage({ comparisonNo }: Props) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const isEditMode = comparisonNo != null

  // Header fields — non-adminの場合、userロード完了後にshopNoを設定
  const [shopNo, setShopNo] = useState(isAdmin ? '' : String(user?.shopNo ?? ''))
  useEffect(() => {
    if (!isAdmin && user?.shopNo && shopNo === '') {
      setShopNo(String(user.shopNo))
    }
  }, [isAdmin, user?.shopNo, shopNo])
  const [partnerNo, setPartnerNo] = useState('')
  const [destinationNo, setDestinationNo] = useState('')
  const [comparisonDate, setComparisonDate] = useState(
    new Date().toISOString().split('T')[0]
  )
  const [title, setTitle] = useState('')
  const [note, setNote] = useState('')

  // Groups
  const [groups, setGroups] = useState<GroupRow[]>([createEmptyGroup()])
  const [initialized, setInitialized] = useState(!isEditMode)

  // Master data
  const shopsQuery = useShops(isAdmin)
  const partnersQuery = usePartners(shopNo)
  const destinationsQuery = useDestinations(partnerNo)

  // Edit mode: load existing data
  const detailQuery = useQuery({
    queryKey: ['estimate-comparison', comparisonNo],
    queryFn: () => api.get<ComparisonResponse>(`/estimate-comparisons/${comparisonNo}`),
    enabled: isEditMode,
  })

  useEffect(() => {
    if (!isEditMode || !detailQuery.data || initialized) return
    const c = detailQuery.data
    setShopNo(String(c.shopNo))
    setPartnerNo(c.partnerNo != null ? String(c.partnerNo) : '')
    setDestinationNo(c.destinationNo != null ? String(c.destinationNo) : '')
    setComparisonDate(c.comparisonDate ?? '')
    setTitle(c.title ?? '')
    setNote(c.note ?? '')
    setGroups(
      c.groups.map((g) => ({
        id: crypto.randomUUID(),
        baseGoodsNo: g.baseGoodsNo,
        baseGoodsCode: g.baseGoodsCode ?? '',
        baseGoodsName: g.baseGoodsName,
        baseSpecification: g.baseSpecification ?? '',
        basePurchasePrice: g.basePurchasePrice,
        baseGoodsPrice: g.baseGoodsPrice,
        baseContainNum: g.baseContainNum,
        groupNote: g.groupNote ?? '',
        details: g.details.map((d) => ({
          id: crypto.randomUUID(),
          goodsNo: d.goodsNo,
          goodsCode: d.goodsCode ?? '',
          goodsName: d.goodsName,
          specification: d.specification ?? '',
          purchasePrice: d.purchasePrice,
          proposedPrice: d.proposedPrice,
          containNum: d.containNum,
          detailNote: d.detailNote ?? '',
          supplierNo: d.supplierNo,
        })),
      }))
    )
    setInitialized(true)
  }, [isEditMode, detailQuery.data, initialized])

  const updateGroup = useCallback((groupId: string, updater: GroupRow | ((prev: GroupRow) => GroupRow)) => {
    setGroups((prev) => prev.map((g) => {
      if (g.id !== groupId) return g
      return typeof updater === 'function' ? updater(g) : updater
    }))
  }, [])

  const removeGroup = useCallback((groupId: string) => {
    setGroups((prev) => prev.filter((g) => g.id !== groupId))
  }, [])

  const addGroup = useCallback(() => {
    setGroups((prev) => [...prev, createEmptyGroup()])
  }, [])

  const saveMutation = useMutation({
    mutationFn: (request: ComparisonCreateRequest) => {
      if (isEditMode) {
        return api.put<ComparisonResponse>(`/estimate-comparisons/${comparisonNo}`, request)
      }
      return api.post<ComparisonResponse>('/estimate-comparisons', request)
    },
    onSuccess: (data) => {
      queryClient.invalidateQueries({ queryKey: ['estimate-comparisons'] })
      if (isEditMode) {
        queryClient.invalidateQueries({ queryKey: ['estimate-comparison', comparisonNo] })
      }
      toast.success(isEditMode ? '比較見積を更新しました' : '比較見積を作成しました')
      router.push(`/estimate-comparisons/${data.comparisonNo}`)
    },
    onError: (error) => {
      const message = error instanceof Error ? error.message : '保存に失敗しました'
      toast.error(message)
    },
  })

  const handleSave = () => {
    if (!shopNo) {
      toast.error('店舗を選択してください')
      return
    }
    if (!comparisonDate) {
      toast.error('作成日を入力してください')
      return
    }
    if (groups.length === 0) {
      toast.error('グループを1つ以上追加してください')
      return
    }
    // Validate group names
    for (let i = 0; i < groups.length; i++) {
      if (!groups[i].baseGoodsName.trim()) {
        toast.error(`グループ${i + 1}の基準品名を入力してください`)
        return
      }
      // Validate detail names
      for (let j = 0; j < groups[i].details.length; j++) {
        if (!groups[i].details[j].goodsName.trim()) {
          toast.error(`グループ${i + 1}の代替提案${j + 1}の商品名を入力してください`)
          return
        }
      }
    }

    const request: ComparisonCreateRequest = {
      shopNo: Number(shopNo),
      partnerNo: partnerNo ? Number(partnerNo) : null,
      destinationNo: destinationNo ? Number(destinationNo) : null,
      comparisonDate,
      title: title || null,
      note: note || null,
      groups: groups.map((g, gi) => ({
        baseGoodsNo: g.baseGoodsNo,
        baseGoodsCode: g.baseGoodsCode || null,
        baseGoodsName: g.baseGoodsName,
        baseSpecification: g.baseSpecification || null,
        basePurchasePrice: g.basePurchasePrice,
        baseGoodsPrice: g.baseGoodsPrice,
        baseContainNum: g.baseContainNum,
        displayOrder: gi + 1,
        groupNote: g.groupNote || null,
        details: g.details.map((d, di) => ({
          goodsNo: d.goodsNo,
          goodsCode: d.goodsCode || null,
          goodsName: d.goodsName,
          specification: d.specification || null,
          purchasePrice: d.purchasePrice,
          proposedPrice: d.proposedPrice,
          containNum: d.containNum,
          detailNote: d.detailNote || null,
          displayOrder: di + 1,
          supplierNo: d.supplierNo,
        })),
      })),
    }

    saveMutation.mutate(request)
  }

  if (isEditMode && detailQuery.isLoading) return <LoadingSpinner />
  if (isEditMode && detailQuery.isError) return <ErrorMessage onRetry={() => detailQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title={isEditMode ? `比較見積 #${comparisonNo} 編集` : '比較見積 新規作成'}
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

      {/* ヘッダ情報 */}
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
              <Label>得意先</Label>
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
                placeholder="得意先を選択"
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
                placeholder="納品先を選択"
                searchPlaceholder="納品先を検索..."
                disabled={!partnerNo}
                clearable
              />
            </div>
            <div className="space-y-2">
              <Label>作成日 *</Label>
              <Input
                type="date"
                value={comparisonDate}
                onChange={(e) => setComparisonDate(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>タイトル</Label>
              <Input
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="タイトル"
              />
            </div>
            <div className="space-y-2 md:col-span-2 lg:col-span-3">
              <Label>社内メモ</Label>
              <Textarea
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="社内メモ（印刷時非表示）"
                rows={2}
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* グループリスト */}
      <div className="space-y-4">
        {groups.map((group, idx) => (
          <ComparisonGroupForm
            key={group.id}
            group={group}
            groupIndex={idx}
            shopNo={shopNo}
            isAdmin={isAdmin}
            onUpdate={(updater) => updateGroup(group.id, updater)}
            onRemove={() => removeGroup(group.id)}
          />
        ))}

        {groups.length === 0 && (
          <div className="rounded-lg border border-dashed p-8 text-center text-muted-foreground">
            グループがありません。「グループ追加」ボタンから追加してください
          </div>
        )}

        <Button variant="outline" onClick={addGroup} className="w-full">
          <Plus className="mr-2 h-4 w-4" />
          グループ追加
        </Button>
      </div>
    </div>
  )
}
