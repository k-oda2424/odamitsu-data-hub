import { Suspense } from 'react'
import { AccountsPayableLedgerPage } from '@/components/pages/finance/accounts-payable-ledger'

export default function AccountsPayableLedger() {
  return (
    <Suspense fallback={null}>
      <AccountsPayableLedgerPage />
    </Suspense>
  )
}
