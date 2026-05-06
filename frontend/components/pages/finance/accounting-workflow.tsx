'use client'

import { useRouter } from 'next/navigation'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { PageHeader } from '@/components/features/common/PageHeader'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import {
  BookOpen,
  Receipt,
  BarChart3,
  ArrowRight,
  FileSpreadsheet,
  Wallet,
  CheckCircle2,
  Clock,
  AlertCircle,
  Loader2,
  RefreshCw,
} from 'lucide-react'
import { formatDate } from '@/lib/utils'

interface CashbookHistory {
  periodLabel: string | null
  fileName: string | null
  processedAt: string | null
  rowCount: number
  totalIncome: number
  totalPayment: number
}

interface InvoiceLatest {
  shopNo: number
  closingDate: string
  count: number
}

interface BatchJobStatus {
  jobName: string
  status: string
  exitCode: string
  startTime: string | null
  endTime: string | null
}

interface AccountingStatus {
  cashbookHistory: CashbookHistory[]
  smilePurchaseLatestDate: string | null
  smilePaymentLatestDate: string | null
  invoiceLatest: InvoiceLatest[]
  accountsPayableLatestMonth: string | null
  batchJobs: BatchJobStatus[]
}

/**
 * バッチジョブ名から日本語ラベルへの解決マップ。
 * NOTE: backend SF-H05 で `BatchJobCatalog` enum を新設後は
 * `/api/v1/batch/job-catalog` から取得して同期する想定。当面は文字列リテラルで OK。
 */
const JOB_LABELS: Record<string, string> = {
  purchaseFileImport: '仕入ファイル',
  smilePaymentImport: '支払取込',
  accountsPayableAggregation: '買掛集計',
  accountsPayableVerification: '買掛検証',
  accountsPayableSummary: '買掛サマリ',
  accountsReceivableSummary: '売掛集計',
  purchaseJournalIntegration: '仕入仕訳CSV',
  salesJournalIntegration: '売上仕訳CSV',
}

type StepTheme = 'amber' | 'sky' | 'violet' | 'emerald' | 'rose'

interface StepThemeTokens {
  text: string
  bgLight: string
  bgIcon: string
  border: string
  scheduleBadge: string
}

const THEME_TOKENS: Record<StepTheme, StepThemeTokens> = {
  amber: {
    text: 'text-amber-700',
    bgLight: 'bg-amber-50',
    bgIcon: 'bg-amber-100',
    border: 'border-amber-200',
    scheduleBadge: 'bg-amber-500',
  },
  sky: {
    text: 'text-sky-700',
    bgLight: 'bg-sky-50',
    bgIcon: 'bg-sky-100',
    border: 'border-sky-200',
    scheduleBadge: 'bg-sky-500',
  },
  violet: {
    text: 'text-violet-700',
    bgLight: 'bg-violet-50',
    bgIcon: 'bg-violet-100',
    border: 'border-violet-200',
    scheduleBadge: 'bg-violet-500',
  },
  emerald: {
    text: 'text-emerald-700',
    bgLight: 'bg-emerald-50',
    bgIcon: 'bg-emerald-100',
    border: 'border-emerald-200',
    scheduleBadge: 'bg-emerald-500',
  },
  rose: {
    text: 'text-rose-700',
    bgLight: 'bg-rose-50',
    bgIcon: 'bg-rose-100',
    border: 'border-rose-200',
    scheduleBadge: 'bg-rose-500',
  },
}

/**
 * ISO8601 文字列から日付部分のみを抽出。
 * - `2026-04-05T10:00:00.123` → `2026-04-05`
 * - 不正値は元文字列を返す (UI に "—" 等の代替が出るのは呼び出し側の責務)
 *
 * NOTE: date-fns 未インストールのため `slice(0, 10)` で代替。
 * 将来 date-fns 導入時は `format(parseISO(s), 'yyyy-MM-dd')` に置換する。
 */
function isoDate(iso: string | null | undefined): string | null {
  if (!iso) return null
  // YYYY-MM-DD prefix を切り出し (タイムゾーン情報は無視)
  return iso.slice(0, 10)
}

