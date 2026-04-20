# 売掛金MF連携（売上帳） 設計書

作成日: 2026-04-17
対象ブランチ: feat/accounts-receivable-mf（新設予定）
関連対称機能: `/finance/accounts-payable`（買掛金一覧） + `/finance/payment-mf-import`（買掛仕入MF変換）

## 1. 目的と業務上の意味

日々のSMILE受注連携で取得した `t_order_detail` から、**請求先**（`m_partner.invoice_partner_code` でまとめた単位）への**売上帳**を月次で集計し、SMILE から出力した**請求書一覧と突合**して一致確認したうえで、マネーフォワードクラウド会計向けの仕訳CSV（売掛金/売上高）を出力する画面。

### 突合キー
- 集計は「店舗 × `m_partner.invoice_partner_code` × 税率 × ゴミ袋フラグ × (都度現金時のみ注文番号)」
- 検証は「店舗 × 請求先コード × 締め日文字列（`YYYY/MM/末` or `YYYY/MM/DD`）」で `t_invoice` と突合
- 差額 ≤ 3円: 一致扱い（請求書金額に按分）、差額 > 3円: 不一致
- 「上様」(999999, 得意先コード7桁以上) は請求書金額で常に上書き
- イズミ(得意先000231): 当月15日締め + 前月が四半期特殊月(2/5/8/11月)なら前月末締めの請求書も検証対象に合算

### 買掛側との対称構造

| 要素 | 買掛側 | 売掛側 |
|---|---|---|
| 集計元 | `t_purchase_detail` | `t_order_detail` |
| サマリ | `t_accounts_payable_summary` | `t_accounts_receivable_summary` |
| 突合先 | `t_smile_payment`（SMILE支払情報） | `t_invoice`（SMILE請求書） |
| 集計単位 | `m_payment_supplier`（支払先） | `m_partner.invoice_partner_code`（請求先） |
| 検証サービス | `SmilePaymentVerifier` | **`InvoiceVerifier`**（新設） |
| 許容誤差 | 5円 | 3円（既存維持） |
| 画面 | `/finance/accounts-payable` | **`/finance/accounts-receivable`**（新設） |
| 仕訳出力 | 買掛金/仕入（PurchaseJournalIntegration） | 売掛金/売上（SalesJournalIntegration 既存流用） |
| 仕訳CSV DL | 画面からブラウザDL（v2） | 画面からブラウザDL（新設） |

## 2. スコープ

### 本設計に含むもの

- `t_accounts_receivable_summary` への運用系カラム追加（7カラム）
- `InvoiceVerifier` Service 新設（既存 Tasklet から検証ロジック抽出）
- `TAccountsReceivableSummaryTasklet` 改修
  - 締め日タイプ指定パラメータ追加（15日/20日/月末/すべて）
  - 検証NG行も保存（現状は破棄）
  - 手動確定行の保護
- `salesJournalIntegrationJob` 既存流用（`mf_export_enabled=true` のみ対象に改修）
- `/finance/accounts-receivable` 画面新設
  - 一覧表示（検証結果バッジ、差額、請求書金額）
  - 手動確定ダイアログ（行単位）
  - 再集計ボタン（締め日選択）
  - 請求書アップロードボタン（既存 `InvoiceImportDialog` 再利用）
  - 一括検証ボタン
  - 検証済みMF CSV出力ボタン（ブラウザDL）
- Controller `AccountsReceivableController` 新設
- E2E テスト

### 含まないもの

- **定時バッチ設定**: 画面からの手動起動のみ（Q13-2 (d) で確定）
- **売上値引 / 雑所得**: Phase 1 では扱わず、将来の入金処理画面で実装（⑭ 案 D で確定）
- **MF API/MCP 照合機能**: Phase 2 以降で `t_mf_export_lot` と併せて実装
- **請求書CSVインポート機能の変更**: 現状維持（`/finance/invoices` 画面 + 新規に `/finance/accounts-receivable` 画面からも起動可能にするのみ）
- **買掛側・現金出納帳側のロット管理**: Phase 2 で全体同時に対応

## 3. 現状分析

### 既存アセット（再利用）

