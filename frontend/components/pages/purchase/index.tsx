'use client'

import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'next/navigation'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops, usePaymentSuppliers, useSuppliers } from '@/hooks/use-master-data'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle,
} from '@/components/ui/dialog'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatCurrency, formatDate } from '@/lib/utils'
import type {
  PurchaseHeaderResponse, PurchaseListResponse, PurchaseDetailResponse,
} from '@/types/purchase'

interface SearchState {
  shopNo: string
  paymentSupplierNo: string
  supplierNo: string
  fromDate: string
  toDate: string
  purchaseNo: string
  searched: Record<string, string> | null
}

const columns: Column<PurchaseHeaderResponse>[] = [
  { key: 'purchaseNo', header: '仕入No', sortable: true, render: (r) => String(r.purchaseNo) },
  { key: 'purchaseCode', header: '仕入コード', render: (r) => r.purchaseCode ?? '' },
  { key: 'purchaseDate', header: '仕入日', sortable: true, render: (r) => r.purchaseDate ? formatDate(r.purchaseDate) : '' },
  { key: 'supplierName', header: '仕入先', sortable: true, render: (r) => r.supplierName ?? `#${r.supplierNo}` },
  { key: 'taxRate', header: '税率', render: (r) => r.taxRate != null ? `${r.taxRate}%` : '' },
  { key: 'purchaseAmount', header: '税抜金額', render: (r) => <span className="tabular-nums">{formatCurrency(r.purchaseAmount ?? 0)}</span> },
  { key: 'includeTaxAmount', header: '税込金額', render: (r) => <span className="tabular-nums">{formatCurrency(r.includeTaxAmount ?? 0)}</span> },
  { key: 'note', header: '備考', render: (r) => r.note ?? '' },
]

