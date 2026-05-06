# 請求機能 設計書

作成日: 2026-05-04
対象ブランチ: refactor/code-review-fixes
種別: 逆生成 (現状コードから抽出)

参考既存ドキュメント:
- `claudedocs/spec-invoice.md` (旧仕様 / 一部現状と乖離)
- `claudedocs/spec-invoice-import.md` (取込スキル仕様)
- `~/.claude/skills/s-invoice-import/SKILL.md`

---

## 1. 目的と業務上の意味

SMILE 基幹システムから出力される **請求実績 Excel** (得意先×締日単位の集計済みデータ) を新基盤に取り込み、得意先ごとに **請求一覧** として閲覧・運用するための機能。

### 業務上の意味
- 得意先 × 締日 (例: `2025/11/末`, `2025/07/20`) 単位で「前回残高 / 入金合計 / 繰越残高 / 純売上 / 税額 / 税込売上 / 今回請求額」を一覧化する **得意先請求の社内台帳**。
- 経理担当者が **入金日 (`payment_date`)** を 1 件ずつ／一括 (グループ単位) で記録し、入金消込の進捗を管理する。
- 同じ請求実績は売掛金検証バッチ (`InvoiceVerifier`) からも参照され、`t_accounts_receivable_summary` との **金額突合** に用いられる (= 売掛金画面の検証ロジックの「正解値」)。
- 取引先には **「得意先 = `partner_code`」** という SMILE のコード体系をそのまま使い、第1事業部 (`shop_no=1`) と第2事業部 / 松山 (`shop_no=2`) の 2 軸で分離管理する。

### 旧システム対応
| 旧画面 | 旧 URL | 新 URL |
|--------|--------|--------|
| 請求書一覧 | `/finance/invoice/list` | `/finance/invoices` |
| 請求書詳細 | `/finance/invoice/detail/{id}` | 一覧内で完結 (Dialog なし、入金日インライン) |

---

## 2. スコープ

### 含むもの
- SMILE 請求実績 Excel (`Sheet1`) からの取込 (`POST /finance/invoices/import`)
- 締月 / ショップ / 得意先コード / 得意先名 を絞り込み軸とした検索 (`GET /finance/invoices`)
- 入金日の単件更新 (`PUT /finance/invoices/{id}/payment-date`) と一括更新 (`PUT /finance/invoices/bulk-payment-date`)
- 入金日一括更新を効率化する **入金グループ (Partner Group)** マスタ CRUD (`/finance/partner-groups`)
- 売掛金検証 (`InvoiceVerifier`) からの請求書参照 (Read-only)

### 含まないもの
- 請求書の発行・印刷・PDF 出力 (本機能は閲覧/取込のみ)
- 請求明細 (商品別) の取り込み — Excel ソースに明細列なし、本機能は集計値のみ
- 売掛金集計 (`t_accounts_receivable_summary`) のメンテナンス UI — `/finance/accounts-receivable` の責務
- マネーフォワード仕訳出力 — `/finance/cashbook-import` 系の責務
- 請求実績の手動編集・新規作成 (UI 上は `paymentDate` のみ更新可。他列は Excel 取込専用)

---

## 3. データモデル

### 3.1 Entity: `TInvoice` (`t_invoice`)

`backend/src/main/java/jp/co/oda32/domain/model/finance/TInvoice.java:25`

| カラム | 型 | NULL | 説明 |
|--------|------|------|------|
| `invoice_id` | `Integer` (PK, IDENTITY) | NOT NULL | 請求 ID (自動採番) |
| `partner_code` | `String` | NOT NULL | 得意先コード (6 桁 0 詰め) |
| `partner_name` | `String` | NOT NULL | 得意先名 (松山 999999 → "上様") |
| `closing_date` | `String` | NOT NULL | 締め日 (`YYYY/MM/末` or `YYYY/MM/DD`) |
| `previous_balance` | `BigDecimal` | NULL | 前回請求残高 |
| `total_payment` | `BigDecimal` | NULL | 入金合計 |
| `carry_over_balance` | `BigDecimal` | NULL | 繰越残高 |
| `net_sales` | `BigDecimal` | NULL | 純売上 (税抜) |
| `tax_price` | `BigDecimal` | NULL | 消費税額 |
| `net_sales_including_tax` | `BigDecimal` | NULL | 純売上額 (税込) |
| `current_billing_amount` | `BigDecimal` | NULL | 今回請求額 |
| `shop_no` | `Integer` | NULL (実態 NOT NULL) | ショップ番号 (1=第1事業部, 2=松山) |
| `payment_date` | `LocalDate` | NULL | 入金日 (UI から設定) |