**バックエンド**
- Entity: `TAccountsReceivableSummary` (PK: `shopNo`, `partnerNo`, `transactionMonth`, `taxRate`, `isOtakeGarbageBag`)
  - `taxIncludedAmountChange` / `taxExcludedAmountChange` = 集計計算値（tmp）
  - `taxIncludedAmount` / `taxExcludedAmount` = CSV出力済み確定値（現行は CSV 出力時に change からコピー）
  - `cutoffDate` = 実際の締め日
  - `orderNo` = 都度現金払い時の注文番号
- Service: `TAccountsReceivableSummaryService`（`findByDateRange`, `save` 等）
- Repository: `TAccountsReceivableSummaryRepository`
- バッチ:
  - `TAccountsReceivableSummaryTasklet` / `AccountsReceivableSummaryConfig` - 集計（`t_order_detail` → `t_accounts_receivable_summary`）
  - `AccountsReceivableToSalesJournalTasklet` / `SalesJournalIntegrationConfig` - CSV出力（Job名: `salesJournalIntegration`）
- 請求書: `TInvoice` Entity, `TInvoiceService.findByShopNoAndPartnerCodeAndClosingDate`
- 勘定科目マスタ: `MfAccountMaster` / `MfAccountMasterService.findByFinancialStatementItemAndAccountName`
- 請求書インポート: `InvoiceImportService`, `InvoiceImportDialog` (Frontend)

**フロントエンド**
- 請求書一覧: `/finance/invoices`（`invoices.tsx`, `InvoiceImportDialog.tsx`）
- 買掛側参考: `accounts-payable.tsx`, `payment-mf-import.tsx`, `BulkVerifyDialog.tsx`, `VerifyDialog.tsx`
- 共通: `PageHeader`, `SearchableSelect`, `DataTable`, `ConfirmDialog`, `sonner`, `TanStack Query`

### ギャップ（買掛側との差分）

| # | 項目 | 現状 | あるべき姿 |
|---|---|---|---|
| G1 | 売掛金一覧画面 | 未実装 | 新設 |
| G2 | 検証結果永続化 | 検証NGは保存されない（ログのみ） | 検証NGも保存、`verification_result` で記録 |
| G3 | 手動確定 | 無し | 行単位の手動確定ダイアログ |
| G4 | 備考 | 無し | `verification_note` で永続化 |
| G5 | MF出力可否切替 | 無し | `mf_export_enabled` トグル |
| G6 | 締め日指定の再集計 | 1引数で全グループ実行 | 締め日タイプ指定で個別実行可能 |
| G7 | 画面からCSV出力 | IntelliJ 手動起動のみ | ボタン1クリックでブラウザDL |
| G8 | 検証ロジック分離 | Tasklet 内 inline | `InvoiceVerifier` Service に抽出 |

## 4. データモデル変更

### 4.1 追加カラム

```sql
ALTER TABLE t_accounts_receivable_summary
  ADD COLUMN verification_result     INTEGER,              -- 1:一致, 0:不一致, NULL:未検証
  ADD COLUMN mf_export_enabled       BOOLEAN DEFAULT FALSE,-- MF出力対象
  ADD COLUMN verified_manually       BOOLEAN DEFAULT FALSE,-- 手動確定フラグ
  ADD COLUMN verification_note       TEXT,                 -- 備考
  ADD COLUMN invoice_amount          NUMERIC(15, 2),       -- 請求書金額（税込）
  ADD COLUMN verification_difference NUMERIC(15, 2),       -- 差額
  ADD COLUMN invoice_no              BIGINT;               -- 突合した請求書No
```

SQL ファイル: `backend/src/main/resources/sql/alter_accounts_receivable_summary_add_verification_fields.sql`

### 4.2 既存カラムの意味整理（(c) 案確定）

| カラム | 意味 |
|---|---|
| `tax_included_amount_change` | 毎回の集計結果（=計算値） |
| `tax_included_amount` | **検証/手動確定済みの連携金額**（CSV出力の元値） |
| `tax_excluded_amount_change` | 同上（税抜） |
| `tax_excluded_amount` | 同上（税抜） |

- 再集計時: `*_change` を上書き、`*_amount` は手動確定行のみ保持（非手動は上書き）
- 手動確定時: `*_amount` = `*_change` として確定、`verified_manually=true`
- CSV出力時: `mf_export_enabled=true` 行の `*_amount` を使用

