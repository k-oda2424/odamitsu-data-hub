'use client'

import { useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { handleApiError } from '@/lib/api-error-handler'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { PageHeader } from '@/components/features/common/PageHeader'
import { ConfirmDialog } from '@/components/features/common/ConfirmDialog'
import {
  Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogFooter,
} from '@/components/ui/dialog'
import {
  Select, SelectTrigger, SelectValue, SelectContent, SelectItem,
} from '@/components/ui/select'
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert'
import { Checkbox } from '@/components/ui/checkbox'
import { Download, Upload, AlertCircle, AlertTriangle, Scale, History, CheckCheck, ShieldAlert, FileDown } from 'lucide-react'
import Link from 'next/link'
import { toast } from 'sonner'
import type {
  PaymentMfPreviewResponse, PaymentMfPreviewRow,
  PaymentMfRule, PaymentMfRuleRequest, RuleKind,
  PaymentMfVerifyResult,
} from '@/types/payment-mf'
import { RULE_KINDS, TAX_CATEGORIES } from '@/types/payment-mf'
import { AmountSourceTooltip } from '@/components/common/AmountSourceTooltip'

type RuleDialogState = { sourceName: string; amount: number | null } | null

export default function PaymentMfImportPage() {
  const queryClient = useQueryClient()
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<PaymentMfPreviewResponse | null>(null)
  const [ruleDialog, setRuleDialog] = useState<RuleDialogState>(null)
  const [form, setForm] = useState<PaymentMfRuleRequest>(blankRuleRequest())
  const [confirmVerify, setConfirmVerify] = useState(false)
  // G2-M2: per-supplier 1 円不一致がある時に「強制実行に同意」のチェック必須
  const [forceAcknowledged, setForceAcknowledged] = useState(false)
  // G2-M2 (Frontend Major): force apply 専用ダイアログ。
  // 100 件省略表示で承認内容と実処理が乖離するリスクを抑えるため、
  // CSV ダウンロード + 全件確認 checkbox + 反映理由 textarea を必須化する。
  const [forceDialogOpen, setForceDialogOpen] = useState(false)
  const [reviewedAll, setReviewedAll] = useState(false)
  const [forceReason, setForceReason] = useState('')

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
    onError: (e) => handleApiError(e, { fallbackMessage: 'プレビュー失敗' }),
  })

  const rePreviewMut = useMutation({
    mutationFn: async (uploadId: string) =>
      api.post<PaymentMfPreviewResponse>(`/finance/payment-mf/preview/${uploadId}`),
    onSuccess: (r) => {
      setPreview(r)
      if (r.errorCount === 0) toast.success('すべてのエラーが解消されました')
    },
    onError: (e) => handleApiError(e, { fallbackMessage: '再プレビュー失敗' }),
  })

  const createRuleMut = useMutation({
    mutationFn: async (req: PaymentMfRuleRequest) =>
      api.post<PaymentMfRule>('/finance/payment-mf/rules', req),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['payment-mf-rules'] })
      toast.success('ルールを追加しました')
      setRuleDialog(null)
      setForm(blankRuleRequest())
      // 再プレビューが既に走っている場合はスキップ（連続ルール追加時のフリッカ防止）
      if (preview && !rePreviewMut.isPending) rePreviewMut.mutate(preview.uploadId)
    },
    onError: (e) => handleApiError(e, { fallbackMessage: 'ルール登録失敗' }),
  })

  const verifyMut = useMutation({
    mutationFn: async (args: { uploadId: string; force: boolean; forceReason?: string }) => {
      // backend が `forceReason` 未対応でも Spring の default Jackson 設定 (FAIL_ON_UNKNOWN_PROPERTIES=false) で無視される。
      // 別エージェントが backend 側の必須化を進めている前提で、force=true 時は常に送る。
      const body: { force: boolean; forceReason?: string } = { force: args.force }
      if (args.force && args.forceReason) body.forceReason = args.forceReason
      return api.post<PaymentMfVerifyResult>(
        `/finance/payment-mf/verify/${args.uploadId}`,
        body,
      )
    },
    onSuccess: (r, vars) => {
      const baseMsg =
        `買掛金一覧に反映しました（一致 ${r.matchedCount} / 差異 ${r.diffCount} / 買掛金なし ${r.notFoundCount}）`
      // P1-08 Q3-(ii): 手動確定保護でスキップされた supplier があれば別途通知
      if (r.skippedManuallyVerifiedCount > 0) {
        toast.success(baseMsg)
        toast.warning(
          `手動確定済の ${r.skippedManuallyVerifiedCount} 件は保護のため上書きしませんでした`
        )
      } else {
        toast.success(baseMsg)
      }
      if (vars.force) {
        toast.warning('per-supplier 1 円不一致を強制反映しました（audit log に記録済）')
      }
      // 反映成功後は force 同意状態をリセット
      setForceAcknowledged(false)
      setReviewedAll(false)
      setForceReason('')
    },
    // G3-M12: PER_SUPPLIER_MISMATCH 等の business code は handleApiError で個別誘導
    onError: (e) => handleApiError(e, { fallbackMessage: '反映失敗' }),
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
      const url = URL.createObjectURL(blob)
      a.href = url
      a.download = suggest
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      // revoke は非同期ダウンロード完了を待つ（同期 revoke は大容量 CSV で稀に失敗する）
      setTimeout(() => URL.revokeObjectURL(url), 1000)
      toast.success('CSVをダウンロードしました')
    } catch (e) {
      handleApiError(e, { fallbackMessage: 'ダウンロード失敗' })
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
        <div className="rounded bg-muted/50 p-3 text-xs space-y-2 leading-relaxed">
          <div className="font-medium text-foreground">どのファイルを選べばいい？</div>
          <ul className="list-disc pl-5 space-y-1">
            <li>
              経理が作成した <b>「振込み明細〇〇.xlsx」</b>（例: <code>振込み明細08-2-5.xlsx</code>）を
              そのままアップロードしてください。ファイル名の末尾 <code>-5</code> が5日払い、
              <code>-20</code> が20日払いです（シートは自動判別）。
            </li>
            <li>
              保管場所の目安:
              <br />
              <code>\\Smile-srv\共有\BKUP-001\Documents\Documents\買掛関係\支払\{'{'}年{'}'}\{'{'}支払月{'}'}\</code>
            </li>
            <li>
              取引月（買掛金突合先）の決まり方:
              <ul className="list-[circle] pl-5">
                <li>
                  送金日が <b>5日・20日</b> いずれも <b>前月20日締め</b> の買掛が対象です。
                </li>
                <li>例: 2026/1/5 支払 → 取引月 <b>2025-12-20</b></li>
                <li>例: 2026/1/20 支払 → 取引月 <b>2025-12-20</b></li>
                <li>例: 2026/2/5 支払 → 取引月 <b>2026-01-20</b></li>
                <li>例: 2026/2/20 支払 → 取引月 <b>2026-01-20</b></li>
              </ul>
              ※ 送金日は Excel の <code>E1</code> セルから自動取得します。プレビュー上部の
              「送金日 / 対応取引月」で必ず確認してください。
            </li>
            <li>
              対象シート: 20日払い＝「支払い明細」 / 5日払い＝「振込明細」。shop_no=1 固定。
            </li>
            <li>
              同じ月を2回読み込むと <b>買掛金一覧への反映（手動確定）が上書き</b>されます。
              二重取込しないようご注意ください。
            </li>
          </ul>
        </div>
      </div>

      {preview && (
        <>
          {/* P1-08 L1: 同一ハッシュ Excel 過去取込の警告 */}
          {preview.duplicateWarning && (
            <Alert className="border-amber-500 bg-amber-50">
              <AlertTriangle className="h-4 w-4 text-amber-700" />
              <AlertTitle className="text-amber-800">
                同一内容のファイルが既に取込済です
              </AlertTitle>
              <AlertDescription className="text-amber-900/90">
                <div>
                  前回取込: {formatDateTime(preview.duplicateWarning.previousUploadedAt)}
                  {preview.duplicateWarning.previousFilename && (
                    <> （ファイル: <code className="rounded bg-white px-1">{preview.duplicateWarning.previousFilename}</code>）</>
                  )}
                </div>
                <div>
                  ハッシュが一致しているため <b>内容が完全に同じ</b> です。
                  修正版でない場合は重複取込の可能性があります。
                </div>
              </AlertDescription>
            </Alert>
          )}

          {/* P1-08 L2: 同 (shop, transferDate) 確定済の警告 */}
          {preview.appliedWarning && (
            <Alert variant="destructive" className="border-red-400 bg-red-50">
              <AlertCircle className="h-4 w-4" />
              <AlertTitle>この月は既に確定済です</AlertTitle>
              <AlertDescription>
                <div>
                  確定日時: {formatDateTime(preview.appliedWarning.appliedAt)}
                  （取引月: <b>{preview.appliedWarning.transactionMonth}</b> / 振込日: <b>{preview.appliedWarning.transferDate}</b>）
                </div>
                <div>
                  再確定すると <b>確定済の値を上書き</b> します。
                  ただし単一仕入先で <code>verified_manually=true</code> で個別確定された行は <b>保護のため上書きされません</b>。
                </div>
                <div>
                  続行する場合は下の「買掛金一覧へ反映」を確認した上で実行してください。
                </div>
              </AlertDescription>
            </Alert>
          )}

          {/*
            G2-M2 (2026-05-06): per-supplier 1 円整合性違反の警告 + 強制実行同意 UI。
            違反 1 件以上で「買掛金一覧へ反映」「CSVダウンロード」をブロックし、
            checkbox にチェック後にのみ force=true で実行可能。
          */}
          {(preview.amountReconciliation?.perSupplierMismatches?.length ?? 0) > 0 && (
            <Alert variant="destructive" className="border-red-500 bg-red-50">
              <ShieldAlert className="h-4 w-4" />
              <AlertTitle>
                per-supplier 1 円整合性違反 (
                {preview.amountReconciliation!.perSupplierMismatches.length} 件)
              </AlertTitle>
              <AlertDescription>
                <div className="mb-2">
                  以下の仕入先で <b>請求額 ≠ 振込 + 送料 + 値引 + 早払 + 相殺</b> となっています。
                  Excel 入力ミスの可能性が高いため、原則は経理に連絡し
                  <b>振込明細 Excel を修正してから再アップロード</b> してください。
                </div>
                <div className="max-h-48 overflow-auto rounded border border-red-200 bg-white p-2 font-mono text-xs leading-snug">
                  {preview.amountReconciliation!.perSupplierMismatches.slice(0, 100).map((m, i) => (
                    <div key={i} className="break-all">{m}</div>
                  ))}
                  {preview.amountReconciliation!.perSupplierMismatches.length > 100 && (
                    <div className="text-muted-foreground">
                      ...（残り {preview.amountReconciliation!.perSupplierMismatches.length - 100} 件は省略 / 下の CSV で全件確認可）
                    </div>
                  )}
                </div>
                {/*
                  G2-M2 (Frontend Major): 100 件超は UI で省略するため、全件確認のための
                  CSV ダウンロード経路を提供。force 反映前に Excel で目視レビュー可能。
                */}
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => downloadMismatchesCsv(preview.amountReconciliation!.perSupplierMismatches)}
                  >
                    <FileDown className="mr-1 h-4 w-4" />
                    全 {preview.amountReconciliation!.perSupplierMismatches.length} 件 CSV ダウンロード
                  </Button>
                  <span className="text-xs text-muted-foreground">
                    強制反映時は全件 audit log に保存されます。
                  </span>
                </div>
                <div className="mt-3 flex items-start gap-2 rounded border border-red-300 bg-red-100 p-2 text-xs">
                  <Checkbox
                    id="force-acknowledge"
                    checked={forceAcknowledged}
                    onCheckedChange={(v) => setForceAcknowledged(v === true)}
                    className="mt-0.5"
                  />
                  <Label htmlFor="force-acknowledge" className="cursor-pointer">
                    Excel 修正不能/業務承認済 のため <b>強制反映</b> する。
                    実行内容と全違反明細は <code>finance_audit_log</code> に記録されます。
                  </Label>
                </div>
              </AlertDescription>
            </Alert>
          )}

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
                variant={hasPerSupplierMismatch(preview) ? 'destructive' : 'outline'}
                disabled={
                  preview.errorCount > 0
                  || verifyMut.isPending
                  || (hasPerSupplierMismatch(preview) && !forceAcknowledged)
                }
                onClick={() => {
                  // G2-M2 (Frontend Major): force 反映時は CSV download + 反映理由 + 全件確認 checkbox
                  // を持つ専用ダイアログへ。通常確定 / 再確定は従来の ConfirmDialog を使う。
                  if (hasPerSupplierMismatch(preview)) {
                    setReviewedAll(false)
                    setForceReason('')
                    setForceDialogOpen(true)
                  } else {
                    setConfirmVerify(true)
                  }
                }}
              >
                <CheckCheck className="mr-1 h-4 w-4" />
                {verifyMut.isPending
                  ? '反映中...'
                  : hasPerSupplierMismatch(preview)
                    ? '強制反映 (force=true)'
                    : '買掛金一覧へ反映'}
              </Button>
              {/*
                G2-M2: CSV ダウンロード経路には force 上書きを設けない (Excel 修正が正しい運用)。
                per-supplier 不一致がある間は無効化する。
              */}
              <Button
                onClick={download}
                disabled={preview.errorCount > 0 || hasPerSupplierMismatch(preview)}
                title={
                  hasPerSupplierMismatch(preview)
                    ? 'per-supplier 1 円不一致のため CSV 出力は不可。Excel を修正してください'
                    : undefined
                }
              >
                <Download className="mr-1 h-4 w-4" />
                CSVダウンロード
              </Button>
            </div>
          </div>

          {preview.unregisteredSources.length > 0 && (
            <div className="rounded border border-orange-300 bg-orange-50 p-4">
              <div className="mb-2 flex items-center justify-between gap-2">
                <div className="flex items-center gap-2 text-sm font-medium text-orange-700">
                  <AlertCircle className="h-4 w-4" />
                  マスタ未登録の送り先 ({preview.unregisteredSources.length})
                </div>
                <Button asChild size="sm" variant="outline">
                  <Link href="/finance/payment-mf-rules">
                    <Scale className="mr-1 h-4 w-4" />
                    マッピングマスタを確認
                  </Link>
                </Button>
              </div>
              <p className="mb-2 text-xs text-orange-800">
                略称違い（例: 「カミイソ ㈱」⇔「カミイソ産商 ㈱」）で未登録扱いになる場合があります。
                まずは <Link href="/finance/payment-mf-rules" className="underline font-medium">マッピングマスタ</Link> で類似名を検索し、既存ルールの送り先名を Excel 表記に合わせて修正してください。
                該当がなければ「ルール追加」で新規登録できます。
              </p>
              <div className="space-y-1">
                {preview.unregisteredSources.map((c) => (
                  <div key={c} className="flex items-center justify-between gap-2 text-sm">
                    <code className="rounded bg-white px-2 py-0.5">{c}</code>
                    <div className="flex gap-2">
                      <Button asChild size="sm" variant="ghost">
                        <Link href={`/finance/payment-mf-rules?q=${encodeURIComponent(c)}`}>
                          マスタで検索
                        </Link>
                      </Button>
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
                  <th className="p-1 text-right">請求額<AmountSourceTooltip source="INVOICE" /></th>
                  <th className="p-1 text-right">買掛金<AmountSourceTooltip source="PAYABLE_SUMMARY" /></th>
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
                  <tr key={`${r.excelRowIndex ?? 'x'}-${r.paymentSupplierCode ?? 'x'}-${i}`} className={rowBgClass(r)}>
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

      {/*
        通常確定 / 再確定ダイアログ。
        force=true 経路は下の専用 Dialog (forceDialogOpen) で扱うため、ここでは扱わない。
      */}
      <ConfirmDialog
        open={confirmVerify}
        onOpenChange={setConfirmVerify}
        title={preview?.appliedWarning ? '既に確定済の月を再確定' : '買掛金一覧へ反映'}
        description={
          preview?.appliedWarning
            ? `この月は ${formatDateTime(preview.appliedWarning.appliedAt)} に既に確定済です。再確定すると確定済の値を上書きします（手動確定 verified_manually=true 行は保護されます）。続行しますか？`
            : 'この突合結果で買掛金一覧を検証確定します。よろしいですか？（verified_manually=true として手動確定扱いになります）'
        }
        confirmLabel={preview?.appliedWarning ? '上書きして再確定する' : '反映する'}
        onConfirm={() => {
          // ダイアログを即座に閉じることで二重起動を防止する（mutation 完了前の連打対策）。
          setConfirmVerify(false)
          if (preview && !verifyMut.isPending) {
            verifyMut.mutate({ uploadId: preview.uploadId, force: false })
          }
        }}
      />

      {/*
        G2-M2 (Frontend Major, 2026-05-06): 強制反映 (force=true) 専用ダイアログ。
        100 件省略表示で承認内容と実処理が乖離するリスクを抑えるため、
        - 全 mismatch CSV ダウンロード
        - 全件確認 checkbox (reviewedAll)
        - 反映理由 textarea (forceReason, audit log に記録予定)
        の3点を必須化する。`!reviewedAll || !forceReason.trim()` の間は実行不可。
      */}
      <Dialog open={forceDialogOpen} onOpenChange={setForceDialogOpen}>
        <DialogContent className="max-w-2xl">
          <DialogHeader>
            <DialogTitle>強制反映 (force=true) の確認</DialogTitle>
            <DialogDescription>
              per-supplier 1 円整合性違反を許容して買掛金一覧へ反映します。
              全違反明細は <code>finance_audit_log.force_mismatch_details</code> (JSONB) に記録されます。
            </DialogDescription>
          </DialogHeader>
          {preview && hasPerSupplierMismatch(preview) && (
            <div className="space-y-4">
              <Alert variant="destructive">
                <AlertTriangle className="h-4 w-4" />
                <AlertTitle>
                  per-supplier 整合性違反 {preview.amountReconciliation!.perSupplierMismatches.length} 件
                </AlertTitle>
                <AlertDescription>
                  <div className="mb-2 text-xs">
                    1 円単位の不一致があります。force=true で反映すると、誤った金額が
                    買掛金一覧および MF CSV に反映される可能性があります。
                    まず CSV を全件ダウンロードし、内容を確認してください。
                  </div>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => downloadMismatchesCsv(preview.amountReconciliation!.perSupplierMismatches)}
                  >
                    <FileDown className="mr-1 h-4 w-4" />
                    全 {preview.amountReconciliation!.perSupplierMismatches.length} 件 CSV ダウンロード
                  </Button>
                </AlertDescription>
              </Alert>

              <div className="space-y-1">
                <Label htmlFor="force-reason" className="text-xs">
                  反映理由 (audit log に記録されます) <span className="text-red-600">*</span>
                </Label>
                <Textarea
                  id="force-reason"
                  value={forceReason}
                  onChange={(e) => setForceReason(e.target.value)}
                  placeholder="例: 期末締めのため端数許容で反映 / supplier XX の手数料調整など"
                  rows={3}
                />
              </div>

              <div className="flex items-start gap-2 rounded border border-red-300 bg-red-50 p-2 text-xs">
                <Checkbox
                  id="reviewed-all"
                  checked={reviewedAll}
                  onCheckedChange={(v) => setReviewedAll(v === true)}
                  className="mt-0.5"
                />
                <Label htmlFor="reviewed-all" className="cursor-pointer">
                  全 {preview.amountReconciliation!.perSupplierMismatches.length} 件の違反内容を確認しました
                  （CSV ダウンロード or 上のリストで全件レビュー済）
                </Label>
              </div>
            </div>
          )}
          <DialogFooter>
            <Button variant="outline" onClick={() => setForceDialogOpen(false)}>
              キャンセル
            </Button>
            <Button
              variant="destructive"
              disabled={!reviewedAll || !forceReason.trim() || verifyMut.isPending}
              onClick={() => {
                // 二重起動防止のため即座に閉じる。
                setForceDialogOpen(false)
                if (preview && hasPerSupplierMismatch(preview) && !verifyMut.isPending) {
                  verifyMut.mutate({
                    uploadId: preview.uploadId,
                    force: true,
                    forceReason: forceReason.trim(),
                  })
                }
              }}
            >
              強制反映する (force=true)
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

/**
 * P1-08: ISO 8601 OffsetDateTime を `yyyy-MM-dd HH:mm` (JST 表示) にフォーマットする。
 * パース失敗時は元文字列をそのまま返す (UX を壊さない)。
 */
function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '-'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  const pad = (n: number) => n.toString().padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

/**
 * G2-M2: per-supplier 1 円整合性違反が 1 件でもあるか判定する。
 * preview / amountReconciliation / perSupplierMismatches のいずれかが null なら false。
 */
function hasPerSupplierMismatch(preview: PaymentMfPreviewResponse | null): boolean {
  if (!preview || !preview.amountReconciliation) return false
  const mm = preview.amountReconciliation.perSupplierMismatches
  return Array.isArray(mm) && mm.length > 0
}

/**
 * G2-M2 (Frontend Major, 2026-05-06): per-supplier mismatch を CSV 化してダウンロードする。
 *
 * <p>UI 側は性能維持のため `slice(0, 100)` で表示を省略しているが、force 反映前にユーザーが
 * 全件を目視確認できる経路として CSV ダウンロードを提供する。Excel で開いて検索/並び替えが可能。
 *
 * <p>BOM (`﻿`) を先頭に付与して Excel での文字化けを回避。double quote は `""` で escape。
 */
function downloadMismatchesCsv(mismatches: string[]): void {
  const header = 'no,detail'
  const lines = mismatches.map((m, i) => `${i + 1},"${m.replace(/"/g, '""')}"`)
  const csv = [header, ...lines].join('\r\n')
  const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  const today = new Date().toISOString().slice(0, 10)
  a.download = `payment-mf-mismatches-${today}.csv`
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
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
  // P1-03 案 D / P1-07 案 D: 副行 (PAYABLE_*/DIRECT_PURCHASE_* FEE/DISCOUNT/EARLY/OFFSET) は
  // 薄い indigo で親 (PAYABLE/DIRECT_PURCHASE) と区別。5日払い・20日払いとも同色で構造を強調。
  if (
    r.ruleKind &&
    (r.ruleKind.startsWith('PAYABLE_') || r.ruleKind.startsWith('DIRECT_PURCHASE_'))
  )
    return 'bg-indigo-50'
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
  if (status === 'NA') return <span className="text-muted-foreground">対象外</span>
  return <span className="text-muted-foreground">–</span>
}