**ユニーク制約**: `(partner_code, closing_date, shop_no)` (`TInvoice.java:22-24`)

**設計上の注意**:
- `closing_date` は **String 型**。「末日」時は `YYYY/MM/末` (全角ではなく漢字 1 字)、それ以外の特定日締めは `YYYY/MM/DD`。NFKC インデックスが張られている (`V005__create_nfkc_indexes.sql`)
- 監査フィールド (`add_*` / `modify_*`) は **持っていない**。`del_flg` も無い (取込で物理 UPSERT)
- `payment_date` は取込時に上書きされない (既存値保持: `InvoiceImportService.java:163`)

### 3.2 Entity: `MPartnerGroup` (`m_partner_group`) + `m_partner_group_member`

`backend/src/main/java/jp/co/oda32/domain/model/finance/MPartnerGroup.java:18`

| カラム | 型 | 説明 |
|--------|------|------|
| `partner_group_id` | `Integer` (PK, IDENTITY) | グループ ID |
| `group_name` | `String` (NOT NULL) | グループ名 |
| `shop_no` | `Integer` (NOT NULL) | 所属事業部 |
| `partner_codes` | `List<String>` | 子テーブル `m_partner_group_member.partner_code` を `@ElementCollection(EAGER)` で保持 |

入金日一括更新時の **多得意先の一括選択ボタン** として機能 (例: 「イズミグループ」=000231, 000232, ...)。

### 3.3 関連テーブル / マスタ参照
| 種別 | 参照先 | 用途 |
|------|--------|------|
| 売掛金集計 | `t_accounts_receivable_summary` | `InvoiceVerifier` で `t_invoice` と突合 (read-only)。一致時に `verification_result=1` / `mfExportEnabled=true`、不一致は `mf_export_enabled=false` (`InvoiceVerifier.java:131-176`) |
| ショップマスタ | `m_shop` (経由 `useShops` フック) | 第1/第2事業部の選択肢 |

---

## 4. API 設計

エンドポイントはすべて `FinanceController` (`backend/src/main/java/jp/co/oda32/api/finance/FinanceController.java`) に集約。共通プレフィックス `/api/v1/finance`、`@PreAuthorize("isAuthenticated()")`。

### 4.1 一覧検索: `GET /api/v1/finance/invoices`

`FinanceController.java:423-432`

| パラメータ | 型 | 必須 | 説明 |
|------------|------|------|------|
| `shopNo` | Integer | 任意 | 完全一致 |
| `partnerCode` | String | 任意 | **前方一致** (`TInvoiceSpecification#partnerCodeContains`, `likePrefixNormalized`) |
| `partnerName` | String | 任意 | 部分一致 NFKC (`likeNormalized`) |
| `closingDate` | String | 任意 | **前方一致** (`yyyy/MM` で締月指定可能) |

**Response**: `List<InvoiceResponse>` — `TInvoice` を全列マッピング (`InvoiceResponse.from`, `InvoiceResponse.java:26`)

**ソート**: `closingDate` 降順 (`TInvoiceService.java:163`)

**所感**: 旧 `getAllInvoices()` は残存しているが Controller からは非経由 (`TInvoiceService.java:31`)

### 4.2 入金日 単件更新: `PUT /api/v1/finance/invoices/{invoiceId}/payment-date`

`FinanceController.java:434-445`

**Request Body**: `PaymentDateUpdateRequest`
```json
{ "paymentDate": "2026-04-30" }
```
- `paymentDate` は `@NotNull` (`PaymentDateUpdateRequest.java:11`)

**Response**: 更新後 `InvoiceResponse`、対象なしは 404。

### 4.3 入金日 一括更新: `PUT /api/v1/finance/invoices/bulk-payment-date`

`FinanceController.java:464-475`

