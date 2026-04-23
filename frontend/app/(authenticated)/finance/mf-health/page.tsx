import { Suspense } from 'react'
import { MfHealthPage } from '@/components/pages/finance/mf-health'

export default function MfHealth() {
  return (
    <Suspense fallback={null}>
      <MfHealthPage />
    </Suspense>
  )
}