export function PurchaseListPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0

  const urlParams = useSearchParams()
  const [drilldownApplied, setDrilldownApplied] = useState(false)

  const initial: SearchState = {
    shopNo: isAdmin ? '' : String(user?.shopNo ?? ''),
    paymentSupplierNo: '',
    supplierNo: '',
    fromDate: '',
    toDate: '',
    purchaseNo: '',
    searched: null,
  }
  const [state, setState] = useState<SearchState>(initial)

  const shopsQuery = useShops(isAdmin)
  const effectiveShopNo = state.shopNo || (isAdmin ? '' : String(user?.shopNo ?? ''))
  const paymentSuppliersQuery = usePaymentSuppliers(effectiveShopNo)
  const suppliersQuery = useSuppliers(effectiveShopNo)

  const supplierNameParam = urlParams.get('supplierName')
  const transactionMonthParam = urlParams.get('transactionMonth')

  const drilldownPatch = useMemo(() => {
    const shopParam = urlParams.get('shopNo')
    const psParam = urlParams.get('paymentSupplierNo')
    const sParam = urlParams.get('supplierNo')
    const fromParam = urlParams.get('fromDate')
    const toParam = urlParams.get('toDate')
    if (!shopParam && !psParam && !sParam && !fromParam && !toParam) return null
    const searched: Record<string, string> = {}
    if (shopParam) searched.shopNo = shopParam
    if (psParam) searched.paymentSupplierNo = psParam
    if (sParam) searched.supplierNo = sParam
    if (fromParam) searched.fromDate = fromParam
    if (toParam) searched.toDate = toParam
    return { shopParam, psParam, sParam, fromParam, toParam, searched }
  }, [urlParams])

  useEffect(() => {
    if (drilldownApplied) return
    if (isAdmin && !shopsQuery.data) return
    if (!drilldownPatch) {
      setDrilldownApplied(true)
      return
    }
    setState((prev) => ({
      ...prev,
      shopNo: drilldownPatch.shopParam ?? prev.shopNo,
      paymentSupplierNo: drilldownPatch.psParam ?? '',
      supplierNo: drilldownPatch.sParam ?? '',
      fromDate: drilldownPatch.fromParam ?? '',
      toDate: drilldownPatch.toParam ?? '',
      searched: drilldownPatch.searched,
    }))
    setDrilldownApplied(true)
  }, [shopsQuery.data, isAdmin, drilldownApplied, drilldownPatch])

  const queryString = useMemo(() => {
    if (!state.searched) return null
    const sp = new URLSearchParams()
    Object.entries(state.searched).forEach(([k, v]) => {
      if (v) sp.set(k, v)
    })
    return sp.toString()
  }, [state.searched])

  const listQuery = useQuery({
    queryKey: ['purchases', queryString],
    queryFn: () => api.get<PurchaseListResponse>(`/purchases?${queryString}`),
    enabled: queryString !== null,
  })

  const handleSearch = () => {
    setState((prev) => {
      const s: Record<string, string> = {}
      if (prev.shopNo) s.shopNo = prev.shopNo
      if (prev.paymentSupplierNo) s.paymentSupplierNo = prev.paymentSupplierNo
      if (prev.supplierNo) s.supplierNo = prev.supplierNo
      if (prev.fromDate) s.fromDate = prev.fromDate
      if (prev.toDate) s.toDate = prev.toDate
      if (prev.purchaseNo) s.purchaseNo = prev.purchaseNo
      return { ...prev, searched: s }
    })
  }

  const handleReset = () => {
    setState(initial)
  }

  const [detailRow, setDetailRow] = useState<PurchaseHeaderResponse | null>(null)

  return (
    <div className="space-y-4">
      <PageHeader title="仕入一覧" />

      {(state.paymentSupplierNo || supplierNameParam || transactionMonthParam) && state.searched && (
        <div className="flex items-center justify-between rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm">
          <div>
            🔍 <b>絞り込み中</b>
            {supplierNameParam && <>: <span className="font-medium">{decodeURIComponent(supplierNameParam)}</span></>}
            {transactionMonthParam && <>（買掛取引月: <code>{transactionMonthParam}</code> 対応期間）</>}
          </div>
        </div>
      )}

      <div className="rounded border p-4 grid grid-cols-1 md:grid-cols-3 gap-3">
        {isAdmin && (
          <div className="space-y-1">
            <Label>店舗</Label>
            <Select value={state.shopNo} onValueChange={(v) => setState({ ...state, shopNo: v })}>
              <SelectTrigger><SelectValue placeholder="店舗を選択" /></SelectTrigger>
              <SelectContent>
                {(shopsQuery.data ?? []).map((s) => (
                  <SelectItem key={s.shopNo} value={String(s.shopNo)}>{s.shopName}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        <div className="space-y-1">
          <Label>支払先</Label>
          <SearchableSelect
            value={state.paymentSupplierNo}
            onValueChange={(v) => setState({ ...state, paymentSupplierNo: v, supplierNo: '' })}
            options={(paymentSuppliersQuery.data ?? []).map((p) => ({
              value: String(p.paymentSupplierNo),
              label: `${p.paymentSupplierCode ?? ''} ${p.paymentSupplierName}`.trim(),
            }))}
            searchPlaceholder="支払先を検索..."
            clearable
          />
        </div>
        <div className="space-y-1">
          <Label>仕入先（個別）</Label>
          <SearchableSelect
            value={state.supplierNo}
            onValueChange={(v) => setState({ ...state, supplierNo: v, paymentSupplierNo: '' })}
            options={(suppliersQuery.data ?? []).map((s) => ({
              value: String(s.supplierNo),
              label: s.supplierName,
            }))}
            searchPlaceholder="仕入先を検索..."
            clearable
          />
        </div>
        <div className="space-y-1">
          <Label>仕入日 from</Label>
          <Input type="date" value={state.fromDate}
            onChange={(e) => setState({ ...state, fromDate: e.target.value })} />
        </div>
        <div className="space-y-1">
          <Label>仕入日 to</Label>
          <Input type="date" value={state.toDate}
            onChange={(e) => setState({ ...state, toDate: e.target.value })} />
        </div>
        <div className="space-y-1">
          <Label>仕入No</Label>
          <Input type="number" value={state.purchaseNo}
            onChange={(e) => setState({ ...state, purchaseNo: e.target.value })} />
        </div>
        <div className="md:col-span-3 flex justify-end gap-2">
          <Button variant="outline" onClick={handleReset}>リセット</Button>
          <Button onClick={handleSearch}>検索</Button>
        </div>
      </div>

      {state.searched && listQuery.data?.summary && (
        <div className="rounded border bg-card p-4">
          <div className="text-sm font-medium mb-2">集計（明細単位）</div>
          <table className="w-full text-sm">
            <thead className="bg-muted/40">
              <tr>
                <th className="p-1 text-left">税率</th>
                <th className="p-1 text-right">明細件数</th>
                <th className="p-1 text-right">税抜</th>
                <th className="p-1 text-right">消費税</th>
                <th className="p-1 text-right">税込</th>
              </tr>
            </thead>
            <tbody>
              {listQuery.data.summary.byTaxRate.map((b) => (
                <tr key={String(b.taxRate)} className="border-t">
                  <td className="p-1">{b.taxRate}%</td>
                  <td className="p-1 text-right tabular-nums">{b.rows.toLocaleString()}</td>
                  <td className="p-1 text-right tabular-nums">{formatCurrency(b.amountExcTax)}</td>
                  <td className="p-1 text-right tabular-nums">{formatCurrency(b.taxAmount)}</td>
                  <td className="p-1 text-right tabular-nums">{formatCurrency(b.amountIncTax)}</td>
                </tr>
              ))}
              <tr className="border-t bg-muted/20 font-semibold">
                <td className="p-1">合計</td>
                <td className="p-1 text-right tabular-nums">{listQuery.data.summary.totalRows.toLocaleString()}</td>
                <td className="p-1 text-right tabular-nums">{formatCurrency(listQuery.data.summary.totalAmountExcTax)}</td>
                <td className="p-1 text-right tabular-nums">{formatCurrency(listQuery.data.summary.totalTaxAmount)}</td>
                <td className="p-1 text-right tabular-nums">{formatCurrency(listQuery.data.summary.totalAmountIncTax)}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}

      {!state.searched ? (
        <div className="rounded-lg border p-8 text-center text-muted-foreground">
          検索条件を入力して「検索」を押してください
        </div>
      ) : listQuery.isLoading ? <LoadingSpinner />
        : listQuery.isError ? <ErrorMessage onRetry={() => listQuery.refetch()} />
        : (
        <DataTable
          data={listQuery.data?.rows ?? []}
          columns={columns}
          searchPlaceholder="テーブル内検索..."
          onRowClick={(r) => setDetailRow(r)}
        />
      )}

      <Dialog open={detailRow !== null} onOpenChange={(o) => { if (!o) setDetailRow(null) }}>
        <DialogContent className="max-w-5xl">
          <DialogHeader>
            <DialogTitle>
              仕入明細 — No. {detailRow?.purchaseNo} / {detailRow?.purchaseDate} / {detailRow?.supplierName}
            </DialogTitle>
          </DialogHeader>
          {detailRow && <PurchaseDetails purchaseNo={detailRow.purchaseNo} />}
        </DialogContent>
      </Dialog>
    </div>
  )
}

function PurchaseDetails({ purchaseNo }: { purchaseNo: number }) {
  const { data, isLoading, isError } = useQuery({
    queryKey: ['purchase-details', purchaseNo],
    queryFn: () => api.get<PurchaseDetailResponse[]>(`/purchases/${purchaseNo}/details`),
  })
  if (isLoading) return <LoadingSpinner />
  if (isError) return <div className="text-red-600">明細取得失敗</div>
  return (
    <div className="overflow-auto">
      <table className="w-full text-xs">
        <thead className="sticky top-0 bg-muted">
          <tr>
            <th className="p-1">明細No</th>
            <th className="p-1">商品コード</th>
            <th className="p-1">商品名</th>
            <th className="p-1 text-right">数量</th>
            <th className="p-1 text-right">単価</th>
            <th className="p-1">税率</th>
            <th className="p-1 text-right">税抜小計</th>
            <th className="p-1 text-right">税込小計</th>
          </tr>
        </thead>
        <tbody>
          {(data ?? []).map((d) => (
            <tr key={d.purchaseDetailNo} className="border-t">
              <td className="p-1">{d.purchaseDetailNo}</td>
              <td className="p-1">{d.goodsCode ?? ''}</td>
              <td className="p-1">{d.goodsName ?? ''}</td>
              <td className="p-1 text-right tabular-nums">{d.goodsNum?.toLocaleString() ?? ''}</td>
              <td className="p-1 text-right tabular-nums">{d.goodsPrice?.toLocaleString() ?? ''}</td>
              <td className="p-1">{d.taxRate != null ? `${d.taxRate}%` : ''}</td>
              <td className="p-1 text-right tabular-nums">{formatCurrency(d.subtotal ?? 0)}</td>
              <td className="p-1 text-right tabular-nums">{formatCurrency(d.includeTaxSubtotal ?? 0)}</td>
            </tr>
          ))}
          {(data ?? []).length === 0 && (
            <tr><td colSpan={8} className="p-4 text-center text-muted-foreground">明細なし</td></tr>
          )}
        </tbody>
      </table>
    </div>
  )
}
