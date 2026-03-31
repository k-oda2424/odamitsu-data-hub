# 設計書: 仕入価格管理画面

> **ステータス: 実装完了（2026-03-31）**
> 詳細な画面仕様は `claudedocs/purchase-feature-spec.md` を参照。

## 1. Background / Problem

仕入価格の管理（一覧表示・価格変更予定の登録・一括入力）がフロントエンドに未実装。バックエンドのEntity/Service/Batchは旧システムから移行済みだがAPIが不足している。

## 2. Requirements

### Functional Requirements
- FR-1: 仕入価格マスタ（MPurchasePrice）の検索・一覧表示
- FR-2: 仕入価格一覧から行クリックで価格変更予定をDialog入力
- FR-3: 仕入価格変更予定（MPurchasePriceChangePlan）の検索・一覧表示
- FR-4: 仕入価格変更の一括入力（ヘッダー＋明細の複数行入力）
- FR-5: サイドバーメニュー名「仕入単価」→「仕入価格一覧」に変更、サブメニュー追加

## 3. 画面構成

| 画面 | URL | 説明 |
|------|-----|------|
| 仕入価格一覧 | `/purchase-prices` | MPurchasePrice検索＋変更予定Dialog |
| 仕入価格変更一覧 | `/purchase-prices/changes` | MPurchasePriceChangePlan検索 |
| 仕入価格変更一括入力 | `/purchase-prices/changes/bulk-input` | ヘッダー＋明細一括登録 |

## 4. API設計

### 4.1 仕入価格 API（PurchasePriceController拡充）

#### GET /api/v1/purchase-prices
検索パラメータ追加:
- `shopNo` (Integer, optional)
- `goodsName` (String, optional)
- `goodsCode` (String, optional)
- `supplierNo` (Integer, optional)

レスポンス `PurchasePriceResponse` を拡充:
```java
purchasePriceNo, goodsNo, goodsCode, goodsName, makerName,
supplierNo, supplierName, shopNo, partnerNo, destinationNo,
goodsPrice, includeTaxGoodsPrice, taxRate, taxCategory,
lastPurchaseDate, periodFrom, periodTo
```

### 4.2 仕入価格変更予定 API（新規Controller）

#### GET /api/v1/purchase-price-changes
検索パラメータ:
- `shopNo` (Integer, optional)
- `supplierCode` (String, optional)
- `goodsCode` (String, optional)
- `janCode` (String, optional)
- `changeReason` (String, optional)
- `changePlanDateFrom` (LocalDate, optional)
- `changePlanDateTo` (LocalDate, optional)

レスポンス `PurchasePriceChangePlanResponse`:
```java
purchasePriceChangePlanNo, shopNo, goodsCode, goodsName, janCode,
supplierCode, supplierName, beforePrice, afterPrice,
changePlanDate, changeReason, changeContainNum,
partnerNo, destinationNo,
partnerPriceChangePlanCreated, purchasePriceReflect
```

#### POST /api/v1/purchase-price-changes
単品登録。リクエスト `PurchasePriceChangePlanCreateRequest`:
```java
shopNo, goodsCode, supplierCode, beforePrice, afterPrice,
changePlanDate, changeReason, changeContainNum,
partnerNo(optional), destinationNo(optional)
```

#### POST /api/v1/purchase-price-changes/bulk
一括登録。リクエスト `PurchasePriceChangePlanBulkRequest`:
```java
shopNo, supplierCode, changePlanDate, changeReason,
partnerNo(optional), destinationNo(optional),
details: [{ goodsCode, beforePrice, afterPrice, changeContainNum }]
```

#### DELETE /api/v1/purchase-price-changes/{id}
論理削除（del_flg更新）。

## 5. フロントエンド設計

