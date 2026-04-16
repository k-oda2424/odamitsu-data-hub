# 買掛金一覧画面 設計書

作成日: 2026-04-14
対象ブランチ: feat/bcart-shipping-input（派生予定: feat/accounts-payable）
旧システム参照: `C:\project\stock-app\src\main\resources\templates\finance\accountsPayable\`

## 1. 目的と業務上の意味

日々の仕入データ（`t_purchase` / `t_purchase_detail`）から、各**支払先**（`m_payment_supplier`）への**買掛帳**を月次で集計・更新し、支払先から届いた**請求書の金額と突合**して一致/不一致を確認する画面。

### 突合キー（支払先単位）
- `m_supplier`（商品ごとの仕入先）と `m_payment_supplier`（実際の支払先）は多:1 で紐づく（`m_supplier.payment_supplier_no`）
- 買掛は支払先単位で合算して `t_accounts_payable_summary` に格納（集計バッチが親子を解決）
- `t_accounts_payable_summary.supplier_no` / `supplier_code` は **実質 `payment_supplier_no` / `payment_supplier_code`**（カラム名は既存DBスキーマ踏襲）
- 請求書も支払先単位で届くため、突合キーは支払先コード
- 画面の「仕入先」列・プルダウンはすべて支払先を指す（Frontend は `usePaymentSuppliers` = `/masters/payment-suppliers` を使用）

- 買掛帳 = 「その月に、この仕入先にいくら払うか」を確定させる社内台帳
- 突合対象 = 仕入先から送られてくる請求書の金額（現状は SMILE 基幹に登録された支払データ `t_smile_payment` と照合）
- 不一致があれば経理担当者が原因を調査し、検証済み金額を手入力で確定 → マネーフォワードへ仕訳連携

現状の画面（`frontend/components/pages/finance/accounts-payable.tsx`）はページネーション一覧のみで、**業務上必要な「突合状況の把握」「検証入力」「再集計トリガ」が欠落**している。本設計でこれらを補う。

## 2. スコープ

### 本設計に含むもの
- 買掛金一覧画面の再設計（月次ビュー、検証ステータス表示、未検証/不一致件数サマリ）
- 詳細/検証入力ダイアログ（または詳細ページ）
- 必要な API の追加・変更
- 再集計バッチ起動ボタン（月次の買掛帳更新）

### 含まないもの
- バッチ本体のロジック変更（`AccountsPayableSummaryTasklet` / `AccountsPayableVerificationTasklet` は既存のまま）
- マネーフォワード連携仕訳出力（別画面 `/finance/cashbook-import` 系の責務）
- 売掛金（`t_invoice`）の機能

## 3. 現状分析

### 既存アセット（再利用）

**バックエンド**
- Entity: `TAccountsPayableSummary` (PK: `shopNo`, `supplierNo`, `transactionMonth`, `taxRate`)
  - `taxIncludedAmountChange` / `taxExcludedAmountChange` = 社内買掛金額（仕入データから算出）
  - `taxIncludedAmount` / `taxExcludedAmount` = 検証済み支払額（SMILE / 手入力）
  - `paymentDifference` = 差額
  - `verificationResult` = 1:一致 / 0:不一致 / null:未検証
  - `mfExportEnabled` = MF 連携可否
- Service: `TAccountsPayableSummaryService`（`findPaged` / `getByPK` / `save` / `findByTransactionMonth` あり）
- Repository: `TAccountsPayableSummaryRepository`
- Controller: `FinanceController#listAccountsPayable`（一覧 GET のみ）
- バッチ:
  - `AccountsPayableSummaryJob` — `t_purchase_detail` 集計 → `t_accounts_payable_summary` UPSERT
  - `AccountsPayableVerificationJob` — `t_smile_payment` と突合 → `verificationResult` / `paymentDifference` 更新
  - `AccountsPayableAggregationJob` / `PurchaseJournalIntegrationJob` — MF 連携用

**フロントエンド**
- 既存の `accounts-payable.tsx`: DataTable + ページネーション + `formatCurrency`
- 共通: `PageHeader`, `SearchableSelect`, `Dialog`, `Badge`, `sonner`, `TanStack Query`

### ギャップ

