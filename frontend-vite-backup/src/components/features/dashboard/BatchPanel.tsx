import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { api } from '@/lib/api-client'
import type { LucideIcon } from 'lucide-react'

interface BatchPanelProps {
  title: string
  description: string
  icon: LucideIcon
  color: 'yellow' | 'red' | 'blue' | 'green'
  jobName: string
}

const colorMap = {
  yellow: {
    bg: 'bg-amber-500',
    border: 'border-amber-500',
    headerBg: 'bg-amber-500',
    footerBg: 'bg-amber-50',
    text: 'text-white',
  },
  red: {
    bg: 'bg-red-500',
    border: 'border-red-500',
    headerBg: 'bg-red-500',
    footerBg: 'bg-red-50',
    text: 'text-white',
  },
  blue: {
    bg: 'bg-blue-600',
    border: 'border-blue-600',
    headerBg: 'bg-blue-600',
    footerBg: 'bg-blue-50',
    text: 'text-white',
  },
  green: {
    bg: 'bg-green-500',
    border: 'border-green-500',
    headerBg: 'bg-green-500',
    footerBg: 'bg-green-50',
    text: 'text-white',
  },
}

export function BatchPanel({ title, description, icon: Icon, color, jobName }: BatchPanelProps) {
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
    <div className={`rounded-lg border-t-4 ${colors.border} bg-card shadow-sm overflow-hidden`}>
      <div className={`${colors.headerBg} ${colors.text} p-4`}>
        <div className="flex items-start gap-3">
          <Icon className="h-10 w-10 shrink-0 opacity-90" />
          <div className="flex-1 text-right">
            <div className="text-lg font-bold">{title}</div>
          </div>
        </div>
        <p className="mt-2 text-sm opacity-90 leading-relaxed">{description}</p>
      </div>
      <div className={`${colors.footerBg} px-4 py-3 flex items-center gap-3`}>
        <Button
          size="sm"
          variant="outline"
          onClick={handleExecute}
          disabled={status === 'running'}
        >
          {status === 'running' ? '実行中…' : 'バッチ起動'}
        </Button>
        {status === 'completed' && (
          <span className="text-sm text-green-600 font-medium">完了</span>
        )}
        {status === 'failed' && (
          <span className="text-sm text-red-600 font-medium">実行失敗</span>
        )}
      </div>
    </div>
  )
}
