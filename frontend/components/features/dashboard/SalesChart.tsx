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
      <BarChart data={data} margin={{ left: 10, right: 10, top: 5, bottom: 5 }}>
        <defs>
          <linearGradient id="bar-this-year" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="hsl(350, 80%, 55%)" stopOpacity={0.9} />
            <stop offset="100%" stopColor="hsl(350, 80%, 55%)" stopOpacity={0.6} />
          </linearGradient>
          <linearGradient id="bar-last-year" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="hsl(220, 80%, 58%)" stopOpacity={0.9} />
            <stop offset="100%" stopColor="hsl(220, 80%, 58%)" stopOpacity={0.6} />
          </linearGradient>
        </defs>
        <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" strokeOpacity={0.4} vertical={false} />
        <XAxis
          dataKey="month"
          tick={{ fontSize: 12, fill: 'hsl(var(--muted-foreground))' }}
          axisLine={false}
          tickLine={false}
        />
        <YAxis
          width={60}
          tickFormatter={formatAxisValue}
          tick={{ fontSize: 12, fill: 'hsl(var(--muted-foreground))' }}
          axisLine={false}
          tickLine={false}
        />
        <Tooltip
          formatter={(value) => formatCurrency(Number(value))}
          contentStyle={{
            backgroundColor: 'hsl(var(--popover))',
            border: '1px solid hsl(var(--border))',
            borderRadius: '8px',
            boxShadow: '0 4px 12px rgb(0 0 0 / 0.1)',
            fontSize: '13px',
          }}
          labelStyle={{ color: 'hsl(var(--foreground))', fontWeight: 600, marginBottom: 4 }}
          cursor={{ fill: 'hsl(var(--muted))', opacity: 0.2 }}
        />
        <Legend
          iconType="circle"
          iconSize={8}
          wrapperStyle={{ fontSize: '13px', paddingTop: 16 }}
        />
        <Bar
          dataKey="thisYear"
          fill="url(#bar-this-year)"
          name={thisYearLabel}
          radius={[6, 6, 0, 0]}
          maxBarSize={28}
        />
        <Bar
          dataKey="lastYear"
          fill="url(#bar-last-year)"
          name={lastYearLabel}
          radius={[6, 6, 0, 0]}
          maxBarSize={28}
        />
      </BarChart>
    </ResponsiveContainer>
  )
}
