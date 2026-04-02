'use client'

import { useState } from 'react'
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
}

const colorMap = {
  amber: {
    gradient: 'from-amber-500 to-orange-500',
    iconBg: 'bg-amber-500/10',
    iconText: 'text-amber-600',
    ring: 'ring-amber-200',
    stepBg: 'bg-amber-500',
  },
  rose: {
    gradient: 'from-rose-500 to-pink-500',
    iconBg: 'bg-rose-500/10',
    iconText: 'text-rose-600',
    ring: 'ring-rose-200',
    stepBg: 'bg-rose-500',
  },
  sky: {
    gradient: 'from-sky-500 to-blue-500',
    iconBg: 'bg-sky-500/10',
    iconText: 'text-sky-600',
    ring: 'ring-sky-200',
    stepBg: 'bg-sky-500',
  },
  emerald: {
    gradient: 'from-emerald-500 to-teal-500',
    iconBg: 'bg-emerald-500/10',
    iconText: 'text-emerald-600',
    ring: 'ring-emerald-200',
    stepBg: 'bg-emerald-500',
  },
}

export function BatchPanel({ title, description, detail, icon: Icon, color, step, jobName }: BatchPanelProps) {
  const [status, setStatus] = useState<'idle' | 'running' | 'completed' | 'failed'>('idle')
  const colors = colorMap[color]

  const handleExecute = async () => {
    setStatus('running')
    try {
      await api.post(`/batch/execute/${jobName}`)
      setStatus('completed')
    } catch {
      setStatus('failed')
    }
  }

  return (
    <div className="group relative flex flex-col rounded-xl bg-card shadow-sm ring-1 ring-border/50 transition-all duration-200 hover:shadow-md hover:ring-border">
      {/* Gradient top bar */}
      <div className={`h-1 rounded-t-xl bg-gradient-to-r ${colors.gradient}`} />

      <div className="flex flex-1 flex-col p-5">
        {/* Header */}
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

        {/* Description */}
        <p className="mt-3 text-[13px] leading-relaxed text-muted-foreground">
          {description}
        </p>
        <p className="mt-1 text-[11px] leading-relaxed text-muted-foreground/60 break-all">
          {detail}
        </p>

        {/* Spacer */}
        <div className="flex-1" />

        {/* Action */}
        <div className="mt-4 flex items-center gap-2">
          <Button
            size="sm"
            onClick={handleExecute}
            disabled={status === 'running'}
            className={`bg-gradient-to-r ${colors.gradient} text-white shadow-sm hover:opacity-90 transition-opacity`}
          >
            {status === 'running' ? (
              <><Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />実行中</>
            ) : (
              <><Play className="mr-1.5 h-3.5 w-3.5" />バッチ起動</>
            )}
          </Button>
          {status === 'completed' && (
            <span className="flex items-center gap-1 text-xs font-medium text-emerald-600">
              <CheckCircle2 className="h-3.5 w-3.5" />完了
            </span>
          )}
          {status === 'failed' && (
            <span className="flex items-center gap-1 text-xs font-medium text-destructive">
              <XCircle className="h-3.5 w-3.5" />失敗
            </span>
          )}
        </div>
      </div>
    </div>
  )
}