**Request Body**: `BulkPaymentDateRequest`
```json
{ "invoiceIds": [101, 102, 103], "paymentDate": "2026-04-30" }
```
- `invoiceIds`: `@NotEmpty`
- `paymentDate`: `@NotNull`

**Response**: `{ "updatedCount": <int> }`
**特記**: 要求件数と取得件数が異なる場合は **WARN ログのみ** で 200 を返却 (部分成功を許容)。

### 4.4 取込: `POST /api/v1/finance/invoices/import`

`FinanceController.java:447-462`

**Content-Type**: `multipart/form-data`

| パラメータ | 型 | 必須 | 説明 |
|------------|------|------|------|
| `file` | MultipartFile (.xlsx) | 必須 | SMILE 請求実績 Excel |
| `shopNo` | Integer | 任意 | 未指定時はファイル名に「松山」が含まれれば 2、なければ 1 (`InvoiceImportService.java:55-57`) |

**Response (200)**: `InvoiceImportResult`
```json
{
  "closingDate": "2025/11/末",
  "shopNo": 1,
  "totalRows": 312,
  "insertedRows": 5,
  "updatedRows": 307,
  "skippedRows": 0,
  "errors": []
}
```
**Response (400)**: `{ "message": "<理由>" }` — `IllegalArgumentException` (拡張子不正 / Sheet1 締日解析失敗 等)
**Response (500)**: `{ "message": "インポート処理中にエラーが発生しました: ..." }`

### 4.5 入金グループ CRUD: `/api/v1/finance/partner-groups`

| Method | Path | 説明 | DTO |
|--------|------|------|-----|
| GET | `/partner-groups?shopNo={n}` | 一覧 (グループ名昇順, `MPartnerGroupRepository.java:12`) | `List<PartnerGroupResponse>` |
| POST | `/partner-groups` | 新規作成 | `PartnerGroupRequest` |
| PUT | `/partner-groups/{id}` | 更新 (`partnerCodes` は **clear → addAll** で全置換, `MPartnerGroupService.java:45-46`) | `PartnerGroupRequest` |
| DELETE | `/partner-groups/{id}` | 削除 (存在しない場合 `IllegalArgumentException`) | 204 No Content |

**`PartnerGroupRequest`** (`PartnerGroupRequest.java`):
```json
{
  "groupName": "イズミグループ",  // @NotBlank
  "shopNo": 1,                       // @NotNull
  "partnerCodes": ["000231", "000232"]  // @NotNull, @Size(min=1)
}
```

---

## 5. インポート処理フロー

### 5.1 全体フロー (Service: `InvoiceImportService.importFromExcel`)

`InvoiceImportService.java:40-188`

```
[Excel受信] 
  └─ ファイル名/拡張子チェック (.xlsx 必須, MIME 任意)
  └─ shopNo 確定 (引数 ≧ ファイル名「松山」判定)
  └─ Workbook open (Sheet1 優先, 無ければ先頭)
[Phase 0] 締日抽出 (Row2, A 列)
  └─ NFKC 正規化 → /(\d{4})年\s*(\d{1,2})月\s*(\d{1,2})日締/ で年月日取得
  └─ 日 == 月末日 → "YYYY/MM/末"、それ以外 → "YYYY/MM/DD"
[Phase 1] 行パース (5 行目以降, index=4 から)
  └─ E 列に「総合計」を見たら break
  └─ A 列 (得意先コード) を 6 桁 0 詰めに正規化 (`<009896>` も剥がす)
  └─ 松山 (shop=2) かつ partnerCode=999999 はスキップ
  └─ B 列 (得意先名) が空かつ非 999999 はスキップ
  └─ 999999 で得意先名が空 → 「上様」固定
  └─ 5/6/8/9/10/11/12 列を BigDecimal (HALF_UP, scale=0) で取得
  └─ TInvoice.builder() → parsedInvoices に蓄積
[Phase 2] UPSERT
  └─ 既存行を (shopNo, closingDate) で一括 SELECT
  └─ partner_code → existing の Map を構築
  └─ 各 parsed について
       ├─ existing あり → 9 列上書き (paymentDate は保持)
       └─ existing なし → そのまま新規 insert
  └─ saveAll で一括コミット (@Transactional, all-or-nothing)
[Phase 3] InvoiceImportResult ビルド + INFO ログ
```

