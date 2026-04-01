import { use } from 'react'
import { QuoteImportDetailPage } from '@/components/pages/purchase-price/import-detail'

export default function QuoteImportDetail({ params }: { params: Promise<{ importId: string }> }) {
  const { importId } = use(params)
  return <QuoteImportDetailPage importId={Number(importId)} />
}
