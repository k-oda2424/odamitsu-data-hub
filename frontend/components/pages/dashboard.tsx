'use client'

import { useQuery } from '@tanstack/react-query'
import { ShoppingCart, LifeBuoy, MessageSquare, ListChecks, BarChart3, Bell } from 'lucide-react'
import { api } from '@/lib/api-client'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { SalesChart } from '@/components/features/dashboard/SalesChart'
import { BatchPanel } from '@/components/features/dashboard/BatchPanel'
import { WorkflowGuide } from '@/components/features/dashboard/WorkflowGuide'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import type { SalesSummary } from '@/types'

function buildChartData(rawData: SalesSummary[]) {
  const now = new Date()
  const fiscalYearStart = now.getMonth() >= 6 ? now.getFullYear() : now.getFullYear() - 1
  const thisYearStart = fiscalYearStart
  const lastYearStart = fiscalYearStart - 1

  const months = ['7月', '8月', '9月', '10月', '11月', '12月', '1月', '2月', '3月', '4月', '5月', '6月']

  const thisYearMap = new Map<string, number>()
  const lastYearMap = new Map<string, number>()

  for (const item of rawData) {
    const parts = item.month.replace('/', '-').split('-')
    if (parts.length < 2) continue
    const year = parseInt(parts[0])
    const month = parseInt(parts[1])

    const belongsToFiscalYear = month >= 7 ? year : year - 1

    const monthLabel = `${month}月`
    if (belongsToFiscalYear === thisYearStart) {
      thisYearMap.set(monthLabel, (thisYearMap.get(monthLabel) ?? 0) + item.totalSales)
    } else if (belongsToFiscalYear === lastYearStart) {
      lastYearMap.set(monthLabel, (lastYearMap.get(monthLabel) ?? 0) + item.totalSales)
    }
  }

  const chartData = months.map((m) => ({
    month: m,
    thisYear: thisYearMap.get(m) ?? 0,
    lastYear: lastYearMap.get(m) ?? 0,
  }))

  return {
    chartData,
    thisYearLabel: `${thisYearStart}年度`,
    lastYearLabel: `${lastYearStart}年度`,
  }
}

export function DashboardPage() {
  const salesQuery = useQuery({
    queryKey: ['dashboard', 'sales-summary'],
    queryFn: () => api.get<SalesSummary[]>('/dashboard/sales-summary'),
  })

  const { chartData, thisYearLabel, lastYearLabel } = buildChartData(salesQuery.data ?? [])

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold">Dashboard</h1>

      <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <BatchPanel
          title="新規受注取込"
          description="小田光オンラインから新規受注データを取り込み、Smileへの売上明細連携ファイルを作成します
場所：\\Smile-srv\共有\業務内容\ネットショップ関連\連携ファイル\smileへ連携"
          icon={ShoppingCart}
          color="yellow"
          jobName="bCartOrderImport"
        />
        <BatchPanel
          title="売上明細取込"
          description="Smileから売上明細データファイル取込を行います
事前にSmileの随時業務＞テキスト出力（明細）＞売上明細でファイルを出力してください"
          icon={LifeBuoy}
          color="red"
          jobName="oneDayClose"
        />
        <BatchPanel
          title="出荷実績CSV"
          description="小田光オンラインに取り込む出荷実績CSVを作成します
場所：\\Smile-srv\共有\業務内容\ネットショップ関連\連携ファイル\小田光オンラインへ連携"
          icon={MessageSquare}
          color="blue"
          jobName="bCartLogisticsCsvExport"
        />
        <BatchPanel
          title="新規会員取込"
          description="小田光オンラインから新規会員情報を取り込み、SMILEへ連携するファイルを生成します
場所：\\Smile-srv\共有\業務内容\ネットショップ関連\連携ファイル\smileへ連携"
          icon={ListChecks}
          color="green"
          jobName="bCartMemberUpdate"
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-medium flex items-center gap-2">
              <BarChart3 className="h-4 w-4" />
              売上推移
            </CardTitle>
          </CardHeader>
          <CardContent>
            {salesQuery.isLoading ? (
              <LoadingSpinner />
            ) : salesQuery.isError ? (
              <ErrorMessage onRetry={() => salesQuery.refetch()} />
            ) : (
              <SalesChart
                data={chartData}
                thisYearLabel={thisYearLabel}
                lastYearLabel={lastYearLabel}
              />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader className="pb-2">
            <CardTitle className="text-base font-medium flex items-center gap-2">
              <Bell className="h-4 w-4" />
              受注から出荷の流れ
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <WorkflowGuide />
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
