import { type Column } from '@/components/features/common/DataTable'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import type { SalesGoodsDetailResponse, Supplier } from '@/types/goods'

export const salesGoodsColumns: Column<SalesGoodsDetailResponse>[] = [
  { key: 'goodsNo', header: '商品No', sortable: true },
  { key: 'goodsCode', header: '商品コード', sortable: true },
  { key: 'goodsName', header: '商品名', sortable: true },
  { key: 'supplierName', header: '仕入先' },
  {
    key: 'purchasePrice',
    header: '仕入単価',
    render: (item) => item.purchasePrice?.toLocaleString() ?? '',
  },
  {
    key: 'goodsPrice',
    header: '売単価',
    render: (item) => item.goodsPrice?.toLocaleString() ?? '',
  },
]

interface SalesGoodsSearchFieldsProps {
  goodsName: string
  onGoodsNameChange: (value: string) => void
  goodsCode: string
  onGoodsCodeChange: (value: string) => void
  keyword: string
  onKeywordChange: (value: string) => void
  supplierNo: string
  onSupplierNoChange: (value: string) => void
  suppliers: Supplier[]
}

export function SalesGoodsSearchFields({
  goodsName,
  onGoodsNameChange,
  goodsCode,
  onGoodsCodeChange,
  keyword,
  onKeywordChange,
  supplierNo,
  onSupplierNoChange,
  suppliers,
}: SalesGoodsSearchFieldsProps) {
  return (
    <>
      <div className="space-y-2">
        <Label>商品名</Label>
        <Input
          placeholder="商品名を入力"
          value={goodsName}
          onChange={(e) => onGoodsNameChange(e.target.value)}
        />
      </div>
      <div className="space-y-2">
        <Label>商品コード</Label>
        <Input
          placeholder="商品コードを入力"
          value={goodsCode}
          onChange={(e) => onGoodsCodeChange(e.target.value)}
        />
      </div>
      <div className="space-y-2">
        <Label>キーワード</Label>
        <Input
          placeholder="キーワードを入力"
          value={keyword}
          onChange={(e) => onKeywordChange(e.target.value)}
        />
      </div>
      <div className="space-y-2">
        <Label>仕入先</Label>
        <SearchableSelect
          value={supplierNo}
          onValueChange={onSupplierNoChange}
          options={suppliers.map((supplier) => ({
            value: String(supplier.supplierNo),
            label: supplier.supplierName,
          }))}
          searchPlaceholder="仕入先を検索..."
        />
      </div>
    </>
  )
}
