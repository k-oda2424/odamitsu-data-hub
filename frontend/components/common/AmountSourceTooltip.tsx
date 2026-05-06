'use client'

import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Info } from 'lucide-react'

/**
 * AmountSource: 金額表示列の「数字の出どころ」を表す enum。
 * 設計書: claudedocs/design-source-of-truth-hierarchy.md (DESIGN-DECISION T1)
 *
 * 各画面のテーブルヘッダーに <AmountSourceTooltip source="..." /> を添えることで
 * 「この列は何由来か」を説明可能にする。経理担当・税理士監査時の説明資料を兼ねる。
 */
export type AmountSource =
  | 'INVOICE'           // 仕入先請求書 (原本)
  | 'SMILE'             // SMILE 仕入実績 (t_purchase / t_purchase_detail)
  | 'PAYABLE_SUMMARY'   // 自社買掛集計 (t_accounts_payable_summary.tax_included_amount_change)
  | 'VERIFIED_AMOUNT'   // 振込確定額 (t_accounts_payable_summary.verified_amount)
  | 'MF_JOURNAL'        // MF 仕訳 (MF API /journals)
  | 'MF_TRIAL_BALANCE'  // MF 試算表 (MF API /trial_balance/bs, 検証用)
  | 'OPENING_BALANCE'   // 期首残 (m_supplier_opening_balance)
  | 'CLOSING_CALC'      // 累積残 (closing = opening + change − payment_settled, 派生値)
  | 'AR_INVOICE'        // 売掛 請求 (t_invoice)
  | 'AR_VERIFIED'       // 売掛 検証済 (t_accounts_receivable_summary.verified_amount)

const SOURCE_LABEL: Record<AmountSource, { title: string; description: string }> = {
  INVOICE: {
    title: '仕入先請求書 (原本)',
    description: '仕入先から届いた請求書 (原本) の金額。取引の最終権威 (★★★)。',
  },
  SMILE: {
    title: 'SMILE 仕入実績',
    description: '自社基幹システム SMILE で計算した仕入額。t_purchase / t_purchase_detail から集計。',
  },
  PAYABLE_SUMMARY: {
    title: '自社買掛集計 (税込)',
    description: '月次バッチで supplier × 税率に集計した買掛金額。t_accounts_payable_summary.tax_included_amount_change 由来。',
  },
  VERIFIED_AMOUNT: {
    title: '振込確定額',
    description: '振込明細 Excel または手動から確定した実際の振込額。t_accounts_payable_summary.verified_amount 由来。銀行通帳と一致するべき値。',
  },
  MF_JOURNAL: {
    title: 'MF 仕訳',
    description: 'マネーフォワード会計に登録された仕訳金額。MF API /journals から取得 (credit − debit を月次集計)。',
  },
  MF_TRIAL_BALANCE: {
    title: 'MF 試算表',
    description: 'マネーフォワード上の月末残高。MF API /trial_balance/bs から取得 (検証用、自社 DB の書込先ではない)。',
  },
  OPENING_BALANCE: {
    title: '期首残',
    description: 'MF 期首残高仕訳 (#1) から取得した期首残 + 手動補正。m_supplier_opening_balance.effective_balance 由来。',
  },
  CLOSING_CALC: {
    title: '累積残 (派生値)',
    description: '計算式: opening + change − payment_settled。自社 T 勘定の月末残高定義。',
  },
  AR_INVOICE: {
    title: '請求 (売掛)',
    description: 't_invoice 由来の請求書金額 (SMILE 請求実績 Excel から取込)。',
  },
  AR_VERIFIED: {
    title: '入金確定額 (売掛)',
    description: 't_accounts_receivable_summary.verified_amount 由来の入金確定額。',
  },
}

interface AmountSourceTooltipProps {
  source: AmountSource
}

export function AmountSourceTooltip({ source }: AmountSourceTooltipProps) {
  const { title, description } = SOURCE_LABEL[source]
  return (
    <TooltipProvider>
      <Tooltip>
        <TooltipTrigger asChild>
          <span
            className="inline-flex"
            // ヘッダーがソート可能な列の場合、Info ホバーで sort をトリガしないよう伝播停止
            onClick={(e) => e.stopPropagation()}
          >
            <Info
              className="ml-1 h-3 w-3 text-muted-foreground cursor-help"
              aria-label={`数字源: ${title}`}
            />
          </span>
        </TooltipTrigger>
        <TooltipContent className="max-w-xs">
          <div className="font-medium">{title}</div>
          <div className="text-xs opacity-80 mt-1">{description}</div>
        </TooltipContent>
      </Tooltip>
    </TooltipProvider>
  )
}
