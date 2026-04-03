'use client'

import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover'
import { Search } from 'lucide-react'
import type { PurchasePriceResponse } from '@/types/purchase-price'

interface GoodsSearchPopoverProps {
  shopNo: number
  supplierNo: number | null
  initialKeyword: string
  onSelect: (goods: { goodsCode: string; goodsNo: number; goodsName: string }) => void
  onSkip: () => void
}

export function GoodsSearchPopover({
  shopNo,
  supplierNo,
  initialKeyword,
  onSelect,
  onSkip,
}: GoodsSearchPopoverProps) {
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState(initialKeyword)
  const [searchTerm, setSearchTerm] = useState('')

  const searchQuery = useQuery({
    queryKey: ['goods-search', shopNo, supplierNo, searchTerm],
    queryFn: () => {
      const params = new URLSearchParams()
      params.append('shopNo', String(shopNo))
      if (searchTerm) {
        // 数字のみの場合は商品コードで検索、それ以外は商品名で検索
        if (/^\d+$/.test(searchTerm.trim())) {
          params.append('goodsCode', searchTerm.trim())
          // 商品コード検索時は仕入先フィルタを外す（別仕入先の商品も検索可能に）
        } else {
          params.append('goodsName', searchTerm)
          if (supplierNo) params.append('supplierNo', String(supplierNo))
        }
      } else {
        if (supplierNo) params.append('supplierNo', String(supplierNo))
      }
      return api.get<PurchasePriceResponse[]>(`/purchase-prices?${params.toString()}`)
    },
    enabled: !!searchTerm && open,
  })

  const handleSearch = () => {
    setSearchTerm(keyword)
  }

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault()
      handleSearch()
    }
  }

  const handleSelect = (item: PurchasePriceResponse) => {
    onSelect({
      goodsCode: item.goodsCode ?? '',
      goodsNo: item.goodsNo,
      goodsName: item.goodsName ?? '',
    })
    setOpen(false)
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button variant="outline" size="sm">
          <Search className="mr-1 h-3 w-3" />
          突合
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-[400px] p-3" align="start">
        <div className="space-y-2">
          <div className="flex gap-1">
            <Input
              placeholder="商品名 or 商品コードで検索..."
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={handleKeyDown}
              className="h-8 text-sm"
              autoFocus
            />
            <Button size="sm" className="h-8" onClick={handleSearch}>
              検索
            </Button>
          </div>

          <div className="max-h-[200px] overflow-y-auto">
            {searchQuery.isLoading && (
              <div className="py-3 text-center text-xs text-muted-foreground">検索中...</div>
            )}
            {searchQuery.data && searchQuery.data.length === 0 && (
              <div className="py-3 text-center text-xs text-muted-foreground">該当なし</div>
            )}
            {searchQuery.data?.map((item) => (
              <button
                key={item.purchasePriceNo}
                className="w-full text-left px-2 py-1.5 text-sm rounded hover:bg-accent flex justify-between items-center"
                onClick={() => handleSelect(item)}
              >
                <div>
                  <span className="font-mono text-xs text-muted-foreground mr-2">
                    {item.goodsCode}
                  </span>
                  <span>{item.goodsName}</span>
                </div>
                <span className="text-xs text-muted-foreground">
                  ¥{item.goodsPrice?.toLocaleString()}
                </span>
              </button>
            ))}
          </div>

          <button
            className="w-full text-center py-1.5 text-sm text-muted-foreground hover:text-foreground border-t"
            onClick={() => {
              onSkip()
              setOpen(false)
            }}
          >
            該当なし（スキップ）
          </button>
        </div>
      </PopoverContent>
    </Popover>
  )
}
