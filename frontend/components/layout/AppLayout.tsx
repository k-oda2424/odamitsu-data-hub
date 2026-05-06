'use client'

import type { ReactNode } from 'react'
import { SidebarProvider } from '@/components/ui/sidebar'
import { AppSidebar } from './Sidebar'
import { Header } from './Header'
import { MfReAuthBanner } from '@/components/common/MfReAuthBanner'
import { MfScopeBanner } from '@/components/common/MfScopeBanner'

export function AppLayout({ children }: { children: ReactNode }) {
  return (
    <SidebarProvider>
      <div className="flex h-screen w-full">
        <div className="print:hidden">
          <AppSidebar />
        </div>
        <div className="flex flex-1 flex-col overflow-hidden">
          <Header />
          {/* P1-04 案 α: MF refresh_token 540 日寿命予兆 banner。
              header の直下、main scroll 領域の外に固定して全画面で常時表示する。
              非表示条件 (未接続 / N>60 / 認証前) は MfReAuthBanner 内部で判定。
              T6 (2026-05-04): MfScopeBanner も並列配置。両 banner は queryKey
              'mf-oauth-status' を共有し、TanStack Query cache で 1 fetch にまとめる。
              非表示条件 (未接続 / scopeOk=true / 認証前) は内部で判定。 */}
          <div className="px-4 pt-2 print:hidden empty:hidden">
            <MfReAuthBanner />
            <MfScopeBanner />
          </div>
          <main className="flex-1 overflow-auto p-6 print:p-0 print:overflow-visible">{children}</main>
        </div>
      </div>
    </SidebarProvider>
  )
}
