'use client'

import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useAuth } from '@/lib/auth'
import { useShops } from '@/hooks/use-master-data'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { Play, Loader2, CheckCircle2, XCircle, Terminal } from 'lucide-react'
import { toast } from 'sonner'

interface JobDefinition {
  jobName: string
  category: string
  description: string
  available: boolean
  requiresShopNo: string
}

interface JobStatus {
  jobName: string
  status: string
  exitCode?: string
  startTime?: string
  endTime?: string
  exitMessage?: string
}

export function BatchManagementPage() {
  const { user } = useAuth()
  const isAdmin = user?.shopNo === 0
  const queryClient = useQueryClient()
  const [selectedShopNo, setSelectedShopNo] = useState<string>(isAdmin ? '1' : String(user?.shopNo ?? '1'))
  // ポーリング対象のジョブ名セット
  const [pollingJobs, setPollingJobs] = useState<Set<string>>(new Set())

  const shopsQuery = useShops(isAdmin)

  const jobsQuery = useQuery({
    queryKey: ['batch-jobs'],
    queryFn: () => api.get<JobDefinition[]>('/batch/jobs'),
  })

  // 実行中ジョブのステータスをポーリング（5秒間隔）
  const statusQuery = useQuery({
    queryKey: ['batch-status', ...Array.from(pollingJobs)],
    queryFn: async () => {
      const results: Record<string, JobStatus> = {}
      for (const jobName of pollingJobs) {
        results[jobName] = await api.get<JobStatus>(`/batch/status/${jobName}`)
      }
      return results
    },
    enabled: pollingJobs.size > 0,
    refetchInterval: pollingJobs.size > 0 ? 5000 : false,
  })

  // ポーリング結果を監視し、完了/失敗したらポーリング対象から外す
  useEffect(() => {
    if (!statusQuery.data) return
    const finishedJobs: string[] = []
    for (const [jobName, status] of Object.entries(statusQuery.data)) {
      if (status.status === 'COMPLETED') {
        toast.success(`${jobName} が完了しました`)
        finishedJobs.push(jobName)
      } else if (status.status === 'FAILED') {
        toast.error(`${jobName} が失敗しました${status.exitMessage ? ': ' + status.exitMessage : ''}`)
        finishedJobs.push(jobName)
      }
    }
    if (finishedJobs.length > 0) {
      setPollingJobs((prev) => {
        const next = new Set(prev)
        finishedJobs.forEach((j) => next.delete(j))
        return next
      })
    }
  }, [statusQuery.data])

  const executeMutation = useMutation({
    mutationFn: ({ jobName, shopNo }: { jobName: string; shopNo?: number }) => {
      const params = shopNo != null ? `?shopNo=${shopNo}` : ''
      return api.post<{ message: string }>(`/batch/execute/${jobName}${params}`)
    },
    onSuccess: (_, { jobName }) => {
      // ポーリング対象に追加
      setPollingJobs((prev) => new Set(prev).add(jobName))
      toast.info(`${jobName} を起動しました`)
    },
    onError: (error: Error, { jobName }) => {
      toast.error(`${jobName}: ${error.message}`)
    },
  })

  const handleExecute = (job: JobDefinition) => {
    const shopNo = job.requiresShopNo === 'true' ? Number(selectedShopNo) : undefined
    executeMutation.mutate({ jobName: job.jobName, shopNo })
  }

  // ジョブの表示用ステータスを判定
  const getJobDisplayStatus = (jobName: string): 'idle' | 'running' | 'completed' | 'failed' => {
    if (pollingJobs.has(jobName)) return 'running'
    const status = statusQuery.data?.[jobName]
    if (status?.status === 'COMPLETED') return 'completed'
    if (status?.status === 'FAILED') return 'failed'
    return 'idle'
  }

  if (jobsQuery.isLoading) return <LoadingSpinner />
  if (jobsQuery.isError) return <ErrorMessage onRetry={() => jobsQuery.refetch()} />

  const jobs = jobsQuery.data ?? []
  const categories = [...new Set(jobs.map((j) => j.category))]

  return (
    <div className="space-y-6">
      <PageHeader
        title="バッチ管理"
        description="SMILE連携・集計バッチの実行管理"
      />

      {/* ショップ選択 */}
      <Card>
        <CardContent className="pt-4">
          <div className="flex items-center gap-3">
            <span className="text-sm font-medium">実行対象ショップ:</span>
            <Select value={selectedShopNo} onValueChange={setSelectedShopNo}>
              <SelectTrigger className="w-48">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {(shopsQuery.data ?? []).map((shop) => (
                  <SelectItem key={shop.shopNo} value={String(shop.shopNo)}>
                    {shop.shopName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <span className="text-xs text-muted-foreground">※ マスタ取込系ジョブに適用されます</span>
          </div>
        </CardContent>
      </Card>

      {/* IntelliJ実行ガイド */}
      <Card className="border-dashed">
        <CardContent className="pt-4">
          <div className="flex items-start gap-3">
            <Terminal className="h-5 w-5 text-muted-foreground mt-0.5 shrink-0" />
            <div className="text-sm">
              <p className="font-medium">IntelliJ IDEAでの実行・デバッグ</p>
              <p className="mt-1 text-muted-foreground">
                Run Configuration → Main class: <code className="rounded bg-muted px-1.5 py-0.5 text-xs">jp.co.oda32.BatchApplication</code>
              </p>
              <p className="text-muted-foreground">
                Program arguments: <code className="rounded bg-muted px-1.5 py-0.5 text-xs">--spring.profiles.active=batch,dev --spring.batch.job.name=goodsFileImport shopNo=1</code>
              </p>
              <p className="mt-1 text-xs text-muted-foreground">
                ※ ジョブ名を変えて実行。デバッグモードではブレークポイントで停止可能。
              </p>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Job list by category */}
      {categories.map((cat) => (
        <Card key={cat}>
          <CardHeader className="pb-2">
            <CardTitle className="text-base">{cat}</CardTitle>
          </CardHeader>
          <CardContent>
            <div className="divide-y">
              {jobs.filter((j) => j.category === cat).map((job) => {
                const displayStatus = getJobDisplayStatus(job.jobName)
                const isRunning = displayStatus === 'running'
                return (
                  <div key={job.jobName} className="flex items-center justify-between py-3 first:pt-0 last:pb-0">
                    <div>
                      <div className="flex items-center gap-2">
                        <span className="font-medium text-sm">{job.description}</span>
                        {!job.available && <Badge variant="outline" className="text-xs">未実装</Badge>}
                        {job.requiresShopNo === 'true' && <Badge variant="secondary" className="text-xs">Shop:{selectedShopNo}</Badge>}
                        {displayStatus === 'completed' && (
                          <span className="flex items-center gap-1 text-xs text-emerald-600">
                            <CheckCircle2 className="h-3.5 w-3.5" />完了
                          </span>
                        )}
                        {displayStatus === 'failed' && (
                          <span className="flex items-center gap-1 text-xs text-destructive">
                            <XCircle className="h-3.5 w-3.5" />失敗
                          </span>
                        )}
                      </div>
                      <span className="text-xs text-muted-foreground font-mono">{job.jobName}</span>
                    </div>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => handleExecute(job)}
                      disabled={isRunning || !job.available}
                    >
                      {isRunning ? (
                        <><Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />実行中...</>
                      ) : (
                        <><Play className="mr-1.5 h-3.5 w-3.5" />実行</>
                      )}
                    </Button>
                  </div>
                )
              })}
            </div>
          </CardContent>
        </Card>
      ))}
    </div>
  )
}