function StatusChip({ label, value, warn }: { label: string; value: string | null; warn?: boolean }) {
  if (!value) {
    return (
      <span className="inline-flex items-center gap-1 rounded-full bg-gray-100 px-2.5 py-1 text-xs text-gray-500">
        <AlertCircle className="h-3 w-3" />
        {label}: 未実行
      </span>
    )
  }
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium ${
      warn ? 'bg-amber-100 text-amber-700' : 'bg-emerald-100 text-emerald-700'
    }`}>
      <CheckCircle2 className="h-3 w-3" />
      {label}: {value}
    </span>
  )
}

function BatchChip({ job }: { job: BatchJobStatus }) {
  const label = JOB_LABELS[job.jobName] ?? job.jobName
  const isOk = job.status === 'COMPLETED'
  const date = isoDate(job.startTime)

  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium ${
      isOk ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'
    }`}>
      {isOk ? <CheckCircle2 className="h-3 w-3" /> : <AlertCircle className="h-3 w-3" />}
      {label} {date ?? ''}
    </span>
  )
}

interface WorkflowStep {
  step: number
  title: string
  icon: React.ReactNode
  timing: string
  description: string
  actions: { label: string; href: string }[]
  note?: string
  theme: StepTheme
  statusRenderer?: (status: AccountingStatus) => React.ReactNode
}

const RECEIVABLE_JOB_NAMES = ['accountsReceivableSummary', 'salesJournalIntegration'] as const
const PAYABLE_JOB_NAMES = [
  'accountsPayableAggregation',
  'accountsPayableVerification',
  'accountsPayableSummary',
  'purchaseJournalIntegration',
] as const

function makeSteps(): WorkflowStep[] {
  return [
    {
      step: 1,
      title: '現金出納帳取込',
      icon: <BookOpen className="h-5 w-5 text-amber-600" />,
      timing: '毎月21日',
      description: 'SMILEの現金出納帳データを取り込み、MoneyForward用の仕訳CSVを出力',
      actions: [{ label: '出納帳取込を開く', href: '/finance/cashbook-import' }],
      note: '土日を挟む場合は翌月曜に実施',
      theme: 'amber',
      statusRenderer: (s) => {
        if (!s.cashbookHistory || s.cashbookHistory.length === 0) {
          return <StatusChip label="現金出納帳" value={null} />
        }
        return (
          <div className="flex flex-wrap gap-1.5">
            {s.cashbookHistory.map((h) => {
              const date = isoDate(h.processedAt)
              return (
                <StatusChip
                  // 同じ periodLabel が複数アップロードされても processedAt で区別可能。
                  key={`${h.periodLabel ?? 'unknown'}-${h.processedAt ?? h.fileName ?? ''}`}
                  label={h.periodLabel ?? '不明'}
                  value={date ? `${date} (${h.rowCount}件)` : null}
                />
              )
            })}
          </div>
        )
      },
    },
    {
      step: 2,
      title: 'SMILE仕入取込',
      icon: <FileSpreadsheet className="h-5 w-5 text-sky-600" />,
      timing: '買掛金集計の前',
      description: 'SMILEの仕入データ（支払情報）をバッチで取り込み。shopNo を指定して実行',
      actions: [{ label: 'バッチ管理', href: '/batch' }],
      note: '「SMILE仕入ファイル取込」「SMILE支払情報取込」を店舗ごとに実行',
      theme: 'sky',
      statusRenderer: (s) => (
        <div className="flex flex-wrap gap-1.5">
          <StatusChip label="仕入ファイル最新" value={s.smilePurchaseLatestDate} />
          <StatusChip label="支払取込最新" value={s.smilePaymentLatestDate} />
          {s.batchJobs
            .filter((j) => j.jobName === 'purchaseFileImport' || j.jobName === 'smilePaymentImport')
            .map((j) => (
              <BatchChip key={j.jobName} job={j} />
            ))}
        </div>
      ),
    },
    {
      step: 3,
      title: '請求データ取込',
      icon: <Receipt className="h-5 w-5 text-violet-600" />,
      timing: '月3回（締日+7日）',
      description: 'SMILEの請求実績Excelをアップロード。15日締/20日締/月末締の3回',
      actions: [{ label: '請求書画面', href: '/finance/invoices' }],
      note: '請求書画面の「Excelインポート」ボタンから取込',
      theme: 'violet',
      statusRenderer: (s) => (
        <div className="flex flex-wrap gap-1.5">
          {s.invoiceLatest.length > 0 ? (
            s.invoiceLatest.map((inv) => (
              <StatusChip key={inv.shopNo} label={`店舗${inv.shopNo} 最新締日`} value={`${inv.closingDate}（${inv.count}件）`} />
            ))
          ) : (
            <StatusChip label="請求データ" value={null} />
          )}
        </div>
      ),
    },
    {
      step: 4,
      title: '売掛金MF連携',
      icon: <BarChart3 className="h-5 w-5 text-emerald-600" />,
      timing: '③と同時',
      description: '売掛金の集計と仕訳CSVをMoneyForward向けに出力',
      actions: [{ label: 'バッチ管理', href: '/batch' }],
      note: 'バッチ実行順: 売掛集計 → 売上仕訳CSV出力',
      theme: 'emerald',
      statusRenderer: (s) => {
        const jobs = s.batchJobs.filter((j) =>
          (RECEIVABLE_JOB_NAMES as readonly string[]).includes(j.jobName),
        )
        if (jobs.length === 0) return <StatusChip label="売掛集計/売上仕訳CSV" value={null} />
        return (
          <div className="flex flex-wrap gap-1.5">
            {jobs.map((j) => (
              <BatchChip key={j.jobName} job={j} />
            ))}
          </div>
        )
      },
    },
    {
      step: 5,
      title: '買掛金集計・連携',
      icon: <Wallet className="h-5 w-5 text-rose-600" />,
      timing: '毎月27日前後',
      description: '買掛金の集計 → 検証 → サマリ → 仕訳CSV出力を順番に実行',
      actions: [
        { label: '買掛金画面', href: '/finance/accounts-payable' },
        { label: 'バッチ管理', href: '/batch' },
      ],
      note: 'バッチ実行順: 買掛金集計 → 買掛金検証 → 買掛金サマリ → 仕入仕訳CSV出力',
      theme: 'rose',
      statusRenderer: (s) => (
        <div className="flex flex-wrap gap-1.5">
          <StatusChip label="買掛金サマリ最新月" value={s.accountsPayableLatestMonth} />
          {s.batchJobs
            .filter((j) => (PAYABLE_JOB_NAMES as readonly string[]).includes(j.jobName))
            .map((j) => (
              <BatchChip key={j.jobName} job={j} />
            ))}
        </div>
      ),
    },
  ]
}

