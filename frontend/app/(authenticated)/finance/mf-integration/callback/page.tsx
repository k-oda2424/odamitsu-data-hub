import { Suspense } from 'react'
import { MfIntegrationCallbackPage } from '@/components/pages/finance/mf-integration-callback'

// Next.js 16 では useSearchParams を使うコンポーネントは Suspense でラップ必須 (F-2)。
// 無いとビルドエラーまたはページ全体が CSR にフォールバックする。
export default function Page() {
  return (
    <Suspense fallback={null}>
      <MfIntegrationCallbackPage />
    </Suspense>
  )
}
