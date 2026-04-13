import { use } from 'react'
import { ComparisonDetailPage } from '@/components/pages/estimate-comparison/detail'

export default function Page({ params }: { params: Promise<{ comparisonNo: string }> }) {
  const { comparisonNo } = use(params)
  return <ComparisonDetailPage comparisonNo={Number(comparisonNo)} />
}
