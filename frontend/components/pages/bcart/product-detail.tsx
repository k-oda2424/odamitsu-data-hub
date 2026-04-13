'use client'

import { useState, useEffect, useMemo, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { toast } from 'sonner'
import { ArrowLeft, Save, Upload, Loader2, Eye, EyeOff, History } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import type { BCartProduct, BCartProductDescriptionUpdate, BCartChangeHistory, BCartCategory } from '@/types/bcart'
import { api } from '@/lib/api-client'

interface JobStatus {
  jobName: string
  status: string
  startTime?: string
  exitMessage?: string
}

function useBatchWithPolling(jobName: string, label: string) {
  const queryClient = useQueryClient()
  const [polling, setPolling] = useState(false)
  const [launchedAt, setLaunchedAt] = useState<string | null>(null)

  const statusQuery = useQuery({
    queryKey: ['batch-status', jobName],
    queryFn: () => api.get<JobStatus>(`/batch/status/${jobName}`),
    enabled: polling,
    refetchInterval: polling ? 3000 : false,
  })

  useEffect(() => {
    if (!polling || !statusQuery.data || !launchedAt) return
    const s = statusQuery.data
    if (s.startTime && new Date(s.startTime).getTime() < new Date(launchedAt).getTime()) return
    if (s.status === 'COMPLETED') {
      toast.success(`${label}が完了しました`)
      setPolling(false)
      queryClient.invalidateQueries({ queryKey: ['bcart', 'products'] })
    } else if (s.status === 'FAILED') {
      toast.error(`${label}が失敗しました${s.exitMessage ? ': ' + s.exitMessage : ''}`)
      setPolling(false)
    }
  }, [statusQuery.data, polling, launchedAt, label, queryClient])

  const start = useCallback(() => {
    api.post<{ message: string }>(`/batch/execute/${jobName}`).then(() => {
      toast.info(`${label}を起動しました...`)
      setLaunchedAt(new Date().toISOString())
      setPolling(true)
    }).catch(() => toast.error(`${label}の起動に失敗しました`))
  }, [jobName, label])

  return { start, isRunning: polling }
}

export default function BCartProductDetailPage({ productId }: { productId: number }) {
  const router = useRouter()
  const queryClient = useQueryClient()

  const { data: product, isLoading } = useQuery({
    queryKey: ['bcart', 'products', productId],
    queryFn: () => api.get<BCartProduct>(`/bcart/products/${productId}`),
  })

  const { data: history } = useQuery({
    queryKey: ['bcart', 'products', productId, 'history'],
    queryFn: () => api.get<BCartChangeHistory[]>(`/bcart/products/${productId}/history`),
  })

  const { data: categories } = useQuery({
    queryKey: ['bcart', 'categories'],
    queryFn: () => api.get<BCartCategory[]>('/bcart/categories'),
  })

  const allCategories = useMemo(
    () => categories ? categories.flatMap((c) => [c, ...(c.children ?? [])]) : [],
    [categories]
  )

  const reflect = useBatchWithPolling('bCartProductDescriptionUpdate', '商品説明反映')

  const [form, setForm] = useState<BCartProductDescriptionUpdate>({})

  useEffect(() => {
    if (product) {
      setForm({
        name: product.name ?? '',
        catchCopy: product.catchCopy ?? '',
        description: product.description ?? '',
        prependText: product.prependText ?? '',
        appendText: product.appendText ?? '',
        middleText: product.middleText ?? '',
        rvPrependText: product.rvPrependText ?? '',
        rvAppendText: product.rvAppendText ?? '',
        rvMiddleText: product.rvMiddleText ?? '',
        metaTitle: product.metaTitle ?? '',
        metaKeywords: product.metaKeywords ?? '',
        metaDescription: product.metaDescription ?? '',
      })
    }
  }, [product?.id])

  const saveMutation = useMutation({
    mutationFn: () => api.put<BCartProduct>(`/bcart/products/${productId}/description`, form),
    onSuccess: () => {
      toast.success('商品情報を保存しました')
      queryClient.invalidateQueries({ queryKey: ['bcart', 'products', productId] })
    },
    onError: () => toast.error('保存に失敗しました'),
  })

  if (isLoading) return <div className="p-4">読み込み中...</div>
  if (!product) return <div className="p-4">商品が見つかりません</div>

  return (
    <div className="p-4 space-y-4">
      {/* ヘッダー */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-3">
          <Button variant="ghost" size="icon" onClick={() => router.push('/bcart/products')}>
            <ArrowLeft className="h-5 w-5" />
          </Button>
          <div>
            <h1 className="text-2xl font-bold">{product.name}</h1>
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <span>ID: {product.id}</span>
              <span>管理番号: {product.mainNo}</span>
              {product.categoryName && <Badge variant="secondary">{product.categoryName}</Badge>}
              {product.flag === '表示'
                ? <Badge variant="secondary"><Eye className="h-3 w-3 mr-1" />表示</Badge>
                : <Badge variant="outline"><EyeOff className="h-3 w-3 mr-1" />非表示</Badge>}
            </div>
          </div>
        </div>
        <Button variant="outline" size="sm" onClick={reflect.start} disabled={reflect.isRunning}>
          {reflect.isRunning
            ? <Loader2 className="h-4 w-4 mr-1 animate-spin" />
            : <Upload className="h-4 w-4 mr-1" />}
          {reflect.isRunning ? '反映中...' : 'B-CARTに反映'}
        </Button>
      </div>

      <Tabs defaultValue="info">
        <TabsList>
          <TabsTrigger value="info">商品情報</TabsTrigger>
          <TabsTrigger value="sets">セット一覧 ({product.sets?.length ?? 0})</TabsTrigger>
          <TabsTrigger value="history"><History className="h-4 w-4 mr-1" />変更履歴</TabsTrigger>
        </TabsList>

        {/* 商品情報タブ */}
        <TabsContent value="info" className="space-y-4 mt-4">
          <div className="grid gap-4">
            <div className="grid grid-cols-2 gap-4">
              <div>
                <Label>商品名</Label>
                <Input value={form.name ?? ''} onChange={(e) => setForm({ ...form, name: e.target.value })} />
              </div>
              <div>
                <Label>キャッチコピー</Label>
                <Input value={form.catchCopy ?? ''} onChange={(e) => setForm({ ...form, catchCopy: e.target.value })} />
              </div>
            </div>

            <div>
              <Label>商品説明</Label>
              <Textarea rows={6} value={form.description ?? ''} onChange={(e) => setForm({ ...form, description: e.target.value })} />
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div>
                <Label>上部フリースペース（PC）</Label>
                <Textarea rows={3} value={form.prependText ?? ''} onChange={(e) => setForm({ ...form, prependText: e.target.value })} />
              </div>
              <div>
                <Label>中部フリースペース（PC）</Label>
                <Textarea rows={3} value={form.middleText ?? ''} onChange={(e) => setForm({ ...form, middleText: e.target.value })} />
              </div>
              <div>
                <Label>下部フリースペース（PC）</Label>
                <Textarea rows={3} value={form.appendText ?? ''} onChange={(e) => setForm({ ...form, appendText: e.target.value })} />
              </div>
            </div>

            <div className="grid grid-cols-3 gap-4">
              <div>
                <Label>上部フリースペース（レスポンシブ）</Label>
                <Textarea rows={3} value={form.rvPrependText ?? ''} onChange={(e) => setForm({ ...form, rvPrependText: e.target.value })} />
              </div>
              <div>
                <Label>中部フリースペース（レスポンシブ）</Label>
                <Textarea rows={3} value={form.rvMiddleText ?? ''} onChange={(e) => setForm({ ...form, rvMiddleText: e.target.value })} />
              </div>
              <div>
                <Label>下部フリースペース（レスポンシブ）</Label>
                <Textarea rows={3} value={form.rvAppendText ?? ''} onChange={(e) => setForm({ ...form, rvAppendText: e.target.value })} />
              </div>
            </div>

            <div className="space-y-2">
              <h3 className="text-sm font-semibold text-muted-foreground">META情報</h3>
              <div className="grid grid-cols-3 gap-4">
                <div>
                  <Label>title</Label>
                  <Input value={form.metaTitle ?? ''} onChange={(e) => setForm({ ...form, metaTitle: e.target.value })} />
                </div>
                <div>
                  <Label>keywords</Label>
                  <Input value={form.metaKeywords ?? ''} onChange={(e) => setForm({ ...form, metaKeywords: e.target.value })} />
                </div>
                <div>
                  <Label>description</Label>
                  <Input value={form.metaDescription ?? ''} onChange={(e) => setForm({ ...form, metaDescription: e.target.value })} />
                </div>
              </div>
            </div>
          </div>

          <div className="flex gap-2 pt-4">
            <Button onClick={() => saveMutation.mutate()} disabled={saveMutation.isPending}>
              <Save className="h-4 w-4 mr-1" />
              {saveMutation.isPending ? '保存中...' : '保存'}
            </Button>
          </div>
        </TabsContent>

        {/* セット一覧タブ */}
        <TabsContent value="sets" className="mt-4">
          {product.sets && product.sets.length > 0 ? (
            <div className="border rounded-lg overflow-hidden">
              <table className="w-full text-sm">
                <thead className="bg-muted">
                  <tr>
                    <th className="px-3 py-2 text-left">セットID</th>
                    <th className="px-3 py-2 text-left">品番</th>
                    <th className="px-3 py-2 text-left">JANコード</th>
                    <th className="px-3 py-2 text-left">セット名</th>
                    <th className="px-3 py-2 text-right">単価</th>
                    <th className="px-3 py-2 text-right">仕入価格</th>
                    <th className="px-3 py-2 text-center">在庫</th>
                    <th className="px-3 py-2 text-center">価格同期</th>
                  </tr>
                </thead>
                <tbody>
                  {product.sets.map((s) => (
                    <tr key={s.id} className="border-t">
                      <td className="px-3 py-2 text-muted-foreground">{s.id}</td>
                      <td className="px-3 py-2">{s.productNo}</td>
                      <td className="px-3 py-2">{s.janCode}</td>
                      <td className="px-3 py-2">{s.name}</td>
                      <td className="px-3 py-2 text-right">{s.unitPrice?.toLocaleString() ?? '-'}</td>
                      <td className="px-3 py-2 text-right">{s.purchasePrice?.toLocaleString() ?? '-'}</td>
                      <td className="px-3 py-2 text-center">{s.stock ?? '-'}</td>
                      <td className="px-3 py-2 text-center">
                        {s.bCartPriceReflected
                          ? <Badge variant="secondary" className="text-xs">同期済</Badge>
                          : <Badge variant="outline" className="text-xs text-orange-500 border-orange-500">未反映</Badge>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">セットがありません</p>
          )}
        </TabsContent>

        {/* 変更履歴タブ */}
        <TabsContent value="history" className="mt-4">
          {history && history.length > 0 ? (
            <div className="space-y-2">
              {history.map((h) => (
                <div key={h.id} className="flex items-start gap-2 text-sm border-b pb-2">
                  <div className="text-muted-foreground whitespace-nowrap">
                    {new Date(h.changedAt).toLocaleString('ja-JP')}
                  </div>
                  <Badge variant="outline" className="text-xs">{h.changeType}</Badge>
                  {h.fieldName && <span className="text-muted-foreground">{h.fieldName}:</span>}
                  {h.beforeValue && <span className="line-through text-red-500 max-w-40 truncate">{h.beforeValue}</span>}
                  {h.beforeValue && h.afterValue && <span>→</span>}
                  {h.afterValue && <span className="text-green-600 max-w-40 truncate">{h.afterValue}</span>}
                  {h.bCartReflected ? (
                    <Badge variant="secondary" className="text-xs ml-auto">反映済</Badge>
                  ) : (
                    <Badge variant="outline" className="text-xs ml-auto text-orange-500 border-orange-500">未反映</Badge>
                  )}
                </div>
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">変更履歴はありません</p>
          )}
        </TabsContent>
      </Tabs>
    </div>
  )
}
