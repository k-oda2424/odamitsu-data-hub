# 請求実績Excelインポート 機能仕様書

## 1. 概要

SMILEから出力される請求実績Excel（Sheet1）をWebアップロードし、`t_invoice`テーブルにUPSERTする。従来は手動で「第1事業部変換後」シートを作成していた工程を自動化する。

### 入力ファイル
- SMILEの「請求一覧表」Excel（`.xlsx`）
- Sheet1のみを使用
- 第1事業部（shop_no=1）と第2事業部・松山（shop_no=2）で別ファイル

### 出力
- `t_invoice`テーブルへのUPSERT（partner_code + closing_date + shop_no で一意）

---

## 2. Excelフォーマット

### Sheet1 構造（共通）
| 行 | 内容 |
|----|------|
| Row 1 | タイトル「請求一覧表」（スキップ） |
| Row 2 | 「20XX年MM月DD日締 今回請求分」→ 締日を導出 |
| Row 3 | サブヘッダー（スキップ） |
| Row 4 | カラムヘッダー（スキップ） |
| Row 5〜 | データ行 |
| 最終行 | E列に「【総合計】」（スキップ） |

### データ列マッピング
| Excel列 | Sheet1ヘッダー | t_invoice カラム | 変換 |
|---------|---------------|-----------------|------|
| A | コード（得意先） | partner_code | 6桁0埋め。松山は`<>`除去後0埋め |
| B | 得意先名 | partner_name | そのまま |
| — | （Row2から導出） | closing_date | 下記「締日導出ロジック」参照 |
| F | 前回請求残高 | previous_balance | BigDecimal |
| G | 今回入金額 | total_payment | BigDecimal |
| I | 繰越残高 | carry_over_balance | BigDecimal |
| J | 税抜売上額 | net_sales | BigDecimal |
| K | 消費税額 | tax_price | BigDecimal |
| L | 税込売上額 | net_sales_including_tax | BigDecimal |
| M | 今回請求残高 | current_billing_amount | BigDecimal |
| — | （ファイル名判定） | shop_no | 「松山」含む→2、それ以外→1 |

### 使用しない列
- C列（担当者コード）
- D列（担当者名）
- E列（回収予定日）
- H列（値引調整額）

---

## 3. 変換ロジック

### 3.1 締日導出（Row2から）

Row2の値: `「2026年 2月28日締 今回請求分」`

1. 正規表現で年・月・日を抽出: `(\d{4})年\s*(\d{1,2})月\s*(\d{1,2})日締`
2. その月の末日を計算
3. 抽出した日 == 末日 → `YYYY/MM/末`
4. 抽出した日 != 末日 → `YYYY/MM/DD`（2桁0埋め）

例:
- `2025年11月30日締` → 11月末日=30 → `2025/11/末`
- `2025年 7月20日締` → 7月末日=31, 20≠31 → `2025/07/20`

### 3.2 得意先コード変換

- **第1事業部**: 数値（例: `29`）→ `String.format("%06d", value)` → `"000029"`
- **第2事業部（松山）**: `<009896>` → `<>` 除去 → `"009896"` → 6桁0埋め（既に6桁なら変換不要）
  - 数値の場合もある（例: `181`）→ `"000181"`

### 3.3 shop_no判定

- ファイル名（オリジナルファイル名）に「松山」を含む → `shop_no = 2`
- それ以外 → `shop_no = 1`

### 3.4 スキップ条件

| 条件 | 理由 |
|------|------|
| A列（得意先コード）がnull/空 | データなし |
| E列に「総合計」を含む | 合計行 |
| 第2事業部かつ得意先コード=999999 | 松山に上様なし |
| B列（得意先名）がnull/空 かつ 得意先コード≠999999 | 不完全データ |

※ 第1事業部の999999（上様）は有効データとして取り込む

### 3.5 Row2の前処理

- Row2の文字列にNFKC正規化を適用（全角数字・全角スペース対策）
- 正規化後に正規表現で年・月・日を抽出

### 3.6 UPSERT

- **トランザクション**: `importFromExcel()` メソッド全体を `@Transactional` で囲む（all-or-nothing）
- 検索キー: `(partner_code, closing_date, shop_no)`
- 既存レコードあり → 金額フィールドを更新（payment_dateは保持）
- 既存レコードなし → 新規挿入
- 全行パース＋バリデーション完了後にまとめて永続化

### 3.7 入力検証