| # | 項目 | 現状 | あるべき姿 |
|---|---|---|---|
| G1 | 取引月の指定 | 不可（全期間が混ざる） | `month` picker で指定、デフォルト=当月20日締め |
| G2 | 検証ステータス表示 | 未表示 | Badge（一致/不一致/未検証） + 差額の色分け |
| G3 | 突合サマリ | 無し | 未検証件数・不一致件数のアラート |
| G4 | 仕入先名 | `supplierName` を DTO に含んでいる前提だが、Response に無い | `supplierName` / `supplierCode` を `AccountsPayableResponse` に追加 |
| G5 | 支払額手動入力 | 無し | 詳細ダイアログで検証済み金額＆備考入力 → PUT |
| G6 | 再集計・再検証トリガ | 無し | 2 つのバッチ起動ボタン（管理者のみ） |
| G7 | MF 出力可否切替 | 無し | `mfExportEnabled` の ON/OFF トグル |
| G8 | 一覧 API の絞り込み | `shopNo`, `supplierNo` のみ | `transactionMonth`, `verificationResult` を追加 |

## 4. 画面設計

### 4.1 ルート
- 一覧: `/finance/accounts-payable`
- 既存 `app/(authenticated)/finance/accounts-payable/page.tsx` を再利用
- 詳細はダイアログ（モーダル）で表示。別ルートは切らない（URL 共有性より、一覧スクロール状態保持を優先）

### 4.2 ページレイアウト

```
┌──────────────────────────────────────────────────────────┐
│ 買掛金一覧                          [再集計] [再検証]      │ PageHeader + actions
├──────────────────────────────────────────────────────────┤
│ 取引月 [2026-03 ▼] 仕入先 [すべて ▼] 検証 [すべて/未検証/不一致] [検索] │
├──────────────────────────────────────────────────────────┤
│ ⚠ 未検証 5件 / 不一致 2件（差額合計 -3,240円）                │ サマリアラート
├──────────────────────────────────────────────────────────┤
│ 仕入先Code 仕入先名      税率  買掛(税込) SMILE支払 差額   状態   MF  操作  │
│ 0001      ○○商事      10%   1,234,000 1,234,000   0   [一致] ✓  [詳細]│
│ 0002      △△工業      10%     543,210   540,000  3,210 [不一致]✓  [詳細]│
│ 0003      □□商店       8%     108,000      null  null  [未検証]✓  [検証]│
│ …                                                                        │
├──────────────────────────────────────────────────────────┤
│ ページネーション                                                          │
└──────────────────────────────────────────────────────────┘
```

### 4.3 検索条件

| 項目 | 型 | 必須 | デフォルト |
|---|---|---|---|
| 取引月 | `<input type="month">` | ○ | 当月20日（20日未満なら前月20日） |
| 仕入先 | `SearchableSelect` | - | すべて |
| 検証ステータス | `Select` (all/unverified/unmatched/matched) | - | すべて |

admin (`shopNo=0`) の場合はショップセレクトも表示（既存ルールに従う）。

### 4.4 サマリアラート

取引月内で集計:
- 未検証件数（`verificationResult IS NULL`）
- 不一致件数（`verificationResult = 0`）
- 不一致の差額合計

未検証 or 不一致が 0 件以上ならオレンジ（警告）系 Alert で表示。

### 4.5 一覧カラム

| カラム | ソート | 備考 |
|---|---|---|
| 仕入先コード | ○ | `supplierCode`（支払先 = `MPaymentSupplier`）。デフォルトソート ASC |
| 仕入先名 | ○ | `supplierName` |
| 税率 | - | `10%` / `8%` |
| 税抜 / 消費税 | - | 1列2段表示（上:税抜通常、下:消費税薄色小文字） |
| 振込明細額 | - | `verifiedAmount`（振込明細Excel or 手動検証で入力された税込請求額）、null は「-」 |
| SMILE支払額 | - | `taxIncludedAmount`、null は「-」 |
| 差額 | - | `paymentDifference`、不一致は赤 |
| 検証状態 | - | Badge: 一致(緑) / 不一致(赤) / 未検証(グレー)。`verified_manually=true` なら「手動」サブバッジ付与 |
| MF出力 | - | Switch: on/off（`mfExportEnabled`） |
| 操作 | - | 未検証→「検証」／検証済→「詳細」ボタン |

