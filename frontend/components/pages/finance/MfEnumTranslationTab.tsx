'use client'

import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Sparkles, Save, Plus, Trash2, AlertCircle } from 'lucide-react'
import { toast } from 'sonner'
import type { MfEnumKind, MfEnumTranslation } from '@/types/mf-integration'
import { MF_ENUM_KIND_LABELS } from '@/types/mf-integration'

const KINDS: MfEnumKind[] = ['FINANCIAL_STATEMENT', 'CATEGORY']

/**
 * MF 英語 enum → 日本語名 翻訳辞書の管理タブ。
 * 「自動学習」ボタンで既存 mf_account_master + MF /accounts から自動登録。
 * 手動編集も可能。
 */
export function MfEnumTranslationTab() {
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<MfEnumTranslation[] | null>(null)
  const [lastUnresolved, setLastUnresolved] = useState<string[]>([])

  const query = useQuery({
    queryKey: ['mf-enum-translations'],
    queryFn: () => api.get<MfEnumTranslation[]>('/finance/mf-integration/enum-translations'),
  })

  useEffect(() => {
    if (query.data) setDraft(query.data)
  }, [query.data])

  const saveMutation = useMutation({
    mutationFn: (body: MfEnumTranslation[]) =>
      api.put<MfEnumTranslation[]>('/finance/mf-integration/enum-translations', body),
    onSuccess: () => {
      toast.success('翻訳辞書を保存しました')
      queryClient.invalidateQueries({ queryKey: ['mf-enum-translations'] })
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const seedMutation = useMutation({
    mutationFn: () =>
      api.post<{ added: number; unresolved: string[] }>(
        '/finance/mf-integration/enum-translations/auto-seed',
      ),
    onSuccess: (res) => {
      toast.success(`自動学習: ${res.added} 件追加`)
      setLastUnresolved(res.unresolved ?? [])
      if (res.unresolved && res.unresolved.length > 0) {
        toast.warning(
          `日本語化できなかった enum が ${res.unresolved.length} 件あります。下の警告を確認してください。`,
        )
      }
      queryClient.invalidateQueries({ queryKey: ['mf-enum-translations'] })
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) {
        toast.error('MF 認証エラー。「接続」タブで再認証してください。')
      } else {
        toast.error(e.message)
      }
    },
  })

  if (query.isLoading || !draft) {
    return <div className="text-xs text-muted-foreground">辞書読み込み中...</div>
  }

  const update = (idx: number, patch: Partial<MfEnumTranslation>) => {
    setDraft(draft.map((r, i) => (i === idx ? { ...r, ...patch } : r)))
  }
  const remove = (idx: number) => setDraft(draft.filter((_, i) => i !== idx))
  const add = () =>
    setDraft([
      ...draft,
      { id: null, enumKind: 'CATEGORY', englishCode: '', japaneseName: '' },
    ])

  const save = () => {
    const invalid = draft.find(
      (r) => !r.enumKind || !r.englishCode.trim() || !r.japaneseName.trim(),
    )
    if (invalid) {
      toast.error('入力不足の行があります')
      return
    }
    saveMutation.mutate(draft)
  }

  const groupedByKind = (kind: MfEnumKind) =>
    draft.map((r, i) => ({ row: r, idx: i })).filter((x) => x.row.enumKind === kind)

  return (
    <div className="space-y-3">
      <Card>
        <CardContent className="pt-4 space-y-3">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">MF 英語 enum → 日本語 翻訳辞書</p>
              <p className="text-xs text-muted-foreground">
                勘定科目同期時に、MF API の <code>financial_statement_type</code> と{' '}
                <code>category</code> を日本語化するための辞書です。
                「自動学習」で既存 <code>mf_account_master</code> の日本語値と MF API を突合して自動登録できます。
              </p>
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => seedMutation.mutate()}
                disabled={seedMutation.isPending}
              >
                <Sparkles className="mr-1 h-4 w-4" />
                {seedMutation.isPending ? '学習中...' : '自動学習'}
              </Button>
              <Button size="sm" onClick={save} disabled={saveMutation.isPending}>
                <Save className="mr-1 h-4 w-4" />
                保存
              </Button>
            </div>
          </div>

          {lastUnresolved.length > 0 && (
            <div className="rounded border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800">
              <div className="flex items-start gap-2">
                <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
                <div>
                  <p className="font-medium">
                    日本語名の手入力が必要な enum が {lastUnresolved.length} 件あります
                  </p>
                  <p>
                    下の表で黄色ハイライトされた行の日本語欄を入力 →「保存」してください。
                  </p>
                  <ul className="mt-1 ml-4 list-disc font-mono">
                    {lastUnresolved.map((e) => (
                      <li key={e}>{e}</li>
                    ))}
                  </ul>
                </div>
              </div>
            </div>
          )}

          {KINDS.map((kind) => {
            const rows = groupedByKind(kind)
            return (
              <div key={kind} className="rounded border">
                <div className="bg-muted/50 px-3 py-2 text-xs font-medium">
                  {MF_ENUM_KIND_LABELS[kind]}
                  <Badge variant="secondary" className="ml-2">
                    {rows.length}
                  </Badge>
                </div>
                {rows.length === 0 ? (
                  <div className="px-3 py-4 text-xs text-muted-foreground">
                    未登録
                  </div>
                ) : (
                  <table className="w-full text-xs">
                    <thead className="text-left">
                      <tr className="border-t">
                        <th className="px-3 py-1.5 font-medium w-[40%]">英語 enum</th>
                        <th className="px-3 py-1.5 font-medium">日本語名</th>
                        <th className="w-12" />
                      </tr>
                    </thead>
                    <tbody>
                      {rows.map(({ row, idx }) => (
                        <tr key={idx} className="border-t">
                          <td className="px-3 py-1">
                            <Input
                              className="h-7 text-xs font-mono"
                              value={row.englishCode}
                              onChange={(e) => update(idx, { englishCode: e.target.value })}
                              placeholder="CASH_AND_DEPOSITS"
                            />
                          </td>
                          <td className="px-3 py-1">
                            <Input
                              className={
                                'h-7 text-xs ' +
                                (row.japaneseName.trim() === ''
                                  ? 'border-amber-400 bg-amber-50 ring-1 ring-amber-300'
                                  : '')
                              }
                              value={row.japaneseName}
                              onChange={(e) => update(idx, { japaneseName: e.target.value })}
                              placeholder="日本語名を入力"
                            />
                          </td>
                          <td className="text-right pr-2">
                            <Button
                              size="sm"
                              variant="ghost"
                              onClick={() => remove(idx)}
                              aria-label="削除"
                            >
                              <Trash2 className="h-3.5 w-3.5" />
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            )
          })}

          <div className="flex justify-between">
            <Button size="sm" variant="outline" onClick={add}>
              <Plus className="mr-1 h-4 w-4" />
              行追加
            </Button>
            <Label className="text-xs text-muted-foreground">
              登録件数: <Badge variant="secondary">{draft.length}</Badge>
            </Label>
          </div>
        </CardContent>
      </Card>
    </div>
  )
}
