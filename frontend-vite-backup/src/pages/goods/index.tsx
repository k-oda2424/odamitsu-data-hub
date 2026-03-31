import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchForm } from '@/components/features/common/SearchForm'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { useNavigate } from 'react-router-dom'
import { Plus } from 'lucide-react'

interface Goods {
  goodsNo: number
  goodsName: string
  janCode: string
  makerNo: number
  makerName: string
  keyword: string
  specification: string
  discontinuedFlg: string
  delFlg: string
  [key: string]: unknown
}

interface Maker {
  makerNo: number
  makerName: string
  [key: string]: unknown
}

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
  const navigate = useNavigate()

  const [goodsName, setGoodsName] = useState('')
  const [keyword, setKeyword] = useState('')
  const [janCode, setJanCode] = useState('')
  const [makerNo, setMakerNo] = useState<string>('')
  const [searchParams, setSearchParams] = useState<Record<string, string>>({})

  const makersQuery = useQuery({
    queryKey: ['makers'],
    queryFn: () => api.get<Maker[]>('/masters/makers'),
  })

  const goodsQuery = useQuery({
    queryKey: ['goods', searchParams],
    queryFn: () => {
      const params = new URLSearchParams()
      if (searchParams.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams.keyword) params.append('keyword', searchParams.keyword)
      if (searchParams.janCode) params.append('janCode', searchParams.janCode)
      if (searchParams.makerNo) params.append('makerNo', searchParams.makerNo)
      const qs = params.toString()
      return api.get<Goods[]>(`/goods${qs ? `?${qs}` : ''}`)
    },
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
    setSearchParams({})
  }

  if (goodsQuery.isLoading) return <LoadingSpinner />
  if (goodsQuery.isError) return <ErrorMessage onRetry={() => goodsQuery.refetch()} />

  return (
    <div className="space-y-6">
      <PageHeader
        title="商品マスタ"
        actions={
          <Button onClick={() => navigate('/goods/create')}>
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
          <Select value={makerNo} onValueChange={setMakerNo}>
            <SelectTrigger>
              <SelectValue placeholder="選択してください" />
            </SelectTrigger>
            <SelectContent>
              {(makersQuery.data ?? []).map((maker) => (
                <SelectItem key={maker.makerNo} value={String(maker.makerNo)}>
                  {maker.makerName}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </SearchForm>

      <DataTable
        data={goodsQuery.data ?? []}
        columns={columns}
        searchPlaceholder="テーブル内を検索..."
        onRowClick={(item) => navigate(`/goods/${item.goodsNo}`)}
      />
    </div>
  )
}
