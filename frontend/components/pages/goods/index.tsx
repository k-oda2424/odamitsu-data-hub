'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useMakers } from '@/hooks/use-master-data'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchForm } from '@/components/features/common/SearchForm'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { useRouter } from 'next/navigation'
import { Plus } from 'lucide-react'

import type { GoodsResponse as Goods, Maker } from '@/types/goods'

const columns: Column<Goods>[] = [
  { key: 'goodsNo', header: '商品No', sortable: true },
  { key: 'goodsName', header: '商品名', sortable: true },
  { key: 'janCode', header: 'JANコード' },
  { key: 'makerName', header: 'メーカー', sortable: true },
  { key: 'keyword', header: 'キーワード' },
  { key: 'specification', header: '規格' },
  {
    key: 'discontinuedFlg',
    header: '状態',
    render: (item) =>
      item.discontinuedFlg === '1' ? (
        <Badge variant="destructive">廃番</Badge>
      ) : (
        <Badge variant="secondary">有効</Badge>
      ),
  },
]

export function GoodsListPage() {
  const router = useRouter()

  const [goodsName, setGoodsName] = useState('')
  const [keyword, setKeyword] = useState('')
  const [janCode, setJanCode] = useState('')
  const [makerNo, setMakerNo] = useState<string>('')
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(null)

  const makersQuery = useMakers()

  const goodsQuery = useQuery({
    queryKey: ['goods', searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams?.keyword) params.append('keyword', searchParams.keyword)
      if (searchParams?.janCode) params.append('janCode', searchParams.janCode)
      if (searchParams?.makerNo) params.append('makerNo', searchParams.makerNo)
      const qs = params.toString()
      return api.get<Goods[]>(`/goods${qs ? `?${qs}` : ''}`)
    },
    enabled: searchParams !== null,
  })

  const handleSearch = () => {
    setSearchParams({
      goodsName,
      keyword,
      janCode,
      makerNo,
    })
  }

  const handleReset = () => {
    setGoodsName('')
    setKeyword('')
    setJanCode('')
    setMakerNo('')
    setSearchParams(null)
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader
        title="商品マスタ"
        actions={
          <Button onClick={() => router.push('/goods/create')}>
            <Plus className="mr-2 h-4 w-4" />
            商品登録
          </Button>
        }
      />

      <SearchForm onSearch={handleSearch} onReset={handleReset}>
        <div className="space-y-2">
          <Label>商品名</Label>
          <Input
            placeholder="商品名を入力"
            value={goodsName}
            onChange={(e) => setGoodsName(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>キーワード</Label>
          <Input
            placeholder="キーワードを入力"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>JANコード</Label>
          <Input
            placeholder="JANコードを入力"
            value={janCode}
            onChange={(e) => setJanCode(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>メーカー</Label>
          <SearchableSelect
            value={makerNo}
            onValueChange={setMakerNo}
            options={(makersQuery.data ?? []).map((maker) => ({
              value: String(maker.makerNo),
              label: maker.makerName,
            }))}
            searchPlaceholder="メーカーを検索..."
          />
        </div>
      </SearchForm>

      {!hasSearched ? (
        <div className="rounded-lg border p-8 text-center text-muted-foreground">
          検索条件を入力して「検索」ボタンを押してください
        </div>
      ) : goodsQuery.isLoading ? (
        <LoadingSpinner />
      ) : goodsQuery.isError ? (
        <ErrorMessage onRetry={() => goodsQuery.refetch()} />
      ) : (
        <DataTable
          data={goodsQuery.data ?? []}
          columns={columns}
          searchPlaceholder="テーブル内を検索..."
          onRowClick={(item) => router.push(`/goods/${item.goodsNo}`)}
        />
      )}
    </div>
  )
}
