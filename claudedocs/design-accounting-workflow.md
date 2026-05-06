# 経理ワークフロー画面 設計書

作成日: 2026-05-04
対象ブランチ: refactor/code-review-fixes
種別: 逆生成 (現状コードから抽出)
関連設計書:
- `claudedocs/design-accounts-payable.md`
- `claudedocs/design-accounts-payable-ledger.md`
- `claudedocs/design-mf-cashbook-import.md`
- `claudedocs/design-payment-mf-import.md`

---

## 1. 目的と業務上の意味

### 1.1 なぜこの画面があるのか

小田光の月次経理処理は、複数の独立した画面・バッチを「正しい順序」で実行する必要がある。
具体的には以下のような業務フロー上の依存関係があり、ステップを飛ばしたり順序を間違えると後段の集計値が崩れる。

- **SMILE 仕入取込** が終わっていないと **買掛金集計** が古いデータで走る
- **請求データ取込** が終わっていないと **売掛金 MF 連携** で月跨ぎの請求が落ちる
- **買掛集計 → 検証 → サマリ → 仕訳CSV** はこの順序固定（前段の出力を後段が消費）

経理担当者は月に十数回の操作を、締日 (15日 / 20日 / 月末) に応じて入れ子で実施する。
個別画面を直接巡回すると「今日やるべき作業はどれか」「先週の `accountsPayableSummary` バッチは成功したか」「最新の請求取込は何日付までか」を毎回別 URL で確認する必要があり、抜け漏れリスクが高い。

本画面 `/finance/workflow` は、**月次経理ワークフロー全体の進捗ダッシュボード**として:
1. 月次スケジュール (15日締 / 20日締 / 毎月21日 / 月末締の 4 グループ) を一覧表示
2. 5 ステップ (出納帳取込 → SMILE仕入取込 → 請求取込 → 売掛金 MF → 買掛金集計) の状態を可視化
3. 各ステップから関連画面・バッチ管理画面へワンクリック導線を提供
4. 直近のバッチ実行結果 (`COMPLETED` / その他) を Chip 表示

を提供する。ステップ数・順序・スケジュール・実行ジョブ名はすべてフロントエンドにハードコードされており (`accounting-workflow.tsx:98-256`)、データソース側 (`AccountingStatusService`) は「読み取り専用の状態スナップショット」を返すのみという責務分離になっている。

### 1.2 誰が使うか
- 経理担当者 (毎日〜月数回)
- 開発・運用担当 (バッチ失敗時の影響範囲確認)

### 1.3 旧システムとの差分
旧 stock-app には対応する集約画面は存在しない。新システムで月次経理を一画面に集約するため新設された UI。

---

## 2. スコープ

### 2.1 含むもの
- ワークフロー 5 ステップの状態表示
- 月次スケジュール (4 ブロック) のカード表示
- 各ステップから関連画面 (`/finance/cashbook-import`, `/batch`, `/finance/invoices`, `/finance/accounts-payable`) への導線
- 直近のバッチ実行履歴 7 ジョブを横断表示

### 2.2 含まないもの
- 各ステップ内部の操作 (取込 / 集計 / 出力) — それぞれ専用画面に委譲
- バッチ起動 (`/batch` 画面に委譲)
- 経理データ自体の編集・閲覧 (`/finance/accounts-payable` 等に委譲)
- ステップの完了/未完了の自動判定ロジック (現状は「最終実行日時を表示するだけ」で、月次完了マークは持たない)
- 手動でのステップ完了マーキング、チェックリスト永続化

---

## 3. データモデル / 状態管理

### 3.1 サーバー側集約クラス

**`AccountingStatusService`** (`backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java:15-120`)

`@Transactional(readOnly = true)` の単一メソッド `getStatus()` が、`Map<String, Object>` を返す。
JPA Entity は経由せず、`EntityManager.createNativeQuery()` で 5 種類の生 SQL を発行する純粋なリードオンリー集約。

