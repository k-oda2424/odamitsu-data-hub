'use client'

import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchForm } from '@/components/features/common/SearchForm'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { formatNumber } from '@/lib/utils'
import { Upload, Plus } from 'lucide-react'
import { toast } from 'sonner'
import { InvoiceImportDialog } from './InvoiceImportDialog'
import { PartnerGroupDialog } from './PartnerGroupDialog'

// ==================== Types ====================

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

interface PartnerGroup {
  partnerGroupId: number
  groupName: string
  shopNo: number
  partnerCodes: string[]
}

function formatMoney(val: number | null): string {
  if (val == null) return '-'
  return formatNumber(val)
}

// F1: Checkbox cell extracted to avoid full-table re-render on toggle
function SelectCell({ invoiceId, selectedIds, onToggle }: {
  invoiceId: number
  selectedIds: Set<number>
  onToggle: (id: number) => void
}) {
  return (
    <Checkbox
      checked={selectedIds.has(invoiceId)}
      onCheckedChange={() => onToggle(invoiceId)}
      onClick={(e) => e.stopPropagation()}
    />
  )
}

// F2: Date cell with key to force remount on data refresh
function PaymentDateCell({ invoiceId, paymentDate, onUpdate }: {
  invoiceId: number
  paymentDate: string | null
  onUpdate: (id: number, val: string) => void
}) {
  return (
    <Input
      key={`${invoiceId}-${paymentDate ?? ''}`}
      type="date"
      defaultValue={paymentDate ?? ''}
      className="h-7 w-36 text-sm"
      onClick={(e) => e.stopPropagation()}
      onBlur={(e) => {
        const newVal = e.target.value
        if (newVal !== (paymentDate ?? '')) {
          onUpdate(invoiceId, newVal)
        }
      }}
    />
  )
}

// ==================== Main Component ====================

