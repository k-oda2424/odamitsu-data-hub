# 設計書: AI見積取込データ管理・突合画面

## 1. Background / Problem

Claude Codeの見積ファイル取込スキル（`/quote-import`）でPDF/Excel/CSV/画像からJANコード・商品名・規格・価格を抽出できるようになった。しかし以下の課題がある：

1. 見積の**仕入先名**とシステムの仕入先が自動では紐付かない
2. 見積の**商品名**とシステムの商品名が一致しない
3. **JANコードがないメーカー**もある
4. 見積に載っている商品が**システムに未登録**の場合がある
5. 人間が最終的な突合を行う必要がある

## 2. 全体データフロー（C方式: ワーク→直接反映）

```
Claude Code (PDF解析)
    ↓ POST /api/v1/quote-imports (JSON一括投入)
t_quote_import_header（ヘッダー: 仕入先名・適用日・件数）
w_quote_import_detail（ワーク明細: 未処理の商品のみ）
    ↓
Step 0: 仕入先の突合（ヘッダーレベル）
    見積の「花王」→ システムのどの仕入先？
    ↓
Step 1: 商品の突合（明細レベル）
    ├─ 既存商品に突合 → MPurchasePriceChangePlan作成 + ワーク行削除
    ├─ 新規商品作成   → MGoods + WSalesGoods + MPurchasePriceChangePlan作成 + ワーク行削除
    └─ スキップ       → ワーク行削除のみ

ワーク明細が0件 → ヘッダーも削除可能（完了）
```

## 3. 画面構成

| 画面 | URL | 説明 |
|------|-----|------|
| AI取込一覧 | `/purchase-prices/imports` | 取込バッチ一覧（残件数・仕入先突合状態） |
| 突合画面 | `/purchase-prices/imports/[importId]` | 仕入先突合 + 商品突合 + 新規商品登録 |

### サイドバーメニュー追加
```
仕入
├ ...（既存メニュー）
└ AI見積取込 → /purchase-prices/imports
```

## 4. Data Model

### t_quote_import_header（取込ヘッダー）

| カラム | 型 | 説明 |
|--------|----|----|
| quote_import_id | SERIAL (PK) | 取込ID |
| shop_no | INTEGER NOT NULL | 店舗番号 |
| supplier_name | VARCHAR(200) | 仕入先名（見積記載の原文） |
| supplier_code | VARCHAR(50) | 仕入先コード（突合後にセット） |
| supplier_no | INTEGER | 仕入先番号（突合後にセット） |
| file_name | VARCHAR(500) | 元ファイル名 |
| quote_date | DATE | 見積日 |
| effective_date | DATE | 適用予定日 |
| change_reason | VARCHAR(10) | 変更理由（PU/PD/ES） |
| price_type | VARCHAR(20) | 税込/税抜 |
| total_count | INTEGER | 取込時の明細総数（進捗計算用。不変） |
| del_flg | VARCHAR(1) DEFAULT '0' | 削除フラグ |
| add_date_time | TIMESTAMP | 登録日時 |
| add_user_no | INTEGER | 登録者 |
| modify_date_time | TIMESTAMP | 更新日時 |
| modify_user_no | INTEGER | 更新者 |

- `supplier_code` / `supplier_no` は取込直後はNULL。仕入先突合で確定する
- `total_count` は取込時に固定。ワーク残件と比較して進捗率を算出

### w_quote_import_detail（ワーク明細 ← 未処理のみ）

| カラム | 型 | 説明 |
|--------|----|----|
| quote_import_detail_id | SERIAL (PK) | 明細ID |
| quote_import_id | INTEGER (FK) | 取込ID |
| row_no | INTEGER | 見積上の行番号 |
| jan_code | VARCHAR(20) | JANコード（NULL可: ないメーカーもある） |
| quote_goods_name | VARCHAR(500) NOT NULL | 見積上の商品名（原文保持） |
| quote_goods_code | VARCHAR(100) | 見積上のメーカー品番（あれば） |
| specification | VARCHAR(200) | 規格・容量（700mL等） |
| quantity_per_case | INTEGER | 入数 |
| old_price | DECIMAL(12,2) | 旧価格（個単価） |
| new_price | DECIMAL(12,2) | 新価格（個単価） |
| old_box_price | DECIMAL(12,2) | 旧価格（箱単価） |
| new_box_price | DECIMAL(12,2) | 新価格（箱単価） |
| add_date_time | TIMESTAMP | 登録日時 |

