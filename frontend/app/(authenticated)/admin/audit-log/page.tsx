import { Suspense } from 'react'
import { AuditLogPage } from '@/components/pages/admin/audit-log'

export default function AuditLog() {
  return (
    <Suspense fallback={null}>
      <AuditLogPage />
    </Suspense>
  )
}