export function InvoiceListPage() {
  const { user } = useAuth()
  const queryClient = useQueryClient()
  const isAdmin = user?.shopNo === 0
  const userShopNo = user?.shopNo ?? 1

  // Search state
  const [shopNo, setShopNo] = useState<string>(isAdmin ? '' : String(userShopNo))
  const [closingDate, setClosingDate] = useState('')
  const [partnerCode, setPartnerCode] = useState('')
  const [partnerName, setPartnerName] = useState('')
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(null)

  // Selection state
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set())
  const [bulkPaymentDate, setBulkPaymentDate] = useState('')
  const [filterGroupId, setFilterGroupId] = useState<string | null>(null)

  // Dialog state
  const [importDialogOpen, setImportDialogOpen] = useState(false)
  const [groupDialogOpen, setGroupDialogOpen] = useState(false)

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

  const effectiveShopNo = searchParams?.shopNo ? Number(searchParams.shopNo) : userShopNo
  const groupsQuery = useQuery({
    queryKey: ['partner-groups', effectiveShopNo],
    queryFn: () => api.get<PartnerGroup[]>(`/finance/partner-groups?shopNo=${effectiveShopNo}`),
  })

  // F6: use .mutate directly (stable ref per TanStack Query)
  const paymentDateMutation = useMutation({
    mutationFn: ({ invoiceId, paymentDate }: { invoiceId: number; paymentDate: string | null }) =>
      api.put<Invoice>(`/finance/invoices/${invoiceId}/payment-date`, { paymentDate }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
    },
    onError: () => {
      toast.error('入金日の更新に失敗しました')
    },
  })

  const bulkPaymentDateMutation = useMutation({
    mutationFn: (data: { invoiceIds: number[]; paymentDate: string }) =>
      api.put<{ updatedCount: number }>('/finance/invoices/bulk-payment-date', data),
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['invoices'] })
      setSelectedIds(new Set())
      setBulkPaymentDate('')
      setFilterGroupId(null)
      toast.success(`${result.updatedCount}件の入金日を更新しました`)
    },
    onError: () => {
      toast.error('一括更新に失敗しました')
    },
  })

  // ==================== Handlers ====================

  const handlePaymentDateChange = (invoiceId: number, value: string) => {
    paymentDateMutation.mutate({ invoiceId, paymentDate: value || null })
  }

  const handleSearch = () => {
    setSelectedIds(new Set())
    setFilterGroupId(null)
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
    setSelectedIds(new Set())
    setFilterGroupId(null)
  }

  const toggleSelect = (invoiceId: number) => {
    setSelectedIds((prev) => {
      const next = new Set(prev)
      if (next.has(invoiceId)) { next.delete(invoiceId) } else { next.add(invoiceId) }
      return next
    })
  }

  // F3: memoize filtered data with Set for O(1) lookup
  const allInvoices = invoiceQuery.data ?? []
  const filterGroup = filterGroupId
    ? (groupsQuery.data ?? []).find((g) => String(g.partnerGroupId) === filterGroupId)
    : null

  const invoices = useMemo(() => {
    if (!filterGroup) return allInvoices
    const codeSet = new Set(filterGroup.partnerCodes)
    return allInvoices.filter((inv) => codeSet.has(inv.partnerCode))
  }, [allInvoices, filterGroup])

  const allSelected = invoices.length > 0 && selectedIds.size === invoices.length

  // F5: toggleSelectAll operates on filtered invoices
  const toggleSelectAll = () => {
    if (allSelected) {
      setSelectedIds(new Set())
    } else {
      setSelectedIds(new Set(invoices.map((inv) => inv.invoiceId)))
    }
  }

  const handleGroupSelect = (groupId: string) => {
    if (groupId === '__none__') {
      setSelectedIds(new Set())
      setFilterGroupId(null)
      return
    }
    const group = (groupsQuery.data ?? []).find((g) => String(g.partnerGroupId) === groupId)
    if (!group) return
    setFilterGroupId(groupId)
    const codeSet = new Set(group.partnerCodes)
    const matchingIds = allInvoices
      .filter((inv) => codeSet.has(inv.partnerCode))
      .map((inv) => inv.invoiceId)
    setSelectedIds(new Set(matchingIds))
  }

  const handleBulkPaymentDate = () => {
    if (selectedIds.size === 0 || !bulkPaymentDate) return
    bulkPaymentDateMutation.mutate({
      invoiceIds: Array.from(selectedIds),
      paymentDate: bulkPaymentDate,
    })
  }

  // F1: columns without selectedIds dependency — checkbox uses extracted SelectCell
  const columns: Column<Invoice>[] = useMemo(() => [
    {
      key: '_select',
      header: '',
      render: (item: Invoice) => (
        <SelectCell invoiceId={item.invoiceId} selectedIds={selectedIds} onToggle={toggleSelect} />
      ),
    },
    {
      key: 'partnerCode',
      header: '得意先',
      sortable: true,
      render: (item: Invoice) => (
        <div>
          <div className="text-xs text-muted-foreground">{item.partnerCode}</div>
          <div>{item.partnerName}</div>
        </div>
      ),
    },
    { key: 'closingDate', header: '締日', sortable: true },
    {
      key: 'previousBalance',
      header: '前回残高 / 入金',
      render: (item: Invoice) => (
        <div className="text-right tabular-nums">
          <div>{formatMoney(item.previousBalance)}</div>
          <div className="text-xs text-muted-foreground">{formatMoney(item.totalPayment)}</div>
        </div>
      ),
    },
    {
      key: 'carryOverBalance',
      header: '繰越残高',
      render: (item: Invoice) => <span className="block text-right tabular-nums">{formatMoney(item.carryOverBalance)}</span>,
    },
    {
      key: 'netSalesIncludingTax',
      header: '税込売上',
      render: (item: Invoice) => <span className="block text-right tabular-nums">{formatMoney(item.netSalesIncludingTax)}</span>,
    },
    {
      key: 'currentBillingAmount',
      header: '今回請求額',
      render: (item: Invoice) => <span className="block text-right tabular-nums font-bold">{formatMoney(item.currentBillingAmount)}</span>,
      sortable: true,
    },
    {
      key: 'paymentDate',
      header: '入金日',
      render: (item: Invoice) => (
        <PaymentDateCell invoiceId={item.invoiceId} paymentDate={item.paymentDate} onUpdate={handlePaymentDateChange} />
      ),
    },
  ], []) // eslint-disable-line react-hooks/exhaustive-deps -- render functions use closures that read latest state via SelectCell/PaymentDateCell props

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader
        title="請求書一覧"
        actions={
          <Button variant="outline" onClick={() => setImportDialogOpen(true)}>
            <Upload className="mr-2 h-4 w-4" />
            インポート
          </Button>
        }
      />

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
          <Input type="month" value={closingDate} onChange={(e) => setClosingDate(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label>得意先コード</Label>
          <Input placeholder="得意先コードを入力" value={partnerCode} onChange={(e) => setPartnerCode(e.target.value)} />
        </div>
        <div className="space-y-2">
          <Label>得意先名</Label>
          <Input placeholder="得意先名を入力" value={partnerName} onChange={(e) => setPartnerName(e.target.value)} />
        </div>
      </SearchForm>

      {/* Bulk action bar */}
      {hasSearched && invoices.length > 0 && (
        <div className="flex items-center gap-3 rounded-lg border bg-muted/30 px-4 py-3">
          <Checkbox
            checked={allSelected}
            onCheckedChange={toggleSelectAll}
            aria-label="全て選択"
          />
          <span className="text-sm text-muted-foreground">
            {selectedIds.size > 0 ? `${selectedIds.size}件選択中` : '全選択'}
          </span>
          <div className="mx-2 h-5 w-px bg-border" />
          <div className="w-72">
            <SearchableSelect
              value={filterGroupId ?? ''}
              onValueChange={(val) => handleGroupSelect(val || '__none__')}
              options={(groupsQuery.data ?? []).map((g) => ({
                value: String(g.partnerGroupId),
                label: `${g.groupName}（${g.partnerCodes.length}件）`,
              }))}
              placeholder="グループ選択"
              searchPlaceholder="グループ名を検索..."
              emptyMessage="グループが見つかりません"
            />
          </div>
          <Button variant="ghost" size="sm" onClick={() => setGroupDialogOpen(true)}>
            <Plus className="mr-1 h-3 w-3" />
            グループ管理
          </Button>
          <div className="ml-auto flex items-center gap-2">
            <Input
              type="date"
              value={bulkPaymentDate}
              onChange={(e) => setBulkPaymentDate(e.target.value)}
              className="h-8 w-40 text-sm"
            />
            <Button
              size="sm"
              onClick={handleBulkPaymentDate}
              disabled={selectedIds.size === 0 || !bulkPaymentDate || bulkPaymentDateMutation.isPending}
            >
              {bulkPaymentDateMutation.isPending ? '更新中...' : `一括反映（${selectedIds.size}件）`}
            </Button>
          </div>
        </div>
      )}

      {/* グループ合計 */}
      {filterGroup && invoices.length > 0 && (
        <div className="flex items-center gap-6 rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm dark:border-blue-900 dark:bg-blue-950">
          <span className="font-medium">{filterGroup.groupName} 合計（{invoices.length}件）</span>
          <div className="flex items-center gap-1">
            <span className="text-muted-foreground">税込売上:</span>
            <span className="tabular-nums font-medium">
              {formatMoney(invoices.reduce((sum, inv) => sum + (inv.netSalesIncludingTax ?? 0), 0))}
            </span>
          </div>
          <div className="flex items-center gap-1">
            <span className="text-muted-foreground">今回請求額:</span>
            <span className="tabular-nums font-bold">
              {formatMoney(invoices.reduce((sum, inv) => sum + (inv.currentBillingAmount ?? 0), 0))}
            </span>
          </div>
        </div>
      )}

      {!hasSearched ? (
        <div className="rounded-lg border p-8 text-center text-muted-foreground">
          検索条件を入力して「検索」ボタンを押してください
        </div>
      ) : invoiceQuery.isLoading ? (
        <LoadingSpinner />
      ) : invoiceQuery.isError ? (
        <ErrorMessage onRetry={() => invoiceQuery.refetch()} />
      ) : (
        <DataTable data={invoices} columns={columns} searchPlaceholder="テーブル内を検索..." />
      )}

      <InvoiceImportDialog open={importDialogOpen} onOpenChange={setImportDialogOpen} />
      <PartnerGroupDialog
        open={groupDialogOpen}
        onOpenChange={setGroupDialogOpen}
        groups={groupsQuery.data ?? []}
        shopNo={effectiveShopNo}
      />
    </div>
  )
}
