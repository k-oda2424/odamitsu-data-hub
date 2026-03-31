import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider, useAuth } from '@/lib/auth'
import { Toaster } from '@/components/ui/sonner'
import { TooltipProvider } from '@/components/ui/tooltip'
import { LoginPage } from '@/pages/login'
import { DashboardPage } from '@/pages/dashboard'
import { StockListPage } from '@/pages/stock/index'
import { StockLogPage } from '@/pages/stock/log'
import { GoodsListPage } from '@/pages/goods/index'
import { GoodsCreatePage } from '@/pages/goods/create'
import { OrderListPage } from '@/pages/order/index'
import { PurchaseListPage } from '@/pages/purchase/index'
import { EstimateListPage } from '@/pages/estimate/index'
import { AccountsPayablePage } from '@/pages/finance/accounts-payable'
import { InvoiceListPage } from '@/pages/finance/invoices'
import { BCartShippingPage } from '@/pages/bcart/shipping'
import { MasterPage } from '@/pages/master/index'
import { AppLayout } from '@/components/layout/AppLayout'
import type { ReactNode } from 'react'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000,
      retry: 1,
    },
  },
})

function AuthGuard({ children }: { children: ReactNode }) {
  const { isAuthenticated, isLoading } = useAuth()

  if (isLoading) {
    return (
      <div className="flex h-screen items-center justify-center">
        <div className="text-muted-foreground">Loading...</div>
      </div>
    )
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  return <>{children}</>
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route
        path="/*"
        element={
          <AuthGuard>
            <AppLayout>
              <Routes>
                <Route path="/" element={<Navigate to="/dashboard" replace />} />
                <Route path="/dashboard" element={<DashboardPage />} />
                <Route path="/stock" element={<StockListPage />} />
                <Route path="/stock/log" element={<StockLogPage />} />
                <Route path="/goods" element={<GoodsListPage />} />
                <Route path="/goods/create" element={<GoodsCreatePage />} />
                <Route path="/orders" element={<OrderListPage />} />
                <Route path="/purchases" element={<PurchaseListPage />} />
                <Route path="/estimates" element={<EstimateListPage />} />
                <Route path="/finance/accounts-payable" element={<AccountsPayablePage />} />
                <Route path="/finance/invoices" element={<InvoiceListPage />} />
                <Route path="/bcart/shipping" element={<BCartShippingPage />} />
                <Route path="/masters" element={<MasterPage />} />
              </Routes>
            </AppLayout>
          </AuthGuard>
        }
      />
    </Routes>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <TooltipProvider>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
          <Toaster />
        </TooltipProvider>
      </AuthProvider>
    </QueryClientProvider>
  )
}
