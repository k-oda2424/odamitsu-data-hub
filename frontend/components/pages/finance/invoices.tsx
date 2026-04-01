'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchForm } from '@/components/features/common/SearchForm'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog'
import { formatNumber } from '@/lib/utils'
import { toast } from 'sonner'

interface Invoice {
  invoiceId: number
  partnerCode: string
  partnerName: string
  closingDate: string
  previousBalance: number | null
  totalPayment: number | null
  carryOverBalance: number | null
  netSales: number | null
  taxPrice: number | null
  netSalesIncludingTax: number | null
  currentBillingAmount: number | null
  shopNo: number
  paymentDate: string | null
}

function formatMoney(val: number | null): string {
  if (val == null) return '-'
  return formatNumber(val)
}

const columns: Column<Invoice>[] = [
  { key: 'invoiceId', header: 'ID', sortable: true },
  { key: 'partnerCode', header: '得意先コード', sortable: true },
  { key: 'partnerName', header: '得意先名', sortable: true },
  { key: 'closingDate', header: '締日', sortable: true },
  {
    key: 'previousBalance',
    header: '前回残高',
    render: (item) => <span className="block text-right">{formatMoney(item.previousBalance)}</span>,
  },
  {
    key: 'totalPayment',
    header: '入金合計',
    render: (item) => <span className="block text-right">{formatMoney(item.totalPayment)}</span>,
  },
  {
    key: 'carryOverBalance',
    header: '繰越残高',
    render: (item) => <span className="block text-right">{formatMoney(item.carryOverBalance)}</span>,
  },
  {
    key: 'netSalesIncludingTax',
    header: '税込売上',
    render: (item) => <span className="block text-right">{formatMoney(item.netSalesIncludingTax)}</span>,
  },
  {
    key: 'currentBillingAmount',
    header: '今回請求額',
    render: (item) => <span className="block text-right font-bold">{formatMoney(item.currentBillingAmount)}</span>,
    sortable: true,
  },
  {
    key: 'paymentDate',
    header: '入金日',
    render: (item) => item.paymentDate ?? '',
  },
]