```
Map keys:
- cashbookHistory          : List<Map> (最新 3 件)
- smilePurchaseLatestDate  : String (最大 denpyou_hizuke)
- smilePaymentLatestDate   : String (最大 import_date)
- invoiceLatest            : List<Map> (最新締日のショップ別件数)
- accountsPayableLatestMonth: String (最大 transaction_month)
- batchJobs                : List<Map> (7 ジョブ × 最新 1 実行)
```

各クエリは `try / catch (Exception)` で囲まれており、テーブル不在や SQL エラー時は WARN ログを出して空リスト / null を返すフェイルセーフ設計 (`AccountingStatusService.java:51-54, 72-75, 102-105, 112-114`)。

### 3.2 各ステータス項目の取得元

| キー | 取得 SQL / テーブル | 出所 |
|---|---|---|
| `cashbookHistory` | `t_cashbook_import_history ORDER BY processed_at DESC LIMIT 3` | `AccountingStatusService.java:38-39` |
| `smilePurchaseLatestDate` | `SELECT MAX(denpyou_hizuke) FROM w_smile_purchase_output_file` | `AccountingStatusService.java:25` |
| `smilePaymentLatestDate` | `SELECT MAX(import_date) FROM t_smile_payment` | `AccountingStatusService.java:26` |
| `invoiceLatest` | 最新 `closing_date` のショップ別件数 (`t_invoice` GROUP BY shop_no) | `AccountingStatusService.java:60-63` |
| `accountsPayableLatestMonth` | `SELECT MAX(transaction_month) FROM t_accounts_payable_summary` | `AccountingStatusService.java:28` |
| `batchJobs` | `batch_job_execution` × `batch_job_instance` の各 job_name 最新 1 件 | `AccountingStatusService.java:81-92` |

> **TODO (SF-H02)**: ステップ 4 (売掛金MF連携) は `salesJournalIntegration` のみを監視しているが、
> 売掛側の対称性を取るため `accountsReceivableSummary` も `IN (...)` リストに追加し、§3.3 表 / §5.4 ステップ 4 にも反映する。
> 詳細は `claudedocs/triage-bgh-clusters.md` SF-H02 セクションを参照。

### 3.3 監視対象バッチジョブ (7 種)

`AccountingStatusService.java:85-87` でハードコードされている `IN (...)` リストと、フロントエンド `accounting-workflow.tsx:60-68` のラベルマッピングが対応する。

| jobName | UI ラベル | 関連ステップ |
|---|---|---|
| `purchaseFileImport` | 仕入ファイル | ステップ 2 |
| `smilePaymentImport` | 支払取込 | ステップ 2 |
| `accountsPayableAggregation` | 買掛集計 | ステップ 5 |
| `accountsPayableVerification` | 買掛検証 | ステップ 5 |
| `accountsPayableSummary` | 買掛サマリ | ステップ 5 |
| `purchaseJournalIntegration` | 仕入仕訳CSV | ステップ 5 |
| `salesJournalIntegration` | 売上仕訳CSV | ステップ 4 |

> 注: 現状 `cashbookImport` 系・`paymentMfImport` 系のジョブは監視対象に含まれていない。
> 出納帳取込は `t_cashbook_import_history` テーブル経由で履歴を取得しているため別経路。

### 3.4 各ステップの「完了判定ロジック」

現状、明示的な完了判定 (BOOLEAN) は **持たない**。代わりに以下のヒューリスティクスで「最終実行日時 / 最終取込日付」を Chip 表示する:

