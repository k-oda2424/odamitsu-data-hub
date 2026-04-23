import { Suspense } from 'react'
import { SupplierBalancesPage } from '@/components/pages/finance/supplier-balances'

export default function SupplierBalances() {
  return (
    <Suspense fallback={null}>
      <SupplierBalancesPage />
    </Suspense>
  )
}