- **突合完了/スキップ/新規作成 → この行を DELETE**
- ヘッダーの `total_count - ワーク残件数 = 処理済み件数`
- ワーク0件 = 全件処理完了

## 5. 突合フロー詳細

### Step 0: 仕入先の突合（ヘッダーレベル）

突合画面の上部に仕入先突合セクションを表示。

```
┌─────────────────────────────────────────────────┐
│ 仕入先の突合                                      │
│                                                   │
│ 見積記載: 花王                                    │
│ システム仕入先: [仕入先を検索... 🔍]  [確定]       │
│                                                   │
│ ※仕入先を確定するまで商品の突合はできません        │
└─────────────────────────────────────────────────┘
```

- SearchableSelectで仕入先を検索・選択
- 確定 → `PUT /api/v1/quote-imports/{id}/supplier` で supplier_code, supplier_no を保存
- 仕入先がシステム未登録の場合 → 「仕入先を新規登録」リンク（既存のマスタ管理画面へ遷移）
- **仕入先が未確定の間は商品突合セクションを非活性にする**

### Step 1-A: 既存商品への突合

商品検索Popoverで候補を選択:

```
[商品を検索...🔍] → Popover表示
┌──────────────────────────────────┐
│ 🔍 クリーン F1 700              │
├──────────────────────────────────┤
│ SG001 ｸﾘｰﾝ&ｸﾘｰﾝF1 700ML  ¥600 │ ← クリックで選択
│ SG002 ｸﾘｰﾝ&ｸﾘｰﾝF1 1.5L   ¥950 │
│ ─── 該当なし（スキップ）──────── │
└──────────────────────────────────┘
```

選択時の処理:
1. `POST /api/v1/quote-imports/{importId}/details/{detailId}/match`
2. バックエンドで MPurchasePriceChangePlan を作成
3. ワーク行を DELETE
4. フロント: テーブルから行が消える（残件数が減る）

### Step 1-B: 新規商品の作成

見積の商品がシステムに存在しない場合、「新規作成」ボタンで登録Dialog表示:

```
[新規作成] → Dialog表示
┌─────────────────────────────────────────────────┐
│ 新規商品登録                                      │
│                                                   │
│ ── 商品マスタ情報 ──────────────────────────────── │
│ 商品名 *    [クリーン&クリーンF1 ボトル       ]    │ ← 見積商品名を初期値
│ JANコード   [4901301508034                    ]    │ ← 見積JANを初期値
│ メーカー    [花王                          🔍]    │ ← 仕入先から推定
│ 規格        [700mL                           ]    │ ← 見積規格を初期値
│ 入数        [6                               ]    │ ← 見積入数を初期値
│ 軽減税率    [ ] 適用する                          │
│                                                   │
│ ── 販売商品情報 ──────────────────────────────── │
│ 商品コード * [                               ]    │ ← 人間が入力（JANで仮登録可）
│ 商品名 *    [クリーン&クリーンF1 ボトル       ]    │
│ 仕入先      花王（自動セット）                     │
│ 標準仕入単価 * [726                          ]    │ ← 見積の新価格を初期値
│ 標準売単価 * [                               ]    │ ← 人間が入力
│                                                   │
│                        [キャンセル]  [登録]        │
└─────────────────────────────────────────────────┘
```

登録時の処理（バックエンド1トランザクション）:
1. MGoods 作成（商品マスタ）
2. WSalesGoods 作成（販売商品ワーク）← goodsCodeは人間入力、JANコードで仮登録もあり
3. MPurchasePriceChangePlan 作成（仕入価格変更予定）
4. ワーク行を DELETE
5. フロント: テーブルから行が消える

### Step 1-C: スキップ

「スキップ」ボタン → ワーク行を DELETE のみ。何も作成しない。

## 6. API設計

### 6.1 取込

#### POST /api/v1/quote-imports
AI解析データを一括投入（Claude Codeのスキルから呼ばれる）。