## 5. バックエンド設計

### 5.1 `InvoiceVerifier` Service（新設）

```java
package jp.co.oda32.batch.finance.service;

@Service
public class InvoiceVerifier {
    /**
     * 売掛金集計結果と請求書を突合して検証結果を返します。
     * 差額が3円未満の場合は請求書金額に按分、3円以上の場合は不一致扱い。
     * 手動確定行 (verified_manually=true) はスキップ。
     * 「上様」(999999) は請求書金額で常に上書き。
     * イズミ(000231) の四半期特殊月は当月15日締め+前月末締めの請求書を合算。
     */
    public Map<InvoiceValidationKey, InvoiceVerificationResult> verify(
        List<TAccountsReceivableSummary> summaries,
        Map<InvoiceValidationKey, TInvoice> invoiceMap,
        LocalDate targetPeriodEndDate
    );
}
```

- 責務: 突合ロジックのみ
- DB保存は呼び出し元（Tasklet or Controller）
- 買掛側 `SmilePaymentVerifier` のインターフェースと対称（`verifyWithSmilePayment` 相当）

### 5.2 `TAccountsReceivableSummaryTasklet` 改修

#### ジョブパラメータ追加
```java
@Value("#{jobParameters['targetDate']}")
private String targetDate;

@Value("#{jobParameters['cutoffType']}")
private String cutoffType; // "15" | "20" | "month_end" | "all"（省略時は "all"）
```

#### 処理フロー改修
1. `cutoffType` に応じて処理グループを絞る
   - `"15"`: `process15thCutoffPartners` のみ
   - `"20"`: `process20thCutoffPartners` のみ
   - `"month_end"`: `processMonthEndCutoffPartners` のみ（都度現金含む）
   - `"all"`: 全グループ（現行動作）
2. 検証ロジックは `InvoiceVerifier` に委譲
3. **検証結果を全件保存**:
   - 一致 → `verification_result=1`, `mf_export_enabled=true`
   - 不一致 → `verification_result=0`, `mf_export_enabled=false`
   - 請求書なし → `verification_result=0`, `mf_export_enabled=false`, `invoice_no=null`
4. **手動確定行の保護**:
   - `verified_manually=true` の行は `*_amount`, `*_change`, 検証結果を上書きしない
   - ただし、請求書との突合情報（`invoice_no`, `invoice_amount`, `verification_difference`）は更新してログ表示する

### 5.3 `AccountsReceivableToSalesJournalTasklet` 改修

```java
// 既存: 全件取得
List<TAccountsReceivableSummary> summaries = tAccountsReceivableSummaryService.findByDateRange(from, to);

// 改修: mf_export_enabled=true のみ
List<TAccountsReceivableSummary> summaries = 
    tAccountsReceivableSummaryService.findByDateRangeAndMfExportEnabled(from, to, true);
```

- CSV出力後の `setTaxIncludedAmount(summary.getTaxIncludedAmountChange())` コピー処理は維持（CSV出力済みマーカーの意味合い）

### 5.4 `AccountsReceivableController`（新設）

エンドポイント（買掛側 `FinanceController#listAccountsPayable` 系と対称）:

| Method | Path | 用途 |
|---|---|---|
| GET | `/api/v1/finance/accounts-receivable` | 一覧取得（ページング、検索条件フィルタ） |
| GET | `/api/v1/finance/accounts-receivable/summary` | サマリ（未検証・不一致件数、差額合計） |
| POST | `/api/v1/finance/accounts-receivable/aggregate` | 再集計バッチ起動（`cutoffType`, `targetDate`） |
| POST | `/api/v1/finance/accounts-receivable/bulk-verify` | 一括検証（画面の検索条件範囲） |
| PUT | `/api/v1/finance/accounts-receivable/{pk}/verify` | 手動確定（行単位） |
| DELETE | `/api/v1/finance/accounts-receivable/{pk}/manual-lock` | 手動確定解除 |
| PATCH | `/api/v1/finance/accounts-receivable/{pk}/mf-export` | MF出力フラグON/OFF |
| GET | `/api/v1/finance/accounts-receivable/export-mf-csv` | CSV ダウンロード |

