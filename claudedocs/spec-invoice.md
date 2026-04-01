# 請求一覧 機能仕様書

## 1. 概要

SMILEから取り込んだ請求実績データ（`t_invoice`）の検索・閲覧・入金日管理を行う画面。得意先ごとの締日単位で、前回残高・入金・売上・今回請求額を一覧表示し、入金日のインライン更新が可能。

### 対象テーブル
- `t_invoice` — 請求実績（SMILEから取り込み）

### データの特徴
- SMILEの請求実績Excelから取り込むデータ（`s-invoice-import`スキルで登録）
- 明細テーブルはなく、得意先×締日の集計済みデータ
- ユニーク制約: (`partner_code`, `closing_date`, `shop_no`)
- `closing_date` は文字列型（`YYYY/MM/末` or `YYYY/MM/dd` 形式）

### 旧システム対応
| 旧画面 | 旧URL | 新URL |
|--------|--------|-------|
| 請求書一覧 | `/finance/invoice/list` | `/finance/invoices` |
| 請求書詳細 | `/finance/invoice/detail/{id}` | 一覧画面内で詳細表示（Dialog） |

---

## 2. データモデル

### 2.1 テーブル: `t_invoice`

| カラム | 型 | 説明 |
|--------|-----|------|
| `invoice_id` | Integer (PK) | 請求ID（自動採番） |
| `partner_code` | String (NOT NULL) | 得意先コード |
| `partner_name` | String (NOT NULL) | 顧客名 |
| `closing_date` | String (NOT NULL) | 締め日（YYYY/MM/末 etc） |
| `previous_balance` | BigDecimal | 前回請求残高 |
| `total_payment` | BigDecimal | 入金合計 |
| `carry_over_balance` | BigDecimal | 繰越残高 |
| `net_sales` | BigDecimal | 純売上 |
| `tax_price` | BigDecimal | 消費税額 |
| `net_sales_including_tax` | BigDecimal | 純売上額（税込） |
| `current_billing_amount` | BigDecimal | 今回請求額 |
| `shop_no` | Integer | ショップ番号 |
| `payment_date` | LocalDate | 入金日 |

---

## 3. 画面仕様

### 3.1 請求一覧画面

**パス**: `/finance/invoices`

#### 現状の実装

現在のフロントエンド（`invoices.tsx`）は簡易版で以下が不足:
- 検索フォームがない（初期表示で全件取得）
- 入金日の表示・更新がない
- 詳細項目（入金合計、繰越残高、消費税等）が表示されない
- ショップフィルタがない

#### 改善後の検索フォーム

| 項目 | 入力方式 | 必須 | 検索条件 | 備考 |
|------|----------|------|----------|------|
| ショップ | セレクト | - | 完全一致 | admin(shopNo=0)のみ表示 |
| 締月 | month入力 | - | 前方一致（YYYY/MM） | `type="month"` → YYYY/MM形式 |
| 得意先コード | テキスト | - | 前方一致 | |
| 得意先名 | テキスト | - | 部分一致 | |

**初期表示**: 検索フォームのみ（テーブル非表示）— 既存パターンに統一

#### 検索結果テーブル

| # | カラム | 説明 | 書式 |
|---|--------|------|------|
| 1 | ID | `invoiceId` | |
| 2 | 得意先コード | `partnerCode` | ソート可 |
| 3 | 得意先名 | `partnerName` | ソート可 |
| 4 | 締日 | `closingDate` | |
| 5 | 前回残高 | `previousBalance` | 通貨書式 |
| 6 | 入金合計 | `totalPayment` | 通貨書式 |
| 7 | 繰越残高 | `carryOverBalance` | 通貨書式 |
| 8 | 税込売上 | `netSalesIncludingTax` | 通貨書式 |
| 9 | 今回請求額 | `currentBillingAmount` | 通貨書式、太字 |
| 10 | 入金日 | `paymentDate` | 日付表示。クリックで編集可 |

**デフォルトソート**: 締日降順

#### 入金日のインライン更新

旧システムでは DataTable 内で日付ピッカーにより入金日をインライン更新していた。

**新システムでの方式**:
- 入金日セルをクリック → DatePicker ポップオーバー表示
- 日付選択 → PUT API → 成功トースト
- or 行クリックで詳細Dialog → Dialog内で入金日を更新

