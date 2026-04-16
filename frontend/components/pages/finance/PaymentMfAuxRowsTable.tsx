'use client'

import { useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Badge } from '@/components/ui/badge'
import { formatCurrency } from '@/lib/utils'
import type { PaymentMfAuxRow, AuxRuleKind } from '@/types/payment-mf'

interface Props {
  transactionMonth: string // yyyy-MM-dd
  onCountChange?: (count: number) => void
}

const RULE_KIND_LABELS: Record<AuxRuleKind, string> = {
  EXPENSE: 'EXPENSE',
  SUMMARY: 'SUMMARY',
  DIRECT_PURCHASE: 'DIRECT_PURCHASE',
}

function AuxRuleKindBadge({ kind }: { kind: AuxRuleKind }) {
  const classes: Record<AuxRuleKind, string> = {
    EXPENSE: 'bg-blue-100 text-blue-800 hover:bg-blue-200',
    SUMMARY: 'bg-amber-100 text-amber-800 hover:bg-amber-200',
    DIRECT_PURCHASE: 'bg-purple-100 text-purple-800 hover:bg-purple-200',
  }
  return <Badge className={classes[kind]}>{RULE_KIND_LABELS[kind]}</Badge>
}

export function PaymentMfAuxRowsTable({ transactionMonth, onCountChange }: Props) {
  const query = useQuery({
    queryKey: ['payment-mf-aux-rows', transactionMonth],
    queryFn: () => {
      const sp = new URLSearchParams()
      sp.set('transactionMonth', transactionMonth)
      return api.get<PaymentMfAuxRow[]>(`/finance/payment-mf/aux-rows?${sp.toString()}`)
    },
    staleTime: 30_000,
  })

  // onCountChange は親側のインライン関数である可能性があるため ref に保持し、
  // コールバック参照の変化で effect が再発火するのを避ける（依存配列は data のみ）。
  const onCountChangeRef = useRef(onCountChange)
  useEffect(() => {
    onCountChangeRef.current = onCountChange
  }, [onCountChange])

  useEffect(() => {
    if (query.data) onCountChangeRef.current?.(query.data.length)
  }, [query.data])

  const columns: Column<PaymentMfAuxRow>[] = [
    { key: 'transferDate', header: '送金日', sortable: true },
    {
      key: 'ruleKind',
      header: '種別',
      render: (r) => <AuxRuleKindBadge kind={r.ruleKind} />,
    },
    { key: 'sourceName', header: '送り先', sortable: true },
    { key: 'debitAccount', header: '借方勘定' },
    { key: 'debitDepartment', header: '借方部門', render: (r) => r.debitDepartment ?? '' },
    { key: 'debitTax', header: '借方税区分' },
    {
      key: 'amount',
      header: '金額',
      render: (r) => <span className="tabular-nums">{formatCurrency(r.amount)}</span>,
    },
    { key: 'creditAccount', header: '貸方勘定' },
    { key: 'creditTax', header: '貸方税区分' },
    { key: 'summary', header: '摘要', render: (r) => r.summary ?? '' },
    {
      key: 'sourceFilename',
      header: 'ソース',
      render: (r) => (
        <span className="text-xs text-muted-foreground" title={r.sourceFilename ?? ''}>
          {r.sourceFilename ?? '-'}
        </span>
      ),
    },
  ]

  if (query.isLoading) return <LoadingSpinner />
  if (query.isError) return <ErrorMessage onRetry={() => query.refetch()} />

  const rows = query.data ?? []

  if (rows.length === 0) {
    return (
      <div className="rounded border border-amber-300 bg-amber-50 p-6 text-sm text-amber-900">
        <p className="font-medium">この取引月の補助行は登録されていません。</p>
        <p className="mt-2">
          振込明細Excel を「振込明細で一括検証」からアップロードすると、
          EXPENSE/SUMMARY/DIRECT_PURCHASE 行が自動的に保存されます。
        </p>
      </div>
    )
  }

  return <DataTable data={rows} columns={columns} rowKey={(r) => r.auxRowId} />
}
