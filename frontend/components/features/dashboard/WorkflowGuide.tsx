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
    text: '小田光オンラインの管理画面から「新規注文」の中で「未発送」のものを探し、受注明細を確認します。\n在庫に問題がなければ、発送状況を「発送指示」に変更し、納品日を入力します。',
    href: BCART_ADMIN_ORDER,
  },
  {
    number: 3,
    text: '左上にある新規受注取込バッチを起動してください。',
  },
  {
    number: 4,
    text: 'Smileで【随時業務＞テキスト取込（明細）＞売上明細】で③のファイルを取込',
  },
  {
    number: 5,
    text: '必要な場合はSmileで取り込んだ受注伝票を出力してください',
  },
  {
    number: 6,
    text: 'Smile連携ができたことを確認するため、事前にSmileの【随時業務＞テキスト出力（明細）＞売上明細】でファイルを出力してください。\n左から2番目の売上明細取込バッチを起動してこのシステムに取り込んでください。',
  },
  {
    number: 7,
    text: '左メニューのB-Cart出荷処理で連携済みの受注の出荷ステータスを「出荷済」に変更して下部の更新ボタンを押して更新してください。',
  },
  {
    number: 8,
    text: '小田光オンラインへ出荷完了連携します。左から3番目の「出荷実績CSV」バッチを起動してください。',
  },
  {
    number: 9,
    text: '小田光オンラインの管理画面から「受注管理 > 出荷実績インポート」へアクセスします。先ほど作成した出荷実績CSVファイルをインポートします。',
    href: BCART_ADMIN_IMPORT,
  },
]

const NOTE_TEXT =
  '新規得意先が登録された場合は、Smile用得意先コードを作成して、小田光オンラインの会員＞貴社コードに得意先コードを設定します。\n設定後、一番右の新規会員取込バッチを起動し、生成した会員登録用ファイルをSmileへ取り込んでください。'

export function WorkflowGuide() {
  return (
    <div className="space-y-0">
      {steps.map((step) => {
        const content = (
          <div className="border-b px-4 py-3 text-sm leading-relaxed hover:bg-muted/50 transition-colors">
            <span className="font-medium">{'①②③④⑤⑥⑦⑧⑨'[step.number - 1]}</span>
            {step.text.split('\n').map((line, i) => (
              <span key={i}>
                {i > 0 && <br />}
                {line}
              </span>
            ))}
          </div>
        )
        if (step.href) {
          return (
            <a
              key={step.number}
              href={step.href}
              target="_blank"
              rel="noopener noreferrer"
              className="block text-foreground no-underline"
            >
              {content}
            </a>
          )
        }
        return <div key={step.number}>{content}</div>
      })}
      <div className="px-4 py-3 text-sm leading-relaxed bg-muted/30 rounded-b-lg">
        {NOTE_TEXT.split('\n').map((line, i) => (
          <span key={i}>
            {i > 0 && <br />}
            {line}
          </span>
        ))}
      </div>
    </div>
  )
}
