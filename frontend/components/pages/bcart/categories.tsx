'use client'

import { useState, useMemo, useEffect, useCallback } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ChevronRight, FolderOpen, Folder, RefreshCw, Eye, EyeOff, Save, History, Upload, Loader2 } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Switch } from '@/components/ui/switch'
import type { ChangeEvent } from 'react'
import type { BCartCategory, BCartCategoryUpdateRequest, BCartChangeHistory } from '@/types/bcart'
import { api } from '@/lib/api-client'

function fetchCategories(): Promise<BCartCategory[]> {
  return api.get<BCartCategory[]>('/bcart/categories')
}

function fetchCategoryHistory(categoryId: number): Promise<BCartChangeHistory[]> {
  return api.get<BCartChangeHistory[]>(`/bcart/categories/${categoryId}/history`)
}

function updateCategory(categoryId: number, data: BCartCategoryUpdateRequest): Promise<BCartCategory> {
  return api.put<BCartCategory>(`/bcart/categories/${categoryId}`, data)
}

function executeBatch(jobName: string): Promise<{ message: string }> {
  return api.post<{ message: string }>(`/batch/execute/${jobName}`)
}

function CategoryTreeNode({
  category,
  selectedId,
  onSelect,
}: {
  category: BCartCategory
  selectedId: number | null
  onSelect: (cat: BCartCategory) => void
}) {
  const [open, setOpen] = useState(true)
  const hasChildren = category.children && category.children.length > 0
  const isSelected = selectedId === category.id

  return (
    <div>
      {hasChildren ? (
        <Collapsible open={open} onOpenChange={setOpen}>
          <div
            className={`flex items-center gap-1 px-2 py-1.5 rounded cursor-pointer hover:bg-muted ${isSelected ? 'bg-primary/10 font-bold' : ''}`}
            onClick={() => onSelect(category)}
          >
            <CollapsibleTrigger asChild onClick={(e) => e.stopPropagation()}>
              <Button variant="ghost" size="icon" className="h-5 w-5 p-0">
                <ChevronRight className={`h-4 w-4 transition-transform ${open ? 'rotate-90' : ''}`} />
              </Button>
            </CollapsibleTrigger>
            {open ? <FolderOpen className="h-4 w-4 text-amber-500" /> : <Folder className="h-4 w-4 text-amber-500" />}
            <span className="text-sm flex-1">{category.name}</span>
            {category.flag === 0 && <EyeOff className="h-3 w-3 text-muted-foreground" />}
            {!category.bCartReflected && <Badge variant="outline" className="text-xs px-1 py-0 text-orange-500 border-orange-500">未反映</Badge>}
            <Badge variant="secondary" className="text-xs px-1 py-0">{category.children?.length ?? 0}</Badge>
          </div>
          <CollapsibleContent className="pl-6">
            {category.children?.map((child) => (
              <CategoryTreeNode key={child.id} category={child} selectedId={selectedId} onSelect={onSelect} />
            ))}
          </CollapsibleContent>
        </Collapsible>
      ) : (
        <div
          className={`flex items-center gap-1 px-2 py-1.5 pl-8 rounded cursor-pointer hover:bg-muted ${isSelected ? 'bg-primary/10 font-bold' : ''}`}
          onClick={() => onSelect(category)}
        >
          <span className="text-sm flex-1">{category.name}</span>
          {category.flag === 0 && <EyeOff className="h-3 w-3 text-muted-foreground" />}
          {!category.bCartReflected && <Badge variant="outline" className="text-xs px-1 py-0 text-orange-500 border-orange-500">未反映</Badge>}
        </div>
      )}
    </div>
  )
}

