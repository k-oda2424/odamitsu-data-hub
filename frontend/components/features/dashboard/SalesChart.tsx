'use client'

import { BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Legend, ResponsiveContainer } from 'recharts'
import { formatCurrency } from '@/lib/utils'

interface SalesChartData {
  month: string
  thisYear: number
  lastYear: number
}

interface SalesChartProps {
  data: SalesChartData[]
  thisYearLabel: string
  lastYearLabel: string
}

function formatAxisValue(value: number): string {
  if (value >= 10000) {
    return `${Math.round(value / 10000)}万`
  }
  return String(value)
}

export function SalesChart({ data, thisYearLabel, lastYearLabel }: SalesChartProps) {
  if (data.length === 0) {
    return <div className="text-center text-muted-foreground py-8">データがありません</div>
  }

  return (
    <ResponsiveContainer width="100%" height={350}>
      <BarChart data={data} margin={{ left: 10, right: 10 }}>
        <CartesianGrid strokeDasharray="3 3" />
        <XAxis dataKey="month" />
        <YAxis width={80} tickFormatter={formatAxisValue} />
        <Tooltip formatter={(value) => formatCurrency(Number(value))} />
        <Legend />
        <Bar dataKey="thisYear" fill="rgb(255, 0, 0)" name={thisYearLabel} />
        <Bar dataKey="lastYear" fill="rgba(100, 100, 255, 1)" name={lastYearLabel} />
      </BarChart>
    </ResponsiveContainer>
  )
}
