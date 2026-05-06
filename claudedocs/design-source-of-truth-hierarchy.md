# 数字の権威階層 (Source of Truth Hierarchy)

作成日: 2026-05-06
対象: 経理・財務管理の全機能 (買掛金・売掛金・MF 連携)
DESIGN-DECISION: T1
ステータス: 確定 (案 A 採用)

## 1. 目的と背景

経理処理では同じ取引について複数の「金額」が存在する。例えば 1 件の仕入には:

- **請求書 (原本)**: 仕入先からの請求金額
- **SMILE 仕入実績**: 自社基幹で計算した仕入額
- **自社買掛集計**: 月次バッチで集計した買掛金額
- **振込確定額**: 振込明細 Excel から取り込んだ実際の振込額
- **MF 仕訳**: マネーフォワード会計に登録された仕訳金額

これらは **本来一致するべき** だが、入力ズレ・按分丸め・値引/早払/送料/相殺・経過月差などで微妙に不一致になる。

画面ごとに参照する数字が異なる場合、

- 経理担当: 「どっちが正しいの?」と混乱
- 税理士監査時: 「この数字とその数字、なぜ違う?」と説明できない
- 開発: 「どの数字を信用してロジックを組むか」が曖昧

これを防ぐため、**権威階層 (どの数字が "正" か)** を明文化する。

## 2. 数字源の一覧