リクエスト:
```json
{
  "shopNo": 1,
  "supplierName": "花王",
  "fileName": "小田光様_26年5月価格改定御見積書.pdf",
  "quoteDate": "2026-01-30",
  "effectiveDate": "2026-05-01",
  "changeReason": "PU",
  "priceType": "税抜",
  "details": [
    {
      "rowNo": 1,
      "janCode": "4901301508034",
      "quoteGoodsName": "クリーン&クリーンF1 ボトル",
      "quoteGoodsCode": null,
      "specification": "700mL",
      "quantityPerCase": 6,
      "oldPrice": 661,
      "newPrice": 726,
      "oldBoxPrice": 3966,
      "newBoxPrice": 4356
    }
  ]
}
```

※ `supplierCode` / `supplierNo` は投入時点では不要（仕入先未突合）

#### GET /api/v1/quote-imports
取込一覧。

レスポンス:
```json
[
  {
    "quoteImportId": 1,
    "supplierName": "花王",
    "supplierCode": null,
    "supplierNo": null,
    "fileName": "小田光様_26年5月...",
    "effectiveDate": "2026-05-01",
    "changeReason": "PU",
    "totalCount": 160,
    "remainingCount": 115,
    "addDateTime": "2026-04-01T10:00:00"
  }
]
```

- `remainingCount`: ワーク明細の残件数（SELECT COUNT）
- 進捗率 = `(totalCount - remainingCount) / totalCount`

#### GET /api/v1/quote-imports/{importId}
ヘッダー + ワーク明細（未処理分のみ）を返す。

#### DELETE /api/v1/quote-imports/{importId}
ヘッダー論理削除 + ワーク明細を全DELETE。

### 6.2 仕入先突合

#### PUT /api/v1/quote-imports/{importId}/supplier
仕入先を確定する。

リクエスト:
```json
{
  "supplierCode": "022000",
  "supplierNo": 15
}
```

### 6.3 商品突合（既存商品）

#### POST /api/v1/quote-imports/{importId}/details/{detailId}/match
既存商品に突合。バックエンドで以下を実行:
1. MPurchasePriceChangePlan を作成
2. ワーク行を DELETE

リクエスト:
```json
{
  "goodsCode": "SG001",
  "goodsNo": 1
}
```

### 6.4 新規商品作成

#### POST /api/v1/quote-imports/{importId}/details/{detailId}/create-new
新規商品を作成して突合。バックエンドで以下を1トランザクションで実行:
1. MGoods 作成
2. WSalesGoods 作成
3. MPurchasePriceChangePlan 作成
4. ワーク行を DELETE

リクエスト:
```json
{
  "goods": {
    "goodsName": "クリーン&クリーンF1 ボトル",
    "janCode": "4901301508034",
    "makerNo": 5,
    "specification": "700mL",
    "caseContainNum": 6,
    "applyReducedTaxRate": false
  },
  "salesGoods": {
    "goodsCode": "4901301508034",
    "goodsName": "クリーン&クリーンF1 ボトル 700mL",
    "purchasePrice": 726,
    "goodsPrice": 900
  }
}
```

### 6.5 スキップ

#### DELETE /api/v1/quote-imports/{importId}/details/{detailId}
ワーク行を DELETE のみ。

### 6.6 一括JAN自動突合

#### POST /api/v1/quote-imports/{importId}/auto-match-jan
JANコードが一致する商品を一括で突合。
- ワーク明細のうちJANコードがNULLでないものを対象
- `GET /api/v1/goods?janCode={jan}` で一致を確認
- 一致した分 → MPurchasePriceChangePlan作成 + ワーク行DELETE
- レスポンス: 自動突合できた件数

## 7. UI設計

### 7.1 AI取込一覧（`/purchase-prices/imports`）

| 列 | 説明 |
|---|------|
| 仕入先 | 見積記載名。突合済みならシステム名も表示 |
| ファイル名 | 元ファイル名 |
| 適用日 | 変更予定日 |
| 変更理由 | PU/PD/ES のラベル表示 |
| 進捗 | `処理済み/全件` + プログレスバー |
| 仕入先突合 | 済/未 のBadge |
| 操作 | [突合画面へ] |

### 7.2 突合画面（`/purchase-prices/imports/[importId]`）

上部: ヘッダー情報 + 仕入先突合セクション
下部: 商品突合テーブル（仕入先確定後に活性化）

商品突合テーブル列:

