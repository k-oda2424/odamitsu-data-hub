import { use } from 'react'
import { ComparisonFormPage } from '@/components/pages/estimate-comparison/form'

export default function Page({ params }: { params: Promise<{ comparisonNo: string }> }) {
  const { comparisonNo } = use(params)
  return <ComparisonFormPage comparisonNo={Number(comparisonNo)} />
}
