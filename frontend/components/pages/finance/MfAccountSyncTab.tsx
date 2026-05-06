'use client'

import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { api, ApiError } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Badge } from '@/components/ui/badge'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { AlertCircle, Eye, Loader2, PlayCircle } from 'lucide-react'
import { toast } from 'sonner'
import type { MfAccountSyncResult } from '@/types/mf-integration'

/**
 * mf_account_master を MF API と同期するタブ。
 * 1. 「プレビュー」で差分計算（dry-run）
 * 2. 差分を確認後「適用」で DB 洗い替え
 */
export function MfAccountSyncTab() {
  const [preview, setPreview] = useState<MfAccountSyncResult | null>(null)
  const [confirmOpen, setConfirmOpen] = useState(false)

  const previewMutation = useMutation({
    mutationFn: () =>
      api.get<MfAccountSyncResult>('/finance/mf-integration/account-sync/preview'),
    onSuccess: (res) => {
      setPreview(res)
      setConfirmOpen(true)
    },
    onError: (e: Error) => {
      if (e instanceof ApiError && e.status === 401) {
        toast.error('MF 認証エラー。「接続」タブで再認証してください。')
      } else {
        toast.error(e.message)
      }
    },
  })

  const applyMutation = useMutation({
    mutationFn: () =>
      api.post<MfAccountSyncResult>('/finance/mf-integration/account-sync/apply'),
    onSuccess: (res) => {
      toast.success(
        `同期完了: 追加 ${res.insertCount} / 更新 ${res.updateCount} / 削除 ${res.deleteCount}`,
      )
      setPreview(res)
      setConfirmOpen(false)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const hasChanges =
    preview != null &&
    (preview.insertCount > 0 || preview.updateCount > 0 || preview.deleteCount > 0)

  return (
    <div className="space-y-3">
      <Card>
        <CardContent className="pt-4 space-y-3">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm font-medium">勘定科目同期</p>
              <p className="text-xs text-muted-foreground">
                MF クラウド会計から勘定科目マスタ (<code>/accounts</code> +{' '}
                <code>/taxes</code>) を取得し、enum 翻訳辞書で日本語化して
                <code>mf_account_master</code> と突合。追加・更新・削除の差分をプレビュー後、適用できます。
              </p>
            </div>
            <Button
              onClick={() => previewMutation.mutate()}
              disabled={previewMutation.isPending}
            >
              {previewMutation.isPending ? (
                <>
                  <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                  プレビュー中...
                </>
              ) : (
                <>
                  <Eye className="mr-1 h-4 w-4" />
                  プレビュー
                </>
              )}
            </Button>
          </div>

          <div className="rounded border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800">
            <div className="flex items-start gap-2">
              <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
              <div>
                <p className="font-medium">適用時の注意</p>
                <ul className="list-disc ml-4 mt-1 space-y-0.5">
                  <li>
                    MF に存在しない既存行は <b>物理削除</b> されます（「運用中の勘定科目が MF から消えた」状態のみ該当）。
                  </li>
                  <li>
                    英語 enum の日本語化に失敗した場合は英語のまま保存されます。先に「enum 翻訳辞書」タブで確認してください。
                  </li>
                  <li>
                    税区分 (<code>tax_classification</code>) は MF <code>/taxes</code> の <code>name</code> で上書きします。
                  </li>
                  <li>
                    <b>更新件数が多く出ることがあります</b>: <code>display_order</code> は MF 返却順の連番で保存しているため、
                    MF 側で勘定科目を 1 つ追加・並び替えするだけで、それ以降の全行の順序が 1 つずつずれて大量 UPDATE になります。
                    内容自体は変わっていないので問題ありません。
                  </li>
                </ul>
              </div>
            </div>
          </div>
        </CardContent>
      </Card>

      <Dialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <DialogContent className="max-w-3xl">
          <DialogHeader>
            <DialogTitle>勘定科目同期 プレビュー</DialogTitle>
            <DialogDescription>
              以下の差分を <code>mf_account_master</code> に適用します。問題なければ「適用」を押してください。
            </DialogDescription>
          </DialogHeader>

          {preview && (
            <div className="space-y-3 text-sm">
              <div className="flex gap-2">
                <Badge className="bg-emerald-600 hover:bg-emerald-700">
                  追加 {preview.insertCount}
                </Badge>
                <Badge className="bg-blue-600 hover:bg-blue-700">
                  更新 {preview.updateCount}
                </Badge>
                <Badge variant="destructive">削除 {preview.deleteCount}</Badge>
                {preview.unknownEnums.length > 0 && (
                  <Badge variant="outline" className="border-amber-500 text-amber-700">
                    未翻訳 enum {preview.unknownEnums.length}
                  </Badge>
                )}
              </div>

              {preview.unknownEnums.length > 0 && (
                <div className="rounded border border-amber-300 bg-amber-50 p-3 text-xs text-amber-800">
                  <p className="font-medium mb-1">翻訳辞書に未登録の enum（英語のまま保存されます）</p>
                  <ul className="list-disc ml-4 font-mono">
                    {preview.unknownEnums.map((e) => (
                      <li key={e}>{e}</li>
                    ))}
                  </ul>
                </div>
              )}

              {preview.insertSamples.length > 0 && (
                <Section title="追加（サンプル先頭 10 件）">
                  <SampleTable
                    rows={preview.insertSamples.map((r) => ({
                      label: fmtKey(r.accountName, r.subAccountName),
                      detail: `${r.category ?? '-'} / 税: ${r.taxClassification ?? '-'}`,
                    }))}
                  />
                </Section>
              )}

              {preview.updateSamples.length > 0 && (
                <Section title="更新（サンプル先頭 10 件）">
                  <SampleTable
                    rows={preview.updateSamples.map((r) => ({
                      label: fmtKey(r.accountName, r.subAccountName),
                      detail: r.changes,
                    }))}
                  />
                </Section>
              )}

              {preview.deleteSamples.length > 0 && (
                <Section title="削除（サンプル先頭 10 件）">
                  <SampleTable
                    rows={preview.deleteSamples.map((r) => ({
                      label: fmtKey(r.accountName, r.subAccountName),
                      detail: `${r.category ?? '-'} / 税: ${r.taxClassification ?? '-'}`,
                    }))}
                  />
                </Section>
              )}

              {!hasChanges && (
                <p className="text-xs text-muted-foreground">
                  差分なし。MF と <code>mf_account_master</code> は既に同期済みです。
                </p>
              )}
            </div>
          )}

          <DialogFooter>
            <Button variant="outline" onClick={() => setConfirmOpen(false)}>
              キャンセル
            </Button>
            <Button
              onClick={() => applyMutation.mutate()}
              disabled={!hasChanges || applyMutation.isPending}
            >
              {applyMutation.isPending ? (
                <>
                  <Loader2 className="mr-1 h-4 w-4 animate-spin" />
                  適用中...
                </>
              ) : (
                <>
                  <PlayCircle className="mr-1 h-4 w-4" />
                  適用
                </>
              )}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <details className="rounded border" open>
      <summary className="cursor-pointer select-none bg-muted/50 px-3 py-1.5 text-xs font-medium">
        {title}
      </summary>
      <div className="p-2">{children}</div>
    </details>
  )
}

function SampleTable({ rows }: { rows: { label: string; detail: string }[] }) {
  return (
    <table className="w-full text-xs">
      <tbody>
        {rows.map((r, i) => (
          <tr key={i} className="border-t first:border-t-0">
            <td className="px-2 py-1 w-1/3 font-mono">{r.label}</td>
            <td className="px-2 py-1 text-muted-foreground">{r.detail}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

function fmtKey(accountName: string, subAccountName: string | null): string {
  return subAccountName ? `${accountName} / ${subAccountName}` : accountName
}