- ファイル拡張子が`.xlsx`であること
- Content-Typeが `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` であること
- Sheet1が存在すること
- Row2から締日が正常にパースできること
- ファイル名がnullでないこと（shop_no判定に必要）
- 999999の得意先名がnull/空の場合は「上様」をデフォルト設定

---

## 4. API設計

### 4.1 インポートエンドポイント（新規）

```
POST /api/v1/finance/invoices/import
Content-Type: multipart/form-data
```

**リクエスト:**
- `file`: Excel ファイル（.xlsx）— MultipartFile
- ファイルサイズ上限: 10MB（`application.yml` で `spring.servlet.multipart.max-file-size=10MB` を設定）

**レスポンス:**
```json
{
  "closingDate": "2026/02/末",
  "shopNo": 1,
  "totalRows": 184,
  "insertedRows": 150,
  "updatedRows": 34,
  "skippedRows": 1,
  "errors": []
}
```

**エラーレスポンス（400）:**
```json
{
  "message": "Row2から締日を解析できません: '不正な文字列'",
  "errors": []
}
```

---

## 5. 実装ファイル

### 5.1 新規ファイル

| ファイル | 内容 |
|---------|------|
| `dto/finance/InvoiceImportResult.java` | インポート結果DTO |
| `domain/service/finance/InvoiceImportService.java` | Excelパース + UPSERT ロジック |

### 5.2 変更ファイル

| ファイル | 変更内容 |
|---------|---------|
| `api/finance/FinanceController.java` | importエンドポイント追加 |
| `frontend/components/pages/finance/invoices.tsx` | インポートボタン + Dialog追加 |

### 5.3 変更なし（既存流用）

- `TInvoice.java` — Entity
- `TInvoiceRepository.java` — `findByShopNoAndPartnerCodeAndClosingDate()` を使用
- `TInvoiceService.java` — `saveInvoice()`, `findByShopNoAndPartnerCodeAndClosingDate()` を使用

---

## 6. バックエンド詳細設計

### 6.1 InvoiceImportResult.java

```java
@Data @Builder
public class InvoiceImportResult {
    private String closingDate;
    private int shopNo;
    private int totalRows;
    private int insertedRows;
    private int updatedRows;
    private int skippedRows;
    private List<String> errors;
}
```

### 6.2 InvoiceImportService.java

```
importFromExcel(MultipartFile file) → InvoiceImportResult

1. ファイル名から shop_no を判定
2. Sheet1を開く
3. Row2を正規表現でパース → closingDate
4. Row5〜を走査:
   a. スキップ条件チェック
   b. 得意先コード変換
   c. TInvoiceエンティティ構築
   d. UPSERT（findByShopNoAndPartnerCodeAndClosingDate → save）
5. 結果サマリを返却
```

### 6.3 FinanceController.java 追加

```java
@PostMapping("/invoices/import")
public ResponseEntity<?> importInvoices(@RequestParam("file") MultipartFile file)
```

---

## 7. フロントエンド詳細設計

### 7.1 インポートUI

- PageHeaderの右側に「インポート」ボタン追加
- クリックでDialogを表示
- Dialog内:
  - ファイル選択（input type=file, accept=.xlsx）
  - 「取込実行」ボタン
  - 実行中はローディング表示
  - 完了後: 結果サマリ表示（挿入/更新/スキップ件数）
  - 「閉じる」ボタン（成功時はテーブル再検索）

---

## 8. 実装決定事項

| # | 項目 | 決定 | 実装状況 |
|---|------|------|----------|
| 1 | APIアプローチ | バッチジョブではなくREST API（ファイルアップロード） | 完了 |
| 2 | トランザクション | 全行パース→一括saveAll（all-or-nothing） | 完了 |
| 3 | N+1対策 | 事前にSpecificationで一括取得→Mapルックアップ | 完了 |
| 4 | ファイル検証 | 拡張子 + Content-Type + Sheet1存在チェック | 完了 |
| 5 | BigDecimal精度 | setScale(0, HALF_UP)で整数化 | 完了 |
| 6 | MaxUploadSize | GlobalExceptionHandlerでハンドリング（10MB上限） | 完了 |

## 9. テスト方針

### バックエンド
- InvoiceImportService の単体テスト
  - 第1事業部ファイルのパース
  - 第2事業部（松山）ファイルのパース
  - 締日導出（末日/非末日）
  - 得意先コード変換（数値/`<>`付き）
  - スキップ条件（総合計行、松山の999999）
  - UPSERT（新規挿入/更新）

### フロントエンド
- E2Eテスト（10件全パス）
  - 既存7件 + インポートボタン表示、Dialog表示、ファイルアップロード結果表示
