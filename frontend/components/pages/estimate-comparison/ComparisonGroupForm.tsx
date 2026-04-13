'use client'

import { useCallback, useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Trash2, Plus, Search } from 'lucide-react'
import { calcProfit, calcProfitRate, calcCaseProfit, fmt, fmtRate } from '@/lib/estimate-calc'
import { GoodsSearchDialog, type SelectedGoods } from '@/components/pages/estimate/GoodsSearchDialog'
import type { GroupRow, DetailRow } from '@/types/estimate-comparison'
import { createEmptyDetail } from '@/types/estimate-comparison'
import { api } from '@/lib/api-client'
import type { EstimateGoodsSearchResponse } from '@/types/estimate'
import { toast } from 'sonner'

interface Props {
  group: GroupRow
  groupIndex: number
  shopNo: string
  isAdmin: boolean
  onUpdate: (updater: GroupRow | ((prev: GroupRow) => GroupRow)) => void
  onRemove: () => void
}

export function ComparisonGroupForm({ group, groupIndex, shopNo, isAdmin, onUpdate, onRemove }: Props) {
  const [goodsSearchOpen, setGoodsSearchOpen] = useState(false)
  const [searchTarget, setSearchTarget] = useState<'base' | string>('base') // 'base' or detail id

  const updateBaseField = useCallback(<K extends keyof GroupRow>(key: K, value: GroupRow[K]) => {
    onUpdate((prev) => ({ ...prev, [key]: value }))
  }, [onUpdate])

  const updateDetail = useCallback((detailId: string, updates: Partial<DetailRow>) => {
    onUpdate((prev) => ({
      ...prev,
      details: prev.details.map((d) =>
        d.id === detailId ? { ...d, ...updates } : d
      ),
    }))
  }, [onUpdate])

  const addDetail = useCallback(() => {
    onUpdate((prev) => ({ ...prev, details: [...prev.details, createEmptyDetail()] }))
  }, [onUpdate])

  const removeDetail = useCallback((detailId: string) => {
    onUpdate((prev) => ({ ...prev, details: prev.details.filter((d) => d.id !== detailId) }))
  }, [onUpdate])

  const searchGoodsByCode = useCallback(async (code: string, target: 'base' | string) => {
    if (!code || !shopNo) return
    try {
      const result = await api.get<EstimateGoodsSearchResponse>(`/estimates/goods-search?shopNo=${shopNo}&code=${encodeURIComponent(code)}`)
      if (!result) return
      if (target === 'base') {
        onUpdate((prev) => ({
          ...prev,
          baseGoodsNo: result.goodsNo,
          baseGoodsCode: result.goodsCode,
          baseGoodsName: result.goodsName,
          baseSpecification: result.specification ?? '',
          basePurchasePrice: result.purchasePrice,
          baseContainNum: result.changeContainNum ?? result.containNum,
        }))
      } else {
        updateDetail(target, {
          goodsNo: result.goodsNo,
          goodsCode: result.goodsCode,
          goodsName: result.goodsName,
          specification: result.specification ?? '',
          purchasePrice: result.purchasePrice,
          containNum: result.changeContainNum ?? result.containNum,
        })
      }
    } catch {
      toast.error('商品が見つかりません')
    }
  }, [shopNo, onUpdate, updateDetail])

  const handleGoodsSelect = useCallback((goods: SelectedGoods) => {
    if (searchTarget === 'base') {
      onUpdate((prev) => ({
        ...prev,
        baseGoodsNo: goods.goodsNo,
        baseGoodsCode: goods.goodsCode,
        baseGoodsName: goods.goodsName,
        baseSpecification: goods.specification,
        basePurchasePrice: goods.purchasePrice,
        baseContainNum: goods.containNum,
      }))
    } else {
      updateDetail(searchTarget, {
        goodsNo: goods.goodsNo,
        goodsCode: goods.goodsCode,
        goodsName: goods.goodsName,
        specification: goods.specification,
        purchasePrice: goods.purchasePrice,
        containNum: goods.containNum,
      })
    }
    setGoodsSearchOpen(false)
  }, [searchTarget, onUpdate, updateDetail])

  const openSearch = (target: 'base' | string) => {
    setSearchTarget(target)
    setGoodsSearchOpen(true)
  }

  return (
    <>
      <Card>
        <CardHeader className="pb-3">
          <div className="flex items-center justify-between">
            <CardTitle className="text-base">グループ {groupIndex + 1}</CardTitle>
            <Button variant="ghost" size="sm" onClick={onRemove}>
              <Trash2 className="h-4 w-4 text-destructive" />
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* 基準品情報 */}
          <div className="rounded-md border p-3 space-y-3 bg-muted/30">
            <Label className="font-medium">基準品</Label>
            <div className="grid gap-3 md:grid-cols-3">
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground">商品コード</Label>
                <div className="flex gap-1">
                  <Input
                    value={group.baseGoodsCode}
                    onChange={(e) => updateBaseField('baseGoodsCode', e.target.value)}
                    onBlur={(e) => searchGoodsByCode(e.target.value, 'base')}
                    onKeyDown={(e) => { if (e.key === 'Enter') searchGoodsByCode(group.baseGoodsCode, 'base') }}
                    placeholder="コード"
                    className="text-sm"
                  />
                  <Button variant="outline" size="icon" className="shrink-0" onClick={() => openSearch('base')} aria-label="商品検索">
                    <Search className="h-4 w-4" />
                  </Button>
                </div>
              </div>
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground">商品名 *</Label>
                <Input
                  value={group.baseGoodsName}
                  onChange={(e) => updateBaseField('baseGoodsName', e.target.value)}
                  placeholder="商品名"
                  className="text-sm"
                />
              </div>
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground">規格</Label>
                <Input
                  value={group.baseSpecification}
                  onChange={(e) => updateBaseField('baseSpecification', e.target.value)}
                  placeholder="規格"
                  className="text-sm"
                />
              </div>
            </div>
            <div className="grid gap-3 md:grid-cols-4">
              {isAdmin && (
                <div className="space-y-1">
                  <Label className="text-xs text-muted-foreground">仕入単価</Label>
                  <Input
                    type="number"
                    value={group.basePurchasePrice ?? ''}
                    onChange={(e) => updateBaseField('basePurchasePrice', e.target.value ? Number(e.target.value) : null)}
                    placeholder="0"
                    className="text-sm"
                  />
                </div>
              )}
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground">販売単価</Label>
                <Input
                  type="number"
                  value={group.baseGoodsPrice ?? ''}
                  onChange={(e) => updateBaseField('baseGoodsPrice', e.target.value ? Number(e.target.value) : null)}
                  placeholder="0"
                  className="text-sm"
                />
              </div>
              <div className="space-y-1">
                <Label className="text-xs text-muted-foreground">入数</Label>
                <Input
                  type="number"
                  value={group.baseContainNum ?? ''}
                  onChange={(e) => updateBaseField('baseContainNum', e.target.value ? Number(e.target.value) : null)}
                  placeholder="1"
                  className="text-sm"
                />
              </div>
              {isAdmin && (
                <div className="space-y-1">
                  <Label className="text-xs text-muted-foreground">粗利</Label>
                  <div className="flex items-center h-9 text-sm text-muted-foreground">
                    {fmt(calcProfit(group.baseGoodsPrice, group.basePurchasePrice))}
                    {' / '}
                    {fmtRate(calcProfitRate(group.baseGoodsPrice, group.basePurchasePrice))}
                  </div>
                </div>
              )}
            </div>
            <div className="space-y-1">
              <Label className="text-xs text-muted-foreground">グループ備考</Label>
              <Input
                value={group.groupNote}
                onChange={(e) => updateBaseField('groupNote', e.target.value)}
                placeholder="備考"
                className="text-sm"
              />
            </div>
          </div>

          {/* 代替提案リスト */}
          <div className="space-y-3">
            <div className="flex items-center justify-between">
              <Label className="font-medium">代替提案</Label>
              <Button variant="outline" size="sm" onClick={addDetail}>
                <Plus className="mr-1 h-3 w-3" />
                代替提案を追加
              </Button>
            </div>

            {group.details.length === 0 ? (
              <div className="rounded-md border border-dashed p-4 text-center text-sm text-muted-foreground">
                代替提案がありません。「代替提案を追加」ボタンから追加してください
              </div>
            ) : (
              group.details.map((detail, detailIdx) => (
                <div key={detail.id} className="rounded-md border p-3 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-sm font-medium">代替 {detailIdx + 1}</span>
                    <Button variant="ghost" size="sm" onClick={() => removeDetail(detail.id)}>
                      <Trash2 className="h-3 w-3 text-destructive" />
                    </Button>
                  </div>
                  <div className="grid gap-3 md:grid-cols-3">
                    <div className="space-y-1">
                      <Label className="text-xs text-muted-foreground">商品コード</Label>
                      <div className="flex gap-1">
                        <Input
                          value={detail.goodsCode}
                          onChange={(e) => updateDetail(detail.id, { goodsCode: e.target.value })}
                          onBlur={(e) => searchGoodsByCode(e.target.value, detail.id)}
                          onKeyDown={(e) => { if (e.key === 'Enter') searchGoodsByCode(detail.goodsCode, detail.id) }}
                          placeholder="コード"
                          className="text-sm"
                        />
                        <Button variant="outline" size="icon" className="shrink-0" onClick={() => openSearch(detail.id)} aria-label="商品検索">
                          <Search className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                    <div className="space-y-1">
                      <Label className="text-xs text-muted-foreground">商品名 *</Label>
                      <Input
                        value={detail.goodsName}
                        onChange={(e) => updateDetail(detail.id, { goodsName: e.target.value })}
                        placeholder="商品名"
                        className="text-sm"
                      />
                    </div>
                    <div className="space-y-1">
                      <Label className="text-xs text-muted-foreground">規格</Label>
                      <Input
                        value={detail.specification}
                        onChange={(e) => updateDetail(detail.id, { specification: e.target.value })}
                        placeholder="規格"
                        className="text-sm"
                      />
                    </div>
                  </div>
                  <div className="grid gap-3 md:grid-cols-4">
                    {isAdmin && (
                      <div className="space-y-1">
                        <Label className="text-xs text-muted-foreground">仕入単価</Label>
                        <Input
                          type="number"
                          value={detail.purchasePrice ?? ''}
                          onChange={(e) => updateDetail(detail.id, { purchasePrice: e.target.value ? Number(e.target.value) : null })}
                          placeholder="0"
                          className="text-sm"
                        />
                      </div>
                    )}
                    <div className="space-y-1">
                      <Label className="text-xs text-muted-foreground">提案販売単価</Label>
                      <Input
                        type="number"
                        value={detail.proposedPrice ?? ''}
                        onChange={(e) => updateDetail(detail.id, { proposedPrice: e.target.value ? Number(e.target.value) : null })}
                        placeholder="0"
                        className="text-sm"
                      />
                    </div>
                    <div className="space-y-1">
                      <Label className="text-xs text-muted-foreground">入数</Label>
                      <Input
                        type="number"
                        value={detail.containNum ?? ''}
                        onChange={(e) => updateDetail(detail.id, { containNum: e.target.value ? Number(e.target.value) : null })}
                        placeholder="1"
                        className="text-sm"
                      />
                    </div>
                    {isAdmin && (
                      <div className="space-y-1">
                        <Label className="text-xs text-muted-foreground">粗利</Label>
                        <div className="flex items-center h-9 text-sm text-muted-foreground">
                          {fmt(calcProfit(detail.proposedPrice, detail.purchasePrice))}
                          {' / '}
                          {fmtRate(calcProfitRate(detail.proposedPrice, detail.purchasePrice))}
                        </div>
                      </div>
                    )}
                  </div>
                  <div className="space-y-1">
                    <Label className="text-xs text-muted-foreground">備考</Label>
                    <Input
                      value={detail.detailNote}
                      onChange={(e) => updateDetail(detail.id, { detailNote: e.target.value })}
                      placeholder="備考"
                      className="text-sm"
                    />
                  </div>
                </div>
              ))
            )}
          </div>
        </CardContent>
      </Card>

      <GoodsSearchDialog
        open={goodsSearchOpen}
        onOpenChange={setGoodsSearchOpen}
        shopNo={shopNo}
        onSelect={handleGoodsSelect}
      />
    </>
  )
}
