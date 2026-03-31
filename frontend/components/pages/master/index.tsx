'use client'

import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'

interface Maker {
  makerNo: number
  makerName: string
  [key: string]: unknown
}

interface Warehouse {
  warehouseNo: number
  warehouseName: string
  companyNo: number
  [key: string]: unknown
}

const makerColumns: Column<Maker>[] = [
  { key: 'makerNo', header: 'メーカーNo', sortable: true },
  { key: 'makerName', header: 'メーカー名', sortable: true },
]

const warehouseColumns: Column<Warehouse>[] = [
  { key: 'warehouseNo', header: '倉庫No', sortable: true },
  { key: 'warehouseName', header: '倉庫名', sortable: true },
  { key: 'companyNo', header: '会社No' },
]

export function MasterPage() {
  const makersQuery = useQuery({
    queryKey: ['masters', 'makers'],
    queryFn: () => api.get<Maker[]>('/masters/makers'),
  })

  const warehousesQuery = useQuery({
    queryKey: ['masters', 'warehouses'],
    queryFn: () => api.get<Warehouse[]>('/masters/warehouses'),
  })

  return (
    <div className="space-y-6">
      <PageHeader title="マスタ管理" />
      <Tabs defaultValue="makers">
        <TabsList>
          <TabsTrigger value="makers">メーカー</TabsTrigger>
          <TabsTrigger value="warehouses">倉庫</TabsTrigger>
        </TabsList>
        <TabsContent value="makers">
          <Card>
            <CardHeader>
              <CardTitle>メーカー一覧</CardTitle>
            </CardHeader>
            <CardContent>
              {makersQuery.isLoading ? (
                <LoadingSpinner />
              ) : makersQuery.isError ? (
                <ErrorMessage onRetry={() => makersQuery.refetch()} />
              ) : (
                <DataTable data={makersQuery.data ?? []} columns={makerColumns} />
              )}
            </CardContent>
          </Card>
        </TabsContent>
        <TabsContent value="warehouses">
          <Card>
            <CardHeader>
              <CardTitle>倉庫一覧</CardTitle>
            </CardHeader>
            <CardContent>
              {warehousesQuery.isLoading ? (
                <LoadingSpinner />
              ) : warehousesQuery.isError ? (
                <ErrorMessage onRetry={() => warehousesQuery.refetch()} />
              ) : (
                <DataTable data={warehousesQuery.data ?? []} columns={warehouseColumns} />
              )}
            </CardContent>
          </Card>
        </TabsContent>
      </Tabs>
    </div>
  )
}
