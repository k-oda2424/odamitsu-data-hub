import { Suspense } from 'react'
import { SalesGoodsWorkListPage } from '@/components/pages/sales-goods/work-list'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'

export default function SalesGoodsWork() {
  return (
    <Suspense fallback={<LoadingSpinner />}>
      <SalesGoodsWorkListPage />
    </Suspense>
  )
}
