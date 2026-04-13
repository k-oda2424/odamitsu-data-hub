'use client'

import { useState, useEffect, useRef } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api-client'
import { Play, CheckCircle2, XCircle, Loader2 } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

interface BatchPanelProps {
  title: string
  description: string
  detail: string
  icon: LucideIcon
  color: 'amber' | 'rose' | 'sky' | 'emerald'
  step: number
  jobName: string
  shopNo?: number
}

interface JobStatus {
  jobName: string
  status: string
  exitCode?: string
  startTime?: string
}

const colorMap = {
  amber: {
    gradient: 'from-amber-500 to-orange-500',
    iconBg: 'bg-amber-500/10',
    iconText: 'text-amber-600',
    stepBg: 'bg-amber-500',
  },
  rose: {
    gradient: 'from-rose-500 to-pink-500',
    iconBg: 'bg-rose-500/10',
    iconText: 'text-rose-600',
    stepBg: 'bg-rose-500',
  },
  sky: {
    gradient: 'from-sky-500 to-blue-500',
    iconBg: 'bg-sky-500/10',
    iconText: 'text-sky-600',
    stepBg: 'bg-sky-500',
  },
  emerald: {
    gradient: 'from-emerald-500 to-teal-500',
    iconBg: 'bg-emerald-500/10',
    iconText: 'text-emerald-600',
    stepBg: 'bg-emerald-500',
  },
}

export function BatchPanel({ title, description, detail, icon: Icon, color, step, jobName, shopNo }: BatchPanelProps) {
  const [polling, setPolling] = useState(false)
  const [launchedAt, setLaunchedAt] = useState<string | null>(null)
  const [displayStatus, setDisplayStatus] = useState<'idle' | 'running' | 'completed' | 'failed'>('idle')
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const colors = colorMap[color]

  // アンマウント時にタイマーをクリア
  useEffect(() => () => { if (timerRef.current) clearTimeout(timerRef.current) }, [])

  const statusQuery = useQuery({
    queryKey: ['batch-panel-status', jobName],
    queryFn: () => api.get<JobStatus>(`/batch/status/${jobName}`),
    enabled: polling,
    refetchInterval: polling ? 5000 : false,
  })

  useEffect(() => {
    if (!polling || !statusQuery.data || !launchedAt) return
    const { status, startTime } = statusQuery.data
    // 起動後に開始された実行のみ判定（前回の結果を無視）
    if (startTime && new Date(startTime).getTime() < new Date(launchedAt).getTime()) return
    if (status === 'COMPLETED') {
      setDisplayStatus('completed')
      setPolling(false)
    } else if (status === 'FAILED') {
      setDisplayStatus('failed')
      setPolling(false)
    }
  }, [polling, statusQuery.data, launchedAt])

  const handleExecute = async () => {
    setDisplayStatus('running')
    try {
      const params = shopNo != null ? `?shopNo=${shopNo}` : ''
      await api.post(`/batch/execute/${jobName}${params}`)
      // 非同期実行のため、ジョブレコード作成を待ってからポーリング開始
      setLaunchedAt(new Date().toISOString())
      timerRef.current = setTimeout(() => setPolling(true), 3000)
    } catch {
      setDisplayStatus('failed')
    }
  }

  return (
    <div className="group relative flex flex-col rounded-xl bg-card shadow-sm ring-1 ring-border/50 transition-all duration-200 hover:shadow-md hover:ring-border">
      <div className={`h-1 rounded-t-xl bg-gradient-to-r ${colors.gradient}`} />
      <div className="flex flex-1 flex-col p-5">
        <div className="flex items-start gap-3">
          <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${colors.iconBg}`}>
            <Icon className={`h-5 w-5 ${colors.iconText}`} />
          </div>
          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2">
              <span className={`inline-flex h-5 w-5 items-center justify-center rounded-full ${colors.stepBg} text-[10px] font-bold text-white`}>
                {step}
              </span>
              <h3 className="font-semibold text-sm truncate">{title}</h3>
            </div>
          </div>
        </div>
        <p className="mt-3 text-[13px] leading-relaxed text-muted-foreground">{description}</p>
        <p className="mt-1 text-[11px] leading-relaxed text-muted-foreground/60 break-all">{detail}</p>
        <div className="flex-1" />
        <div className="mt-4 flex items-center gap-2">
          <Button
            size="sm"
            onClick={handleExecute}
            disabled={displayStatus === 'running'}
            className={`bg-gradient-to-r ${colors.gradient} text-white shadow-sm hover:opacity-90 transition-opacity`}
          >
            {displayStatus === 'running' ? (
              <><Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />実行中...</>
            ) : (
              <><Play className="mr-1.5 h-3.5 w-3.5" />バッチ起動</>
            )}
          </Button>
          {displayStatus === 'completed' && (
            <span className="flex items-center gap-1 text-xs font-medium text-emerald-600">
              <CheckCircle2 className="h-3.5 w-3.5" />完了
            </span>
          )}
          {displayStatus === 'failed' && (
            <span className="flex items-center gap-1 text-xs font-medium text-destructive">
              <XCircle className="h-3.5 w-3.5" />失敗
            </span>
          )}
        </div>
      </div>
    </div>
  )
}