| ステップ | 完了サイン | 表示形式 |
|---|---|---|
| 1. 出納帳取込 | `t_cashbook_import_history` に当月分の行が存在 | period_label + processed_at + row_count |
| 2. SMILE仕入取込 | `smilePurchaseLatestDate` / `smilePaymentLatestDate` が当月内 + 直近バッチが COMPLETED | 日付 Chip + バッチ Chip |
| 3. 請求データ取込 | `invoiceLatest[*].closingDate` が当月締日 | shopNo × closingDate × count Chip |
| 4. 売掛金MF連携 | `salesJournalIntegration` ジョブが COMPLETED | バッチ Chip |
| 5. 買掛金集計・連携 | `accountsPayableLatestMonth` が当月 + 4 ジョブ COMPLETED | 月 Chip + バッチ Chip × 4 |

判断の最終解釈はユーザー (経理担当) に委ねられる。
未実行は `StatusChip` で `value === null` の灰色 "未実行" 表示 (`accounting-workflow.tsx:41-48`)、`COMPLETED` 以外のジョブは赤の `BatchChip` (`accounting-workflow.tsx:75-80`)。

---

## 4. API 設計

### 4.1 エンドポイント一覧

| Method | Path | Controller | 認可 |
|---|---|---|---|
| GET | `/api/v1/finance/accounting-status` | `FinanceController#getAccountingStatus` (`FinanceController.java:506-509`) | `@PreAuthorize("isAuthenticated()")` (クラス継承) |

ワークフロー画面が直接呼ぶエンドポイントはこの 1 本のみ。
他のステップ操作系 API (請求取込 / 買掛金 verify / バッチ起動など) は本画面では呼ばず、リンク先画面の責務。

### 4.2 Request

クエリパラメータ・リクエストボディなし。
ショップ番号も受け付けず、サーバー側で全ショップ横断の最新値を返す (現状仕様)。

### 4.3 Response

`Content-Type: application/json`, `Map<String, Object>` を `LinkedHashMap` で返す (`AccountingStatusService.java:22`)。

```jsonc
{
  "cashbookHistory": [
    {
      "periodLabel": "2026/04",
      "fileName": "cashbook_202604.xlsx",
      "processedAt": "2026-04-21T10:34:12.345",
      "rowCount": 187,
      "totalIncome": 12345678,
      "totalPayment": 9876543
    }
    // ...最大 3 件
  ],
  "smilePurchaseLatestDate": "2026-04-30",
  "smilePaymentLatestDate":  "2026-04-30",
  "invoiceLatest": [
    { "shopNo": 1, "closingDate": "2026-04-20", "count": 124 },
    { "shopNo": 2, "closingDate": "2026-04-20", "count": 41 }
  ],
  "accountsPayableLatestMonth": "2026-04-20",
  "batchJobs": [
    {
      "jobName": "accountsPayableAggregation",
      "status": "COMPLETED",
      "exitCode": "COMPLETED",
      "startTime": "2026-04-27T08:00:00",
      "endTime":   "2026-04-27T08:02:34"
    }
    // ... ジョブ名アルファベット順 7 件
  ]
}
```

### 4.4 フロントエンド型定義

`AccountingStatus` 型は `accounting-workflow.tsx:31-38` にローカル定義。
Backend の `Map<String, Object>` を、TypeScript 側で structural typing として受ける形になっている (DTO クラスを介さない)。

```ts
interface AccountingStatus {
  cashbookHistory: CashbookHistory[]
  smilePurchaseLatestDate: string | null
  smilePaymentLatestDate:  string | null
  invoiceLatest: { shopNo: number; closingDate: string; count: number }[]
  accountsPayableLatestMonth: string | null
  batchJobs: { jobName: string; status: string; exitCode: string; startTime: string | null; endTime: string | null }[]
}
```

`CashbookHistory` (`accounting-workflow.tsx:22-29`) は同ファイル内に別 interface 定義。

### 4.5 Caching / Refetch

`useQuery({ staleTime: Infinity })` (`accounting-workflow.tsx:265`) — 一度取得したら自動 refetch しない。
画面遷移して戻るたびに最新を取りたい場合は React Query の `refetchOnMount` / 手動 `invalidateQueries` が必要 (現状 UI から手動 refresh 操作なし。**改善余地**)。

