'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { PageHeader } from '@/components/features/common/PageHeader'
import {
  Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import {
  Select, SelectTrigger, SelectValue, SelectContent, SelectItem,
} from '@/components/ui/select'
import { Download, Upload, AlertCircle, Scale, History, CheckCheck } from 'lucide-react'
import Link from 'next/link'
import { toast } from 'sonner'
import type {
  PaymentMfPreviewResponse, PaymentMfPreviewRow,
  PaymentMfRule, PaymentMfRuleRequest, RuleKind,
  PaymentMfVerifyResult,
} from '@/types/payment-mf'
import { RULE_KINDS, TAX_CATEGORIES } from '@/types/payment-mf'

type RuleDialogState = { sourceName: string; amount: number | null } | null

export default function PaymentMfImportPage() {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<PaymentMfPreviewResponse | null>(null)
  const [ruleDialog, setRuleDialog] = useState<RuleDialogState>(null)
  const [form, setForm] = useState<PaymentMfRuleRequest>(blankRuleRequest())

  const previewMut = useMutation({
    mutationFn: async (f: File) => {
      const fd = new FormData()
      fd.append('file', f)
      return api.postForm<PaymentMfPreviewResponse>('/finance/payment-mf/preview', fd)
    },
    onSuccess: (r) => {
      setPreview(r)
      if (r.errorCount === 0) toast.success(`プレビュー成功: ${r.totalRows}件`)
      else toast.warning(`マスタ未登録 ${r.errorCount} 件。登録後に再プレビューしてください`)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const rePreviewMut = useMutation({
    mutationFn: async (uploadId: string) =>
      api.post<PaymentMfPreviewResponse>(`/finance/payment-mf/preview/${uploadId}`),
    onSuccess: (r) => {
      setPreview(r)
      if (r.errorCount === 0) toast.success('すべてのエラーが解消されました')
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const createRuleMut = useMutation({
    mutationFn: async (req: PaymentMfRuleRequest) =>
      api.post<PaymentMfRule>('/finance/payment-mf/rules', req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payment-mf-rules'] })
      toast.success('ルールを追加しました')
      setRuleDialog(null)
      setForm(blankRuleRequest())
      if (preview) rePreviewMut.mutate(preview.uploadId)
    },
    onError: (e: Error) => toast.error(e.message),
  })

  const verifyMut = useMutation({
    mutationFn: async (uploadId: string) =>
      api.post<PaymentMfVerifyResult>(`/finance/payment-mf/verify/${uploadId}`),
    onSuccess: (r) => {
      toast.success(
        `買掛金一覧に反映しました（一致 ${r.matchedCount} / 差異 ${r.diffCount} / 買掛金なし ${r.notFoundCount}）`
      )
    },
    onError: (e: Error) => toast.error(`反映失敗: ${e.message}`),
  })

  const download = async () => {
    if (!preview) return
    try {
      const { blob, filename } = await api.downloadPost(
        `/finance/payment-mf/convert/${preview.uploadId}`
      )
      const date = preview.transferDate?.replaceAll('-', '') ?? 'unknown'
      const suggest = filename ?? `買掛仕入MFインポートファイル_${date}.csv`
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = suggest
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(a.href)
      toast.success('CSVをダウンロードしました')
    } catch (e) {
      toast.error(`ダウンロード失敗: ${(e as Error).message}`)
    }
  }

  const openRuleDialog = (row: PaymentMfPreviewRow) => {
    setForm({
      ...blankRuleRequest(),
      sourceName: row.sourceName ?? '',
      paymentSupplierCode: row.paymentSupplierCode ?? undefined,
      ruleKind: row.paymentSupplierCode ? 'PAYABLE' : 'EXPENSE',
      debitAccount: row.paymentSupplierCode ? '買掛金' : '荷造運賃',
      debitTaxCategory: row.paymentSupplierCode ? '対象外' : '課税仕入 10%',
      summaryTemplate: '{source_name}',
    })
    setRuleDialog({ sourceName: row.sourceName ?? '', amount: row.amount })
  }

  return (
    <div className="space-y-4">
      <PageHeader
        title="買掛仕入MF変換"
        actions={
          <>
            <Button asChild variant="outline" size="sm">
              <Link href="/finance/payment-mf-rules">
                <Scale className="mr-1 h-4 w-4" />
                ルールマスタ
              </Link>
            </Button>
            <Button asChild variant="outline" size="sm">
              <Link href="/finance/payment-mf-history">
                <History className="mr-1 h-4 w-4" />
                変換履歴
              </Link>
            </Button>
          </>
        }
      />

      <div className="rounded border p-4 space-y-3">
        <Label>振込み明細Excelファイル（.xlsx）</Label>
        <div className="flex items-center gap-2">
          <Input
            type="file"
            accept=".xlsx"
            onChange={(e) => setFile(e.target.files?.[0] ?? null)}
            className="max-w-md"
          />
          <Button
            onClick={() => file && previewMut.mutate(file)}
            disabled={!file || previewMut.isPending}
          >
            <Upload className="mr-1 h-4 w-4" />
            {previewMut.isPending ? '解析中...' : 'プレビュー'}
          </Button>
        </div>
        <p className="text-xs text-muted-foreground">
          「支払い明細」(20日払い) または「振込明細」(5日払い) シートを読み込みます。shop_no=1 固定。
        </p>
      </div>

      {preview && (
        <>
          <div className="flex flex-wrap items-center justify-between gap-4 rounded border bg-card p-4">
            <div className="space-y-1 text-sm">
              <div>ファイル: <span className="font-medium">{preview.fileName}</span></div>
              <div>
                送金日: <span className="font-medium">{preview.transferDate ?? '-'}</span>
                {preview.transactionMonth && (
                  <> / 対応取引月: <span className="font-medium">{preview.transactionMonth}</span></>
                )}
              </div>
              <div>
                合計: <span className="font-medium">{preview.totalAmount.toLocaleString()}</span> 円
                / 行数: {preview.totalRows}
              </div>
              <div className="flex gap-3 text-xs">
                <span className="text-green-700">一致 {preview.matchedCount}</span>
                <span className="text-amber-700">差異 {preview.diffCount}</span>
                <span className="text-red-600">買掛金なし {preview.unmatchedCount}</span>
                {preview.errorCount > 0 && (
                  <span className="text-red-600 font-semibold">未登録 {preview.errorCount}</span>
                )}
              </div>
            </div>
            <div className="flex gap-2">
              <Button
                variant="outline"
                disabled={preview.errorCount > 0 || verifyMut.isPending}
                onClick={() => {
                  if (confirm('この突合結果で買掛金一覧を検証確定します。よろしいですか？\n（verified_manually=true として手動確定扱いになります）')) {
                    verifyMut.mutate(preview.uploadId)
                  }
                }}
              >
                <CheckCheck className="mr-1 h-4 w-4" />
                {verifyMut.isPending ? '反映中...' : '買掛金一覧へ反映'}
              </Button>
              <Button onClick={download} disabled={preview.errorCount > 0}>
                <Download className="mr-1 h-4 w-4" />
                CSVダウンロード
              </Button>
            </div>
          </div>

          {preview.unregisteredSources.length > 0 && (
            <div className="rounded border border-orange-300 bg-orange-50 p-4">
              <div className="mb-2 flex items-center gap-2 text-sm font-medium text-orange-700">
                <AlertCircle className="h-4 w-4" />
                マスタ未登録の送り先 ({preview.unregisteredSources.length})
              </div>
              <div className="space-y-1">
                {preview.unregisteredSources.map((c) => (
                  <div key={c} className="flex items-center justify-between gap-2 text-sm">
                    <code className="rounded bg-white px-2 py-0.5">{c}</code>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => {
                        const row = preview.rows.find((r) => r.sourceName === c)
                        if (row) openRuleDialog(row)
                      }}
                    >
                      ルール追加
                    </Button>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="overflow-auto rounded border">
            <table className="min-w-full text-xs">
              <thead className="sticky top-0 bg-muted">
                <tr>
                  <th className="p-1">行</th>
                  <th className="p-1">コード</th>
                  <th className="p-1">送り先</th>
                  <th className="p-1 text-right">請求額</th>
                  <th className="p-1 text-right">買掛金</th>
                  <th className="p-1 text-right">差額</th>
                  <th className="p-1">状態</th>
                  <th className="p-1">借方</th>
                  <th className="p-1">補助</th>
                  <th className="p-1">税区分</th>
                  <th className="p-1">貸方</th>
                  <th className="p-1">摘要</th>
                  <th className="p-1">タグ</th>
                </tr>
              </thead>
              <tbody>
                {preview.rows.map((r, i) => (
                  <tr key={i} className={rowBgClass(r)}>
                    <td className="p-1 text-center">{r.excelRowIndex || ''}</td>
                    <td className="p-1 text-center">{r.paymentSupplierCode ?? ''}</td>
                    <td className="p-1">{r.sourceName ?? ''}</td>
                    <td className="p-1 text-right">{r.amount?.toLocaleString() ?? ''}</td>
                    <td className="p-1 text-right">{r.payableAmount?.toLocaleString() ?? ''}</td>
                    <td className="p-1 text-right">
                      {r.payableDiff !== null && r.payableDiff !== 0
                        ? r.payableDiff.toLocaleString()
                        : ''}
                    </td>
                    <td className="p-1 text-center">
                      {r.errorType === 'UNREGISTERED' ? (
                        <span className="text-red-600">未登録</span>
                      ) : (
                        <MatchBadge status={r.matchStatus} supplierNo={r.supplierNo}
                          txMonth={preview.transactionMonth} />
                      )}
                    </td>
                    <td className="p-1">{r.debitAccount ?? ''}</td>
                    <td className="p-1">{r.debitSubAccount ?? ''}</td>
                    <td className="p-1">{r.debitTax ?? ''}</td>
                    <td className="p-1">{r.creditAccount ?? ''}</td>
                    <td className="p-1">{r.summary ?? ''}</td>
                    <td className="p-1">{r.tag ?? ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      <Dialog open={ruleDialog !== null} onOpenChange={(o) => { if (!o) setRuleDialog(null) }}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>マスタへルール追加</DialogTitle>
          </DialogHeader>
          <div className="grid grid-cols-2 gap-3">
            <Field label="送り先名（Excel B列）">
              <Input value={form.sourceName}
                onChange={(e) => setForm({ ...form, sourceName: e.target.value })} />
            </Field>
            <Field label="支払先コード（買掛金の場合）">
              <Input value={form.paymentSupplierCode ?? ''}
                onChange={(e) => setForm({ ...form, paymentSupplierCode: e.target.value })} />
            </Field>
            <Field label="種別">
              <Select value={form.ruleKind}
                onValueChange={(v) => setForm({ ...form, ruleKind: v as RuleKind })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {RULE_KINDS.map((k) => <SelectItem key={k} value={k}>{k}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
            <Field label="借方勘定">
              <Input value={form.debitAccount}
                onChange={(e) => setForm({ ...form, debitAccount: e.target.value })} />
            </Field>
            <Field label="借方補助">
              <Input value={form.debitSubAccount ?? ''}
                onChange={(e) => setForm({ ...form, debitSubAccount: e.target.value })} />
            </Field>
            <Field label="借方部門">
              <Input value={form.debitDepartment ?? ''}
                onChange={(e) => setForm({ ...form, debitDepartment: e.target.value })} />
            </Field>
            <Field label="借方税区分">
              <Select value={form.debitTaxCategory}
                onValueChange={(v) => setForm({ ...form, debitTaxCategory: v })}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>
                  {TAX_CATEGORIES.map((k) => <SelectItem key={k} value={k}>{k}</SelectItem>)}
                </SelectContent>
              </Select>
            </Field>
            <Field label="摘要テンプレ ({sub_account} / {source_name})">
              <Input value={form.summaryTemplate}
                onChange={(e) => setForm({ ...form, summaryTemplate: e.target.value })} />
            </Field>
            <Field label="タグ">
              <Input value={form.tag ?? ''}
                onChange={(e) => setForm({ ...form, tag: e.target.value })} />
            </Field>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRuleDialog(null)}>キャンセル</Button>
            <Button
              disabled={!form.sourceName || !form.debitAccount || createRuleMut.isPending}
              onClick={() => createRuleMut.mutate(form)}
            >
              {createRuleMut.isPending ? '登録中...' : '登録＆再プレビュー'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

function blankRuleRequest(): PaymentMfRuleRequest {
  return {
    sourceName: '',
    paymentSupplierCode: '',
    ruleKind: 'PAYABLE',
    debitAccount: '買掛金',
    debitSubAccount: '',
    debitDepartment: '',
    debitTaxCategory: '対象外',
    creditAccount: '資金複合',
    creditTaxCategory: '対象外',
    summaryTemplate: '{source_name}',
    tag: '',
    priority: 100,
  }
}

function rowBgClass(r: PaymentMfPreviewRow): string {
  if (r.errorType === 'UNREGISTERED') return 'bg-red-50'
  if (r.ruleKind === 'SUMMARY') return 'bg-slate-50'
  if (r.matchStatus === 'DIFF') return 'bg-amber-50'
  if (r.matchStatus === 'UNMATCHED' && r.ruleKind === 'PAYABLE') return 'bg-red-50'
  return ''
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <Label className="text-xs">{label}</Label>
      {children}
    </div>
  )
}

function MatchBadge({
  status, supplierNo, txMonth,
}: { status: string | null; supplierNo: number | null; txMonth: string | null }) {
  if (status === 'MATCHED') return <span className="text-green-700">🟢 一致</span>
  if (status === 'DIFF')
    return supplierNo && txMonth ? (
      <Link
        className="text-amber-700 underline"
        href={`/finance/accounts-payable?supplierNo=${supplierNo}&transactionMonth=${txMonth}`}
      >
        🟡 差異
      </Link>
    ) : <span className="text-amber-700">🟡 差異</span>
  if (status === 'UNMATCHED') return <span className="text-red-600">🔴 買掛金なし</span>
  return <span className="text-muted-foreground">–</span>
}
