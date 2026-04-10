'use client'

import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { api } from '@/lib/api-client'
import { PageHeader } from '@/components/features/common/PageHeader'
import { LoadingSpinner } from '@/components/features/common/LoadingSpinner'
import { ErrorMessage } from '@/components/features/common/ErrorMessage'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { toast } from 'sonner'
import { Save } from 'lucide-react'

interface ShopLinkedFile {
  shopNo: number
  shopName: string
  smileOrderInputFileName: string | null
  smilePurchaseFileName: string | null
  smileOrderOutputFileName: string | null
  smilePartnerOutputFileName: string | null
  smileDestinationOutputFileName: string | null
  smileGoodsImportFileName: string | null
  bCartLogisticsImportFileName: string | null
  invoiceFilePath: string | null
}

const FIELD_LABELS: { key: keyof Omit<ShopLinkedFile, 'shopNo' | 'shopName'>; label: string }[] = [
  { key: 'smileOrderInputFileName', label: 'SMILE注文入力ファイル' },
  { key: 'smilePurchaseFileName', label: 'SMILE仕入ファイル' },
  { key: 'smileOrderOutputFileName', label: 'SMILE注文出力ファイル' },
  { key: 'smilePartnerOutputFileName', label: 'SMILE得意先出力ファイル' },
  { key: 'smileDestinationOutputFileName', label: 'SMILE納品先出力ファイル' },
  { key: 'smileGoodsImportFileName', label: 'SMILE商品マスタCSV' },
  { key: 'bCartLogisticsImportFileName', label: 'B-CART出荷取込ファイル' },
  { key: 'invoiceFilePath', label: '請求ファイルパス' },
]

function ShopLinkedFileCard({ file }: { file: ShopLinkedFile }) {
  const queryClient = useQueryClient()
  const [formData, setFormData] = useState<Omit<ShopLinkedFile, 'shopNo' | 'shopName'>>(() => {
    const { shopNo: _sn, shopName: _name, ...rest } = file
    return rest
  })

  const mutation = useMutation({
    mutationFn: (data: Omit<ShopLinkedFile, 'shopNo' | 'shopName'>) =>
      api.put<ShopLinkedFile>(`/masters/shop-linked-files/${file.shopNo}`, data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['masters', 'shop-linked-files'] })
      toast.success(`${file.shopName} の連携ファイル設定を保存しました`)
    },
    onError: () => {
      toast.error('保存に失敗しました')
    },
  })

  const handleChange = (key: string, value: string) => {
    setFormData((prev) => ({ ...prev, [key]: value || null }))
  }

  const handleSave = () => {
    mutation.mutate(formData)
  }

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 pb-4">
        <CardTitle className="text-lg">
          {file.shopName}
          <span className="ml-2 text-sm text-muted-foreground font-normal">
            (Shop No: {file.shopNo})
          </span>
        </CardTitle>
        <Button size="sm" onClick={handleSave} disabled={mutation.isPending}>
          <Save className="h-4 w-4 mr-1" />
          保存
        </Button>
      </CardHeader>
      <CardContent>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {FIELD_LABELS.map(({ key, label }) => (
            <div key={key} className="space-y-1.5">
              <Label htmlFor={`${file.shopNo}-${key}`} className="text-xs text-muted-foreground">
                {label}
              </Label>
              <Input
                id={`${file.shopNo}-${key}`}
                value={formData[key] ?? ''}
                onChange={(e) => handleChange(key, e.target.value)}
                placeholder="未設定"
                className="font-mono text-sm"
              />
            </div>
          ))}
        </div>
      </CardContent>
    </Card>
  )
}

export function LinkedFilesPage() {
  const query = useQuery({
    queryKey: ['masters', 'shop-linked-files'],
    queryFn: () => api.get<ShopLinkedFile[]>('/masters/shop-linked-files'),
  })

  return (
    <div className="space-y-6">
      <PageHeader title="連携ファイル設定" />
      {query.isLoading ? (
        <LoadingSpinner />
      ) : query.isError ? (
        <ErrorMessage onRetry={() => query.refetch()} />
      ) : (
        <div className="space-y-6">
          {(query.data ?? []).map((file) => (
            <ShopLinkedFileCard key={file.shopNo} file={file} />
          ))}
          {(query.data ?? []).length === 0 && (
            <p className="text-muted-foreground text-center py-8">連携ファイル設定がありません</p>
          )}
        </div>
      )}
    </div>
  )
}
