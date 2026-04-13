'use client'

import { useState, useCallback, useEffect, useRef } from 'react'
import { useRouter } from 'next/navigation'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, usePartners, useDestinations, useSuppliers } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Badge } from '@/components/ui/badge'
import { ArrowLeft, Plus, Trash2, Search, Save } from 'lucide-react'
import { toast } from 'sonner'
import { GoodsSearchDialog, type SelectedGoods } from './GoodsSearchDialog'
import type {
  EstimateResponse,
  EstimateCreateRequest,
  EstimateGoodsSearchResponse,
} from '@/types/estimate'
import { calcProfit, calcProfitRate, calcCaseProfit, fmt, fmtRate } from '@/lib/estimate-calc'

interface EstimateDetailRow {
  id: string
  goodsNo: number | null
  goodsCode: string
  goodsName: string
  specification: string
  purchasePrice: number | null
  pricePlanInfo: string
  goodsPrice: number | null
  containNum: number | null
  changeContainNum: number | null
  profitRate: number | null
  detailNote: string
  displayOrder: number
  supplierNo: number | null
}

interface EstimateFormPageProps {
  estimateNo?: number
}

function generateRowId() {
  return crypto.randomUUID()
}

function createEmptyRow(displayOrder: number): EstimateDetailRow {
  return {
    id: generateRowId(),
    goodsNo: null,
    goodsCode: '',
    goodsName: '',
    specification: '',
    purchasePrice: null,
    pricePlanInfo: '',
    goodsPrice: null,
    containNum: null,
    changeContainNum: null,
    profitRate: null,
    detailNote: '',
    displayOrder,
    supplierNo: null,
  }
}