サーバサイドページネーション（既存 `Paginated<T>` 再利用）。1ページ 50件。
デフォルトソート: `supplierCode ASC`（`PageableDefault` をバックエンド Controller に設定）。

### 4.6 詳細 / 検証ダイアログ

クリックで Dialog を開く。以下を表示・編集:

**表示（読取専用）**
- 仕入先コード / 仕入先名
- 取引月 / 税率
- 買掛金額（税込 / 税抜）
- SMILE 支払額（税込 / 税抜）
- 差額
- 前回備考（`verification_note`、あれば）
- 手動確定状態 Badge（`verified_manually = true` のとき「手動確定済」緑表示）

**入力**
- 検証済み支払額（税込） `number`（初期値: `taxIncludedAmount ?? taxIncludedAmountChange`）
- 備考 `textarea`（500字、DB永続化）

**ボタン**
- 「更新」: PUT → 一致判定（差額 ≤ 100円 で一致） → `verified_manually=true` をセット → 一覧 refetch、toast
- 「手動確定解除」: admin のみ表示。`verified_manually=false` に戻し、次回 SMILE 再検証バッチで上書き可能にする
- 「キャンセル」

判定ロジックは旧 stock-app と同じ（税抜金額を税率から逆算、差額 abs ≤ 100 で一致）。

### 4.7 アクションボタン

`PageHeader` の actions に 3 つ（admin のみ表示）:
- **仕入明細取込(SMILE)**: `POST /api/v1/batch/execute/purchaseFileImport`
  - `m_shop_linked_file` 登録の全 CSV を取込（`purchase_import.csv` + `purchase_import2_*.csv`）
  - `inputFile` / `shopNo` / `targetDate` パラメータ不要
- **再集計**: `POST /api/v1/batch/execute/accountsPayableAggregation?targetDate=yyyyMMdd`
  - 集計のみの軽量ジョブ（従来の `accountsPayableSummary` から変更）。SMILE支払取込・検証は含まない
  - Init ステップ（検証フラグリセット）→ 集計ステップの2ステップ構成
- **再検証(SMILE)**: `POST /api/v1/batch/execute/accountsPayableVerification?targetDate=yyyyMMdd`
  - SMILE支払取込ステップを含むため `inputFile` 自動付与（`input/smile_payment_import.csv`）
  - `verified_manually = true` の行は突合対象からスキップされる

#### バッチ起動状態の可視化

ボタン押下後:
1. 3秒後にステータスポーリングを開始（`GET /api/v1/batch/status/{jobName}`、5秒間隔）
2. 実行中: `Loader2` スピナー + ボタン disabled
3. 完了: `CheckCircle2` 緑。一覧を自動再取得（TanStack Query invalidate）
4. FAILED: `XCircle` 赤 + `exitMessage` をエラートーストで表示

バッチエンドポイントは既存 `BatchController#execute/{jobName}` を流用。
多重実行時は `ThreadPoolTaskExecutor` + `AbortPolicy` により 429 Too Many Requests が返り、
フロントで「他のバッチが実行中」トーストを表示。

## 5. API 設計

### 5.1 一覧取得（変更）

`GET /api/v1/finance/accounts-payable`

| パラメータ | 型 | 必須 | 説明 |
|---|---|---|---|
| `shopNo` | Integer | - | admin のみ指定可 |
| `transactionMonth` | `yyyy-MM-dd` | - | 未指定時は当月20日 |
| `supplierNo` | Integer | - | 支払先 |
| `verificationResult` | `null\|0\|1\|all` | - | `unverified`/`unmatched`/`matched`/`all` |
| `page` / `size` / `sort` | - | - | 既存 |

`AccountsPayableResponse` に以下を追加:
- `supplierCode: String`
- `supplierName: String`
- `verificationResult: Integer` (既存 Entity には有るが DTO に無い)
- `mfExportEnabled: Boolean`

### 5.2 サマリ取得（新規）

`GET /api/v1/finance/accounts-payable/summary?transactionMonth=yyyy-MM-dd`

Response:
```json
{
  "transactionMonth": "2026-03-20",
  "totalCount": 42,
  "unverifiedCount": 5,
  "unmatchedCount": 2,
  "unmatchedDifferenceSum": -3240
}
```

