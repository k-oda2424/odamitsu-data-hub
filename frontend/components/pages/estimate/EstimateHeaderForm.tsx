'use client'

import { useMemo } from 'react'
import type { UseQueryResult } from '@tanstack/react-query'
import { SearchableSelect } from '@/components/features/common/SearchableSelect'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Card, CardContent } from '@/components/ui/card'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'

interface ShopOption {
  shopNo: number
  shopName: string
}

interface PartnerOption {
  partnerNo: number
  partnerCode?: string | null
  partnerName: string
}

interface DestinationOption {
  destinationNo: number
  destinationCode?: string | null
  destinationName: string
}

interface Props {
  isAdmin: boolean
  shopNo: string
  onShopNoChange: (v: string) => void
  partnerNo: string
  onPartnerNoChange: (v: string) => void
  destinationNo: string
  onDestinationNoChange: (v: string) => void
  estimateDate: string
  onEstimateDateChange: (v: string) => void
  priceChangeDate: string
  onPriceChangeDateChange: (v: string) => void
  recipientName: string
  onRecipientNameChange: (v: string) => void
  requirement: string
  onRequirementChange: (v: string) => void
  note: string
  onNoteChange: (v: string) => void
  proposalMessage: string
  onProposalMessageChange: (v: string) => void
  shopsQuery: UseQueryResult<ShopOption[]>
  partnersQuery: UseQueryResult<PartnerOption[]>
  destinationsQuery: UseQueryResult<DestinationOption[]>
  destinationFallback?: {
    destinationNo: number
    destinationName: string | null
    destinationCode: string | null
  } | null
}

export function EstimateHeaderForm({
  isAdmin,
  shopNo,
  onShopNoChange,
  partnerNo,
  onPartnerNoChange,
  destinationNo,
  onDestinationNoChange,
  estimateDate,
  onEstimateDateChange,
  priceChangeDate,
  onPriceChangeDateChange,
  recipientName,
  onRecipientNameChange,
  requirement,
  onRequirementChange,
  note,
  onNoteChange,
  proposalMessage,
  onProposalMessageChange,
  shopsQuery,
  partnersQuery,
  destinationsQuery,
  destinationFallback,
}: Props) {
  const destinationOptions = useMemo(() => {
    const base = (destinationsQuery.data ?? []).map((d) => ({
      value: String(d.destinationNo),
      label: `${d.destinationCode ?? ''} ${d.destinationName}`.trim(),
    }))
    if (!destinationFallback) return base
    const fallbackValue = String(destinationFallback.destinationNo)
    if (base.some((opt) => opt.value === fallbackValue)) return base
    return [
      {
        value: fallbackValue,
        label: `${destinationFallback.destinationCode ?? ''} ${destinationFallback.destinationName ?? ''}`.trim(),
      },
      ...base,
    ]
  }, [destinationsQuery.data, destinationFallback])

  return (
    <Card>
      <CardContent className="pt-4">
        <div className="grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {isAdmin && (
            <div className="space-y-2">
              <Label>
                店舗 <span className="text-destructive">*</span>
              </Label>
              <Select
                value={shopNo}
                onValueChange={(v) => {
                  onShopNoChange(v)
                  onPartnerNoChange('')
                  onDestinationNoChange('')
                }}
              >
                <SelectTrigger>
                  <SelectValue placeholder="店舗を選択" />
                </SelectTrigger>
                <SelectContent>
                  {(shopsQuery.data ?? []).map((s) => (
                    <SelectItem key={s.shopNo} value={String(s.shopNo)}>
                      {s.shopName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
          )}
          <div className="space-y-2">
            <Label>
              得意先 <span className="text-destructive">*</span>
            </Label>
            <SearchableSelect
              value={partnerNo}
              onValueChange={(v) => {
                onPartnerNoChange(v)
                onDestinationNoChange('')
              }}
              options={(partnersQuery.data ?? []).map((p) => ({
                value: String(p.partnerNo),
                label: `${p.partnerCode} ${p.partnerName}`,
              }))}
              placeholder="得意先を選択"
              searchPlaceholder="得意先を検索..."
            />
          </div>
          <div className="space-y-2">
            <Label>納品先</Label>
            <SearchableSelect
              value={destinationNo}
              onValueChange={onDestinationNoChange}
              options={destinationOptions}
              placeholder="納品先を選択"
              searchPlaceholder="納品先を検索..."
              clearable
            />
          </div>
          <div className="space-y-2">
            <Label>
              見積日 <span className="text-destructive">*</span>
            </Label>
            <Input
              type="date"
              value={estimateDate}
              onChange={(e) => onEstimateDateChange(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label>
              価格改定日 <span className="text-destructive">*</span>
            </Label>
            <Input
              type="date"
              value={priceChangeDate}
              onChange={(e) => onPriceChangeDateChange(e.target.value)}
            />
          </div>
          <div className="space-y-2">
            <Label>担当者名（宛名）</Label>
            <Input
              value={recipientName}
              onChange={(e) => onRecipientNameChange(e.target.value)}
              placeholder="例: 森社長"
            />
          </div>
          <div className="space-y-2">
            <Label>要件（得意先向け・印刷表示）</Label>
            <Input
              value={requirement}
              onChange={(e) => onRequirementChange(e.target.value)}
              placeholder="例: ○○仕様で見積依頼"
            />
          </div>
          <div className="space-y-2">
            <Label>社内メモ（印刷非表示）</Label>
            <Input
              value={note}
              onChange={(e) => onNoteChange(e.target.value)}
              placeholder="社内向けメモ"
            />
          </div>
          <div className="space-y-2 md:col-span-2 lg:col-span-3">
            <Label>提案文（得意先向けメッセージ・明細後に表示）</Label>
            <textarea
              value={proposalMessage}
              onChange={(e) => onProposalMessageChange(e.target.value)}
              placeholder="例: 価格をご確認いただき、問題なければサンプルを取寄せますのでホルダーに入るかどうかの確認に進ませていただきたい。"
              className="flex min-h-20 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-xs transition-[color,box-shadow] outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50"
              rows={3}
            />
          </div>
        </div>
      </CardContent>
    </Card>
  )
}
