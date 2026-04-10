'use client'

import { useState, useEffect, useCallback, useMemo } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, usePartners, useDestinations } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import { Checkbox } from '@/components/ui/checkbox'
import { ArrowLeft, Plus, X, Star, FileText, Printer } from 'lucide-react'
import { toast } from 'sonner'
import { GoodsSearchDialog, type SelectedGoods } from './GoodsSearchDialog'
import { calcProfit, calcProfitRate, calcCaseProfit, calcTotalProfit, fmt, fmtRate } from '@/lib/estimate-calc'
import type { CompareGoodsResponse, ComparisonItem, ComparisonGoodsData } from '@/types/estimate'

const MAX_ITEMS = 10
const SESSION_KEY = 'estimate-compare'

function generateId() {
  return crypto.randomUUID()
}

export function EstimateComparisonPage() {
  const router = useRouter()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  // Condition state
  const [shopNo, setShopNo] = useState<string>(isAdmin ? '' : String(user?.shopNo ?? ''))
  const [partnerNo, setPartnerNo] = useState<string>('')
  const [destinationNo, setDestinationNo] = useState<string>('')

  // Comparison items
  const [items, setItems] = useState<ComparisonItem[]>([])

  // Dialog
  const [goodsDialogOpen, setGoodsDialogOpen] = useState(false)

  // Print mode
  const [customerPrintMode, setCustomerPrintMode] = useState(false)

  // sessionStorage restored flag (prevents save before restore)
  const [restored, setRestored] = useState(false)

  // Master data
  const shopsQuery = useShops(isAdmin)
  const partnersQuery = usePartners(shopNo)
  const destinationsQuery = useDestinations(partnerNo)

  // Restore from sessionStorage (mount-once)
  useEffect(() => {
    const raw = sessionStorage.getItem(SESSION_KEY)
    if (raw) {
      try {
        const saved = JSON.parse(raw)
        if (saved.shopNo) setShopNo(String(saved.shopNo))
        if (saved.partnerNo) setPartnerNo(String(saved.partnerNo))
        if (saved.destinationNo) setDestinationNo(String(saved.destinationNo))
        if (saved.items) setItems(saved.items)
      } catch { /* ignore */ }
    }
    setRestored(true)
  }, [])

  // Save to sessionStorage (only after restore completes, debounced 300ms)
  useEffect(() => {
    if (!restored) return
    const handle = window.setTimeout(() => {
      sessionStorage.setItem(SESSION_KEY, JSON.stringify({
        shopNo, partnerNo, destinationNo, items,
      }))
    }, 300)
    return () => window.clearTimeout(handle)
  }, [restored, shopNo, partnerNo, destinationNo, items])

  // Fetch price info for registered goods (goodsNo != null)
  // sort してキー安定化（順序変動による不要な再フェッチを防ぐ）
  const registeredGoodsNos = useMemo(
    () => items
      .map((i) => i.goods.goodsNo)
      .filter((n): n is number => n != null)
      .sort((a, b) => a - b),
    [items],
  )

  const compareQuery = useQuery({
    queryKey: ['compare-goods', shopNo, registeredGoodsNos, partnerNo, destinationNo],
    queryFn: () => {
      const params = new URLSearchParams({ shopNo })
      registeredGoodsNos.forEach((n) => params.append('goodsNoList', String(n)))
      if (partnerNo) params.append('partnerNo', partnerNo)
      if (destinationNo) params.append('destinationNo', destinationNo)
      return api.get<CompareGoodsResponse[]>(`/estimates/compare-goods?${params.toString()}`)
    },
    enabled: registeredGoodsNos.length > 0 && !!shopNo,
  })

  // Merge API response into items
  const enrichedItems = useMemo(() => {
    if (!compareQuery.data) return items
    const apiMap = new Map(compareQuery.data.map((g) => [g.goodsNo, g]))
    return items.map((item) => {
      if (item.goods.goodsNo == null) return item
      const apiData = apiMap.get(item.goods.goodsNo)
      if (!apiData) return item
      return {
        ...item,
        goods: {
          ...item.goods,
          goodsCode: apiData.goodsCode ?? item.goods.goodsCode,
          goodsName: apiData.goodsName ?? item.goods.goodsName,
          specification: apiData.specification ?? item.goods.specification,
          janCode: apiData.janCode ?? item.goods.janCode,
          makerName: apiData.makerName ?? item.goods.makerName,
          supplierName: apiData.supplierName ?? item.goods.supplierName,
          supplierNo: apiData.supplierNo ?? item.goods.supplierNo,
          purchasePrice: apiData.purchasePrice ?? item.goods.purchasePrice,
          nowGoodsPrice: apiData.nowGoodsPrice ?? item.goods.nowGoodsPrice,
          containNum: apiData.containNum ?? item.goods.containNum,
          changeContainNum: apiData.changeContainNum ?? item.goods.changeContainNum,
          pricePlanInfo: apiData.pricePlanInfo ?? item.goods.pricePlanInfo,
          planAfterPrice: apiData.planAfterPrice ?? item.goods.planAfterPrice,
        },
      }
    })
  }, [items, compareQuery.data])

  const baseItem = enrichedItems.find((i) => i.isBase)

  // Add goods from dialog or search
  const handleAddGoods = useCallback((selected: SelectedGoods) => {
    setItems((prev) => {
      // Duplicate check
      const isDuplicate = selected.goodsNo != null
        ? prev.some((i) => i.goods.goodsNo === selected.goodsNo)
        : prev.some((i) => i.goods.goodsCode === selected.goodsCode && i.goods.goodsNo == null)
      if (isDuplicate) {
        toast.warning('この商品は既に追加されています')
        return prev
      }
      if (prev.length >= MAX_ITEMS) {
        toast.warning(`比較対象は最大${MAX_ITEMS}商品までです`)
        return prev
      }

      const newGoods: ComparisonGoodsData = {
        goodsNo: selected.goodsNo,
        goodsCode: selected.goodsCode,
        goodsName: selected.goodsName,
        specification: selected.specification,
        janCode: selected.janCode,
        makerName: null,
        supplierName: null,
        supplierNo: null,
        purchasePrice: selected.purchasePrice,
        nowGoodsPrice: null,
        containNum: selected.containNum,
        changeContainNum: null,
        pricePlanInfo: null,
        planAfterPrice: null,
        source: selected.source,
      }

      const isFirst = prev.length === 0
      return [...prev, {
        id: generateId(),
        goods: newGoods,
        isBase: isFirst,
        simulatedPrice: null,
        simulatedQty: null,
      }]
    })
  }, [])

  const handleRemove = useCallback((id: string) => {
    setItems((prev) => {
      const filtered = prev.filter((i) => i.id !== id)
      // If removed item was base, promote first remaining
      if (filtered.length > 0 && !filtered.some((i) => i.isBase)) {
        filtered[0] = { ...filtered[0], isBase: true }
      }
      return filtered
    })
  }, [])

  const handleSetBase = useCallback((id: string) => {
    setItems((prev) => prev.map((i) => ({ ...i, isBase: i.id === id })))
  }, [])

  const handleUpdateSimulation = useCallback((id: string, field: 'simulatedPrice' | 'simulatedQty', value: string) => {
    const numVal = value === '' ? null : Number(value)
    setItems((prev) => prev.map((i) => i.id === id ? { ...i, [field]: numVal } : i))
  }, [])

  /** 店舗変更時: 依存する得意先・配送先・商品リストをクリア */
  const handleShopChange = useCallback((next: string) => {
    setShopNo(next)
    setPartnerNo('')
    setDestinationNo('')
    setItems([])
  }, [])

  /** 得意先変更時: 配送先をクリア（商品は保持） */
  const handlePartnerChange = useCallback((next: string) => {
    setPartnerNo(next)
    setDestinationNo('')
  }, [])

  const handleCreateEstimate = useCallback((item: ComparisonItem) => {
    const prefill = {
      shopNo: shopNo ? Number(shopNo) : undefined,
      partnerNo: partnerNo ? Number(partnerNo) : undefined,
      destinationNo: destinationNo ? Number(destinationNo) : null,
      details: [{
        goodsNo: item.goods.goodsNo,
        goodsCode: item.goods.goodsCode,
        goodsName: item.goods.goodsName,
        specification: item.goods.specification,
        goodsPrice: item.simulatedPrice,
        purchasePrice: item.goods.purchasePrice,
        containNum: item.goods.containNum,
        supplierNo: item.goods.supplierNo,
      }],
    }
    sessionStorage.setItem('estimate-prefill', JSON.stringify(prefill))
    router.push('/estimates/create')
  }, [router, shopNo, partnerNo, destinationNo])

  return (
    <div className="space-y-4">
      <PageHeader
        title="比較見積"
        actions={
          <Button variant="outline" size="sm" onClick={() => router.push('/estimates')}>
            <ArrowLeft className="mr-1 h-4 w-4" />
            見積一覧
          </Button>
        }
      />

      {/* Condition Section */}
      <Card>
        <CardContent className="pt-4 pb-4">
          <div className="grid gap-4 grid-cols-1 sm:grid-cols-3">
            {isAdmin && (
              <div className="space-y-1">
                <Label className="text-xs">店舗</Label>
                <SearchableSelect
                  value={shopNo}
                  onValueChange={handleShopChange}
                  options={(shopsQuery.data ?? []).map((s) => ({
                    value: String(s.shopNo),
                    label: s.shopName,
                  }))}
                  placeholder="店舗を選択"
                  searchPlaceholder="店舗を検索..."
                />
              </div>
            )}
            <div className="space-y-1">
              <Label className="text-xs">得意先（任意）</Label>
              <SearchableSelect
                value={partnerNo}
                onValueChange={handlePartnerChange}
                options={(partnersQuery.data ?? []).map((p) => ({
                  value: String(p.partnerNo),
                  label: `${p.partnerCode ?? ''} ${p.partnerName}`,
                }))}
                placeholder="得意先を選択"
                searchPlaceholder="得意先を検索..."
                clearable
              />
            </div>
            <div className="space-y-1">
              <Label className="text-xs">配送先（任意）</Label>
              <SearchableSelect
                value={destinationNo}
                onValueChange={setDestinationNo}
                options={(destinationsQuery.data ?? []).map((d) => ({
                  value: String(d.destinationNo),
                  label: `${d.destinationCode ?? ''} ${d.destinationName}`,
                }))}
                placeholder="配送先を選択"
                searchPlaceholder="配送先を検索..."
                clearable
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Comparison Table */}
      <Card>
        <CardContent className="pt-4 pb-4">
          <div className="flex items-center justify-between mb-3">
            <span className="text-sm font-medium">
              比較商品 ({enrichedItems.length}/{MAX_ITEMS})
            </span>
            <Button
              size="sm"
              onClick={() => setGoodsDialogOpen(true)}
              disabled={!shopNo || enrichedItems.length >= MAX_ITEMS}
            >
              <Plus className="mr-1 h-3 w-3" />
              商品を追加
            </Button>
          </div>

          {enrichedItems.length === 0 ? (
            <div className="text-center py-12 text-muted-foreground text-sm">
              「商品を追加」ボタンから比較する商品を選択してください
            </div>
          ) : (
            <div className="overflow-x-auto">
              <ComparisonTable
                items={enrichedItems}
                baseItem={baseItem}
                isAdmin={isAdmin}
                isLoading={compareQuery.isLoading}
                customerPrintMode={customerPrintMode}
                onRemove={handleRemove}
                onSetBase={handleSetBase}
                onUpdateSimulation={handleUpdateSimulation}
                onCreateEstimate={handleCreateEstimate}
              />
            </div>
          )}
        </CardContent>
      </Card>

      {/* Actions */}
      {enrichedItems.length > 0 && (
        <div className="flex items-center justify-between print:hidden">
          <div className="flex items-center gap-2">
            <Checkbox
              id="customer-print"
              checked={customerPrintMode}
              onCheckedChange={(v) => setCustomerPrintMode(v === true)}
            />
            <Label htmlFor="customer-print" className="text-sm cursor-pointer">
              得意先向け表示（仕入情報を隠す）
            </Label>
          </div>
          <Button variant="outline" size="sm" onClick={() => window.print()}>
            <Printer className="mr-1 h-4 w-4" />
            印刷
          </Button>
        </div>
      )}

      <GoodsSearchDialog
        open={goodsDialogOpen}
        onOpenChange={setGoodsDialogOpen}
        shopNo={shopNo}
        onSelect={handleAddGoods}
      />
    </div>
  )
}