### 5.3 検証更新（新規）

`PUT /api/v1/finance/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/verify`

Request:
```json
{
  "verifiedAmount": 1234000,
  "note": "請求書No.A-001で確認"
}
```

Response: 更新後の `AccountsPayableResponse`。
- 差額・`taxExcludedAmount`・`verificationResult` をサーバ側で算出（旧ロジック踏襲、閾値100円）
- `verification_note` に保存、`verified_manually=true` をセット
- `@Transactional`

### 5.3.1 手動確定解除（新規）

`DELETE /api/v1/finance/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/manual-lock`

- admin のみ (`hasRole('ADMIN')`)
- `verified_manually = false` に戻す（次回 SMILE 再検証バッチの上書き対象に戻る）
- `verification_note` / `taxIncludedAmount` などの値は保持（必要なら手動で `/verify` をやり直す）

### 5.4 MF出力可否トグル（新規）

`PATCH /api/v1/finance/accounts-payable/{shopNo}/{supplierNo}/{transactionMonth}/{taxRate}/mf-export`

Request: `{ "enabled": true }`

### 5.5 再集計 / 再検証

既存 `BatchController`（`api/batch/BatchController.java`）の汎用バッチ起動基盤を再利用する。専用エンドポイントは設ける必要がない。

- `POST /api/v1/batch/execute/accountsPayableSummary` — 買掛金集計バッチ起動
- `POST /api/v1/batch/execute/accountsPayableVerification` — SMILE再検証バッチ起動

方針:
- 既存実装で `@PreAuthorize("hasRole('ADMIN')")` 済み
- **非同期実行**: `ThreadPoolTaskExecutor`（corePool=1, maxPool=2, queue=5）で即座に `202 Accepted` を返す
- **多重実行ガード**: `RejectedExecutionHandler.AbortPolicy` によりキュー溢れ時に `429 Too Many Requests` + 「同時実行数の上限に達しています」メッセージ
- ステータス確認: `GET /api/v1/batch/status/{jobName}` で最新実行の `COMPLETED`/`FAILED` を取得可能
- フロントは 429 を ApiError として捕捉し、toast で「他のバッチが実行中」メッセージを表示

## 6. バックエンド変更点

### 6.1 DB スキーマ（Liquibase）

`t_accounts_payable_summary` に 3 カラム追加:

```sql
ALTER TABLE t_accounts_payable_summary
  ADD COLUMN verified_manually BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN verification_note VARCHAR(500),
  ADD COLUMN verified_amount   NUMERIC;
```

`TAccountsPayableSummary` Entity に対応フィールド追加:
- `@Column(name = "verified_manually", nullable = false) @ColumnDefault("false") private Boolean verifiedManually;`
- `@Column(name = "verification_note") private String verificationNote;`
- `@Column(name = "verified_amount") private BigDecimal verifiedAmount;`

**`verifiedAmount` の用途**:
- 振込明細Excel / 手動検証で提示された税込請求額を保持する
- `tax_included_amount`（MF出力用スナップショット）とは独立管理
- 再集計バッチでは上書きされない（手動/Excel検証の結果を保持するため）
- 100円閾値一致判定は `verifiedAmount` と `taxIncludedAmountChange` を比較
- 手動検証 (`TAccountsPayableSummaryService#applyVerification`) と
  振込明細MF一括検証 (`PaymentMfImportService#applyVerification`) の
  どちらも同一カラムに書込（税率別の複数行には同一値を同期セット）

### 6.2 DTO
- `AccountsPayableResponse` に `supplierCode`, `supplierName`, `verificationResult`, `mfExportEnabled`, `verifiedManually`, `verificationNote` 追加
  - `MSupplier` は `paymentSupplier` 経由で取得（`supplier_code` と `supplier_name` は `MPaymentSupplier` 側）
  - 一覧取得時に N+1 を避けるため、Service で supplier をバルク fetch して Map 渡し
- 新規 `AccountsPayableVerifyRequest`（`verifiedAmount`, `note` 最大500字）
- 新規 `AccountsPayableSummaryResponse`（5.2節）
- 新規 `AccountsPayableRecalcRequest` / `AccountsPayableReverifyRequest`