### 5.1 仕入価格一覧 (`/purchase-prices`)
- SearchForm: 店舗(admin時)・商品名・商品コード・仕入先(SearchableSelect)
- DataTable列: 商品コード, 商品名, メーカー, 仕入先, 仕入価格, 税込価格, 直近仕入日
- 行クリック → Dialog表示:
  - 表示: 商品コード, 商品名, 現在価格(自動セット)
  - 入力: 変更後価格, 変更予定日, 変更理由(Select)
  - 送信 → POST /api/v1/purchase-price-changes

### 5.2 仕入価格変更一覧 (`/purchase-prices/changes`)
- SearchForm: 店舗(admin時)・仕入先コード・商品コード・JANコード・変更理由・変更予定日(From/To)
- DataTable列: 商品コード, 商品名, 仕入先, 変更前価格, 変更後価格, 変更予定日, 変更理由, 反映済み(Badge)
- 一括入力画面へのリンクボタン

### 5.3 仕入価格変更一括入力 (`/purchase-prices/changes/bulk-input`)
- ヘッダー部: 店舗(admin時)・仕入先(SearchableSelect)・変更予定日(DatePicker)・変更理由(Select)
- 明細部: 動的行追加テーブル
  - 列: 商品コード, 商品名(自動表示), 変更前価格(自動表示), 変更後価格(入力), 削除ボタン
  - 商品コード入力後にAPI(GET /purchase-prices?shopNo=X&goodsCode=Y)で現在価格を取得
- 「行追加」ボタン + 「登録」ボタン

### 5.4 サイドバー変更
```
仕入
├ 仕入入力 → /purchases
├ 発注入力 → /send-orders
├ 仕入価格一覧 → /purchase-prices        ← 名前変更
├ 仕入価格変更一覧 → /purchase-prices/changes    ← 新規
└ 仕入価格変更一括入力 → /purchase-prices/changes/bulk-input ← 新規
```

## 6. 変更理由の定数
旧システムの `PurchasePriceChangeReason` に準拠:
- `PU` = 値上
- `PD` = 値下
- `ES` = 販売終了

フロントエンド定数ファイル: `types/purchase-price.ts`

## 7. ファイル一覧

### バックエンド新規・変更
| ファイル | 種別 |
|---------|------|
| `api/purchase/PurchasePriceController.java` | 変更（検索パラメータ追加） |
| `api/purchase/PurchasePriceChangePlanController.java` | **新規** |
| `dto/purchase/PurchasePriceResponse.java` | 変更（フィールド拡充） |
| `dto/purchase/PurchasePriceChangePlanResponse.java` | **新規** |
| `dto/purchase/PurchasePriceChangePlanCreateRequest.java` | **新規** |
| `dto/purchase/PurchasePriceChangePlanBulkRequest.java` | **新規** |

### フロントエンド新規・変更
| ファイル | 種別 |
|---------|------|
| `components/layout/Sidebar.tsx` | 変更（メニュー名・サブメニュー追加） |
| `types/purchase-price.ts` | **新規**（型定義＋定数） |
| `components/pages/purchase-price/index.tsx` | **新規**（仕入価格一覧） |
| `components/pages/purchase-price/change-list.tsx` | **新規**（仕入価格変更一覧） |
| `components/pages/purchase-price/bulk-input.tsx` | **新規**（一括入力） |
| `components/pages/purchase-price/PriceChangeDialog.tsx` | **新規**（変更予定入力Dialog） |
| `app/(authenticated)/purchase-prices/page.tsx` | **新規** |
| `app/(authenticated)/purchase-prices/changes/page.tsx` | **新規** |
| `app/(authenticated)/purchase-prices/changes/bulk-input/page.tsx` | **新規** |

## 8. Edge Cases
- 同一商品・仕入先の変更予定が既に存在する場合: APIで重複チェック、エラー返却
- 一括入力で同一商品コードが複数行ある場合: フロント側で警告
- 変更後価格が0の場合: バリデーションでブロック
- admin(shopNo=0)の場合: 店舗セレクト表示

## 9. Risks
- 商品コード入力時のAPI呼び出し頻度: debounce(300ms)で制御
- 一括入力の明細行数上限: 100行（パフォーマンス考慮）