function CategoryDetailForm({
  category,
  allCategories,
}: {
  category: BCartCategory
  allCategories: BCartCategory[]
}) {
  const queryClient = useQueryClient()
  const [form, setForm] = useState<BCartCategoryUpdateRequest>({
    name: category.name,
    description: category.description ?? '',
    rvDescription: category.rvDescription ?? '',
    parentCategoryId: category.parentCategoryId,
    metaTitle: category.metaTitle ?? '',
    metaKeywords: category.metaKeywords ?? '',
    metaDescription: category.metaDescription ?? '',
    priority: category.priority,
    flag: category.flag,
    version: category.version,
  })

  const { data: history } = useQuery({
    queryKey: ['bcart', 'categories', category.id, 'history'],
    queryFn: () => fetchCategoryHistory(category.id),
  })

  const mutation = useMutation({
    mutationFn: () => updateCategory(category.id, form),
    onSuccess: () => {
      toast.success('カテゴリを更新しました')
      queryClient.invalidateQueries({ queryKey: ['bcart', 'categories'] })
    },
    onError: (err: Error) => toast.error(err.message),
  })

  const parentCategories = allCategories.filter((c) => c.parentCategoryId === null && c.id !== category.id)

  return (
    <Tabs defaultValue="detail">
      <TabsList>
        <TabsTrigger value="detail">カテゴリ情報</TabsTrigger>
        <TabsTrigger value="history">
          <History className="h-4 w-4 mr-1" />
          変更履歴
        </TabsTrigger>
      </TabsList>

      <TabsContent value="detail" className="space-y-4 mt-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold">ID: {category.id}</h2>
          <div className="flex items-center gap-2">
            {!category.bCartReflected && (
              <Badge variant="outline" className="text-orange-500 border-orange-500">B-CART未反映</Badge>
            )}
          </div>
        </div>

        <div className="grid gap-4">
          <div>
            <Label>カテゴリ名</Label>
            <Input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} />
          </div>

          <div>
            <Label>親カテゴリ</Label>
            <select
              className="w-full h-10 rounded-md border border-input bg-background px-3 py-2 text-sm"
              value={form.parentCategoryId ?? ''}
              onChange={(e) => setForm({ ...form, parentCategoryId: e.target.value ? Number(e.target.value) : null })}
            >
              <option value="">なし（親カテゴリ）</option>
              {parentCategories.map((p) => (
                <option key={p.id} value={p.id}>{p.name}</option>
              ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <Label>表示優先度</Label>
              <Input type="number" value={form.priority} onChange={(e) => setForm({ ...form, priority: Number(e.target.value) })} />
            </div>
            <div className="flex items-center gap-2 pt-6">
              <Switch checked={form.flag === 1} onCheckedChange={(checked) => setForm({ ...form, flag: checked ? 1 : 0 })} />
              <Label>{form.flag === 1 ? '表示' : '非表示'}</Label>
            </div>
          </div>

          <div>
            <Label>説明（PC）</Label>
            <Textarea rows={4} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </div>

          <div>
            <Label>説明（レスポンシブ）</Label>
            <Textarea rows={4} value={form.rvDescription} onChange={(e) => setForm({ ...form, rvDescription: e.target.value })} />
          </div>

          <div className="space-y-2">
            <h3 className="text-sm font-semibold text-muted-foreground">META情報</h3>
            <div>
              <Label>title</Label>
              <Input value={form.metaTitle} onChange={(e) => setForm({ ...form, metaTitle: e.target.value })} />
            </div>
            <div>
              <Label>keywords</Label>
              <Input value={form.metaKeywords} onChange={(e) => setForm({ ...form, metaKeywords: e.target.value })} />
            </div>
            <div>
              <Label>description</Label>
              <Textarea rows={2} value={form.metaDescription} onChange={(e) => setForm({ ...form, metaDescription: e.target.value })} />
            </div>
          </div>
        </div>

        <div className="flex gap-2 pt-4">
          <Button onClick={() => mutation.mutate()} disabled={mutation.isPending}>
            <Save className="h-4 w-4 mr-1" />
            {mutation.isPending ? '保存中...' : '保存'}
          </Button>
        </div>
      </TabsContent>

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
                <span className="line-through text-red-500">{h.beforeValue}</span>
                <span>→</span>
                <span className="text-green-600">{h.afterValue}</span>
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
  )
}

interface JobStatus {
  jobName: string
  status: string
  exitCode?: string
  startTime?: string
  endTime?: string
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
      queryClient.invalidateQueries({ queryKey: ['bcart', 'categories'] })
    } else if (s.status === 'FAILED') {
      toast.error(`${label}が失敗しました${s.exitMessage ? ': ' + s.exitMessage : ''}`)
      setPolling(false)
    }
  }, [statusQuery.data, polling, launchedAt, label, queryClient])

  const mutation = useMutation({
    mutationFn: () => executeBatch(jobName),
    onSuccess: () => {
      toast.info(`${label}を起動しました...`)
      setLaunchedAt(new Date().toISOString())
      setPolling(true)
    },
    onError: () => toast.error(`${label}の起動に失敗しました`),
  })

  return { mutate: mutation.mutate, isRunning: mutation.isPending || polling }
}