// --- Comparison Table Component ---

interface ComparisonTableProps {
  items: ComparisonItem[]
  baseItem: ComparisonItem | undefined
  isAdmin: boolean
  isLoading: boolean
  customerPrintMode: boolean
  onRemove: (id: string) => void
  onSetBase: (id: string) => void
  onUpdateSimulation: (id: string, field: 'simulatedPrice' | 'simulatedQty', value: string) => void
  onCreateEstimate: (item: ComparisonItem) => void
}

function ComparisonTable({
  items, baseItem, isAdmin, isLoading, customerPrintMode,
  onRemove, onSetBase, onUpdateSimulation, onCreateEstimate,
}: ComparisonTableProps) {
  const showCost = isAdmin && !customerPrintMode

  const rows: { key: string; label: string; section?: string; adminOnly?: boolean; render: (item: ComparisonItem) => React.ReactNode }[] = [
    // Header row with base marker and actions
    { key: 'base-marker', label: '', render: (item) => (
      <div className="flex items-center gap-1">
        {item.isBase ? (
          <span className="inline-flex items-center gap-1 text-xs font-medium text-amber-600">
            <Star className="h-3 w-3 fill-amber-400" /> 基準品
          </span>
        ) : (
          <Button
            variant="ghost"
            size="sm"
            className="h-6 text-xs px-1"
            onClick={() => onSetBase(item.id)}
            aria-label={`${item.goods.goodsName || '商品'}を基準品にする`}
          >
            <Star className="h-3 w-3 mr-0.5" /> 基準品にする
          </Button>
        )}
      </div>
    )},
    // Basic info
    { key: 'goodsCode', label: '商品コード', section: '商品情報', render: (item) => (
      <span className="font-mono text-xs">{item.goods.goodsCode || '-'}</span>
    )},
    { key: 'goodsName', label: '商品名', render: (item) => (
      <span className="text-sm">{item.goods.goodsName || '-'}</span>
    )},
    { key: 'specification', label: '規格', render: (item) => (
      <span className="text-xs text-muted-foreground">{item.goods.specification || '-'}</span>
    )},
    { key: 'makerName', label: 'メーカー', render: (item) => (
      <span className="text-xs">{item.goods.makerName || '-'}</span>
    )},
    { key: 'supplierName', label: '仕入先', render: (item) => (
      <span className="text-xs">{item.goods.supplierName || '-'}</span>
    )},
    // Price info (admin only)
    { key: 'purchasePrice', label: '仕入価格', section: '価格情報', adminOnly: true, render: (item) => (
      <span className="tabular-nums">{fmt(item.goods.purchasePrice)}</span>
    )},
    { key: 'pricePlanInfo', label: '価格変更予定', adminOnly: true, render: (item) => (
      <span className="text-xs text-blue-600">{item.goods.pricePlanInfo || '-'}</span>
    )},
    { key: 'containNum', label: '入数', render: (item) => (
      <span className="tabular-nums">{fmt(item.goods.containNum)}</span>
    )},
    // Simulation
    { key: 'simulatedPrice', label: '販売単価', section: 'シミュレーション', render: (item) => (
      <Input
        type="number"
        value={item.simulatedPrice ?? ''}
        onChange={(e) => onUpdateSimulation(item.id, 'simulatedPrice', e.target.value)}
        placeholder={fmt(item.goods.nowGoodsPrice)}
        className="h-7 w-24 text-right tabular-nums text-sm print:border-none print:bg-transparent"
        aria-label={`${item.goods.goodsName || '商品'}の販売単価`}
        data-testid={`simulated-price-${item.id}`}
      />
    )},
    { key: 'simulatedQty', label: '数量(ケース)', render: (item) => (
      <Input
        type="number"
        value={item.simulatedQty ?? ''}
        onChange={(e) => onUpdateSimulation(item.id, 'simulatedQty', e.target.value)}
        placeholder="0"
        className="h-7 w-20 text-right tabular-nums text-sm print:border-none print:bg-transparent"
        aria-label={`${item.goods.goodsName || '商品'}の数量`}
        data-testid={`simulated-qty-${item.id}`}
      />
    )},
    // Calculated fields
    { key: 'profit', label: '粗利額', section: '粗利分析', adminOnly: true, render: (item) => {
      const price = item.simulatedPrice ?? item.goods.nowGoodsPrice
      const profit = calcProfit(price, item.goods.purchasePrice)
      const baseProfit = baseItem ? calcProfit(
        baseItem.simulatedPrice ?? baseItem.goods.nowGoodsPrice,
        baseItem.goods.purchasePrice,
      ) : null
      return <DiffCell value={profit} baseValue={baseProfit} isBase={item.isBase} format={fmt} />
    }},
    { key: 'profitRate', label: '粗利率', adminOnly: true, render: (item) => {
      const price = item.simulatedPrice ?? item.goods.nowGoodsPrice
      const rate = calcProfitRate(price, item.goods.purchasePrice)
      const baseRate = baseItem ? calcProfitRate(
        baseItem.simulatedPrice ?? baseItem.goods.nowGoodsPrice,
        baseItem.goods.purchasePrice,
      ) : null
      return <DiffCell value={rate} baseValue={baseRate} isBase={item.isBase} format={fmtRate} />
    }},
    { key: 'caseProfit', label: 'ケース粗利', adminOnly: true, render: (item) => {
      const price = item.simulatedPrice ?? item.goods.nowGoodsPrice
      const cp = calcCaseProfit(price, item.goods.purchasePrice, item.goods.containNum)
      const baseCp = baseItem ? calcCaseProfit(
        baseItem.simulatedPrice ?? baseItem.goods.nowGoodsPrice,
        baseItem.goods.purchasePrice,
        baseItem.goods.containNum,
      ) : null
      return <DiffCell value={cp} baseValue={baseCp} isBase={item.isBase} format={fmt} />
    }},
    { key: 'totalProfit', label: '合計粗利', adminOnly: true, render: (item) => {
      const price = item.simulatedPrice ?? item.goods.nowGoodsPrice
      const total = calcTotalProfit(price, item.goods.purchasePrice, item.goods.containNum, item.simulatedQty)
      const baseTotal = baseItem ? calcTotalProfit(
        baseItem.simulatedPrice ?? baseItem.goods.nowGoodsPrice,
        baseItem.goods.purchasePrice,
        baseItem.goods.containNum,
        baseItem.simulatedQty,
      ) : null
      return <DiffCell value={total} baseValue={baseTotal} isBase={item.isBase} format={fmt} />
    }},
    // Actions
    { key: 'actions', label: '', render: (item) => (
      <div className="flex gap-1 print:hidden">
        {!item.isBase && (
          <Button
            variant="outline"
            size="sm"
            className="h-7 text-xs"
            onClick={() => onCreateEstimate(item)}
            aria-label={`${item.goods.goodsName || '商品'}で見積作成`}
          >
            <FileText className="mr-1 h-3 w-3" />
            見積作成
          </Button>
        )}
        <Button
          variant="ghost"
          size="sm"
          className="h-7 text-xs text-destructive"
          onClick={() => onRemove(item.id)}
          aria-label={`${item.goods.goodsName || '商品'}を削除`}
        >
          <X className="h-3 w-3" />
        </Button>
      </div>
    )},
  ]

  const visibleRows = rows.filter((r) => !(r.adminOnly && !showCost))

  return (
    <table className="w-full text-sm border-collapse" aria-label="比較見積表">
      <tbody>
        {visibleRows.map((row) => (
          <tr key={row.key} className={row.section ? 'border-t-2 border-border' : 'border-t border-border/50'}>
            <th
              scope="row"
              className="sticky left-0 bg-muted/95 backdrop-blur z-10 px-3 py-1.5 text-left text-xs font-medium text-muted-foreground whitespace-nowrap w-28 min-w-28"
            >
              {row.label}
            </th>
            {items.map((item) => (
              <td
                key={item.id}
                className={`px-3 py-1.5 min-w-40 ${item.isBase ? 'bg-amber-50/50' : ''} ${isLoading && item.goods.goodsNo != null ? 'animate-pulse' : ''}`}
              >
                {row.render(item)}
              </td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

// --- Diff Cell: shows value + comparison arrow ---

interface DiffCellProps {
  value: number | null
  baseValue: number | null
  isBase: boolean
  format: (v: number | null) => string
}

function DiffCell({ value, baseValue, isBase, format }: DiffCellProps) {
  if (value == null) return <span className="text-muted-foreground">-</span>

  const isDeficit = value < 0

  if (isBase || baseValue == null) {
    return (
      <span className={`tabular-nums ${isDeficit ? 'text-red-600 font-medium' : ''}`}>
        {format(value)}
      </span>
    )
  }

  const diff = value - baseValue
  const isImproved = diff > 0
  const isWorse = diff < 0

  return (
    <span className={`tabular-nums ${isDeficit ? 'text-red-600 font-medium' : ''}`}>
      {format(value)}
      {diff !== 0 && (
        <span className={`ml-1 text-xs ${isImproved ? 'text-green-600' : isWorse ? 'text-red-500' : ''}`}>
          {isImproved ? '↑' : '↓'}
        </span>
      )}
    </span>
  )
}
