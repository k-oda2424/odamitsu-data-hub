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
import { Button } from '@/components/ui/button'
import { ArrowLeft, Save } from 'lucide-react'
import { toast } from 'sonner'
import { GoodsSearchDialog, type SelectedGoods } from './GoodsSearchDialog'
import { EstimateHeaderForm } from './EstimateHeaderForm'
import { EstimateDetailTable } from './EstimateDetailTable'
import type {
  EstimateResponse,
  EstimateCreateRequest,
  EstimateGoodsSearchResponse,
} from '@/types/estimate'
import { calcProfitRate } from '@/lib/estimate-calc'

export interface EstimateDetailRow {
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

  const [shopNo, setShopNo] = useState<string>(isAdmin ? '' : String(user?.shopNo ?? ''))
  // 非admin で useAuth が遅延取得された場合に shopNo を埋める（初回のみ）。
  // 依存配列に shopNo を含めないことで、edit/prefill 初期化後の再上書きを防ぐ。
  useEffect(() => {
    if (!isAdmin && user?.shopNo) {
      setShopNo((prev) => (prev === '' ? String(user.shopNo) : prev))
    }
  }, [isAdmin, user?.shopNo])
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

  const [rows, setRows] = useState<EstimateDetailRow[]>([createEmptyRow(1)])
  const [initialized, setInitialized] = useState(false)

  const [goodsDialogOpen, setGoodsDialogOpen] = useState(false)
  const [goodsDialogTargetRowId, setGoodsDialogTargetRowId] = useState<string | null>(null)

  const shopsQuery = useShops(isAdmin)
  const partnersQuery = usePartners(shopNo)
  const destinationsQuery = useDestinations(partnerNo)
  const suppliersQuery = useSuppliers(shopNo)

  const estimateQuery = useQuery({
    queryKey: ['estimate', estimateNo],
    queryFn: () => api.get<EstimateResponse>(`/estimates/${estimateNo}`),
    enabled: isEditMode,
  })

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

  const lastSearchedCodesRef = useRef<Record<string, string>>({})

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
      if (goods.goodsCode && shopNo) {
        searchGoodsByCode(goodsDialogTargetRowId, goods.goodsCode)
      }
    },
    [goodsDialogTargetRowId, shopNo, searchGoodsByCode],
  )

  const openGoodsDialog = useCallback((rowId: string) => {
    setGoodsDialogTargetRowId(rowId)
    setGoodsDialogOpen(true)
  }, [])

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
    // 削除行の検索キャッシュを破棄（immutable に新オブジェクト生成）
    const { [rowId]: _removed, ...rest } = lastSearchedCodesRef.current
    lastSearchedCodesRef.current = rest
  }, [])

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

      <EstimateHeaderForm
        isAdmin={isAdmin}
        shopNo={shopNo}
        onShopNoChange={setShopNo}
        partnerNo={partnerNo}
        onPartnerNoChange={setPartnerNo}
        destinationNo={destinationNo}
        onDestinationNoChange={setDestinationNo}
        estimateDate={estimateDate}
        onEstimateDateChange={setEstimateDate}
        priceChangeDate={priceChangeDate}
        onPriceChangeDateChange={setPriceChangeDate}
        recipientName={recipientName}
        onRecipientNameChange={setRecipientName}
        requirement={requirement}
        onRequirementChange={setRequirement}
        note={note}
        onNoteChange={setNote}
        proposalMessage={proposalMessage}
        onProposalMessageChange={setProposalMessage}
        shopsQuery={shopsQuery}
        partnersQuery={partnersQuery}
        destinationsQuery={destinationsQuery}
      />

      <EstimateDetailTable
        isAdmin={isAdmin}
        shopNo={shopNo}
        rows={rows}
        suppliersQuery={suppliersQuery}
        onUpdateRow={updateRow}
        onAddRow={addRow}
        onRemoveRow={removeRow}
        onSearchByCode={searchGoodsByCode}
        onOpenGoodsDialog={openGoodsDialog}
      />

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