PK は `shopNo/partnerNo/transactionMonth/taxRate/isOtakeGarbageBag` の5要素複合。URL は `/{shopNo}/{partnerNo}/{transactionMonth}/{taxRate}/{isOtakeGarbageBag}` 形式。

#### CSV DL エンドポイント仕様
```
GET /api/v1/finance/accounts-receivable/export-mf-csv
  ?shopNo={shopNo}
  &fromDate={yyyyMMdd}
  &toDate={yyyyMMdd}
  &initialTransactionNo={number, optional default=1001}
  
Response:
  Content-Type: text/csv; charset=Shift_JIS (CP932)
  Content-Disposition: attachment; filename="accounts_receivable_to_sales_journal_{yyyyMMdd}_{yyyyMMdd}.csv"
  Body: MFJournalCsv ヘッダ + 売掛金/売上仕訳行（`mf_export_enabled=true` のみ）
```

CSV生成ロジックは既存 `AccountsReceivableToSalesJournalTasklet#writeToFile` のロジックを Service メソッドに抽出して再利用（Job からもController からも呼べるように）。

## 6. フロントエンド設計

### 6.1 ルート

- 一覧: `/finance/accounts-receivable`
- ページファイル: `app/(authenticated)/finance/accounts-receivable/page.tsx`
- コンポーネント: `components/pages/finance/accounts-receivable.tsx`

### 6.2 ページレイアウト

```
┌──────────────────────────────────────────────────────────────────────┐
│ 売掛金一覧                       [再集計][請求書取込][一括検証][MF CSV出力] │ PageHeader + actions
├──────────────────────────────────────────────────────────────────────┤
│ 店舗 [▼] 期間 [2026-03-21]〜[2026-04-20] 得意先 [▼] 検証 [すべて/未検証/不一致] │
│ [検索]                                                                  │
├──────────────────────────────────────────────────────────────────────┤
│ ⚠ 未検証 3件 / 不一致 1件（差額合計 +1,200円）                            │ サマリアラート
├──────────────────────────────────────────────────────────────────────┤
│ 検証 | 店舗 | 得意先Code | 得意先名 | 締め日 | 取引日 | 税率 |            │
│      税込金額 | 税抜金額 | 請求書金額 | 差額 | 手動 | MF | 備考 | 請求書No │
│ [一致]  1  000231  イズミ     15日  2026/04/15 10% 543,000 493,636 543,000 0  ◯ ✓ -       #1234  │
│ [不一致] 1  301491  クリーンラボ 月末 2026/04/30 10% 120,000 109,090 123,240 -3,240 - - -  #1240  │
│ [未検証] 1  000100  ○○商会   20日  2026/04/20 10% 87,500 79,545 -         -    -  - -      -   │
├──────────────────────────────────────────────────────────────────────┤
│ ページネーション                                                         │
└──────────────────────────────────────────────────────────────────────┘
```

### 6.3 再集計ダイアログ（案 P: ラジオボタン）

```
┌──────────────────────────────────────┐
│ 再集計                                │
├──────────────────────────────────────┤
│ 対象日: [2026-04-20] (yyyy-MM-dd)     │
│                                        │
│ 締め日タイプ:                          │
│   ● すべて                            │
│   ○ 15日締め                          │
│   ○ 20日締め                          │
│   ○ 月末締め                          │
│                                        │
│ ※ 手動確定済みの行は上書きされません    │
│                                        │
│ [キャンセル] [再集計実行]              │
└──────────────────────────────────────┘
```

### 6.4 手動確定ダイアログ（買掛側 `VerifyDialog` 参考・行単位）

```
┌──────────────────────────────────────────────┐
│ 検証確定                                      │
├──────────────────────────────────────────────┤
│ 店舗: 1   得意先: 301491 クリーンラボ         │
│ 税率: 10%  取引月: 2026/04/30                 │
│                                                │
│ 集計金額（税込）: 120,000                      │
│ 請求書金額（税込）: 123,240                   │
│ 差額: -3,240                                  │
│                                                │
│ 確定金額（税込）: [123,240]                   │
│ 確定金額（税抜）: [112,036]                   │
│ 備考:                                         │
│ [____________________________________]       │
│ [____________________________________]       │
│                                                │
│ ☑ MF出力対象にする                            │
│                                                │
│ [キャンセル] [確定]                            │
└──────────────────────────────────────────────┘
```

