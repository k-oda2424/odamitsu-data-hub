import { Suspense } from 'react'
import PaymentMfRulesPage from '@/components/pages/finance/payment-mf-rules'

export default function Page() {
  return (
    <Suspense fallback={null}>
      <PaymentMfRulesPage />
    </Suspense>
  )
}
