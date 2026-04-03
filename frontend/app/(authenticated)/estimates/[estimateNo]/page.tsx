import { use } from 'react'
import { EstimateDetailPage } from '@/components/pages/estimate/detail'

export default function Page({ params }: { params: Promise<{ estimateNo: string }> }) {
  const { estimateNo } = use(params)
  return <EstimateDetailPage estimateNo={Number(estimateNo)} />
}