### 6.3 Service
`TAccountsPayableSummaryService` に以下を追加:
- `findPaged(shopNo, supplierNo, transactionMonth, verificationResult, pageable)` — Specification を拡張
- `summary(shopNo, transactionMonth)` — カウントと差額合計
- `verify(pk, verifiedAmount, note)` — 差額計算＆判定 + `verifiedManually=true` + `verificationNote` 保存
- `releaseManualLock(pk)` — `verifiedManually=false`
- `updateMfExport(pk, enabled)`

### 6.4 Controller
`FinanceController` に 6 エンドポイント追加（一覧は既存を拡張）。再集計/再検証は `BatchController` に逃がす選択肢もあるが、経理ドメインの画面操作なので `FinanceController` に置く。同期起動時の多重実行ガードも Service 層で実装。

### 6.5 バッチ呼び出し & 手入力保護
再集計/再検証は既存 `BatchController#execute(jobName)` を流用。専用 Controller 追加なし。
- ジョブ Bean 名: `accountsPayableSummaryJob`, `accountsPayableVerificationJob`（既存）
- 多重実行ガード: `ThreadPoolTaskExecutor` + `AbortPolicy` → 429 Too Many Requests（既存）

**`SmilePaymentVerifier` の改修**:
- SMILE 支払額との突合対象を取得する際、`verified_manually = true` の行は**スキップ**
- スキップ件数は処理ログに `INFO` で出力（運用時の確認用）
- 既存の `AccountsPayableSummaryTasklet` 側は `taxIncludedAmountChange` / `taxExcludedAmountChange` のみ更新するため、手入力保護と独立（集計値は常に最新）

## 7. フロントエンド変更点

### 7.1 ファイル構成
```
frontend/
├── app/(authenticated)/finance/accounts-payable/page.tsx      (既存、変更なし)
├── components/pages/finance/
│   ├── accounts-payable.tsx                                    (リファクタ)
│   └── accounts-payable/
│       ├── SearchForm.tsx
│       ├── SummaryAlert.tsx
│       ├── VerifyDialog.tsx
│       └── ActionButtons.tsx
└── types/accounts-payable.ts                                   (新規)
```

### 7.2 主要型
```ts
// types/accounts-payable.ts
export interface AccountsPayable {
  shopNo: number
  supplierNo: number
  supplierCode: string
  supplierName: string
  transactionMonth: string          // yyyy-MM-dd
  taxRate: number
  taxIncludedAmountChange: number
  taxExcludedAmountChange: number
  taxIncludedAmount: number | null  // SMILE 支払額（MF出力スナップショット）
  taxExcludedAmount: number | null
  verifiedAmount: number | null     // 振込明細/手入力の税込請求額（再集計で上書きされない）
  paymentDifference: number | null
  verificationResult: 0 | 1 | null
  mfExportEnabled: boolean
  verifiedManually: boolean
  verificationNote: string | null
}

export interface AccountsPayableSummary {
  transactionMonth: string
  totalCount: number
  unverifiedCount: number
  unmatchedCount: number
  unmatchedDifferenceSum: number
}

export const VERIFICATION_FILTER = {
  all: 'すべて',
  unverified: '未検証',
  unmatched: '不一致',
  matched: '一致',
} as const
```

### 7.3 TanStack Query
- `['accounts-payable', params]`: 一覧
- `['accounts-payable-summary', transactionMonth, shopNo]`: サマリ（並列）
- 変更系 mutation は `invalidateQueries` で両方 refetch

### 7.4 初期検索制御
`searchParams` を `null` 初期化せず、デフォルト（当月20日）をクライアント側で計算して即検索。
理由: 買掛金一覧は「開いたらすぐ今月の状況を見たい」UX。一覧が空でも OK。

## 8. セキュリティ / 権限

- 一覧・詳細・検証入力・MF トグル: 認証済ユーザー（既存 `@PreAuthorize("isAuthenticated()")`）
- 再集計・再検証: admin (`shopNo=0`) のみ。Controller で `hasRole('ADMIN')`、フロントでも admin 以外はボタン非表示

shop フィルタは `LoginUserUtil.resolveEffectiveShopNo` を使用（既存）。

## 9. エッジケース

