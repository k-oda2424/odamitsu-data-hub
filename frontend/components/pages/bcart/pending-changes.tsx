'use client'

import { useState, useMemo } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { Diff, Loader2, Upload, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Checkbox } from '@/components/ui/checkbox'
import { Badge } from '@/components/ui/badge'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { api } from '@/lib/api-client'
import type { BCartPendingChange, BCartReflectResult } from '@/types/bcart'

const FIELD_LABELS: Record<string, string> = {
  unit_price: '単価',
  shipping_size: '配送サイズ',
}

export default function BCartPendingChangesPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [lastResult, setLastResult] = useState<BCartReflectResult | null>(null)

  const pendingQuery = useQuery({
    queryKey: ['bcart', 'pending-changes'],
    queryFn: () => api.get<BCartPendingChange[]>('/bcart/pending-changes'),
  })

  const pendingChanges = pendingQuery.data ?? []

  const allChecked = pendingChanges.length > 0 && pendingChanges.every((p) => selected.has(p.productSetId))
  const someChecked = pendingChanges.some((p) => selected.has(p.productSetId))

  const toggleAll = () => {
    if (allChecked) {
      setSelected(new Set())
    } else {
      setSelected(new Set(pendingChanges.map((p) => p.productSetId)))
    }
  }

  const toggleOne = (setId: number) => {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(setId)) next.delete(setId)
      else next.add(setId)
      return next
    })
  }

  const reflectMutation = useMutation({
    mutationFn: (mode: 'selected' | 'all') => {
      if (mode === 'all') {
        return api.post<BCartReflectResult>('/bcart/pending-changes/reflect', { all: true })
      }
      return api.post<BCartReflectResult>('/bcart/pending-changes/reflect', {
        productSetIds: Array.from(selected),
      })
    },
    onSuccess: (result) => {
      setLastResult(result)
      const total = result.succeeded + result.failed + result.skipped
      if (result.failed === 0 && result.succeeded > 0) {
        toast.success(`${result.succeeded}件を B-CART に反映しました`)
      } else if (result.failed > 0) {
        toast.error(`${result.succeeded}件成功 / ${result.failed}件失敗 / ${result.skipped}件スキップ（計${total}件）`)
      } else {
        toast.info(`反映対象がありませんでした（スキップ ${result.skipped}件）`)
      }
      setSelected(new Set())
      queryClient.invalidateQueries({ queryKey: ['bcart', 'pending-changes'] })
      queryClient.invalidateQueries({ queryKey: ['bcart', 'pending-changes-count'] })
      queryClient.invalidateQueries({ queryKey: ['bcart', 'products'] })
    },
    onError: (err: Error) => {
      toast.error(`反映に失敗: ${err.message}`)
    },
  })

  const errorByProductSet = useMemo(() => {
    const map = new Map<number, string>()
    if (lastResult) {
      lastResult.results.filter((r) => r.status === 'FAILED').forEach((r) => {
        if (r.message) map.set(r.productSetId, r.message)
      })
    }
    return map
  }, [lastResult])

  return (
    <div className="space-y-6">
      <PageHeader
        title="B-CART 変更点一覧"
        actions={
          <div className="flex gap-2">
            <Button
              variant="outline"
              size="sm"
              onClick={() => reflectMutation.mutate('selected')}
              disabled={!someChecked || reflectMutation.isPending}
            >
              {reflectMutation.isPending
                ? <Loader2 className="h-4 w-4 mr-1 animate-spin" />
                : <Upload className="h-4 w-4 mr-1" />}
              選択を反映（{selected.size}件）
            </Button>
            <Button
              size="sm"
              onClick={() => reflectMutation.mutate('all')}
              disabled={pendingChanges.length === 0 || reflectMutation.isPending}
            >
              {reflectMutation.isPending
                ? <Loader2 className="h-4 w-4 mr-1 animate-spin" />
                : <Upload className="h-4 w-4 mr-1" />}
              全件反映（{pendingChanges.length}件）
            </Button>
          </div>
        }
      />

      {lastResult && (
        <div className="flex gap-4 rounded-lg border bg-muted/30 px-4 py-3 text-sm">
          <span>前回反映結果:</span>
          <span className="text-green-600">✅ 成功 {lastResult.succeeded}</span>
          <span className="text-red-600">❌ 失敗 {lastResult.failed}</span>
          <span className="text-muted-foreground">⏭ スキップ {lastResult.skipped}</span>
        </div>
      )}

      {pendingQuery.isLoading ? (
        <LoadingSpinner />
      ) : pendingQuery.isError ? (
        <ErrorMessage onRetry={() => pendingQuery.refetch()} />
      ) : pendingChanges.length === 0 ? (
        <div className="rounded-lg border p-8 text-center text-muted-foreground">
          <Diff className="h-8 w-8 mx-auto mb-2 opacity-40" />
          未反映の変更はありません
        </div>
      ) : (
        <div className="rounded-lg border shadow-sm overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted/40">
              <tr className="text-xs uppercase tracking-wider text-muted-foreground">
                <th className="px-3 py-2 w-10">
                  <Checkbox checked={allChecked} onCheckedChange={toggleAll} aria-label="全選択" />
                </th>
                <th className="px-3 py-2 text-left">商品名</th>
                <th className="px-3 py-2 text-left">セット名</th>
                <th className="px-3 py-2 text-left">品番</th>
                <th className="px-3 py-2 text-left">JANコード</th>
                <th className="px-3 py-2 text-left">変更内容</th>
                <th className="px-3 py-2 text-left">最終変更</th>
                <th className="px-3 py-2 w-10"></th>
              </tr>
            </thead>
            <tbody>
              {pendingChanges.map((p) => {
                const failure = errorByProductSet.get(p.productSetId)
                return (
                  <tr key={p.productSetId} className={`border-t ${failure ? 'bg-red-50 dark:bg-red-950' : ''}`}>
                    <td className="px-3 py-2">
                      <Checkbox
                        checked={selected.has(p.productSetId)}
                        onCheckedChange={() => toggleOne(p.productSetId)}
                        aria-label={`${p.productName}を選択`}
                      />
                    </td>
                    <td className="px-3 py-2">{p.productName}</td>
                    <td className="px-3 py-2">{p.setName}</td>
                    <td className="px-3 py-2 text-muted-foreground">{p.productNo}</td>
                    <td className="px-3 py-2 text-muted-foreground tabular-nums">{p.janCode ?? '-'}</td>
                    <td className="px-3 py-2">
                      <div className="space-y-1">
                        {p.changes.map((c) => (
                          <div key={c.field} className="flex items-center gap-2">
                            <Badge variant="outline" className="text-xs">{FIELD_LABELS[c.field] ?? c.field}</Badge>
                            <span className="line-through text-red-500 tabular-nums">{c.before ?? '-'}</span>
                            <span className="text-muted-foreground">→</span>
                            <span className="text-green-600 font-medium tabular-nums">{c.after ?? '-'}</span>
                          </div>
                        ))}
                        {failure && (
                          <div className="text-xs text-red-600 mt-1">エラー: {failure}</div>
                        )}
                      </div>
                    </td>
                    <td className="px-3 py-2 text-muted-foreground text-xs">
                      {p.lastChangedAt ? new Date(p.lastChangedAt).toLocaleString('ja-JP') : '-'}
                    </td>
                    <td className="px-3 py-2">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => router.push(`/bcart/products/${p.productId}`)}
                        aria-label="商品詳細へ"
                      >
                        <ChevronRight className="h-4 w-4" />
                      </Button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