export function InvoiceListPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const isAdmin = user?.shopNo === 0

  // Search state
  const [shopNo, setShopNo] = useState<string>(isAdmin ? '' : String(user?.shopNo ?? ''))
  const [closingDate, setClosingDate] = useState('')
  const [partnerCode, setPartnerCode] = useState('')
  const [partnerName, setPartnerName] = useState('')
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(null)

  // Detail dialog
  const [selectedInvoice, setSelectedInvoice] = useState<Invoice | null>(null)
  const [editPaymentDate, setEditPaymentDate] = useState('')

  const shopsQuery = useShops(isAdmin)

  const invoiceQuery = useQuery({
    queryKey: ['invoices', searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      if (searchParams?.shopNo) params.append('shopNo', searchParams.shopNo)
      if (searchParams?.closingDate) params.append('closingDate', searchParams.closingDate)
      if (searchParams?.partnerCode) params.append('partnerCode', searchParams.partnerCode)
      if (searchParams?.partnerName) params.append('partnerName', searchParams.partnerName)
      const qs = params.toString()
      return api.get<Invoice[]>(`/finance/invoices${qs ? `?${qs}` : ''}`)
    },
    enabled: searchParams !== null,
  })

  const paymentDateMutation = useMutation({
    mutationFn: ({ invoiceId, paymentDate }: { invoiceId: number; paymentDate: string | null }) =>
      api.put<Invoice>(`/finance/invoices/${invoiceId}/payment-date`, { paymentDate }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      setSelectedInvoice(null)
      toast.success('入金日を更新しました')
    },
    onError: () => {
      toast.error('入金日の更新に失敗しました')
    },
  })

  const handleSearch = () => {
    setSearchParams({
      shopNo,
      closingDate: closingDate ? closingDate.replace('-', '/') : '',
      partnerCode,
      partnerName,
    })
  }

  const handleReset = () => {
    setClosingDate('')
    setPartnerCode('')
    setPartnerName('')
    setSearchParams(null)
  }

  const handleRowClick = (invoice: Invoice) => {
    setSelectedInvoice(invoice)
    setEditPaymentDate(invoice.paymentDate ?? '')
  }

  const handleSavePaymentDate = () => {
    if (!selectedInvoice) return
    paymentDateMutation.mutate({
      invoiceId: selectedInvoice.invoiceId,
      paymentDate: editPaymentDate || null,
    })
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader title="請求書一覧" />

      <SearchForm onSearch={handleSearch} onReset={handleReset}>
        {isAdmin && (
          <div className="space-y-2">
            <Label>ショップ</Label>
            <Select value={shopNo} onValueChange={setShopNo}>
              <SelectTrigger>
                <SelectValue placeholder="全て" />
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
          <Label>締月</Label>
          <Input
            type="month"
            value={closingDate}
            onChange={(e) => setClosingDate(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>得意先コード</Label>
          <Input
            placeholder="得意先コードを入力"
            value={partnerCode}
            onChange={(e) => setPartnerCode(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>得意先名</Label>
          <Input
            placeholder="得意先名を入力"
            value={partnerName}
            onChange={(e) => setPartnerName(e.target.value)}
          />
        </div>
      </SearchForm>

      {!hasSearched ? (
        <div className="rounded-lg border p-8 text-center text-muted-foreground">
          検索条件を入力して「検索」ボタンを押してください
        </div>
      ) : invoiceQuery.isLoading ? (
        <LoadingSpinner />
      ) : invoiceQuery.isError ? (
        <ErrorMessage onRetry={() => invoiceQuery.refetch()} />
      ) : (
        <DataTable
          data={invoiceQuery.data ?? []}
          columns={columns}
          searchPlaceholder="テーブル内を検索..."
          onRowClick={handleRowClick}
        />
      )}

      {/* 詳細Dialog */}
      <Dialog open={!!selectedInvoice} onOpenChange={(open) => { if (!open) setSelectedInvoice(null) }}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>請求詳細</DialogTitle>
          </DialogHeader>
          {selectedInvoice && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-3 text-sm">
                <div>
                  <span className="text-muted-foreground">得意先コード</span>
                  <div className="font-medium">{selectedInvoice.partnerCode}</div>
                </div>
                <div>
                  <span className="text-muted-foreground">得意先名</span>
                  <div className="font-medium">{selectedInvoice.partnerName}</div>
                </div>
                <div>
                  <span className="text-muted-foreground">締日</span>
                  <div className="font-medium">{selectedInvoice.closingDate}</div>
                </div>
                <div />
                <div>
                  <span className="text-muted-foreground">前回請求残高</span>
                  <div className="font-medium">{formatMoney(selectedInvoice.previousBalance)}</div>
                </div>
                <div>
                  <span className="text-muted-foreground">入金合計</span>
                  <div className="font-medium">{formatMoney(selectedInvoice.totalPayment)}</div>
                </div>
                <div>
                  <span className="text-muted-foreground">繰越残高</span>
                  <div className="font-medium">{formatMoney(selectedInvoice.carryOverBalance)}</div>
                </div>
                <div />
                <div>
                  <span className="text-muted-foreground">純売上</span>
                  <div className="font-medium">{formatMoney(selectedInvoice.netSales)}</div>
                </div>
                <div>
                  <span className="text-muted-foreground">消費税額</span>
                  <div className="font-medium">{formatMoney(selectedInvoice.taxPrice)}</div>
                </div>
                <div>
                  <span className="text-muted-foreground">税込売上</span>
                  <div className="font-medium">{formatMoney(selectedInvoice.netSalesIncludingTax)}</div>
                </div>
                <div>
                  <span className="text-muted-foreground">今回請求額</span>
                  <div className="text-lg font-bold">{formatMoney(selectedInvoice.currentBillingAmount)}</div>
                </div>
              </div>

              <div className="border-t pt-4 space-y-2">
                <Label>入金日</Label>
                <div className="flex items-center gap-3">
                  <Input
                    type="date"
                    value={editPaymentDate}
                    onChange={(e) => setEditPaymentDate(e.target.value)}
                    className="w-48"
                  />
                  <Button
                    onClick={handleSavePaymentDate}
                    disabled={paymentDateMutation.isPending}
                    size="sm"
                  >
                    {paymentDateMutation.isPending ? '更新中...' : '入金日を更新'}
                  </Button>
                </div>
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  )
}