export function EstimateFormPage({ estimateNo }: EstimateFormPageProps) {
  const router = useRouter()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const isEditMode = estimateNo != null

  // Header form state — non-adminの場合、userロード完了後にshopNoを設定
  const [shopNo, setShopNo] = useState<string>(isAdmin ? '' : String(user?.shopNo ?? ''))
  useEffect(() => {
    if (!isAdmin && user?.shopNo && shopNo === '') {
      setShopNo(String(user.shopNo))
    }
  }, [isAdmin, user?.shopNo, shopNo])
  const [partnerNo, setPartnerNo] = useState<string>('')
  const [destinationNo, setDestinationNo] = useState<string>('')
  const [estimateDate, setEstimateDate] = useState<string>(
    new Date().toISOString().split('T')[0],
  )
  const [priceChangeDate, setPriceChangeDate] = useState<string>('')
  const [note, setNote] = useState<string>('')
  const [requirement, setRequirement] = useState<string>('')
  const [recipientName, setRecipientName] = useState<string>('')
  const [proposalMessage, setProposalMessage] = useState<string>('')

  // Detail rows
  const [rows, setRows] = useState<EstimateDetailRow[]>([createEmptyRow(1)])
  // 初期値は常に false。edit モードは API データ待ち、create モードは prefill チェック後に true にする
  const [initialized, setInitialized] = useState(false)

  // Goods search dialog
  const [goodsDialogOpen, setGoodsDialogOpen] = useState(false)
  const [goodsDialogTargetRowId, setGoodsDialogTargetRowId] = useState<string | null>(null)

  // Master data
  const shopsQuery = useShops(isAdmin)
  const partnersQuery = usePartners(shopNo)
  const destinationsQuery = useDestinations(partnerNo)
  const suppliersQuery = useSuppliers(shopNo)

  // Load existing estimate for edit mode
  const estimateQuery = useQuery({
    queryKey: ['estimate', estimateNo],
    queryFn: () => api.get<EstimateResponse>(`/estimates/${estimateNo}`),
    enabled: isEditMode,
  })

  // Initialize form from existing estimate
  useEffect(() => {
    if (!isEditMode || !estimateQuery.data || initialized) return
    const est = estimateQuery.data
    setShopNo(String(est.shopNo))
    setPartnerNo(String(est.partnerNo))
    setDestinationNo(est.destinationNo ? String(est.destinationNo) : '')
    setEstimateDate(est.estimateDate ?? '')
    setPriceChangeDate(est.priceChangeDate ?? '')
    setNote(est.note ?? '')
    setRequirement(est.requirement ?? '')
    setRecipientName(est.recipientName ?? '')
    setProposalMessage(est.proposalMessage ?? '')

    if (est.details && est.details.length > 0) {
      setRows(
        est.details.map((d, i) => ({
          id: generateRowId(),
          goodsNo: d.goodsNo,
          goodsCode: d.goodsCode ?? '',
          goodsName: d.goodsName ?? '',
          specification: d.specification ?? '',
          purchasePrice: d.purchasePrice,
          pricePlanInfo: d.pricePlanInfo ?? '',
          goodsPrice: d.goodsPrice,
          containNum: d.containNum,
          changeContainNum: d.changeContainNum,
          profitRate: d.profitRate,
          detailNote: d.detailNote ?? '',
          displayOrder: d.displayOrder ?? i + 1,
          supplierNo: null,
        })),
      )
    }
    setInitialized(true)
  }, [isEditMode, estimateQuery.data, initialized])

  // Initialize from comparison page prefill (sessionStorage)
  useEffect(() => {
    if (isEditMode || initialized) return
    const raw = sessionStorage.getItem('estimate-prefill')
    if (!raw) {
      setInitialized(true)
      return
    }
    try {
      const prefill = JSON.parse(raw) as {
        shopNo?: number
        partnerNo?: number
        destinationNo?: number | null
        details?: Array<{
          goodsNo: number | null
          goodsCode: string
          goodsName: string
          specification?: string
          goodsPrice: number | null
          purchasePrice: number | null
          containNum: number | null
          supplierNo: number | null
        }>
      }
      if (prefill.shopNo) setShopNo(String(prefill.shopNo))
      if (prefill.partnerNo) setPartnerNo(String(prefill.partnerNo))
      if (prefill.destinationNo) setDestinationNo(String(prefill.destinationNo))
      if (prefill.details && prefill.details.length > 0) {
        setRows(
          prefill.details.map((d, i) => ({
            id: generateRowId(),
            goodsNo: d.goodsNo,
            goodsCode: d.goodsCode ?? '',
            goodsName: d.goodsName ?? '',
            specification: d.specification ?? '',
            purchasePrice: d.purchasePrice,
            pricePlanInfo: '',
            goodsPrice: d.goodsPrice,
            containNum: d.containNum,
            changeContainNum: null,
            profitRate: null,
            detailNote: '',
            displayOrder: i + 1,
            supplierNo: d.supplierNo,
          })),
        )
      }
    } catch {
      // ignore invalid JSON
    } finally {
      sessionStorage.removeItem('estimate-prefill')
      setInitialized(true)
    }
  }, [isEditMode, initialized])

  // Save mutation
  const saveMutation = useMutation({
    mutationFn: (data: EstimateCreateRequest) => {
      if (isEditMode) {
        return api.put<EstimateResponse>(`/estimates/${estimateNo}`, data)
      }
      return api.post<EstimateResponse>('/estimates', data)
    },
    onSuccess: (data) => {
      toast.success('見積を保存しました')
      queryClient.invalidateQueries({ queryKey: ['estimate', data.estimateNo] })
      queryClient.invalidateQueries({ queryKey: ['estimates'] })
      router.push(`/estimates/${data.estimateNo}`)
    },
    onError: () => {
      toast.error('見積の保存に失敗しました')
    },
  })

  // 前回検索済みコードを記録（二重呼び出し防止）
  const lastSearchedCodesRef = useRef<Record<string, string>>({})

  // 商品コード/JANコード入力 → Ajax検索
  const searchGoodsByCode = useCallback(
    async (rowId: string, code: string) => {
      if (!code.trim() || !shopNo) return
      if (lastSearchedCodesRef.current[rowId] === code) return
      lastSearchedCodesRef.current = { ...lastSearchedCodesRef.current, [rowId]: code }
      try {
        const params = new URLSearchParams({ shopNo, code })
        if (partnerNo) params.append('partnerNo', partnerNo)
        if (destinationNo) params.append('destinationNo', destinationNo)

        const result = await api.get<EstimateGoodsSearchResponse>(
          `/estimates/goods-search?${params.toString()}`,
        )

        setRows((prev) =>
          prev.map((row) => {
            if (row.id !== rowId) return row
            // 備考が空で現行販売単価があれば自動入力（既存バッチ PriceChangeToEstimateCreateTasklet と同形式）
            const autoNote =
              (!row.detailNote || row.detailNote.trim() === '') &&
              result.currentSalesPrice != null && result.currentSalesPrice !== 0
                ? `現単価${result.currentSalesPrice}円`
                : row.detailNote
            return {
              ...row,
              goodsNo: result.goodsNo,
              goodsCode: result.goodsCode ?? '',
              goodsName: result.goodsName ?? '',
              specification: result.specification ?? '',
              purchasePrice: result.purchasePrice,
              containNum: result.containNum,
              changeContainNum: result.changeContainNum,
              pricePlanInfo: result.pricePlanInfo ?? '',
              supplierNo: result.supplierNo ?? row.supplierNo,
              detailNote: autoNote,
            }
          }),
        )
      } catch {
        toast.info(`商品コード「${code}」が見つかりません。商品名・原価を手入力できます。`)
      }
    },
    [shopNo, partnerNo, destinationNo],
  )

  // ポップアップから商品選択
  const handleGoodsDialogSelect = useCallback(
    (goods: SelectedGoods) => {
      if (!goodsDialogTargetRowId) return
      setRows((prev) =>
        prev.map((row) =>
          row.id === goodsDialogTargetRowId
            ? {
                ...row,
                goodsNo: goods.goodsNo,
                goodsCode: goods.goodsCode,
                goodsName: goods.goodsName,
                specification: goods.specification,
                purchasePrice: goods.purchasePrice,
                containNum: goods.containNum,
                pricePlanInfo: '',
              }
            : row,
        ),
      )
      // 商品コードで詳細情報を再取得（特値・pricePlanInfo）
      if (goods.goodsCode && shopNo) {
        searchGoodsByCode(goodsDialogTargetRowId, goods.goodsCode)
      }
    },
    [goodsDialogTargetRowId, shopNo, searchGoodsByCode],
  )

  const openGoodsDialog = (rowId: string) => {
    setGoodsDialogTargetRowId(rowId)
    setGoodsDialogOpen(true)
  }

  const updateRow = useCallback((rowId: string, field: keyof EstimateDetailRow, value: unknown) => {
    setRows((prev) =>
      prev.map((row) => (row.id === rowId ? { ...row, [field]: value } : row)),
    )
  }, [])

  const addRow = useCallback(() => {
    setRows((prev) => [...prev, createEmptyRow(prev.length + 1)])
  }, [])

  const removeRow = useCallback((rowId: string) => {
    setRows((prev) => {
      const filtered = prev.filter((r) => r.id !== rowId)
      return filtered.length === 0 ? [createEmptyRow(1)] : filtered
    })
    // 行削除時に検索キャッシュもクリア
    delete lastSearchedCodesRef.current[rowId]
  }, [])

  // 得意先・配送先・店舗が変わったら検索キャッシュをクリア
  // （特値の取得結果が変わるため、同じ商品コードでも再検索が必要）
  useEffect(() => {
    lastSearchedCodesRef.current = {}
  }, [shopNo, partnerNo, destinationNo])

  const handleSave = () => {
    if (!shopNo) {
      toast.error('店舗を選択してください')
      return
    }
    if (!partnerNo) {
      toast.error('得意先を選択してください')
      return
    }
    if (!estimateDate) {
      toast.error('見積日を入力してください')
      return
    }
    if (!priceChangeDate) {
      toast.error('価格改定日を入力してください')
      return
    }

    const validRows = rows.filter(
      (r) => r.goodsCode.trim() && r.goodsPrice != null && r.goodsPrice > 0,
    )
    // 新規商品（goodsNo=null）は商品名必須
    const invalidNewGoods = validRows.find((r) => r.goodsNo == null && !r.goodsName.trim())
    if (invalidNewGoods) {
      toast.error('新規商品の商品名を入力してください')
      return
    }
    if (validRows.length === 0) {
      toast.error('有効な明細を1件以上入力してください')
      return
    }

    const request: EstimateCreateRequest = {
      shopNo: Number(shopNo),
      partnerNo: Number(partnerNo),
      destinationNo: destinationNo ? Number(destinationNo) : null,
      estimateDate,
      priceChangeDate,
      note,
      requirement: requirement || null,
      recipientName: recipientName || null,
      proposalMessage: proposalMessage || null,
      details: validRows.map((r) => ({
        goodsNo: r.goodsNo,
        goodsCode: r.goodsCode,
        goodsName: r.goodsName,
        specification: r.specification,
        goodsPrice: r.goodsPrice!,
        purchasePrice: r.purchasePrice,
        containNum: r.containNum,
        changeContainNum: r.changeContainNum,
        profitRate: calcProfitRate(r.goodsPrice, r.purchasePrice),
        detailNote: r.detailNote,
        displayOrder: r.displayOrder,
        supplierNo: r.goodsNo == null ? r.supplierNo : null,
      })),
    }

    saveMutation.mutate(request)
  }

  if (isEditMode && estimateQuery.isLoading) return <LoadingSpinner />
  if (isEditMode && estimateQuery.isError)
    return <ErrorMessage onRetry={() => estimateQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title={isEditMode ? `見積修正 #${estimateNo}` : '見積作成'}
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

      {/* Header form */}
      <Card>
        <CardContent className="pt-4">
          <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
            {isAdmin && (
              <div className="space-y-2">
                <Label>
                  店舗 <span className="text-destructive">*</span>
                </Label>
                <Select value={shopNo} onValueChange={(v) => { setShopNo(v); setPartnerNo(''); setDestinationNo('') }}>
                  <SelectTrigger>
                    <SelectValue placeholder="店舗を選択" />
                  </SelectTrigger>
                  <SelectContent>
                    {(shopsQuery.data ?? []).map((s) => (
                      <SelectItem key={s.shopNo} value={String(s.shopNo)}>
                        {s.shopName}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
            )}
            <div className="space-y-2">
              <Label>
                得意先 <span className="text-destructive">*</span>
              </Label>
              <SearchableSelect
                value={partnerNo}
                onValueChange={(v) => { setPartnerNo(v); setDestinationNo('') }}
                options={(partnersQuery.data ?? []).map((p) => ({
                  value: String(p.partnerNo),
                  label: `${p.partnerCode} ${p.partnerName}`,
                }))}
                placeholder="得意先を選択"
                searchPlaceholder="得意先を検索..."
              />
            </div>
            <div className="space-y-2">
              <Label>納品先</Label>
              <SearchableSelect
                value={destinationNo}
                onValueChange={setDestinationNo}
                options={(destinationsQuery.data ?? []).map((d) => ({
                  value: String(d.destinationNo),
                  label: `${d.destinationCode ?? ''} ${d.destinationName}`,
                }))}
                placeholder="納品先を選択"
                searchPlaceholder="納品先を検索..."
                clearable
              />
            </div>
            <div className="space-y-2">
              <Label>
                見積日 <span className="text-destructive">*</span>
              </Label>
              <Input
                type="date"
                value={estimateDate}
                onChange={(e) => setEstimateDate(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>
                価格改定日 <span className="text-destructive">*</span>
              </Label>
              <Input
                type="date"
                value={priceChangeDate}
                onChange={(e) => setPriceChangeDate(e.target.value)}
              />
            </div>
            <div className="space-y-2">
              <Label>担当者名（宛名）</Label>
              <Input
                value={recipientName}
                onChange={(e) => setRecipientName(e.target.value)}
                placeholder="例: 森社長"
              />
            </div>
            <div className="space-y-2">
              <Label>要件（得意先向け・印刷表示）</Label>
              <Input
                value={requirement}
                onChange={(e) => setRequirement(e.target.value)}
                placeholder="例: ○○仕様で見積依頼"
              />
            </div>
            <div className="space-y-2">
              <Label>社内メモ（印刷非表示）</Label>
              <Input
                value={note}
                onChange={(e) => setNote(e.target.value)}
                placeholder="社内向けメモ"
              />
            </div>
            <div className="space-y-2 md:col-span-2 lg:col-span-3">
              <Label>提案文（得意先向けメッセージ・明細後に表示）</Label>
              <textarea
                value={proposalMessage}
                onChange={(e) => setProposalMessage(e.target.value)}
                placeholder="例: 価格をご確認いただき、問題なければサンプルを取寄せますのでホルダーに入るかどうかの確認に進ませていただきたい。"
                className="flex min-h-20 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-xs transition-[color,box-shadow] outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
                rows={3}
              />
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Detail table */}
      <Card>
        <CardContent className="pt-4">
          <div className="mb-3 flex items-center justify-between">
            <h3 className="text-sm font-medium">明細</h3>
            <Button variant="outline" size="sm" onClick={addRow}>
              <Plus className="mr-1 h-3 w-3" />
              行追加
            </Button>
          </div>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="border-b bg-muted/50">
                  <th className="px-2 py-2 text-left font-medium w-36">商品コード</th>
                  <th className="px-2 py-2 text-left font-medium">商品名</th>
                  {isAdmin && (
                    <>
                      <th className="px-2 py-2 text-right font-medium w-24">原価</th>
                      <th className="px-2 py-2 text-left font-medium w-40">原価改訂予定</th>
                    </>
                  )}
                  <th className="px-2 py-2 text-right font-medium w-28">見積単価</th>
                  <th className="px-2 py-2 text-right font-medium w-20">入数</th>
                  {isAdmin && (
                    <>
                      <th className="px-2 py-2 text-right font-medium w-20">粗利</th>
                      <th className="px-2 py-2 text-right font-medium w-20">粗利率</th>
                      <th className="px-2 py-2 text-right font-medium w-24">ケース粗利</th>
                    </>
                  )}
                  <th className="px-2 py-2 text-left font-medium w-32">備考</th>
                  <th className="px-2 py-2 text-left font-medium w-32">仕入先</th>
                  <th className="px-2 py-2 text-center font-medium w-16">順</th>
                  <th className="px-2 py-2 w-10"></th>
                </tr>
              </thead>
              <tbody>
                {rows.map((row) => (
                  <tr key={row.id} className="border-b hover:bg-muted/30">
                    {/* 商品コード + 検索ボタン */}
                    <td className="px-2 py-1">
                      <div className="flex gap-1">
                        <Input
                          value={row.goodsCode}
                          onChange={(e) => updateRow(row.id, 'goodsCode', e.target.value)}
                          onBlur={(e) => searchGoodsByCode(row.id, e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') {
                              e.preventDefault()
                              searchGoodsByCode(row.id, row.goodsCode)
                            }
                          }}
                          className="h-8 font-mono text-xs"
                          placeholder="コード/JAN"
                          disabled={!shopNo}
                        />
                        <Button
                          variant="ghost"
                          size="sm"
                          className="h-8 w-8 p-0 shrink-0"
                          onClick={() => openGoodsDialog(row.id)}
                          title="商品検索"
                          disabled={!shopNo}
                        >
                          <Search className="h-3 w-3" />
                        </Button>
                      </div>
                    </td>
                    {/* 商品名 */}
                    <td className="px-2 py-1">
                      <div className="flex items-center gap-1">
                        <Input
                          value={row.goodsName}
                          onChange={(e) => updateRow(row.id, 'goodsName', e.target.value)}
                          className="h-8 text-xs"
                          placeholder="商品名"
                        />
                        {!row.goodsNo && row.goodsName && (
                          <Badge variant="outline" className="shrink-0 text-[10px] px-1 py-0 border-orange-400 text-orange-600">
                            新規
                          </Badge>
                        )}
                      </div>
                      {row.specification && (
                        <span className="ml-1 text-xs text-muted-foreground">
                          {row.specification}
                        </span>
                      )}
                    </td>
                    {/* 原価 (admin) */}
                    {isAdmin && (
                      <>
                        <td className="px-2 py-1">
                          <Input
                            type="number"
                            value={row.purchasePrice ?? ''}
                            onChange={(e) =>
                              updateRow(
                                row.id,
                                'purchasePrice',
                                e.target.value ? Number(e.target.value) : null,
                              )
                            }
                            className="h-8 text-right tabular-nums"
                            min={0}
                          />
                        </td>
                        <td className="px-2 py-1 text-xs text-muted-foreground">
                          {row.pricePlanInfo}
                        </td>
                      </>
                    )}
                    {/* 見積単価 */}
                    <td className="px-2 py-1">
                      <Input
                        type="number"
                        value={row.goodsPrice ?? ''}
                        onChange={(e) =>
                          updateRow(
                            row.id,
                            'goodsPrice',
                            e.target.value ? Number(e.target.value) : null,
                          )
                        }
                        className="h-8 text-right tabular-nums"
                        min={0}
                      />
                    </td>
                    {/* 入数 */}
                    <td className="px-2 py-1">
                      <Input
                        type="number"
                        value={row.containNum ?? ''}
                        onChange={(e) =>
                          updateRow(
                            row.id,
                            'containNum',
                            e.target.value ? Number(e.target.value) : null,
                          )
                        }
                        className="h-8 w-20 text-right tabular-nums"
                        min={1}
                      />
                    </td>
                    {/* 粗利系 (admin) */}
                    {isAdmin && (
                      <>
                        <td className="px-2 py-1 text-right tabular-nums">
                          {fmt(calcProfit(row.goodsPrice, row.purchasePrice))}
                        </td>
                        <td className="px-2 py-1 text-right tabular-nums">
                          {fmtRate(calcProfitRate(row.goodsPrice, row.purchasePrice))}
                        </td>
                        <td className="px-2 py-1 text-right tabular-nums font-medium">
                          {fmt(calcCaseProfit(row.goodsPrice, row.purchasePrice, row.containNum))}
                        </td>
                      </>
                    )}
                    {/* 備考 */}
                    <td className="px-2 py-1">
                      <Input
                        value={row.detailNote}
                        onChange={(e) => updateRow(row.id, 'detailNote', e.target.value)}
                        className="h-8 text-xs"
                        placeholder="備考"
                      />
                    </td>
                    {/* 仕入先 */}
                    <td className="px-2 py-1">
                      {!row.goodsNo ? (
                        <SearchableSelect
                          value={row.supplierNo != null ? String(row.supplierNo) : ''}
                          onValueChange={(v) => updateRow(row.id, 'supplierNo', v ? Number(v) : null)}
                          options={(suppliersQuery.data ?? []).map((s) => ({
                            value: String(s.supplierNo),
                            label: `${s.supplierCode ?? ''} ${s.supplierName}`.trim(),
                          }))}
                          placeholder="仕入先"
                          searchPlaceholder="仕入先を検索..."
                          clearable
                        />
                      ) : (
                        <span className="text-xs text-muted-foreground">
                          {row.supplierNo
                            ? (suppliersQuery.data ?? []).find((s) => s.supplierNo === row.supplierNo)?.supplierName ?? '-'
                            : '-'}
                        </span>
                      )}
                    </td>
                    {/* 表示順 */}
                    <td className="px-2 py-1">
                      <Input
                        type="number"
                        value={row.displayOrder}
                        onChange={(e) =>
                          updateRow(row.id, 'displayOrder', Number(e.target.value) || 1)
                        }
                        className="h-8 w-14 text-center"
                        min={1}
                      />
                    </td>
                    {/* 削除 */}
                    <td className="px-2 py-1 text-center">
                      <Button
                        variant="ghost"
                        size="sm"
                        className="h-8 w-8 p-0 text-destructive hover:text-destructive"
                        onClick={() => removeRow(row.id)}
                      >
                        <Trash2 className="h-3.5 w-3.5" />
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* 商品検索ダイアログ */}
      <GoodsSearchDialog
        open={goodsDialogOpen}
        onOpenChange={(v) => {
          setGoodsDialogOpen(v)
          if (!v) setGoodsDialogTargetRowId(null)
        }}
        shopNo={shopNo}
        onSelect={handleGoodsDialogSelect}
      />
    </div>
  )
}
