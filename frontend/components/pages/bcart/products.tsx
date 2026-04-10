'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useRouter } from 'next/navigation'
import { Package, Eye, EyeOff, Search } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import type { BCartProduct, BCartCategory } from '@/types/bcart'
import { api } from '@/lib/api-client'

export default function BCartProductsPage() {
  const router = useRouter()
  const [searchName, setSearchName] = useState('')
  const [searchCategoryId, setSearchCategoryId] = useState<string>('')
  const [searchFlag, setSearchFlag] = useState<string>('')
  const [params, setParams] = useState<Record<string, string> | null>(null)

  const { data: categories } = useQuery({
    queryKey: ['bcart', 'categories'],
    queryFn: () => api.get<BCartCategory[]>('/bcart/categories'),
  })

  const { data: products, isLoading } = useQuery({
    queryKey: ['bcart', 'products', params],
    queryFn: () => {
      const query = new URLSearchParams()
      if (params?.name) query.set('name', params.name)
      if (params?.categoryId) query.set('categoryId', params.categoryId)
      if (params?.flag) query.set('flag', params.flag)
      const qs = query.toString()
      return api.get<BCartProduct[]>(`/bcart/products${qs ? '?' + qs : ''}`)
    },
    enabled: params !== null,
  })

  const handleSearch = () => {
    const p: Record<string, string> = {}
    if (searchName) p.name = searchName
    if (searchCategoryId) p.categoryId = searchCategoryId
    if (searchFlag) p.flag = searchFlag
    setParams(p)
  }

  // カテゴリをフラットに（親+子）
  const allCategories = categories
    ? categories.flatMap((c) => [c, ...(c.children ?? [])])
    : []

  return (
    <div className="p-4 space-y-4">
      <h1 className="text-2xl font-bold">B-CART商品マスタ</h1>

      {/* 検索フォーム */}
      <div className="flex items-end gap-4 p-4 border rounded-lg">
        <div className="flex-1">
          <Label>商品名</Label>
          <Input
            value={searchName}
            onChange={(e) => setSearchName(e.target.value)}
            placeholder="商品名で検索"
            onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
          />
        </div>
        <div className="w-48">
          <Label>カテゴリ</Label>
          <select
            className="w-full h-10 rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={searchCategoryId}
            onChange={(e) => setSearchCategoryId(e.target.value)}
          >
            <option value="">すべて</option>
            {allCategories.map((c) => (
              <option key={c.id} value={c.id}>
                {c.parentCategoryId ? '　' : ''}{c.name}
              </option>
            ))}
          </select>
        </div>
        <div className="w-32">
          <Label>状態</Label>
          <select
            className="w-full h-10 rounded-md border border-input bg-background px-3 py-2 text-sm"
            value={searchFlag}
            onChange={(e) => setSearchFlag(e.target.value)}
          >
            <option value="">すべて</option>
            <option value="表示">表示</option>
            <option value="非表示">非表示</option>
          </select>
        </div>
        <Button onClick={handleSearch}>
          <Search className="h-4 w-4 mr-1" />
          検索
        </Button>
      </div>

      {/* 結果テーブル */}
      {isLoading ? (
        <p className="text-sm text-muted-foreground">読み込み中...</p>
      ) : products ? (
        <div className="border rounded-lg overflow-hidden">
          <table className="w-full text-sm">
            <thead className="bg-muted">
              <tr>
                <th className="px-3 py-2 text-left">ID</th>
                <th className="px-3 py-2 text-left">商品名</th>
                <th className="px-3 py-2 text-left">カテゴリ</th>
                <th className="px-3 py-2 text-center">セット数</th>
                <th className="px-3 py-2 text-center">状態</th>
                <th className="px-3 py-2 text-left">更新日</th>
              </tr>
            </thead>
            <tbody>
              {products.map((p) => (
                <tr
                  key={p.id}
                  className="border-t hover:bg-muted/50 cursor-pointer"
                  onClick={() => router.push(`/bcart/products/${p.id}`)}
                >
                  <td className="px-3 py-2 text-muted-foreground">{p.id}</td>
                  <td className="px-3 py-2 font-medium">{p.name}</td>
                  <td className="px-3 py-2">{p.categoryName ?? '-'}</td>
                  <td className="px-3 py-2 text-center">{p.setCount}</td>
                  <td className="px-3 py-2 text-center">
                    {p.flag === '表示' ? (
                      <Badge variant="secondary"><Eye className="h-3 w-3 mr-1" />表示</Badge>
                    ) : (
                      <Badge variant="outline"><EyeOff className="h-3 w-3 mr-1" />非表示</Badge>
                    )}
                  </td>
                  <td className="px-3 py-2 text-muted-foreground">
                    {p.updatedAt ? new Date(p.updatedAt).toLocaleDateString('ja-JP') : '-'}
                  </td>
                </tr>
              ))}
              {products.length === 0 && (
                <tr><td colSpan={6} className="px-3 py-8 text-center text-muted-foreground">該当する商品がありません</td></tr>
              )}
            </tbody>
          </table>
          <div className="px-3 py-2 text-sm text-muted-foreground border-t">
            {products.length}件
          </div>
        </div>
      ) : (
        <div className="flex items-center justify-center h-40 text-muted-foreground">
          <Package className="h-6 w-6 mr-2" />
          検索条件を入力して「検索」を押してください
        </div>
      )}
    </div>
  )
}