### 6.5 一括検証ボタン

- 画面の検索条件範囲 (`shopNo`, `fromDate`, `toDate`) で `POST /bulk-verify` を呼び出し
- レスポンス: `{ matchedCount, mismatchCount, notFoundCount }`
- toast で結果通知 + 一覧再取得

### 6.6 請求書取込ボタン

- 既存 `InvoiceImportDialog` を再利用（import）
- 取込完了後に一覧を invalidate → 再取得

### 6.7 MF CSV出力ボタン

- ConfirmDialog で「現在の検索条件範囲の検証済み売掛金をCSV出力します。よろしいですか？」
- `window.location.href = '/api/v1/finance/accounts-receivable/export-mf-csv?...'`
- ブラウザの標準DL動作で CP932 CSV が保存される

### 6.8 サイドバー追加

`components/layout/Sidebar.tsx` の財務会計セクションに追加:
```
財務会計
├ 買掛金一覧
├ 売掛金一覧 ← NEW
├ 買掛仕入MF変換
├ 現金出納帳MF変換
├ MF仕訳ルール
├ MF取引先マッピング
└ 請求書一覧
```

## 7. 検証ロジック詳細

```
for each 集計結果 (shopNo, invoicePartnerCode, closingDateStr):
  if verified_manually == true:
    continue  # スキップ
    
  invoice = t_invoice.find(shopNo, invoicePartnerCode, closingDateStr)
  
  # 特殊処理
  if invoicePartnerCode == "301491":
    invoice = t_invoice.find(1, "301491", ...)  # クリーンラボは店舗番号1で検索
    
  if invoicePartnerCode == "000231":
    # イズミ四半期特殊処理
    invoices = getSpecialPartnerClosingDates(targetPeriodEndDate)
    invoice = 合計の仮想請求書
    
  if invoicePartnerCode.startsWith("999999") or len(invoicePartnerCode) >= 7:
    # 上様: 請求書金額で常に上書き（按分）
    比率按分で金額調整
    verification_result = 1
    mf_export_enabled = true
    invoice_amount = invoice.netSalesIncludingTax
    continue
    
  if invoice == null:
    verification_result = 0
    mf_export_enabled = false
    invoice_amount = null
    verification_difference = null
    continue
    
  diff = invoice.netSalesIncludingTax - 集計税込金額
  
  if diff == 0:
    verification_result = 1
    mf_export_enabled = true
  elif abs(diff) <= 3:
    # 3円未満許容 → 請求書金額に按分
    比率按分で金額調整
    verification_result = 1
    mf_export_enabled = true
  else:
    verification_result = 0
    mf_export_enabled = false
    
  invoice_amount = invoice.netSalesIncludingTax
  verification_difference = diff
  invoice_no = invoice.invoiceId
```

## 8. 画面カラム構成

| # | 列 | 幅 | 備考 |
|---|---|---|---|
| 1 | 検証結果バッジ | 90px | 一致(緑) / 不一致(赤) / 未検証(灰) |
| 2 | 店舗 | 50px | |
| 3 | 得意先コード | 90px | `partnerCode` |
| 4 | 得意先名 | 180px | `m_partner` から JOIN |
| 5 | 締め日 | 80px | 月末/15日/20日/都度現金 |
| 6 | 取引日 | 90px | `transactionMonth` |
| 7 | 税率 | 60px | 10% / 8% / 0% |
| 8 | 税込金額 | right, 100px | `taxIncludedAmountChange` |
| 9 | 税抜金額 | right, 100px | `taxExcludedAmountChange` |
| 10 | 請求書金額 | right, 100px | `invoiceAmount` |
| 11 | 差額 | right, 80px | `verificationDifference`（赤字表示） |
| 12 | 手動 | center, 50px | `verifiedManually` ✓/- |
| 13 | MF | center, 50px | `mfExportEnabled` ✓/- |
| 14 | 備考 | 150px | `verificationNote`（省略表示） |
| 15 | 請求書No | 80px | `invoiceNo`（クリックで `/finance/invoices?id=` ） |
| 16 | 操作 | 80px | [詳細] ボタン |