---

## 5. 画面設計

### 5.1 ルート構成

```
frontend/app/(authenticated)/finance/workflow/page.tsx       # 1 行: <AccountingWorkflowPage />
frontend/components/pages/finance/accounting-workflow.tsx    # 全 363 行 (中心実装)
```

サイドバー登録: `frontend/components/layout/Sidebar.tsx:86`
> `{ title: '経理業務フロー', icon: ClipboardList, href: '/finance/workflow' }`

### 5.2 画面ブロック

```
+-----------------------------------------------------------+
| PageHeader: 経理業務フロー                                |
+-----------------------------------------------------------+
| 月次スケジュール (4 列グリッド)                           |
|  [15日締 22日頃]  [20日締 27日頃]  [毎月 21日]  [月末締 翌7日頃] |
+-----------------------------------------------------------+
| ワークフローステップ (縦積み 5 枚)                        |
|  [Step 1: 現金出納帳取込    | 毎月21日           ] ...    |
|  [Step 2: SMILE仕入取込    | 買掛金集計の前      ] ...    |
|  [Step 3: 請求データ取込   | 月3回（締日+7日）    ] ...    |
|  [Step 4: 売掛金MF連携     | ③と同時             ] ...    |
|  [Step 5: 買掛金集計・連携 | 毎月27日前後         ] ...    |
+-----------------------------------------------------------+
```

### 5.3 月次スケジュール (`schedule` 配列, `accounting-workflow.tsx:223-256`)

| period | day | tasks (stepNo / label / color) |
|---|---|---|
| 15日締 | 22日頃 | 3 請求取込 (violet), 4 売掛MF (emerald) |
| 20日締 | 27日頃 | 3 請求取込, 4 売掛MF, 5 買掛金 (rose) |
| 毎月 | 21日 | 1 出納帳 (amber) |
| 月末締 | 翌7日頃 | 3 請求取込, 4 売掛MF |

実装はカード上部にスレートグラデーション、本文に小さなナンバーバッジ + ラベル。

### 5.4 ワークフローステップ詳細 (`makeSteps()`, `accounting-workflow.tsx:98-215`)

各ステップは `WorkflowStep` interface (`accounting-workflow.tsx:83-96`) を満たし、
左側にステップ番号 + アイコン (色付きパステル背景)、右側に「タイトル + timing バッジ + 説明 + ステータス Chip 群 + note + アクションボタン」を配置。

#### ステップ 1: 現金出納帳取込
- アイコン: `BookOpen` (amber)
- timing: 毎月21日
- 説明: SMILEの現金出納帳データを取り込み、MoneyForward用の仕訳CSVを出力
- アクション: `[出納帳取込を開く] → /finance/cashbook-import`
- note: 土日を挟む場合は翌月曜に実施
- ステータス: `cashbookHistory` 最大 3 件を `period_label / processed_at (date部) / row_count` で Chip 化

#### ステップ 2: SMILE仕入取込
- アイコン: `FileSpreadsheet` (sky)
- timing: 買掛金集計の前
- 説明: SMILEの仕入データ（支払情報）をバッチで取り込み。shopNo を指定して実行
- アクション: `[バッチ管理] → /batch`
- note: 「SMILE仕入ファイル取込」「SMILE支払情報取込」を店舗ごとに実行
- ステータス: `smilePurchaseLatestDate` / `smilePaymentLatestDate` の Chip + `purchaseFileImport` / `smilePaymentImport` バッチ Chip

#### ステップ 3: 請求データ取込
- アイコン: `Receipt` (violet)
- timing: 月3回（締日+7日）
- 説明: SMILEの請求実績Excelをアップロード。15日締/20日締/月末締の3回
- アクション: `[請求書画面] → /finance/invoices`
- note: 請求書画面の「Excelインポート」ボタンから取込
- ステータス: `invoiceLatest` 配列をショップ別に "店舗{shopNo} 最新締日: {closingDate}（{count}件）" で表示