| 列 | 説明 |
|---|------|
| # | 行番号 |
| JANコード | NULLの場合は「(なし)」 |
| 見積商品名 | 原文 |
| 規格 | 700mL等 |
| 旧単価 | 個単価 |
| 新単価 | 個単価 |
| 操作 | [突合🔍] [新規作成] [スキップ] |

一括操作ボタン:
- 「JAN自動突合」: JANコード一致を一括処理
- ワーク0件表示: 「全件処理完了しました」メッセージ

## 8. 新規商品作成で必要なレコード一覧

| # | テーブル | 必須フィールド | 見積から自動セット | 人間が入力 |
|---|---------|-------------|------------------|-----------|
| 1 | MGoods | goodsName | 商品名, JANコード, 規格, 入数 | メーカー, 軽減税率 |
| 2 | WSalesGoods | shopNo, goodsNo, goodsCode, goodsName, supplierNo, purchasePrice, goodsPrice | shopNo(ヘッダー), supplierNo(仕入先突合済), purchasePrice(新価格) | **goodsCode（必須）**, goodsPrice（売単価） |
| 3 | MPurchasePriceChangePlan | shopNo, goodsCode, supplierCode, afterPrice, changePlanDate, changeReason | 全てヘッダー+明細+Step2から取得 | - |

- goodsCode は人間が入力。JANコードで仮登録も可能
- goodsPrice（売単価）は見積には載らないため人間が入力
- MGoods作成後に自動採番されるgoodsNoを使ってWSalesGoodsを作成

## 9. Edge Cases

- 仕入先がシステム未登録 → 「マスタ管理画面で仕入先を先に登録してください」と案内
- JANコードがNULLの明細 → JAN自動突合の対象外。手動検索のみ
- 同じJANで複数のシステム商品がある場合 → 候補一覧で表示、人間が選択
- 新規作成で商品コードが重複 → バックエンドでユニーク制約エラー → フロントでエラー表示
- 新規作成で商品コードにJANコードを使用 → 許可（仮登録パターン）
- ワーク0件になった後のヘッダー → 一覧から非表示 or 「完了」Badge表示
- 適用日が過去の場合 → 警告表示（取込は許可）

## 10. ファイル一覧

### バックエンド新規

| ファイル | 種別 |
|---------|------|
| `domain/model/purchase/TQuoteImportHeader.java` | Entity |
| `domain/model/purchase/WQuoteImportDetail.java` | Entity (ワーク) |
| `domain/repository/purchase/TQuoteImportHeaderRepository.java` | Repository |
| `domain/repository/purchase/WQuoteImportDetailRepository.java` | Repository |
| `domain/service/purchase/QuoteImportService.java` | Service（@Transactional） |
| `api/purchase/QuoteImportController.java` | Controller |
| `dto/purchase/QuoteImportCreateRequest.java` | Request DTO |
| `dto/purchase/QuoteImportHeaderResponse.java` | Response DTO |
| `dto/purchase/QuoteImportDetailResponse.java` | Response DTO |
| `dto/purchase/QuoteImportMatchRequest.java` | Request DTO |
| `dto/purchase/QuoteImportCreateNewRequest.java` | Request DTO（新規商品） |
| `dto/purchase/QuoteImportSupplierMatchRequest.java` | Request DTO（仕入先突合） |

### フロントエンド新規

| ファイル | 種別 |
|---------|------|
| `types/quote-import.ts` | 型定義 |
| `components/pages/purchase-price/import-list.tsx` | AI取込一覧 |
| `components/pages/purchase-price/import-detail.tsx` | 突合画面 |
| `components/pages/purchase-price/GoodsSearchPopover.tsx` | 商品検索Popover |
| `components/pages/purchase-price/NewGoodsDialog.tsx` | 新規商品登録Dialog |
| `components/pages/purchase-price/SupplierMatchSection.tsx` | 仕入先突合セクション |
| `app/(authenticated)/purchase-prices/imports/page.tsx` | ルート |
| `app/(authenticated)/purchase-prices/imports/[importId]/page.tsx` | ルート |
| `components/layout/Sidebar.tsx` | 変更（メニュー追加） |

### スキル更新

| ファイル | 変更内容 |
|---------|---------|
| `s-quote-import/SKILL.md` | 最終ステップを POST /api/v1/quote-imports に変更 |
