import { Suspense } from 'react'
import { SalesGoodsMasterListPage } from '@/components/pages/sales-goods/master-list'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'

export default function SalesGoodsMaster() {
  return (
    <Suspense fallback={<LoadingSpinner />}>
      <SalesGoodsMasterListPage />
    </Suspense>
  )
}