#### ステップ 4: 売掛金MF連携
- アイコン: `BarChart3` (emerald)
- timing: ③と同時
- 説明: 売掛金の仕訳データをMoneyForward用CSVとして出力
- アクション: `[バッチ管理] → /batch`
- note: バッチ「売上仕訳CSV出力」を実行
- ステータス: `salesJournalIntegration` の最新実行 Chip

#### ステップ 5: 買掛金集計・連携
- アイコン: `Wallet` (rose)
- timing: 毎月27日前後
- 説明: 買掛金の集計 → 検証 → サマリ → 仕訳CSV出力を順番に実行
- アクション: `[買掛金画面] → /finance/accounts-payable`, `[バッチ管理] → /batch`
- note: バッチ実行順: 買掛金集計 → 買掛金検証 → 買掛金サマリ → 仕入仕訳CSV出力
- ステータス: `accountsPayableLatestMonth` Chip + 4 ジョブ (`accountsPayableAggregation` / `accountsPayableVerification` / `accountsPayableSummary` / `purchaseJournalIntegration`) のバッチ Chip

### 5.5 状態表示コンポーネント

#### `StatusChip` (`accounting-workflow.tsx:40-57`)
- `value === null`: 灰色 "未実行" (`AlertCircle` アイコン)
- `value` あり + `warn === true`: 琥珀色 (注意)
- `value` あり + `warn` 無し: 緑色 (`CheckCircle2`)

> 注: 現状 `warn` を `true` にする呼び出し箇所は無い (将来用フック)。

#### `BatchChip` (`accounting-workflow.tsx:59-81`)
- `status === 'COMPLETED'`: 緑色 + チェックアイコン
- それ以外 (`FAILED`, `STARTED`, `STOPPED` 等): 赤色 + アラートアイコン
- `startTime` の日付部分のみ表示 (`split('T')[0]`)

「未着手 / 進行中 / 完了 / エラー」の 4 値を明示する Badge は **無い**。`COMPLETED` か否かの 2 値表示。

### 5.6 CTA (Call To Action)

各ステップ末尾の `actions` 配列で 1〜2 個のボタンを表示。
すべて `Button variant="outline"` + ステップ色のパステル背景。
クリックで `useRouter().push(href)` (`accounting-workflow.tsx:347`)。

| ステップ | ボタン → 遷移先 |
|---|---|
| 1 | 出納帳取込を開く → `/finance/cashbook-import` |
| 2 | バッチ管理 → `/batch` |
| 3 | 請求書画面 → `/finance/invoices` |
| 4 | バッチ管理 → `/batch` |
| 5 | 買掛金画面 → `/finance/accounts-payable`, バッチ管理 → `/batch` |

---

## 6. 各画面との連携

### 6.1 関連画面マップ

| 画面 / 機能 | 役割 | 本画面との関係 |
|---|---|---|
| `/finance/cashbook-import` | 現金出納帳→MF仕訳CSV変換 | ステップ 1 のリンク先。完了履歴を `t_cashbook_import_history` 経由で取得 |
| `/batch` | Spring Batch ジョブ起動・監視 | ステップ 2/4/5 のリンク先 |
| `/finance/invoices` | 請求書一覧・Excelインポート | ステップ 3 のリンク先。最新 `closing_date` を `t_invoice` 経由で取得 |
| `/finance/accounts-payable` | 買掛金一覧・検証 | ステップ 5 のリンク先。最新月を `t_accounts_payable_summary` 経由で取得 |
| `/finance/payment-mf-import` | 振込明細Excel→MF買掛CSV (5日/20日払い) | **本画面のフローには未組み込み** (TODO §8 参照) |

### 6.2 Spring Batch 連携

