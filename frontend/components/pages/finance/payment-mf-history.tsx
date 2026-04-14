'use client'

import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { PageHeader } from '@/components/features/common/PageHeader'
import { Download } from 'lucide-react'
import { toast } from 'sonner'
import type { PaymentMfHistory } from '@/types/payment-mf'

export default function PaymentMfHistoryPage() {
  const { data: items = [], isLoading } = useQuery({
    queryKey: ['payment-mf-history'],
    queryFn: () => api.get<PaymentMfHistory[]>('/finance/payment-mf/history'),
  })

  const download = async (h: PaymentMfHistory) => {
    try {
      const { blob } = await api.download(`/finance/payment-mf/history/${h.id}/csv`)
      const a = document.createElement('a')
      a.href = URL.createObjectURL(blob)
      a.download = h.csvFilename
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(a.href)
      toast.success('CSVをダウンロードしました')
    } catch (e) {
      toast.error(`ダウンロード失敗: ${(e as Error).message}`)
    }
  }

  return (
    <div className="space-y-4">
      <PageHeader title="買掛仕入MF 変換履歴" />
      {isLoading ? <div>読込中...</div> : (
        <div className="overflow-auto rounded border">
          <table className="min-w-full text-sm">
            <thead className="bg-muted">
              <tr>
                <th className="p-2 text-left">送金日</th>
                <th className="p-2 text-left">ソース</th>
                <th className="p-2 text-left">CSV</th>
                <th className="p-2 text-right">行数</th>
                <th className="p-2 text-right">合計額</th>
                <th className="p-2 text-right">🟢</th>
                <th className="p-2 text-right">🟡</th>
                <th className="p-2 text-right">🔴</th>
                <th className="p-2 text-left">作成</th>
                <th className="p-2"></th>
              </tr>
            </thead>
            <tbody>
              {items.map((h) => (
                <tr key={h.id} className="border-t">
                  <td className="p-2">{h.transferDate}</td>
                  <td className="p-2">{h.sourceFilename}</td>
                  <td className="p-2">{h.csvFilename}</td>
                  <td className="p-2 text-right">{h.rowCount}</td>
                  <td className="p-2 text-right">{h.totalAmount.toLocaleString()}</td>
                  <td className="p-2 text-right text-green-700">{h.matchedCount}</td>
                  <td className="p-2 text-right text-amber-700">{h.diffCount}</td>
                  <td className="p-2 text-right text-red-600">{h.unmatchedCount}</td>
                  <td className="p-2">{h.addDateTime?.slice(0, 19).replace('T', ' ')}</td>
                  <td className="p-2 text-right">
                    <Button size="sm" variant="outline" onClick={() => download(h)}>
                      <Download className="mr-1 h-4 w-4" />
                      再DL
                    </Button>
                  </td>
                </tr>
              ))}
              {items.length === 0 && (
                <tr><td colSpan={10} className="p-4 text-center text-muted-foreground">履歴なし</td></tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