注文番号 (`orderNo`) は都度現金払いのみ保持されるため、詳細ダイアログで表示。

## 9. API仕様詳細

### 9.1 GET /accounts-receivable

**Request (query params)**
- `shopNo`: Integer, required (admin は 0 で全店舗)
- `fromDate`: yyyy-MM-dd, optional
- `toDate`: yyyy-MM-dd, optional
- `partnerCode`: String, optional
- `verificationResult`: 0 | 1 | null, optional（ALL と未検証の区別）
- `page`: Integer (default 0)
- `size`: Integer (default 50)
- `sort`: String (default `transactionMonth,desc`)

**Response**
```json
{
  "content": [
    {
      "shopNo": 1,
      "partnerNo": 12345,
      "partnerCode": "000231",
      "partnerName": "イズミ",
      "transactionMonth": "2026-04-15",
      "taxRate": 10.00,
      "isOtakeGarbageBag": false,
      "cutoffDate": 15,
      "orderNo": null,
      "taxIncludedAmountChange": 543000,
      "taxExcludedAmountChange": 493636,
      "taxIncludedAmount": 543000,
      "taxExcludedAmount": 493636,
      "invoiceAmount": 543000,
      "verificationDifference": 0,
      "verificationResult": 1,
      "mfExportEnabled": true,
      "verifiedManually": false,
      "verificationNote": null,
      "invoiceNo": 1234
    }
  ],
  "totalElements": 42,
  "totalPages": 1,
  "number": 0
}
```

### 9.2 POST /accounts-receivable/aggregate

**Request**
```json
{
  "targetDate": "20260420",
  "cutoffType": "all"   // "15" | "20" | "month_end" | "all"
}
```

**Response**
```json
{
  "jobExecutionId": 12345,
  "status": "STARTED"
}
```

- 既存 `BatchController#execute` と同様に 429 多重実行ガード
- 内部で `JobLauncher.run(accountsReceivableSummaryJob, params)` を呼ぶ

### 9.3 POST /accounts-receivable/bulk-verify

**Request** (body)
```json
{
  "shopNo": 1,
  "fromDate": "2026-03-21",
  "toDate": "2026-04-20"
}
```

**Response**
```json
{
  "matchedCount": 35,
  "mismatchCount": 3,
  "notFoundCount": 4,
  "skippedManualCount": 0
}
```

### 9.4 PUT /accounts-receivable/{pk}/verify

**Request**
```json
{
  "taxIncludedAmount": 123240,
  "taxExcludedAmount": 112036,
  "verificationNote": "振込手数料調整分として確認",
  "mfExportEnabled": true
}
```

**Response**: 204 No Content
- `verified_manually = true` で保存
- `verification_result = 1` を強制設定
- `*_amount` を指定値で確定
- `*_change` は現在値を保持

### 9.5 DELETE /accounts-receivable/{pk}/manual-lock

**Response**: 204 No Content
- `verified_manually = false` にする
- 次回の一括検証対象に戻る

### 9.6 PATCH /accounts-receivable/{pk}/mf-export

**Request**
```json
{ "enabled": true }
```

**Response**: 204 No Content

## 10. 実装順序

| # | 作業 | 影響範囲 | 完了条件 |
|---|---|---|---|
| 1 | 設計書作成 | `claudedocs/` | 本書のレビュー完了 |
| 2 | DDL `alter_accounts_receivable_summary_add_verification_fields.sql` | `backend/src/main/resources/sql/` | ローカルDBで ALTER 実行成功 |
| 3 | Entity 拡張 `TAccountsReceivableSummary` | `backend/.../domain/model/finance/` | `./gradlew compileJava` 通過 |
| 4 | Repository / Service メソッド追加 | `backend/.../domain/repository,service/finance/` | `findByDateRangeAndMfExportEnabled` 等 |
| 5 | `InvoiceVerifier` 新設 | `backend/.../batch/finance/service/` | 単体テスト追加 |
| 6 | `TAccountsReceivableSummaryTasklet` 改修 | `backend/.../batch/finance/` | 既存Job 手動実行で検証NGも保存されることを確認 |
| 7 | CSV生成ロジックを Service に抽出 | `backend/.../domain/service/finance/SalesJournalCsvService.java`（新設） | `AccountsReceivableToSalesJournalTasklet` が Service を利用する形に |
| 8 | `AccountsReceivableController` 新設 | `backend/.../api/finance/` | 各エンドポイントで curl 疎通 |
| 9 | Frontend `accounts-receivable.tsx` 実装 | `frontend/components/pages/finance/` | 画面表示・各ボタン動作確認 |
| 10 | ページ追加 `app/(authenticated)/finance/accounts-receivable/page.tsx` | `frontend/app/` | ルート正常 |
| 11 | Sidebar リンク追加 | `frontend/components/layout/Sidebar.tsx` | ナビゲーション確認 |
| 12 | E2E テスト `accounts-receivable.spec.ts` | `frontend/e2e/` | 全ケース PASS |
| 13 | `tsc --noEmit` + `./gradlew compileJava` + 実バックエンド疎通 | - | CLAUDE.md 準拠の増分レビュー |