**推奨**: 行クリックで詳細Dialog方式（他の画面パターンと統一）

#### 詳細Dialog（行クリック時）

| 項目 | 表示/編集 |
|------|-----------|
| 得意先コード | 表示のみ |
| 得意先名 | 表示のみ |
| 締日 | 表示のみ |
| 前回請求残高 | 表示のみ |
| 入金合計 | 表示のみ |
| 繰越残高 | 表示のみ |
| 純売上 | 表示のみ |
| 消費税額 | 表示のみ |
| 税込売上 | 表示のみ |
| 今回請求額 | 表示のみ（強調表示） |
| 入金日 | **編集可能**（DatePicker） |

**更新ボタン**: 入金日のみ更新

---

## 4. API 設計

### 4.1 一覧検索（既存を拡張）

```
GET /api/v1/finance/invoices?shopNo=1&closingDate=2026/03&partnerCode=022&partnerName=病院
```

**追加パラメータ**:
| パラメータ | 型 | 説明 |
|-----------|-----|------|
| partnerName | String | 得意先名（部分一致）※追加 |

**レスポンス**: `List<InvoiceResponse>`（既存DTO、全フィールド含む）

### 4.2 入金日更新（新規）

```
PUT /api/v1/finance/invoices/{invoiceId}/payment-date
```

**リクエスト**:
```json
{
  "paymentDate": "2026-04-15"
}
```

**レスポンス**: 更新後の `InvoiceResponse`

---

## 5. 実装方針

### 5.1 バックエンド（既存資産活用）

**変更不要**（移行済み）:
- Entity: `TInvoice`
- Repository: `TInvoiceRepository`
- Service: `TInvoiceService`（`findByDetailedSpecification` 含む）
- Specification: `TInvoiceSpecification`
- DTO: `InvoiceResponse`

**変更が必要**:
- `FinanceController` — `partnerName` パラメータ追加、入金日更新エンドポイント追加

### 5.2 フロントエンド（大幅改修）

**変更ファイル**:
- `components/pages/finance/invoices.tsx` — 検索フォーム追加、カラム拡張、入金日更新Dialog追加

**変更なし**:
- `types/` — Invoice型は既存コンポーネント内で定義済み（別ファイルへの抽出は任意）
- `app/(authenticated)/finance/invoices/page.tsx` — 既存のまま

### 5.3 実装量の見積もり

| 区分 | ファイル数 | 内容 |
|------|-----------|------|
| バックエンド修正 | 1 | FinanceController 拡張 |
| フロントエンド修正 | 1 | invoices.tsx 全面改修 |
| 新規ファイル | 0 | 既存資産で対応可能 |

---

## 6. 旧システムとの差分

| 項目 | 旧（stock-app） | 新（oda-data-hub） |
|------|----------------|-------------------|
| 検索フォーム | Thymeleaf + Select2 | SearchForm + SearchableSelect |
| テーブル | DataTables + jQuery | DataTable（TanStack Table） |
| 入金日更新 | インラインDatePicker + AJAX | 詳細Dialog内で更新 |
| 詳細画面 | 別ページ（`/finance/invoice/detail/{id}`） | Dialog（画面遷移なし） |
| 初期表示 | 空テーブル表示 | 検索案内メッセージ |
| API | Thymeleaf POSTフォーム | REST API + TanStack Query |

## 7. 実装決定事項

| # | 項目 | 決定 | 実装状況 |
|---|------|------|----------|
| 1 | 入金日の更新方式 | 詳細Dialog内で更新 | 完了 |
| 2 | 詳細表示 | Dialog（画面遷移なし） | 完了 |
| 3 | 金額範囲検索 | 不要 | 完了 |

## 8. 変更ファイル一覧

### バックエンド
- `api/finance/FinanceController.java` — `partnerName`パラメータ追加 + 入金日更新エンドポイント追加

### フロントエンド
- `components/pages/finance/invoices.tsx` — 全面改修（検索フォーム・詳細Dialog・入金日更新）
- `e2e/helpers/mock-api.ts` — 請求モックデータ追加
- `e2e/invoice.spec.ts` — E2Eテスト（7件）
