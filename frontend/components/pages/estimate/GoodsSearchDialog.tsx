'use client'

import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useSuppliers, useMakers } from '@/hooks/use-master-data'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Search } from 'lucide-react'
import { formatNumber } from '@/lib/utils'
import type { SalesGoodsDetailResponse } from '@/types/goods'
import type { EstimateGoodsSearchResponse } from '@/types/estimate'

interface GoodsSearchDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  shopNo: string
  onSelect: (goods: SelectedGoods) => void
}

export interface SelectedGoods {
  goodsNo: number | null
  goodsCode: string
  goodsName: string
  specification: string
  purchasePrice: number | null
  containNum: number | null
  janCode: string | null
  source: 'GOODS' | 'PRICE_PLAN'
}

export function GoodsSearchDialog({ open, onOpenChange, shopNo, onSelect }: GoodsSearchDialogProps) {
  const [supplierNo, setSupplierNo] = useState('')
  const [makerNo, setMakerNo] = useState('')
  const [goodsName, setGoodsName] = useState('')
  const [searchParams, setSearchParams] = useState<{
    supplierNo: string; makerNo: string; goodsName: string
  } | null>(null)

  // ダイアログを開くたびに検索条件をリセット
  useEffect(() => {
    if (open) {
      setSupplierNo('')
      setMakerNo('')
      setGoodsName('')
      setSearchParams(null)
    }
  }, [open])

  const suppliersQuery = useSuppliers(shopNo)
  const makersQuery = useMakers()

  // 販売商品マスタ検索
  const salesGoodsQuery = useQuery({
    queryKey: ['estimate-goods-popup-sales', shopNo, searchParams?.supplierNo, searchParams?.goodsName],
    queryFn: () => {
      const params = new URLSearchParams({ shopNo })
      if (searchParams?.supplierNo) params.append('supplierNo', searchParams.supplierNo)
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      return api.get<SalesGoodsDetailResponse[]>(`/sales-goods/master?${params.toString()}`)
    },
    enabled: searchParams !== null && !!shopNo,
  })

  // 仕入価格変更予定検索（メーカー見積商品）
  const pricePlanQuery = useQuery({
    queryKey: ['estimate-goods-popup-plans', shopNo, searchParams?.goodsName],
    queryFn: () => {
      const params = new URLSearchParams({ shopNo })
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      return api.get<EstimateGoodsSearchResponse[]>(`/estimates/price-plan-goods?${params.toString()}`)
    },
    enabled: searchParams !== null && !!shopNo,
  })

  const handleSearch = () => {
    setSearchParams({ supplierNo, makerNo, goodsName })
  }

  const handleReset = () => {
    setSupplierNo('')
    setMakerNo('')
    setGoodsName('')
    setSearchParams(null)
  }

  const handleSelect = (goods: SelectedGoods) => {
    onSelect(goods)
    onOpenChange(false)
  }

  // 販売商品をフィルタ（メーカーはフロント側フィルタ）
  const filteredSalesGoods = (salesGoodsQuery.data ?? []).filter((g) => {
    if (searchParams?.makerNo && g.makerName) {
      const maker = (makersQuery.data ?? []).find((m) => String(m.makerNo) === searchParams.makerNo)
      if (maker && g.makerName !== maker.makerName) return false
    }
    return true
  })

  // 仕入変更予定（販売商品と重複する商品コードは除外）
  const salesCodes = new Set(filteredSalesGoods.map((s) => s.goodsCode))
  const filteredPricePlans = (pricePlanQuery.data ?? []).filter((g) => {
    const code = g.goodsCode || g.janCode
    return !(code && salesCodes.has(code))
  })

  const isLoading = salesGoodsQuery.isLoading || pricePlanQuery.isLoading
  const hasSearched = searchParams !== null

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="!max-w-[900px] w-[95vw] max-h-[85vh] flex flex-col">
        <DialogHeader>
          <DialogTitle>商品検索</DialogTitle>
        </DialogHeader>

        {/* 検索条件 */}
        <div className="grid gap-3 grid-cols-[1fr_1fr_1fr_auto] items-end">
          <div className="space-y-1">
            <Label className="text-xs">仕入先</Label>
            <SearchableSelect
              value={supplierNo}
              onValueChange={setSupplierNo}
              options={(suppliersQuery.data ?? []).map((s) => ({
                value: String(s.supplierNo),
                label: `${s.supplierCode ?? ''} ${s.supplierName}`,
              }))}
              placeholder="仕入先を選択"
              searchPlaceholder="仕入先を検索..."
              clearable
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">メーカー</Label>
            <SearchableSelect
              value={makerNo}
              onValueChange={setMakerNo}
              options={(makersQuery.data ?? []).map((m) => ({
                value: String(m.makerNo),
                label: m.makerName,
              }))}
              placeholder="メーカーを選択"
              searchPlaceholder="メーカーを検索..."
              clearable
            />
          </div>
          <div className="space-y-1">
            <Label className="text-xs">商品名</Label>
            <Input
              value={goodsName}
              onChange={(e) => setGoodsName(e.target.value)}
              placeholder="商品名を入力"
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  handleSearch()
                }
              }}
            />
          </div>
          <div className="flex gap-2">
            <Button onClick={handleSearch} size="sm">
              <Search className="mr-1 h-3 w-3" />
              検索
            </Button>
            <Button variant="outline" size="sm" onClick={handleReset}>
              リセット
            </Button>
          </div>
        </div>

        {/* 検索結果 */}
        <div className="flex-1 min-h-0 overflow-y-auto border rounded-md mt-2">
          {!hasSearched ? (
            <div className="p-8 text-center text-muted-foreground text-sm">
              検索条件を入力して「検索」ボタンを押してください
            </div>
          ) : isLoading ? (
            <div className="p-8 text-center text-muted-foreground text-sm">検索中...</div>
          ) : (
            <table className="w-full text-sm">
              <thead className="sticky top-0 bg-muted/95 backdrop-blur z-10">
                <tr className="border-b">
                  <th className="px-3 py-2 text-left font-medium">仕入先</th>
                  <th className="px-3 py-2 text-left font-medium">商品コード</th>
                  <th className="px-3 py-2 text-left font-medium">商品名</th>
                  <th className="px-3 py-2 text-right font-medium">仕入単価</th>
                  <th className="px-3 py-2 text-right font-medium">標準売価</th>
                </tr>
              </thead>
              <tbody>
                {filteredSalesGoods.map((g) => (
                  <tr
                    key={`sg-${g.goodsNo}-${g.goodsCode}`}
                    className="border-b hover:bg-accent cursor-pointer"
                    onClick={() => handleSelect({
                      goodsNo: g.goodsNo,
                      goodsCode: g.goodsCode,
                      goodsName: g.goodsName,
                      specification: g.specification ?? '',
                      purchasePrice: g.purchasePrice,
                      containNum: null,
                      janCode: g.janCode,
                      source: 'GOODS',
                    })}
                  >
                    <td className="px-3 py-2 text-xs text-muted-foreground">{g.supplierName}</td>
                    <td className="px-3 py-2 font-mono text-xs">{g.goodsCode}</td>
                    <td className="px-3 py-2">{g.goodsName}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{formatNumber(g.purchasePrice ?? 0)}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{formatNumber(g.goodsPrice ?? 0)}</td>
                  </tr>
                ))}
                {filteredPricePlans.map((g) => (
                  <tr
                    key={`pp-${g.purchasePriceChangePlanNo}`}
                    className="border-b hover:bg-accent cursor-pointer bg-blue-50/50"
                    onClick={() => handleSelect({
                      goodsNo: g.goodsNo,
                      goodsCode: g.goodsCode ?? g.janCode ?? '',
                      goodsName: g.goodsName,
                      specification: g.specification ?? '',
                      purchasePrice: g.purchasePrice,
                      containNum: g.containNum,
                      janCode: g.janCode,
                      source: 'PRICE_PLAN',
                    })}
                  >
                    <td className="px-3 py-2 text-xs text-blue-600">(仕入変更予定)</td>
                    <td className="px-3 py-2 font-mono text-xs">
                      {g.goodsCode ?? (g.janCode ? `[JAN]${g.janCode}` : '')}
                    </td>
                    <td className="px-3 py-2">{g.goodsName}</td>
                    <td className="px-3 py-2 text-right tabular-nums">{g.purchasePrice != null ? formatNumber(g.purchasePrice) : '-'}</td>
                    <td className="px-3 py-2 text-right tabular-nums">-</td>
                  </tr>
                ))}
                {filteredSalesGoods.length === 0 && filteredPricePlans.length === 0 && (
                  <tr>
                    <td colSpan={5} className="px-3 py-8 text-center text-muted-foreground">
                      該当する商品がありません
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          )}
        </div>

        {hasSearched && !isLoading && (
          <div className="text-xs text-muted-foreground text-right">
            販売商品 {filteredSalesGoods.length}件 + 仕入変更予定 {filteredPricePlans.length}件
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
