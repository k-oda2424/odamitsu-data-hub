'use client'

import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { calcProfit, calcProfitRate, calcCaseProfit, fmt, fmtRate } from '@/lib/estimate-calc'
import type { ComparisonGroupResponse, ComparisonDetailResponse } from '@/types/estimate-comparison'

interface Props {
  group: ComparisonGroupResponse
  isAdmin: boolean
}

function DiffIndicator({ current, base }: { current: number | null; base: number | null }) {
  if (current == null || base == null) return null
  const diff = current - base
  if (diff === 0) return <span className="text-muted-foreground ml-1">(±0)</span>
  if (diff > 0) return <span className="text-green-600 ml-1">(↑{fmt(diff)})</span>
  return <span className="text-red-600 ml-1">(↓{fmt(Math.abs(diff))})</span>
}

function DiffRateIndicator({ current, base }: { current: number | null; base: number | null }) {
  if (current == null || base == null) return null
  const diff = current - base
  if (Math.abs(diff) < 0.05) return <span className="text-muted-foreground ml-1">(±0)</span>
  if (diff > 0) return <span className="text-green-600 ml-1">(↑)</span>
  return <span className="text-red-600 ml-1">(↓)</span>
}

export function ComparisonGroupSection({ group, isAdmin }: Props) {
  const baseProfit = calcProfit(group.baseGoodsPrice, group.basePurchasePrice)
  const baseProfitRate = calcProfitRate(group.baseGoodsPrice, group.basePurchasePrice)
  const baseCaseProfit = calcCaseProfit(group.baseGoodsPrice, group.basePurchasePrice, group.baseContainNum)

  return (
    <Card>
      <CardHeader className="pb-3">
        <CardTitle className="text-base">
          グループ{group.groupNo}: {group.baseGoodsName}
          {group.baseSpecification && (
            <span className="ml-2 text-sm font-normal text-muted-foreground">{group.baseSpecification}</span>
          )}
        </CardTitle>
        {group.groupNote && (
          <p className="text-sm text-muted-foreground">{group.groupNote}</p>
        )}
      </CardHeader>
      <CardContent className="p-0">
        <div className="overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b bg-muted/50">
                <th className="px-3 py-2 text-left font-medium w-28"></th>
                <th className="px-3 py-2 text-left font-medium min-w-[150px]">基準品</th>
                {group.details.map((d) => (
                  <th key={d.detailNo} className="px-3 py-2 text-left font-medium min-w-[150px]">
                    代替{d.detailNo}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              <tr className="border-b">
                <td className="px-3 py-1.5 text-muted-foreground">商品コード</td>
                <td className="px-3 py-1.5 font-mono text-xs">{group.baseGoodsCode ?? '-'}</td>
                {group.details.map((d) => (
                  <td key={d.detailNo} className="px-3 py-1.5 font-mono text-xs">{d.goodsCode ?? '-'}</td>
                ))}
              </tr>
              <tr className="border-b">
                <td className="px-3 py-1.5 text-muted-foreground">商品名</td>
                <td className="px-3 py-1.5">{group.baseGoodsName}</td>
                {group.details.map((d) => (
                  <td key={d.detailNo} className="px-3 py-1.5">{d.goodsName}</td>
                ))}
              </tr>
              <tr className="border-b">
                <td className="px-3 py-1.5 text-muted-foreground">規格</td>
                <td className="px-3 py-1.5">{group.baseSpecification ?? '-'}</td>
                {group.details.map((d) => (
                  <td key={d.detailNo} className="px-3 py-1.5">{d.specification ?? '-'}</td>
                ))}
              </tr>
              {isAdmin && (
                <tr className="border-b">
                  <td className="px-3 py-1.5 text-muted-foreground">仕入単価</td>
                  <td className="px-3 py-1.5 text-right tabular-nums">{fmt(group.basePurchasePrice)}</td>
                  {group.details.map((d) => (
                    <td key={d.detailNo} className="px-3 py-1.5 text-right tabular-nums">{fmt(d.purchasePrice)}</td>
                  ))}
                </tr>
              )}
              <tr className="border-b">
                <td className="px-3 py-1.5 text-muted-foreground">販売単価</td>
                <td className="px-3 py-1.5 text-right tabular-nums">{fmt(group.baseGoodsPrice)}</td>
                {group.details.map((d) => (
                  <td key={d.detailNo} className="px-3 py-1.5 text-right tabular-nums">{fmt(d.proposedPrice)}</td>
                ))}
              </tr>
              <tr className="border-b">
                <td className="px-3 py-1.5 text-muted-foreground">入数</td>
                <td className="px-3 py-1.5 text-right tabular-nums">{group.baseContainNum ?? '-'}</td>
                {group.details.map((d) => (
                  <td key={d.detailNo} className="px-3 py-1.5 text-right tabular-nums">{d.containNum ?? '-'}</td>
                ))}
              </tr>
              {isAdmin && (
                <>
                  <tr className="border-b">
                    <td className="px-3 py-1.5 text-muted-foreground">粗利額</td>
                    <td className="px-3 py-1.5 text-right tabular-nums">{fmt(baseProfit)}</td>
                    {group.details.map((d) => {
                      const profit = calcProfit(d.proposedPrice, d.purchasePrice)
                      return (
                        <td key={d.detailNo} className="px-3 py-1.5 text-right tabular-nums">
                          {fmt(profit)}
                          <DiffIndicator current={profit} base={baseProfit} />
                        </td>
                      )
                    })}
                  </tr>
                  <tr className="border-b">
                    <td className="px-3 py-1.5 text-muted-foreground">粗利率</td>
                    <td className="px-3 py-1.5 text-right tabular-nums">{fmtRate(baseProfitRate)}</td>
                    {group.details.map((d) => {
                      const rate = calcProfitRate(d.proposedPrice, d.purchasePrice)
                      return (
                        <td key={d.detailNo} className="px-3 py-1.5 text-right tabular-nums">
                          {fmtRate(rate)}
                          <DiffRateIndicator current={rate} base={baseProfitRate} />
                        </td>
                      )
                    })}
                  </tr>
                  <tr className="border-b">
                    <td className="px-3 py-1.5 text-muted-foreground">ケース粗利</td>
                    <td className="px-3 py-1.5 text-right tabular-nums">{fmt(baseCaseProfit)}</td>
                    {group.details.map((d) => {
                      const caseProfit = calcCaseProfit(d.proposedPrice, d.purchasePrice, d.containNum)
                      return (
                        <td key={d.detailNo} className="px-3 py-1.5 text-right tabular-nums">
                          {fmt(caseProfit)}
                          <DiffIndicator current={caseProfit} base={baseCaseProfit} />
                        </td>
                      )
                    })}
                  </tr>
                </>
              )}
              {group.details.some((d) => d.detailNote) && (
                <tr className="border-b">
                  <td className="px-3 py-1.5 text-muted-foreground">備考</td>
                  <td className="px-3 py-1.5">-</td>
                  {group.details.map((d) => (
                    <td key={d.detailNo} className="px-3 py-1.5 text-muted-foreground">{d.detailNote || '-'}</td>
                  ))}
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </CardContent>
    </Card>
  )
}