### 5.2 列マッピング (Excel → `TInvoice`)

| Excel 列 (0 始) | 内容 | TInvoice フィールド |
|----------------|------|---------------------|
| A (0) | 得意先コード | `partnerCode` (6 桁 0 詰め) |
| B (1) | 得意先名 | `partnerName` |
| E (4) | 「総合計」検出用 | (制御) |
| F (5) | 前回残高 | `previousBalance` |
| G (6) | 入金合計 | `totalPayment` |
| I (8) | 繰越残高 | `carryOverBalance` |
| J (9) | 純売上 | `netSales` |
| K (10) | 消費税 | `taxPrice` |
| L (11) | 税込純売上 | `netSalesIncludingTax` |
| M (12) | 今回請求額 | `currentBillingAmount` |

**注**: H 列 (index=7) はスキップ。元 Excel に何の列があるかは確認 TODO。

### 5.3 セル値読み出し
`getCellStringValue` / `getCellBigDecimal` (`InvoiceImportService.java:248-302`)
- `STRING` / `NUMERIC` / `FORMULA` 全対応 (FORMULA は文字列 → 数値の二段フォールバック)
- 数値は `setScale(0, HALF_UP)` で必ず整数化

### 5.4 突合ロジック: `InvoiceVerifier`

`backend/src/main/java/jp/co/oda32/batch/finance/service/InvoiceVerifier.java:39`

請求書を「期待される売上額」として、`TAccountsReceivableSummary` (税率別に複数行) と突き合わせる。**取込機能とは独立**だが、`t_invoice` の使用先として最重要。

#### 突合キー (`InvoiceValidationKey`)
- `shopNo` (クリーンラボ `301491` は **強制 1**, `InvoiceVerifier.java:206`)
- `partnerCode`
- `closingDateStr` — 各 AR 行の `transactionMonth` から `formatClosingDateForSearch` で組み立て:
  - `MONTH_END` / `CASH_ON_DELIVERY` (cutoff=0/-1) → `YYYY/MM/末`
  - 特定日締め → `YYYY/MM/DD`

#### 判定ルール (`InvoiceVerifier.java:131-191`)
| 状態 | 判定 | フィールド更新 |
|------|------|----------------|
| 請求書見つからず | NotFound | `verificationResult=0`, `mfExportEnabled=false`, `invoiceAmount=null` |
| 上様 (999999) | 常に上書き | 請求書金額に按分 + `verificationResult=1` |
| 差額 0 | 一致 | `verificationResult=1`, `mfExportEnabled=true` |
| 差額 ≦ `batch.accounts-receivable.invoice-amount-tolerance` (default 3 円) | 許容範囲一致 | 一致扱い + 按分 + WARN ログ |
| 差額超過 | 不一致 | `verificationResult=0`, `mfExportEnabled=false` + ERROR ログ |
| `verifiedManually=true` | スキップ | 何もしない |

#### 特殊得意先処理
- **イズミ (000231)**: 当月 15 日締め + (前月が 2/5/8/11 月のとき) 前月末締め も合算 → `findQuarterlyInvoice` (`InvoiceVerifier.java:234-266`) が **仮想 TInvoice** を生成
- **クリーンラボ (301491)**: `shopNo` を強制 1
- **上様 (999999)**: 集計が 0 でなければ請求書金額に按分上書き

#### 按分ロジック (`allocateProportionallyWithRemainder`, `allocateInvoiceByArRatio`)
- 税込合計 → 請求書金額に揃えるため、各税率行に **税込比率で按分**
- 端数は最大金額行で吸収 (sum 合致を保証)
- AR 合計が 0 だが請求書あり → ERROR ログ + 全行 0 を返し `applyMismatch` 側で `mf_export_enabled=false`

---

## 6. 画面設計

### 6.1 一覧画面 `/finance/invoices`

`frontend/components/pages/finance/invoices.tsx`

