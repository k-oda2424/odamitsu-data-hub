'use client'

import { useState } from 'react'
import { ExternalLink, Play, Loader2 } from 'lucide-react'
import { toast } from 'sonner'
import { api } from '@/lib/api-client'

const BCART_ADMIN_ORDER = 'https://odamitsu.i13.bcart.jp/admin/order/list'
const BCART_ADMIN_IMPORT = 'https://odamitsu.i13.bcart.jp/admin/logistics/csv/import'

interface WorkflowStep {
  number: number
  text: string
  href?: string
}

const steps: WorkflowStep[] = [
  {
    number: 1,
    text: '小田光オンラインに得意先から注文が入ります',
    href: BCART_ADMIN_ORDER,
  },
  {
    number: 2,
    text: '管理画面の「新規注文」>「未発送」を確認し、発送状況を「発送指示」に変更、納品日を入力',
    href: BCART_ADMIN_ORDER,
  },
  {
    number: 3,
    text: '上のバッチ「新規受注取込」を起動',
  },
  {
    number: 4,
    text: 'Smileで【随時業務＞テキスト取込（明細）＞売上明細】でファイルを取込',
  },
  {
    number: 5,
    text: '必要に応じてSmileで受注伝票を出力',
  },
  {
    number: 6,
    text: 'Smileで売上明細を出力 → バッチ「売上明細取込」を起動',
  },
  {
    number: 7,
    text: '左メニュー「B-CART出荷」で出荷ステータスを「出荷済」に変更',
  },
  {
    number: 8,
    text: 'バッチ「出荷実績CSV」を起動',
  },
  {
    number: 9,
    text: '小田光オンライン「受注管理 > 出荷実績インポート」でCSVを取込',
    href: BCART_ADMIN_IMPORT,
  },
]

const MEMBER_IMPORT_JOB = 'bCartMemberUpdate'

export function WorkflowGuide() {
  // バッチ完了監視はバッチ管理画面の役割。このボタンは起動の成否のみ toast で伝える。
  const [launching, setLaunching] = useState(false)

  const handleMemberImport = async () => {
    setLaunching(true)
    try {
      await api.post(`/batch/execute/${MEMBER_IMPORT_JOB}`)
      toast.success('新規会員取込バッチを起動しました')
    } catch {
      toast.error('新規会員取込バッチの起動に失敗しました')
    } finally {
      setLaunching(false)
    }
  }

  return (
    <div className="px-5 pb-5">
      <ol className="relative ml-3 border-l-2 border-border/60">
        {steps.map((step) => {
          const isLink = !!step.href
          const Wrapper = isLink ? 'a' : 'div'
          const wrapperProps = isLink
            ? { href: step.href, target: '_blank', rel: 'noopener noreferrer' }
            : {}

          return (
            <li key={step.number} className="relative ml-6 pb-1">
              {/* Step number circle */}
              <span className="absolute -left-[calc(1.5rem+1px)] flex h-6 w-6 items-center justify-center rounded-full bg-primary text-[11px] font-bold text-primary-foreground ring-4 ring-background">
                {step.number}
              </span>

              <Wrapper
                {...wrapperProps}
                className={`block rounded-md px-3 py-2.5 text-[13px] leading-relaxed transition-colors ${
                  isLink
                    ? 'text-foreground hover:bg-accent/60 cursor-pointer group'
                    : 'text-muted-foreground'
                }`}
              >
                {step.text}
                {isLink && (
                  <ExternalLink className="ml-1 inline-block h-3 w-3 text-muted-foreground/50 group-hover:text-foreground transition-colors" />
                )}
              </Wrapper>
            </li>
          )
        })}
      </ol>

      {/* Note */}
      <div className="mt-4 rounded-lg bg-muted/50 px-4 py-3 text-xs leading-relaxed text-muted-foreground">
        <span className="font-medium text-foreground/70">補足: </span>
        新規得意先が登録された場合は、Smile用得意先コードを作成し、小田光オンラインの会員＞貴社コードに設定。その後
        <button
          type="button"
          onClick={handleMemberImport}
          disabled={launching}
          className="mx-1 inline-flex items-center gap-1 rounded-md border border-border bg-background px-2 py-0.5 text-[11px] font-medium text-foreground hover:bg-accent disabled:opacity-60"
        >
          {launching ? (
            <><Loader2 className="h-3 w-3 animate-spin" />起動中</>
          ) : (
            <><Play className="h-3 w-3" />新規会員取込</>
          )}
        </button>
        バッチを起動してください。
      </div>
    </div>
  )
}