## 11. テスト方針

### 11.1 単体テスト
- `InvoiceVerifierTest`: 一致/3円以内差額/3円超差額/請求書なし/上様/イズミ四半期 のケース
- 既存 `TAccountsReceivableSummaryTaskletTest`（あれば）を回帰確認

### 11.2 E2E
- `accounts-receivable.spec.ts`:
  - 一覧表示
  - 検索フィルタ
  - 再集計ボタン（締め日選択ダイアログ）
  - 請求書取込ボタン（既存 InvoiceImportDialog 連携）
  - 一括検証ボタン
  - 手動確定ダイアログ
  - MF CSV出力（DL ヘッダ確認）
  - Sidebar リンク

### 11.3 実バックエンド疎通
CLAUDE.md の「実バックエンド疎通を最低1パス」要件に従い:
1. `./gradlew bootRun --args='--spring.profiles.active=web,dev'` で起動
2. curl で各エンドポイント疎通
3. ブラウザで実画面の各ボタン動作を1パス確認

## 12. 今後の Phase

### Phase 2（後続）
- **ロット管理**: `t_mf_export_lot` 新設、CSV 出力履歴の統一管理（買掛・現金出納帳・売掛）
- **MF API 照合**: MCP or REST で MF 会計から仕訳取得 → ロット別の照合マーク
  - `t_mf_journal_snapshot` キャッシュ
  - `mf_journal_id` / `mf_match_status` をサマリテーブルに追加
- 当面は Claude Code から MCP 経由の月次手動チェックで代替可能

### Phase 3（将来）
- `/finance/invoices` 請求書一覧画面を**入金処理画面**へリフォーム
  - 銀行連携データ（MFからは取得不可、別経路で取り込み想定）と請求書の突合
  - 売上値引 / 雑所得の入力・仕訳展開
  - CSV: 入金処理仕訳（預金/売掛金、売上値引/売掛金、売掛金/雑所得 等）

## 13. 前提・リスク・確認事項

### 前提
- SMILE請求書インポート (`InvoiceImportDialog` → `t_invoice`) の運用は継続
- MF勘定科目マスタ (`MfAccountMaster`) は既にメンテされている（売掛金/売上高/クリーンラボ売上高 等）
- 特殊得意先 301491（クリーンラボ）, 000231（イズミ）, 999999/7桁以上（上様）の取扱いは既存ロジック踏襲

### リスク
- **R1**: 既存 Tasklet の検証ロジックを Service に抽出する際、四半期特殊処理のエッジケース見落とし → 対策: 既存テストデータでの回帰確認、該当期の実データで疎通
- **R2**: CSV DL エンドポイントのメモリ使用量（大量行の場合） → 対策: StreamingResponseBody or fromDate/toDate 必須化
- **R3**: 手動確定行の `*_change` 上書きポリシー（現状案では上書きしない）が業務要件と合うか → 確認: ユーザ運用想定のレビュー

### 確認事項（実装後）
- 締め日タイプ切替の UX 確認
- 一括検証ボタンの性能（画面一覧の検索条件範囲で数千行〜）
- CSV ファイル名・ヘッダ・エンコーディングが既存バッチと完全一致しているか

---

**承認後、実装順序1（本書）→2（DDL）→... と順次着手します。**