#### レイアウト
```
+---------------------------------------------+
| PageHeader: 請求書一覧  [インポート]         |
+---------------------------------------------+
| SearchForm                                   |
|  - ショップ (admin のみ)                     |
|  - 締月 (input type=month)                   |
|  - 得意先コード                              |
|  - 得意先名                                  |
+---------------------------------------------+
| (検索後) Bulk action bar                     |
|  [□全選択] X件選択中 | [グループ▼] [+管理] |
|                  [入金日]  [一括反映(N件)]  |
+---------------------------------------------+
| (グループ選択時) サマリ: 税込売上 / 今回請求額 |
+---------------------------------------------+
| DataTable                                    |
|  | □ | 得意先 | 締日 | 前回残高/入金 |       |
|  | 繰越残高 | 税込売上 | 今回請求額 | 入金日 | |
+---------------------------------------------+
```

#### 主な状態
| state | 役割 |
|-------|------|
| `searchParams: Record<string,string> \| null` | `null` で初期検索抑制 (`enabled: searchParams !== null`) |
| `selectedIds: Set<number>` | 選択中 invoiceId |
| `bulkPaymentDate: string` | 一括反映用日付 |
| `filterGroupId: string \| null` | 現在絞り込み中のグループ |
| `importDialogOpen` / `groupDialogOpen` | ダイアログ可否 |

#### 振る舞い
- **初期表示で API を叩かない** (グローバル規約)。`setSearchParams({...})` で検索開始。
- 締月入力 (`yyyy-MM`) は送信時に `replaceAll('-', '/')` → API は `yyyy/MM` で前方一致。
- **入金日インライン編集**: `PaymentDateCell` が `onBlur` で値変化を検知し `PUT /payment-date`。失敗時 toast。
- **入金グループ絞込み**: グループ選択で `partnerCodes` の Set を用いてクライアントサイドフィルタ + 該当 invoiceId 全選択 → 「一括反映」で `PUT /bulk-payment-date`。
- **admin 判定**: `user.shopNo === 0` で店舗セレクト表示 (`useShops`)。
- セルレンダリングは `SelectCell` / `PaymentDateCell` を抽出して `useMemo` の依存を最小化 (パフォーマンス F1, F2 のコメント参照)。

#### 表示フォーマット
- 金額は `formatNumber` (3 桁カンマ)、null は `'-'` (`formatMoney`, `invoices.tsx:50`)
- 「今回請求額」のみ太字 (`font-bold`)
- 「前回残高 / 入金」は同セルに 2 段表示

### 6.2 取込ダイアログ `InvoiceImportDialog`

`frontend/components/pages/finance/InvoiceImportDialog.tsx`

- ファイル選択 (`accept=".xlsx"`) → 「取込実行」で `POST /invoices/import` (FormData)
- 完了時はダイアログ内に **結果サマリ** (締日 / 事業部 / 処理行数 / 新規 / 更新 / スキップ) を差し替え表示
- onSuccess で `queryClient.invalidateQueries(['invoices'])` → 一覧自動再フェッチ
- `shopNo` パラメータは送らず、サーバ側のファイル名判定に任せている (画面から事業部選択はしない)

### 6.3 入金グループダイアログ `PartnerGroupDialog`

`frontend/components/pages/finance/PartnerGroupDialog.tsx`

- 一覧 (`groups` props) + 編集フォーム (グループ名 / `partnerCodes` を改行 or カンマ区切り textarea)
- `partnerCodes` は `split(/[\n,\s]+/)` で正規化 (空要素除外)
- 削除は `AlertDialog` で確認 → `DELETE /partner-groups/{id}`
- 保存 (`POST` / `PUT`) 後 `['partner-groups']` を invalidate

---

## 7. 既知の制約・注意事項

1. **`closing_date` が String 型**: 日付演算ができないため、月次集計や範囲検索は前方一致 (`yyyy/MM`) で済ませる。LocalDate へのマイグレーションは互換性影響大。
2. **`shop_no` 判定がファイル名依存**: API 引数で明示しないと「松山」文字列マッチに依存。誤判定時は再アップロード必須。
3. **取込はトランザクション 1 個**: `@Transactional` で全行成功か全行失敗。1 行のパース異常 (例: 得意先コードが非数値) で `IllegalArgumentException` を投げ全ロールバック (`InvoiceImportService.java:244`)。
4. **`payment_date` は取込で更新されない**: 既存行に対して保持 (`InvoiceImportService.java:163`)。一度入金記録した行が再取込で消えない設計。
5. **入金日一括更新の部分成功**: 要求 ID 中に存在しないものがあっても **WARN ログのみで 200 成功**。整合性チェックは画面側責務。
6. **`InvoiceVerifier` の特殊得意先ハードコード**:
   - 999999 (上様), 000231 (イズミ), 301491 (クリーンラボ) — マスタ化されておらずコード定数
   - 「四半期特殊月 = 2/5/8/11」もハードコード (`InvoiceVerifier.java:282`)