| ケース | 動作 |
|---|---|
| 取引月にデータ無し | 空のテーブル + サマリは 0 件。エラーにしない |
| `taxIncludedAmountChange = 0` なのに SMILE 支払あり | 差額は SMILE 額そのまま。不一致として表示 |
| 差額 ≤ 100円 | 一致（端数吸収、旧ロジック踏襲） |
| 手入力検証後、バッチで再検証を走らせた | `verified_manually=true` の行は SMILE 再検証からスキップされ保護される。解除は admin が「手動確定解除」ボタンで実行 |
| 再集計/再検証を連打 | `JobExplorer` が RUNNING を検知し 409 Conflict。フロントは toast で通知 |
| 税率が `null` のデータ | 既存エンティティ上は PK なので null 不可のはず。万一 null ならスキップ |
| supplierNo に紐づく MSupplier が無い | `supplierName` は「不明」と表示（旧 HTML と同様） |

## 10. テスト計画（概要）

### バックエンド
- Service: Specification ビルド（4パラメータの組み合わせ）、`verify()` の差額計算・税抜逆算・100円閾値・`verifiedManually` セット・`verificationNote` 保存
- `releaseManualLock` で `verifiedManually=false` に戻ることの確認
- `SmilePaymentVerifier` が `verified_manually=true` の行をスキップすること（単体テスト）
- Controller: 各エンドポイントの 200/400/403（admin 判定）/ 409（多重実行）
- バッチ起動エンドポイント: `JobExplorer` のモックで RUNNING 判定検証

### フロントエンド E2E (Playwright)
- `e2e/accounts-payable.spec.ts`
  1. 一覧表示（モック）
  2. 取引月切替で再検索
  3. 未検証→検証ダイアログ→備考入力＆更新→状態が「一致」+「手動」バッジに変わる
  4. 検証済み行を再度開くと前回備考が表示される
  5. 不一致の差額が赤字
  6. サマリアラートの件数
  7. admin のみ再集計ボタン & 手動確定解除ボタン表示
  8. 手動確定解除 → 再検証 → SMILE 値で上書きされることを確認
  9. MF 出力トグル
  10. 再集計連打で 409 エラー表示
  11. 実バックエンド疎通1パス（既存ルール遵守）

## 11. 実装順序

1. Liquibase: `verified_manually` + `verification_note` カラム追加、Entity 反映
2. DTO 拡張 + 新規 Request/Response 追加
3. Service に `verify()` / `releaseManualLock()` / `summary()` / Spec 拡張
4. `SmilePaymentVerifier` に手入力保護スキップロジック追加 + 単体テスト
5. Controller: `/summary`, `/verify`, `/manual-lock`, `/mf-export`, `/recalculate`, `/reverify` 追加（多重実行ガード含む）
6. フロント: 型定義 + 一覧リファクタ + サマリ表示 + 検証ダイアログ + 手動確定解除 + 再集計/再検証ボタン
7. E2E + 実バックエンド疎通確認
8. 実データでバッチ実測 → 5秒超なら非同期化検討
9. `/code-review` → マージ

## 12. 決定事項サマリ

| 項目 | 決定 |
|---|---|
| 締め日 | 20日固定（旧システム踏襲） |
| 買掛管理ショップ | 第1事業部(shop_no=1)に集約。shop_no=2 の仕入も shop_no=1 の買掛に合算（`FinanceConstants.ACCOUNTS_PAYABLE_SHOP_NO`） |
| 手入力保護 | `verified_manually` フラグでバッチ上書きをブロック、admin のみ解除可。`SmilePaymentVerifier` は true の行をスキップ |
| 備考 | `verification_note` (VARCHAR 500) で DB 永続化 |
| 検証済請求額 | `verified_amount` カラムを追加。振込明細MF一括検証・手動検証ともに同一カラムに書込 |
| バッチ起動方式 | 非同期実行 (`ThreadPoolTaskExecutor`) + `AbortPolicy` → 429 Too Many Requests。3秒後ポーリング5秒間隔で状態可視化 |
| 再集計ジョブ | `accountsPayableAggregation`（軽量版、Init+集計の2ステップ）に変更。CSV不要 |
| 仕入取込ボタン | 「仕入明細取込(SMILE)」で `purchaseFileImport` 起動。`m_shop_linked_file` 登録全ファイル対応 |