export default function BCartCategoriesPage() {
  const [selectedCategory, setSelectedCategory] = useState<BCartCategory | null>(null)

  const { data: categories, isLoading } = useQuery({
    queryKey: ['bcart', 'categories'],
    queryFn: fetchCategories,
  })

  const sync = useBatchWithPolling('bCartCategorySync', 'カテゴリ同期')
  const reflect = useBatchWithPolling('bCartCategoryUpdate', 'カテゴリ反映')

  // ツリー用にフラットリストも用意
  const allFlat = useMemo<BCartCategory[]>(
    () => categories ? categories.flatMap((c) => [c, ...(c.children ?? [])]) : [],
    [categories]
  )

  // カテゴリ一覧が更新されたら、選択中カテゴリも最新データに差し替え
  useEffect(() => {
    if (!selectedCategory || !allFlat.length) return
    const updated = allFlat.find((c) => c.id === selectedCategory.id)
    if (updated && updated.bCartReflected !== selectedCategory.bCartReflected) {
      setSelectedCategory(updated)
    }
  }, [allFlat, selectedCategory])

  return (
    <div className="p-4 space-y-4">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold">B-CARTカテゴリマスタ</h1>
        <div className="flex gap-2">
          <Button variant="outline" size="sm" onClick={() => sync.mutate()} disabled={sync.isRunning}>
            {sync.isRunning
              ? <Loader2 className="h-4 w-4 mr-1 animate-spin" />
              : <RefreshCw className="h-4 w-4 mr-1" />}
            {sync.isRunning ? '同期中...' : 'B-CARTから同期'}
          </Button>
          <Button variant="outline" size="sm" onClick={() => reflect.mutate()} disabled={reflect.isRunning}>
            {reflect.isRunning
              ? <Loader2 className="h-4 w-4 mr-1 animate-spin" />
              : <Upload className="h-4 w-4 mr-1" />}
            {reflect.isRunning ? '反映中...' : 'B-CARTに反映'}
          </Button>
        </div>
      </div>

      <div className="grid grid-cols-[320px_1fr] gap-4 min-h-[600px]">
        {/* 左ペイン: カテゴリツリー */}
        <div className="border rounded-lg p-3 overflow-y-auto max-h-[calc(100vh-180px)]">
          {isLoading ? (
            <p className="text-sm text-muted-foreground">読み込み中...</p>
          ) : categories && categories.length > 0 ? (
            <div className="space-y-0.5">
              {categories.map((cat) => (
                <CategoryTreeNode
                  key={cat.id}
                  category={cat}
                  selectedId={selectedCategory?.id ?? null}
                  onSelect={setSelectedCategory}
                />
              ))}
            </div>
          ) : (
            <p className="text-sm text-muted-foreground">カテゴリがありません。「B-CARTから同期」を実行してください。</p>
          )}
        </div>

        {/* 右ペイン: カテゴリ詳細 */}
        <div className="border rounded-lg p-4">
          {selectedCategory ? (
            <CategoryDetailForm
              key={selectedCategory.id}
              category={selectedCategory}
              allCategories={allFlat}
            />
          ) : (
            <div className="flex items-center justify-center h-full text-muted-foreground">
              左のツリーからカテゴリを選択してください
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