7. **`MPartnerGroup#partnerCodes` は `EAGER`**: グループ件数が増えると一覧 GET の N+1 リスク (現状 dev 環境では問題なし)。
8. **CSRF / 認証**: `@PreAuthorize("isAuthenticated()")` のみ。ロール (admin / user) による操作制限は未実装。誰でも取込・一括更新が可能。
9. **`TInvoice` に監査列が無い**: `add_user_no` / `modify_user_no` を持たないため、誰がいつ入金日を更新したかは追跡できない。
10. **`TInvoiceService` の重複メソッド**: `saveInvoice` / `insert` が同義 (両方 `repository.save`)。Controller 経由の更新パスは `saveInvoice` のみ使用。

---

## 8. 課題 / TODO (コード読解中に気づいたもの)

| # | 区分 | 内容 |
|---|------|------|
| T1 | 仕様確認 | Excel の H 列 (index=7) の意味 — 取込でスキップしている。元データの仕様確認 TODO。 |
| T2 | 設計差分 | `spec-invoice.md` (旧仕様書) は「入金日表示・更新がない」と書かれているが、実装は完了している。古い仕様書のため整合させるか deprecate マーク要。 |
| T3 | コード品質 | `TInvoiceService#saveInvoice` と `#insert` が同一実装 (`TInvoiceService.java:53-66`)。どちらかへ統一。 |
| T4 | コード品質 | `TInvoiceService#getAllInvoices` (`L31`) と `#findBySpecification` (`L133`) は Controller から未使用 (デッドコード)。 |
| T5 | バグリスク | `InvoiceVerifier.findQuarterlyInvoice` で virtual TInvoice を作る際、`invoiceId` を「最初」、`closingDate` を「最後」の請求書から取っており、監査時に整合しないリスク (`InvoiceVerifier.java:259-263`)。 |
| T6 | UI | フロント `Invoice` 型に `paymentDate: string \| null` とあるが、サーバの `InvoiceResponse#paymentDate` は `LocalDate` (Jackson が ISO 文字列でシリアライズ前提)。タイムゾーン揺らぎは無いが型注釈の整合確認 TODO。 |
| T7 | UI | `invoices.tsx:301` の `eslint-disable react-hooks/exhaustive-deps` — `selectedIds` を依存に含めず `SelectCell` 経由で参照。React 19 で正しく再描画されるかの最終検証 TODO。 |
| T8 | 認可 | 取込・一括更新を admin 限定にする要否。現状は authenticated 全員が実行可能。 |
| T9 | データ整合 | `TInvoice.shopNo` が JPA 上 NULL 許容になっている (`@Column(name="shop_no")` のみ, NOT NULL 制約なし) のに対し、ユニーク制約と Entity 用途上は NOT NULL 必須。DDL 側で NOT NULL を確認する必要あり (TODO: 確認)。 |
| T10 | テスト | `InvoiceVerifier` の上様/イズミ/クリーンラボ特殊処理に対する単体テスト網羅性は未確認。 |
| T11 | 設計 | `InvoiceVerifier` の特殊得意先 (999999/000231/301491) と「四半期特殊月」をマスタ化する余地。現状は法人マスタ追加時にコード変更が必要。 |
| T12 | i18n | `closing_date` の「末」漢字リテラル — DB 検索文字列のキー要素。本番運用での移行困難性に注意。 |
| T13 | パフォーマンス | `MPartnerGroup` の `partnerCodes` `@ElementCollection(EAGER)` は N+1 候補。グループ件数増加時に LAZY 化検討。 |
| T14 | 命名 | DTO の `closingDate` フィールドが `String`。LocalDate との混在で型エラー検出が遅れる可能性。 |
