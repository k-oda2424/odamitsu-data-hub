import { use } from 'react'
import { EstimateFormPage } from '@/components/pages/estimate/form'

export default function Page({ params }: { params: Promise<{ estimateNo: string }> }) {
  const { estimateNo } = use(params)
  return <EstimateFormPage estimateNo={Number(estimateNo)} />
}
