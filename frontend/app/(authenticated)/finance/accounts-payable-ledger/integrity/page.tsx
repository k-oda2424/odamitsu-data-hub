import { Suspense } from 'react'
import { IntegrityReportPage } from '@/components/pages/finance/integrity-report'

export default function IntegrityReport() {
  return (
    <Suspense fallback={null}>
      <IntegrityReportPage />
    </Suspense>
  )
}