監視対象 7 ジョブの起動はすべて `/batch` 画面 (`BatchController#execute/{jobName}`) 経由。
本画面はジョブ実行履歴の SELECT のみで、起動命令は出さない。

### 6.3 MoneyForward 連携との関係

- ステップ 1 (出納帳取込) と ステップ 4 (売掛MF) と ステップ 5 (買掛MF) の出力 CSV は最終的に MoneyForward に手動 / API でインポートされる
- MF API ヘルスチェック (`/api/v1/finance/mf-health`) は本画面では呼ばれない (買掛金一覧画面 `/finance/accounts-payable` 側の責務)

### 6.4 SMILE / B-CART 連携

- SMILE 仕入: ステップ 2 で `purchaseFileImport` バッチが `w_smile_purchase_output_file` を更新 → `t_purchase_detail` へ反映
- SMILE 支払: ステップ 2 で `smilePaymentImport` バッチが `t_smile_payment` を更新 → ステップ 5 の `accountsPayableVerification` で消費
- B-CART は本フローには組み込まれていない (`shop_no=1` で SMILE 仕入として吸収される設計、`MEMORY.md` 参照)

---

## 7. 既知の制約・注意事項

### 7.1 サービス層
- `AccountingStatusService` は `EntityManager` 直叩きの NativeQuery を 5 本 + `try / catch (Exception)` フェイルセーフ。テーブル不在時も例外を呑んでログだけ出す
- 戻り値の `Map<String, Object>` は型情報を持たないため、Frontend 側の型ズレに気付きにくい (`row[0]`〜`row[5]` を `toString` または raw Object で詰めている)
- `cashbookHistory` の `rowCount` / `totalIncome` / `totalPayment` は JDBC ドライバ依存の数値型 (`BigDecimal` / `Long`) で返り、`accounting-workflow.tsx:26-28` 側は `number` 想定

### 7.2 認可
- `FinanceController` クラスに `@PreAuthorize("isAuthenticated()")` (`FinanceController.java:77`)
- `accounting-status` エンドポイントは個別アノテーション無し → 認証ユーザーであれば全ショップの最新状態が見える
- ショップ別の絞り込み機能なし

### 7.3 フロントエンド
- `staleTime: Infinity` のため、画面開きっぱなしだと最新化されない
- 手動 refresh ボタン無し
- バッチ Chip は `startTime` のみ表示で `endTime` を使っていない (失敗ジョブの所要時間が見えない)
- ステップ番号・順序・ジョブ名・スケジュールはすべてハードコード — 設定 DB 化されていない

### 7.4 ビジネスルール
- 「ステップ完了」の絶対判定は無く、表示はあくまで「最終実行日時のスナップショット」
- 月次完了マーク (例: "2026年4月度クローズ済") は実装なし
- 月跨ぎで前月分のバッチを再実行した場合、ステップ 5 の Chip は「最新月」を表示するため前月分か当月分かは Chip からは判別できない

### 7.5 ジョブ名の正準名
ジョブ名は現状 **3 箇所**で手動同期されている:

1. `BatchController.JOB_DEFINITIONS` (`backend/src/main/java/jp/co/oda32/api/batch/BatchController.java:34`) — バッチ起動可能ジョブの正準カタログ
2. `AccountingStatusService` の `IN (...)` リスト (`AccountingStatusService.java:85-87`) — ワークフロー画面で監視するジョブ
3. フロントエンド `accounting-workflow.tsx:60-68` の `names` マッピング — UI ラベル

新しいジョブを監視対象に追加するには 3 ファイルすべての更新が必要で、抜け漏れが起きやすい。

> **TODO (SF-H05)**: `BatchJobCatalog` enum / `@ConfigurationProperties` 集約により上記 3 重定義を解消する。
> 集約後は `AccountingStatusService` が `BatchJobCatalog` の `monitoredInWorkflow=true` 行を使い、
> フロントは `/api/v1/batch/job-catalog` を取得してラベルマップを構築する。詳細は
> `claudedocs/triage-bgh-clusters.md` SF-H05 セクションを参照。

