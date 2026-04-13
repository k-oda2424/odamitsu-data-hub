'use client'

import { useRouter } from 'next/navigation'
import { useMemo } from 'react'
import { useQuery } from '@tanstack/react-query'
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
} from 'lucide-react'

interface CashbookHistory {
  periodLabel: string | null
  fileName: string | null
  processedAt: string | null
  rowCount: number
  totalIncome: number
  totalPayment: number
}

interface AccountingStatus {
  cashbookHistory: CashbookHistory[]
  smilePurchaseLatestDate: string | null
  smilePaymentLatestDate: string | null
  invoiceLatest: { shopNo: number; closingDate: string; count: number }[]
  accountsPayableLatestMonth: string | null
  batchJobs: { jobName: string; status: string; exitCode: string; startTime: string | null; endTime: string | null }[]
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

function BatchChip({ job }: { job: { jobName: string; status: string; startTime: string | null } }) {
  const names: Record<string, string> = {
    purchaseFileImport: '仕入ファイル',
    smilePaymentImport: '支払取込',
    accountsPayableAggregation: '買掛集計',
    accountsPayableVerification: '買掛検証',
    accountsPayableSummary: '買掛サマリ',
    purchaseJournalIntegration: '仕入仕訳CSV',
    salesJournalIntegration: '売上仕訳CSV',
  }
  const label = names[job.jobName] ?? job.jobName
  const isOk = job.status === 'COMPLETED'
  const date = job.startTime ? job.startTime.split('T')[0] : null

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
  color: string
  bgLight: string
  bgIcon: string
  borderColor: string
  statusRenderer?: (status: AccountingStatus) => React.ReactNode
}

const makeSteps = (): WorkflowStep[] => [
  {
    step: 1,
    title: '現金出納帳取込',
    icon: <BookOpen className="h-5 w-5 text-amber-600" />,
    timing: '毎月21日',
    description: 'SMILEの現金出納帳データを取り込み、MoneyForward用の仕訳CSVを出力',
    actions: [{ label: '出納帳取込を開く', href: '/finance/cashbook-import' }],
    note: '土日を挟む場合は翌月曜に実施',
    color: 'text-amber-700',
    bgLight: 'bg-amber-50',
    bgIcon: 'bg-amber-100',
    borderColor: 'border-amber-200',
    statusRenderer: (s) => {
      if (!s.cashbookHistory || s.cashbookHistory.length === 0) {
        return <StatusChip label="現金出納帳" value={null} />
      }
      return (
        <div className="flex flex-wrap gap-1.5">
          {s.cashbookHistory.map((h, i) => (
            <StatusChip
              key={i}
              label={h.periodLabel ?? '不明'}
              value={h.processedAt ? h.processedAt.split('T')[0].split('.')[0] + ` (${h.rowCount}件)` : null}
            />
          ))}
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
    color: 'text-sky-700',
    bgLight: 'bg-sky-50',
    bgIcon: 'bg-sky-100',
    borderColor: 'border-sky-200',
    statusRenderer: (s) => (
      <div className="flex flex-wrap gap-1.5">
        <StatusChip label="仕入ファイル最新" value={s.smilePurchaseLatestDate} />
        <StatusChip label="支払取込最新" value={s.smilePaymentLatestDate} />
        {s.batchJobs.filter(j => j.jobName === 'purchaseFileImport' || j.jobName === 'smilePaymentImport').map(j => (
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
    color: 'text-violet-700',
    bgLight: 'bg-violet-50',
    bgIcon: 'bg-violet-100',
    borderColor: 'border-violet-200',
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
    description: '売掛金の仕訳データをMoneyForward用CSVとして出力',
    actions: [{ label: 'バッチ管理', href: '/batch' }],
    note: 'バッチ「売上仕訳CSV出力」を実行',
    color: 'text-emerald-700',
    bgLight: 'bg-emerald-50',
    bgIcon: 'bg-emerald-100',
    borderColor: 'border-emerald-200',
    statusRenderer: (s) => {
      const job = s.batchJobs.find(j => j.jobName === 'salesJournalIntegration')
      return job ? <BatchChip job={job} /> : <StatusChip label="売上仕訳CSV" value={null} />
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
    color: 'text-rose-700',
    bgLight: 'bg-rose-50',
    bgIcon: 'bg-rose-100',
    borderColor: 'border-rose-200',
    statusRenderer: (s) => (
      <div className="flex flex-wrap gap-1.5">
        <StatusChip label="買掛金サマリ最新月" value={s.accountsPayableLatestMonth} />
        {s.batchJobs
          .filter(j => ['accountsPayableAggregation','accountsPayableVerification','accountsPayableSummary','purchaseJournalIntegration'].includes(j.jobName))
          .map(j => <BatchChip key={j.jobName} job={j} />)}
      </div>
    ),
  },
]

interface ScheduleItem {
  period: string
  day: string
  tasks: { stepNo: number; label: string; color: string }[]
}

const schedule: ScheduleItem[] = [
  {
    period: '15日締',
    day: '22日頃',
    tasks: [
      { stepNo: 3, label: '請求取込', color: 'bg-violet-500' },
      { stepNo: 4, label: '売掛MF', color: 'bg-emerald-500' },
    ],
  },
  {
    period: '20日締',
    day: '27日頃',
    tasks: [
      { stepNo: 3, label: '請求取込', color: 'bg-violet-500' },
      { stepNo: 4, label: '売掛MF', color: 'bg-emerald-500' },
      { stepNo: 5, label: '買掛金', color: 'bg-rose-500' },
    ],
  },
  {
    period: '毎月',
    day: '21日',
    tasks: [
      { stepNo: 1, label: '出納帳', color: 'bg-amber-500' },
    ],
  },
  {
    period: '月末締',
    day: '翌7日頃',
    tasks: [
      { stepNo: 3, label: '請求取込', color: 'bg-violet-500' },
      { stepNo: 4, label: '売掛MF', color: 'bg-emerald-500' },
    ],
  },
]

export function AccountingWorkflowPage() {
  const router = useRouter()
  const workflowSteps = useMemo(() => makeSteps(), [])

  const statusQuery = useQuery({
    queryKey: ['accounting-status'],
    queryFn: () => api.get<AccountingStatus>('/finance/accounting-status'),
    staleTime: Infinity,
  })

  const status = statusQuery.data

  return (
    <div className="space-y-6">
      <PageHeader title="経理業務フロー" />

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
                  <span className={`inline-flex h-5 w-5 items-center justify-center rounded-full text-[10px] font-bold text-white ${task.color}`}>
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
        {workflowSteps.map((s) => (
          <Card key={s.step} className={`overflow-hidden border-l-4 ${s.borderColor}`}>
            <CardContent className="p-0">
              <div className="flex">
                {/* ステップ番号 + アイコン */}
                <div className={`flex flex-col items-center justify-center gap-1 px-5 py-4 ${s.bgLight}`}>
                  <div className={`flex h-10 w-10 items-center justify-center rounded-xl ${s.bgIcon} font-bold text-lg ${s.color}`}>
                    {s.step}
                  </div>
                  <div className={`mt-1 ${s.bgIcon} rounded-full p-1.5`}>
                    {s.icon}
                  </div>
                </div>

                {/* コンテンツ */}
                <div className="flex-1 px-5 py-4 space-y-2">
                  <div className="flex items-center gap-3">
                    <h3 className={`text-base font-semibold ${s.color}`}>{s.title}</h3>
                    <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ${s.bgLight} ${s.color} ring-1 ring-inset ${s.borderColor}`}>
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
                    <div className={`flex items-start gap-2 text-xs rounded-lg px-3 py-2 ${s.bgLight} ${s.color}`}>
                      <CheckCircle2 className="h-3.5 w-3.5 shrink-0 mt-0.5 opacity-60" />
                      <span>{s.note}</span>
                    </div>
                  )}

                  <div className="flex gap-2 pt-1">
                    {s.actions.map((action) => (
                      <Button
                        key={action.href}
                        size="sm"
                        className={`${s.bgLight} ${s.color} border ${s.borderColor} hover:opacity-80 shadow-none`}
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
        ))}
      </div>
    </div>
  )
}
