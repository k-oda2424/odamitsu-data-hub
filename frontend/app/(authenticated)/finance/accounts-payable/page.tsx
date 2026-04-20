import { Suspense } from 'react'
import { AccountsPayablePage } from '@/components/pages/finance/accounts-payable'

export default function AccountsPayable() {
  return (
    <Suspense fallback={null}>
      <AccountsPayablePage />
    </Suspense>
  )
}