| 数字源 | 物理ストレージ | 業務的意味 | 権威 |
|---|---|---|---|
| 仕入先請求書 (原本) | 紙 / PDF | 仕入先からの請求金額の最終権威 | ★★★ |
| SMILE 仕入実績 | `t_purchase` / `t_purchase_detail` | 自社基幹で計算した仕入額 (経理が SMILE 入力) | ★★ |
| 自社買掛集計 (税込) | `t_accounts_payable_summary.tax_included_amount_change` | 月次バッチで supplier × 税率に集計 | ★ |
| 振込確定額 | `t_accounts_payable_summary.verified_amount` | 振込明細 Excel or 手動から確定 | ★★ |
| MF 仕訳 | MF API `/journals` | マネーフォワード会計に登録された仕訳 | ★★ |
| MF 試算表 | MF API `/trial_balance/bs` | MF 上の月末残高 (検証用のみ) | ★★ |
| 期首残 (買掛) | `m_supplier_opening_balance` | MF 期首残高仕訳 (#1) + 手動補正 | ★★ |
| 累積残 (closing) | 計算: `opening + change − payment_settled` | 自社 T 勘定定義 | ★ (派生) |
| 売掛 請求 | `t_invoice` | SMILE 請求実績 Excel から取込 | ★★ |
| 売掛 検証済 | `t_accounts_receivable_summary.verified_amount` | 入金確定額 | ★★ |
| 銀行通帳 | (MF API 経由 or 紙) | 実際の出金・入金 | ★★★ |

★★★ = 取引の実体 (これが事実)
★★ = 実体を写す一次データ (人または基幹システムが入力した一次情報)
★ = 一次データから派生する集計値 (バッチ計算結果)

## 3. 権威階層

### 3.1 請求金額 (買掛)

```
1. 仕入先請求書 (原本)              ← 最終権威 (取引の実体)
   ↓ 経理が SMILE に手入力 / Excel 取込
2. SMILE 仕入実績 (t_purchase)
   ↓ 月次バッチ aggregation で supplier × 税率に集計
3. 自社買掛集計 (t_accounts_payable_summary.tax_included_amount_change)
   ↓ MF CSV 出力
4. MF 仕入仕訳 (借方 仕入高)
```

「上位 = 真」。下位で不一致が出たら **下位を上位に合わせる** (ボトムアップで上書きしない)。

### 3.2 振込金額 (買掛)

```
1. 銀行通帳 (実際の出金)            ← 最終権威 (取引の実体)
   ↓ MF 銀行データ連携 or 振込明細 Excel
2. t_accounts_payable_summary.verified_amount
   ↓ MF CSV 出力 (verified-export, 検証済のみ)
3. MF 振込仕訳 (貸方 普通預金)
```

### 3.3 残高 (累積)

```
期首残 (opening):
  m_supplier_opening_balance.effective_balance
  = MF journal #1 (期首残高仕訳) + 手動補正
  → 累積残一覧 / 整合性レポート / 買掛帳 で **同一参照** (P1-02 で統一)

月中残 (change):
  自社計算  = opening + Σ tax_included_amount_change − Σ payment_settled
  MF       = MF /journals の credit − debit を月次集計

月末残 (closing):
  closing = opening + change − payment_settled
  ※ 自社 T 勘定の定義。MF 試算表 (/trial_balance/bs) と突合する
```

### 3.4 売掛 (構造は買掛と対称)

```
1. SMILE 請求実績 Excel (得意先別請求書)
   ↓ Excel 取込
2. t_invoice.current_billing_amount
   ↓ 月次集計
3. t_accounts_receivable_summary.tax_included_amount_change
   ↓ MF CSV 出力
4. MF 売掛仕訳 (借方 売掛金)

入金:
1. 銀行通帳 (入金実績)
2. t_accounts_receivable_summary.verified_amount
3. MF 入金仕訳 (貸方 売掛金)
```

## 4. 不一致時のルール

### 4.1 請求書 vs SMILE 仕入実績

| 状況 | 対応 |
|---|---|
| SMILE > 請求書 | 経理担当が SMILE を修正 (請求書を正とする) |
| SMILE < 請求書 | 同上 |
| 修正後 | 月次集計バッチ (`accountsPayableAggregation`) で `t_accounts_payable_summary` に反映 |

→ **業務手順**: 月初に請求書チェック → SMILE 修正 → 集計バッチ

### 4.2 SMILE 仕入実績 vs 振込実績

| 状況 | 対応 |
|---|---|
| 一致 (差額 ≤ ¥100) | `verification_result=1` で確定 |
| 差額あり | `t_accounts_payable_summary.verification_note` に差額理由を記録 |
| 値引/早払/送料/相殺 | per-supplier に attribute (P1-03 案 D-2 実装済)、税理士監査時に説明可能 |
| `verified_amount` の計算過程 | `auto_adjusted_amount` (V026) に税抜逆算 + 差額記録 |

→ **画面**: `/finance/accounts-payable` の「振込明細で一括検証」または個別の「検証」ダイアログ

### 4.3 自社買掛集計 vs MF

| 状況 | 対応 |
|---|---|
| 一致 (累積差 = 0) | OK |
| 金額差 (MINOR ≤ ¥1,000) | 整合性レポート (`/finance/accounts-payable-ledger/integrity`) で diff 検出、軽量 |
| 金額差 (MAJOR > ¥1,000) | 同上、要対応 |
| MF 側のみ | 自社で取り込み漏れ → 手動仕訳追加 or `MF_APPLY` で自社に書き戻し |
| 自社側のみ | MF への CSV 出力漏れ → CSV 再出力 or 手動仕訳 |

**操作 (`t_consistency_review`)**:
- `IGNORE`: 監査済み・差額許容として記録 (例: MF 側の取引を自社に取り込まない判断)
- `MF_APPLY`: MF 値を自社の `verified_amount` に書き戻し (差額理由を `verification_note` に記録)

snapshot 比較で金額が再変動した場合は自動的に再表示 (V027)。

### 4.4 累積残一覧 vs 整合性レポート

P1-02 で **同一参照源** に統一済:

- `PayableBalanceCalculator` を 1 箇所に集約
- `m_supplier_opening_balance` を opening 注入として両画面で同一参照
- 両画面の cumulative diff は同値で表示される

→ 不一致が起きないことを保証。

## 5. 各画面での参照元一覧

| 画面 | 列 / フィールド | 参照する数字源 | 権威階層上の位置 |
|---|---|---|---|
| `/finance/accounts-payable` (買掛金一覧) | 買掛金額(税込) | `tax_included_amount_change` | ★ PAYABLE_SUMMARY |
| | 振込明細額 | `verified_amount` | ★★ VERIFIED_AMOUNT |
| | 差額 | `payment_difference` (派生) | (派生) |
| `/finance/accounts-payable-ledger` (買掛帳) | 前月繰越 | `opening_balance_tax_included` (期首残注入) | ★★ OPENING_BALANCE |
| | 仕入 | `change_tax_included` | ★ PAYABLE_SUMMARY |
| | 検証額 | `verified_amount` | ★★ VERIFIED_AMOUNT |
| | 支払反映 | `payment_settled_tax_included` (派生) | (派生) |
| | 当月残 | `closing_balance_tax_included` (派生) | (派生) CLOSING_CALC |
| | MF delta | MF `/journals` credit − debit | ★★ MF_JOURNAL |
| | MF 累積残 | MF `/trial_balance/bs` (or journals 累積) | ★★ MF_TRIAL_BALANCE |
| `/finance/accounts-payable/supplier-balances` (累積残一覧) | self 残 | `closing` (派生) | (派生) CLOSING_CALC |
| | MF 残 | MF `/journals` 累積 | ★★ MF_JOURNAL |
| | opening | `m_supplier_opening_balance` | ★★ OPENING_BALANCE |
| `/finance/accounts-payable-ledger/integrity` (整合性レポート) | 自社差額 | `selfDelta` (派生) | (派生) |
| | MF 差額 | MF `/journals` (派生) | ★★ MF_JOURNAL |
| | 累積差 | self vs MF (派生) | (派生) |
| `/finance/accounts-receivable` (売掛金一覧) | 税込金額 | `tax_included_amount_change` | ★ PAYABLE_SUMMARY (AR 版) |
| | 請求書金額 | `invoice_amount` | ★★ AR_INVOICE |
| `/finance/payment-mf-import` (買掛仕入 MF 変換) | 請求額 | Excel 取込値 (= 請求書) | ★★★ INVOICE |
| | 買掛金 | `t_accounts_payable_summary` | ★ PAYABLE_SUMMARY |
| `/finance/invoices` (請求書一覧) | 今回請求額 | `t_invoice.current_billing_amount` | ★★ AR_INVOICE |
| `/finance/supplier-opening-balance` (期首残管理) | 実効値 | `m_supplier_opening_balance.effective_balance` | ★★ OPENING_BALANCE |
| | MF 取得値 | MF journal #1 | ★★ MF_JOURNAL |
| `/finance/cashbook-import` (現金出納帳取込) | 借方/貸方金額 | Excel 取込値 (= 出納帳原本) | ★★★ (file source) |
| `/finance/mf-health` (MF 連携ヘルスチェック) | OAuth / cache | (検証用 metric, 金額表示なし) | n/a |

## 6. 税理士・監査人への説明資料

年次決算時にこのドキュメントを提示することで:

1. **各画面の数字の由来を説明可能**
   - 「買掛金一覧の `振込明細額` は、振込明細 Excel から取り込んだ実際の出金額です。銀行通帳と一致します。」
2. **不一致発生時の対応プロセスを説明可能**
   - 「MF と自社で差額が出た場合、`/finance/accounts-payable-ledger/integrity` で diff を検出し、`MF_APPLY` で MF を正として書き戻すか、`IGNORE` で監査済みマークします。これらは `t_consistency_review` に履歴が残ります。」
3. **権威階層を提示可能**
   - 「請求書 (原本) > SMILE 入力 > 自社集計 > MF 仕訳 の順で正しさを定義しています。下位で問題が出たら上位を確認します。」

## 7. UI 上の表現 (案 A)

各画面のテーブル **ヘッダー** に `<AmountSourceTooltip source="...">` を表示:

- ホバーで「この列は何由来か」「物理ストレージ」「業務的意味」を表示
- ヘッダーのみに付与 (各セルに付けるとうるさい)
- 権威階層の説明はこのドキュメント (DESIGN-DECISION T1) に集約

実装:

- `frontend/components/common/AmountSourceTooltip.tsx`
- shadcn/ui Tooltip (radix-ui ベース) を使用
- AmountSource enum で 10 種を定義 (INVOICE / SMILE / PAYABLE_SUMMARY / VERIFIED_AMOUNT / MF_JOURNAL / MF_TRIAL_BALANCE / OPENING_BALANCE / CLOSING_CALC / AR_INVOICE / AR_VERIFIED)

## 8. 関連 DESIGN-DECISION

| ID | 内容 | 関連 |
|---|---|---|
| T1 | 数字の権威階層 (本ドキュメント) | UI 表現 + 監査説明資料 |
| T2 | 監査証跡基盤 (誰がいつ何を変更したか) | 未着手 (`design-audit-trail-accounts-payable.md` ドラフト済) |
| P1-02 | opening 注入方針確定 (累積残/整合性 統一) | `m_supplier_opening_balance` を両画面で同一参照 |
| P1-03 案 D-2 | per-supplier 値引/早払 attribute | `verification_note` + `auto_adjusted_amount` |
| P1-09 案 D | `verified_amount` 不変条件文書化 | 手動確定行は集計バッチで上書きしない |
| V026 | 自動調整 (`auto_adjusted_amount`) | 税抜逆算 + 差額記録 |
| V027 | `t_consistency_review` (案 X+Y) | IGNORE / MF_APPLY 履歴 |

## 9. 補足: 「業務実態」と「設計書」の優先順位

設計書 (`claudedocs/design-*.md`) と運用実態が食い違う時は **運用実態を優先** する。
特にゴールデンマスタが empty stub している箇所は PASS が正当性を保証しない。

参考: `MEMORY.md feedback_design_doc_vs_operations`
