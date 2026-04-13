'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { useShops, usePartners, useDestinations } from '@/hooks/use-master-data'

import { useAuth } from '@/lib/auth'
import { DataTable, type Column } from '@/components/features/common/DataTable'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { SearchForm } from '@/components/features/common/SearchForm'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { useRouter } from 'next/navigation'

import type { PartnerGoodsResponse } from '@/types/partner-goods'
import { emptyPage, type Paginated } from '@/types/paginated'

const PAGE_SIZE = 50

const columns: Column<PartnerGoodsResponse>[] = [
  { key: 'goodsNo', header: '商品番号', sortable: true },
  { key: 'shopName', header: 'ショップ' },
  { key: 'companyName', header: '得意先', sortable: true },
  { key: 'goodsCode', header: '商品コード', sortable: true },
  { key: 'goodsName', header: '商品名', sortable: true },
  {
    key: 'goodsPrice',
    header: '売価',
    render: (item) => (
      <span className="text-right block">{item.goodsPrice?.toLocaleString() ?? ''}</span>
    ),
    sortable: true,
  },
  {
    key: 'reflectedEstimateNo',
    header: '対応見積',
    render: (item) =>
      item.reflectedEstimateNo ? (
        <a
          href={`/estimates/${item.reflectedEstimateNo}`}
          className="text-blue-600 hover:underline"
          onClick={(e) => e.stopPropagation()}
        >
          見積明細
        </a>
      ) : null,
  },
  {
    key: 'lastSalesDate',
    header: '最終売上日',
    sortable: true,
    render: (item) => item.lastSalesDate ?? '',
  },
]

export function PartnerGoodsListPage() {
  const router = useRouter()
  const { user } = useAuth()

  const isAdmin = user?.shopNo === 0
  const [shopNo, setShopNo] = useState<string>(isAdmin ? '' : String(user?.shopNo ?? ''))
  const [partnerNo, setPartnerNo] = useState<string>('')
  const [destinationNo, setDestinationNo] = useState<string>('')
  const [goodsName, setGoodsName] = useState('')
  const [goodsCode, setGoodsCode] = useState('')
  const [keyword, setKeyword] = useState('')
  const [searchParams, setSearchParams] = useState<Record<string, string> | null>(null)
  const [page, setPage] = useState(0)

  const shopsQuery = useShops(isAdmin)
  const partnersQuery = usePartners(shopNo)
  const destinationsQuery = useDestinations(partnerNo)

  const partnerGoodsQuery = useQuery({
    queryKey: ['partner-goods', searchParams, page],
    queryFn: () => {
      const params = new URLSearchParams()
      if (searchParams?.shopNo) params.append('shopNo', searchParams.shopNo)
      if (searchParams?.partnerCode) params.append('partnerCode', searchParams.partnerCode)
      if (searchParams?.destinationNo) params.append('destinationNo', searchParams.destinationNo)
      if (searchParams?.goodsName) params.append('goodsName', searchParams.goodsName)
      if (searchParams?.goodsCode) params.append('goodsCode', searchParams.goodsCode)
      if (searchParams?.keyword) params.append('keyword', searchParams.keyword)
      params.append('page', String(page))
      params.append('size', String(PAGE_SIZE))
      return api.get<Paginated<PartnerGoodsResponse>>(`/partner-goods?${params.toString()}`)
    },
    enabled: searchParams !== null,
  })

  const selectedPartner = (partnersQuery.data ?? []).find(
    (p) => String(p.partnerNo) === partnerNo,
  )

  const handleShopChange = (value: string) => {
    setShopNo(value)
    setPartnerNo('')
    setDestinationNo('')
  }

  const handlePartnerChange = (value: string) => {
    setPartnerNo(value)
    setDestinationNo('')
  }

  const handleSearch = () => {
    setPage(0)
    setSearchParams({
      shopNo,
      partnerCode: selectedPartner?.partnerCode ?? '',
      destinationNo,
      goodsName,
      goodsCode,
      keyword,
    })
  }

  const handleReset = () => {
    setShopNo(isAdmin ? '' : String(user?.shopNo ?? ''))
    setPartnerNo('')
    setDestinationNo('')
    setGoodsName('')
    setGoodsCode('')
    setKeyword('')
    setPage(0)
    setSearchParams(null)
  }

  const hasSearched = searchParams !== null

  return (
    <div className="space-y-6">
      <PageHeader title="得意先商品マスタ" />

      <SearchForm onSearch={handleSearch} onReset={handleReset}>
        {isAdmin && (
          <div className="space-y-2">
            <Label>ショップ</Label>
            <Select value={shopNo} onValueChange={handleShopChange}>
              <SelectTrigger>
                <SelectValue placeholder="選択してください" />
              </SelectTrigger>
              <SelectContent>
                {(shopsQuery.data ?? []).map((shop) => (
                  <SelectItem key={shop.shopNo} value={String(shop.shopNo)}>
                    {shop.shopName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        <div className="space-y-2">
          <Label>得意先</Label>
          <SearchableSelect
            value={partnerNo}
            onValueChange={handlePartnerChange}
            options={(partnersQuery.data ?? []).map((p) => ({
              value: String(p.partnerNo),
              label: `${p.partnerCode} ${p.partnerName}`,
            }))}
            searchPlaceholder="得意先を検索..."
          />
        </div>
        {partnerNo && (
          <div className="space-y-2">
            <Label>納品先</Label>
            <Select value={destinationNo} onValueChange={setDestinationNo}>
              <SelectTrigger>
                <SelectValue placeholder="選択してください" />
              </SelectTrigger>
              <SelectContent>
                {(destinationsQuery.data ?? []).map((dest) => (
                  <SelectItem key={dest.destinationNo} value={String(dest.destinationNo)}>
                    {dest.destinationName}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        )}
        <div className="space-y-2">
          <Label>商品名</Label>
          <Input
            placeholder="商品名を入力"
            value={goodsName}
            onChange={(e) => setGoodsName(e.target.value)}
          />
        </div>
        <div className="space-y-2">
          <Label>商品コード</Label>
          <Input
            placeholder="商品コードを入力"
            value={goodsCode}
            onChange={(e) => setGoodsCode(e.target.value)}
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
      </SearchForm>

      {!hasSearched ? (
        <div className="rounded-lg border p-8 text-center text-muted-foreground">
          検索条件を入力して「検索」ボタンを押してください
        </div>
      ) : partnerGoodsQuery.isLoading ? (
        <LoadingSpinner />
      ) : partnerGoodsQuery.isError ? (
        <ErrorMessage onRetry={() => partnerGoodsQuery.refetch()} />
      ) : (() => {
        const p = partnerGoodsQuery.data ?? emptyPage<PartnerGoodsResponse>(PAGE_SIZE)
        return (
          <DataTable
            data={p.content}
            columns={columns}
            onRowClick={(item) =>
              router.push(`/partner-goods/${item.partnerNo}/${item.destinationNo}/${item.goodsNo}`)
            }
            serverPagination={{
              page: p.number,
              pageSize: p.size,
              totalElements: p.totalElements,
              totalPages: p.totalPages,
              onPageChange: setPage,
            }}
          />
        )
      })()}
    </div>
  )
}