---

## 8. 課題 / TODO

### 8.1 機能追加候補
- **TODO**: `/finance/payment-mf-import` (買掛仕入MF変換、振込明細Excel取込) をワークフローに組み込む
  - 現状ステップとして登場せず、20日締めの実運用で使われる重要工程が抜けている
  - MEMORY 記載: `feature-payment-mf-import.md` 参照
- **TODO**: 手動 refresh ボタン / 自動 refetch (例えば 1 分ごと)
- **TODO**: 月度クローズボタン (「2026年4月度クローズ確定」) と DB 永続化
- **TODO**: ステップ完了判定の自動化 (例: `accountsPayableLatestMonth >= 当月20日` なら緑、そうでなければ琥珀)
- **TODO**: バッチ Chip の `endTime` 利用 → 所要時間表示
- **TODO**: ショップ単位フィルタ (現状全ショップ横断)

### 8.2 リファクタ候補
- **TODO**: `Map<String, Object>` 戻り値を専用 DTO (`AccountingStatusResponse`) に置き換え、型安全化
- **TODO**: NativeQuery を JPA Repository / `@Query` に集約 (現状 7 ジョブ名がサービス層で文字列ベタ書き)
- **TODO**: ジョブ名定数を `enum BatchJobName` で集約し、`AccountingStatusService` と `BatchChip` の `names` マップで共有
- **TODO**: フロントエンドの `WorkflowStep[]` 配列をサーバ提供 (i18n や画面横断のフロー再利用に備える) — ただし YAGNI 観点で必要性は低い
- **TODO**: `accounting-workflow.tsx` (363 行) は `WorkflowStepCard` / `ScheduleCard` / `StatusChip` / `BatchChip` をサブコンポーネントに分離可能

### 8.3 監視対象に追加すべきジョブ (確認要)
- **TODO: 確認** `cashbookImport` 系のバッチがあるか (現状 `t_cashbook_import_history` 経由で履歴取得しているが、UI 上 `BatchChip` には出ない)
- **TODO: 確認** `paymentMfImport` 系のバッチ名と監視要否
- **TODO: 確認** `salesJournalIntegration` の責務 (現状 UI 上は「売掛MF」と表示するが、ジョブ実装が `t_invoice` を読むのか別テーブルかは未確認)

### 8.4 ドキュメント
- **TODO: 確認** 旧 stock-app に対応する集約画面が無いか (`migration-guide.md` には言及無し)
- **TODO**: 本設計書を `MEMORY.md` の "Features" セクションに登録 (`feature-accounting-workflow.md` リンク)

---

## 付録 A: ファイル一覧

### Backend
- `backend/src/main/java/jp/co/oda32/domain/service/finance/AccountingStatusService.java` (120 行)
- `backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java:506-509` (`getAccountingStatus`)

### Frontend
- `frontend/app/(authenticated)/finance/workflow/page.tsx` (5 行のラッパ)
- `frontend/components/pages/finance/accounting-workflow.tsx` (363 行の本体)
- `frontend/components/layout/Sidebar.tsx:86` (サイドバー登録)

### 関連 (リンク先)
- `frontend/components/pages/finance/cashbook-import.tsx`
- `frontend/components/pages/finance/invoices.tsx`
- `frontend/components/pages/finance/accounts-payable.tsx`
- `frontend/components/pages/batch.tsx` (`/batch`)

### DB テーブル (参照のみ、本機能で書き込みなし)
- `t_cashbook_import_history` (出納帳取込履歴)
- `w_smile_purchase_output_file` (SMILE仕入ワーク)
- `t_smile_payment` (SMILE支払)
- `t_invoice` (請求書)
- `t_accounts_payable_summary` (買掛金サマリ)
- `batch_job_execution` / `batch_job_instance` (Spring Batch メタデータ)