interface ScheduleItem {
  period: string
  day: string
  tasks: { stepNo: number; label: string; theme: StepTheme }[]
}

const schedule: ScheduleItem[] = [
  {
    period: '15日締',
    day: '22日頃',
    tasks: [
      { stepNo: 3, label: '請求取込', theme: 'violet' },
      { stepNo: 4, label: '売掛MF', theme: 'emerald' },
    ],
  },
  {
    period: '20日締',
    day: '27日頃',
    tasks: [
      { stepNo: 3, label: '請求取込', theme: 'violet' },
      { stepNo: 4, label: '売掛MF', theme: 'emerald' },
      { stepNo: 5, label: '買掛金', theme: 'rose' },
    ],
  },
  {
    period: '毎月',
    day: '21日',
    tasks: [{ stepNo: 1, label: '出納帳', theme: 'amber' }],
  },
  {
    period: '月末締',
    day: '翌7日頃',
    tasks: [
      { stepNo: 3, label: '請求取込', theme: 'violet' },
      { stepNo: 4, label: '売掛MF', theme: 'emerald' },
    ],
  },
]

export function AccountingWorkflowPage() {
  const router = useRouter()
  const queryClient = useQueryClient()
  // useMemo は不要 (毎レンダーの static factory 呼び出しで十分軽量、依存も無い)
  const workflowSteps = makeSteps()

  const statusQuery = useQuery({
    queryKey: ['accounting-status'],
    queryFn: () => api.get<AccountingStatus>('/finance/accounting-status'),
    // 30 秒キャッシュ + ウィンドウフォーカスで最新化。
    staleTime: 30_000,
    refetchOnWindowFocus: true,
  })

  const status = statusQuery.data
  const lastFetched = statusQuery.dataUpdatedAt
    ? formatDate(new Date(statusQuery.dataUpdatedAt))
    : null

  const handleRefresh = () => {
    queryClient.invalidateQueries({ queryKey: ['accounting-status'] })
  }

  return (
    <div className="space-y-6">
      <PageHeader
        title="経理業務フロー"
        description={lastFetched ? `最終取得: ${lastFetched}` : undefined}
        actions={
          <Button
            variant="outline"
            size="sm"
            onClick={handleRefresh}
            disabled={statusQuery.isFetching}
          >
            {statusQuery.isFetching ? (
              <Loader2 className="mr-1 h-3 w-3 animate-spin" />
            ) : (
              <RefreshCw className="mr-1 h-3 w-3" />
            )}
            再読み込み
          </Button>
        }
      />

      {statusQuery.isError && (
        <div className="flex items-start gap-2 rounded-md border border-red-300 bg-red-50 p-3 text-sm text-red-800">
          <AlertCircle className="h-4 w-4 mt-0.5 shrink-0" />
          <div className="flex-1">
            <b>ステータス取得に失敗しました。</b>
            <span className="ml-2 text-xs text-red-700">
              {(statusQuery.error as Error | null)?.message ?? 'unknown error'}
            </span>
          </div>
          <Button variant="ghost" size="sm" onClick={handleRefresh}>
            再試行
          </Button>
        </div>
      )}

      {statusQuery.isPending && !statusQuery.isError && (
        <div className="flex items-center gap-2 rounded-md border bg-card p-3 text-sm text-muted-foreground">
          <Loader2 className="h-4 w-4 animate-spin" />
          ステータスを取得しています…
        </div>
      )}

      {/* 月次スケジュール */}
      <div className="grid gap-3 md:grid-cols-4">
        {schedule.map((item) => (
          <Card key={item.period + item.day} className="overflow-hidden">
            <div className="bg-gradient-to-r from-slate-700 to-slate-800 px-4 py-2.5">
              <div className="text-xs text-slate-300">{item.period}</div>
              <div className="text-lg font-bold text-white">{item.day}</div>
            </div>
            <CardContent className="p-3 space-y-1.5">
              {item.tasks.map((task) => (
                <div key={task.stepNo + task.label} className="flex items-center gap-2">
                  <span
                    className={`inline-flex h-5 w-5 items-center justify-center rounded-full text-[10px] font-bold text-white ${THEME_TOKENS[task.theme].scheduleBadge}`}
                  >
                    {task.stepNo}
                  </span>
                  <span className="text-sm">{task.label}</span>
                </div>
              ))}
            </CardContent>
          </Card>
        ))}
      </div>

      {/* ワークフローステップ */}
      <div className="space-y-3">
        {workflowSteps.map((s) => {
          const t = THEME_TOKENS[s.theme]
          return (
            <Card key={s.step} className={`overflow-hidden border-l-4 ${t.border}`}>
              <CardContent className="p-0">
                <div className="flex">
                  {/* ステップ番号 + アイコン */}
                  <div className={`flex flex-col items-center justify-center gap-1 px-5 py-4 ${t.bgLight}`}>
                    <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${t.bgIcon} font-bold text-lg ${t.text}`}>
                      {s.step}
                    </div>
                    <div className={`mt-1 ${t.bgIcon} rounded-full p-1.5`}>
                      {s.icon}
                    </div>
                  </div>

                  {/* コンテンツ */}
                  <div className="flex-1 px-5 py-4 space-y-2">
                    <div className="flex items-center gap-3">
                      <h3 className={`text-base font-semibold ${t.text}`}>{s.title}</h3>
                      <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${t.bgLight} ${t.text} ring-1 ring-inset ${t.border}`}>
                        {s.timing}
                      </span>
                    </div>

                    <p className="text-sm text-muted-foreground leading-relaxed">
                      {s.description}
                    </p>

                    {/* ステータス表示 */}
                    {status && s.statusRenderer && (
                      <div className="flex items-center gap-2">
                        <Clock className="h-3.5 w-3.5 text-muted-foreground shrink-0" />
                        {s.statusRenderer(status)}
                      </div>
                    )}

                    {s.note && (
                      <div className={`flex items-start gap-2 text-xs rounded-lg px-3 py-2 ${t.bgLight} ${t.text}`}>
                        <CheckCircle2 className="h-3.5 w-3.5 shrink-0 mt-0.5 opacity-60" />
                        <span>{s.note}</span>
                      </div>
                    )}

                    <div className="flex gap-2 pt-1">
                      {s.actions.map((action) => (
                        <Button
                          key={action.href}
                          size="sm"
                          className={`${t.bgLight} ${t.text} border ${t.border} hover:opacity-80 shadow-none`}
                          variant="outline"
                          onClick={() => router.push(action.href)}
                        >
                          {action.label}
                          <ArrowRight className="ml-1 h-3 w-3" />
                        </Button>
                      ))}
                    </div>
                  </div>
                </div>
              </CardContent>
            </Card>
          )
        })}
      </div>
    </div>
  )
}

// 公開: E2E テスト・他コンポーネントから AccountingStatus 型を参照可能にする。
// SF-H06 で backend に AccountingStatusResponse record が新設される予定なので、
// 名前と shape はそのまま同期できるよう同じ命名で export する。
export type { AccountingStatus, BatchJobStatus, CashbookHistory, InvoiceLatest }
