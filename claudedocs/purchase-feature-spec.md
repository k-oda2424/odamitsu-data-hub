# 仕入関係 画面仕様書

**作成日**: 2026-03-31
**更新日**: 2026-04-01
**対象システム**: 小田光データ連携基盤 (OdaMitsu Data Hub)

---

## 1. 画面一覧

| # | 画面名 | URL | 実装状況 | 概要 |
|---|--------|-----|---------|------|
| 1 | 仕入入力 | `/purchases` | 実装済（一覧表示） | 仕入データの一覧表示 |
| 2 | 発注入力 | `/send-orders` | **未実装**（メニューのみ） | 発注データの管理 |
| 3 | 仕入価格一覧 | `/purchase-prices` | 実装済 | 仕入価格マスタの検索・一覧＋変更予定Dialog |
| 4 | 仕入価格変更一覧 | `/purchase-prices/changes` | 実装済 | 仕入価格変更予定の検索・一覧 |
| 5 | 仕入価格変更一括入力 | `/purchase-prices/changes/bulk-input` | 実装済 | ヘッダー＋明細の一括登録 |

---

## 2. サイドバーメニュー構成

```
仕入
├ 仕入入力           → /purchases
├ 発注入力           → /send-orders （未実装）
├ 仕入価格一覧       → /purchase-prices
├ 仕入価格変更一覧   → /purchase-prices/changes
└ 仕入価格変更一括入力 → /purchase-prices/changes/bulk-input
```

---

## 3. 画面詳細

### 3.1 仕入入力（`/purchases`）

#### 概要
仕入トランザクション（TPurchase）の一覧を表示する。

#### 検索条件
なし（全件取得）

#### テーブル列

| 列名 | フィールド | ソート |
|------|-----------|--------|
| 仕入No | purchaseNo | - |
| 仕入先 | supplierName | - |
| 仕入金額 | totalAmount | - |
| 仕入日 | purchaseDateTime | - |

#### API
- `GET /api/v1/purchases?shopNo={shopNo}&companyNo={companyNo}`
- `GET /api/v1/purchases/{purchaseNo}` （詳細取得）

---

### 3.2 仕入価格一覧（`/purchase-prices`）

#### 概要
仕入価格マスタ（MPurchasePrice）の検索・一覧表示。行クリックで仕入価格変更予定の入力Dialogを表示する。

#### 検索条件

| フィールド | 型 | コンポーネント | 備考 |
|-----------|----|-----------|----|
| 店舗 | Integer | Select | admin(shopNo=0)の場合のみ表示 |
| 商品名 | String | Input | 部分一致検索 |
| 商品コード | String | Input | 部分一致検索 |
| 仕入先 | Integer | SearchableSelect | 仕入先マスタから選択 |

#### テーブル列

| 列名 | フィールド | ソート |
|------|-----------|--------|
| 商品コード | goodsCode | ○ |
| 商品名 | goodsName | ○ |
| 仕入先 | supplierName | - |
| 仕入価格 | goodsPrice | - |
| 税込価格 | includeTaxGoodsPrice | - |
| 直近仕入日 | lastPurchaseDate | - |

#### 行クリック動作
PriceChangeDialog を表示。選択した行の情報（商品コード・商品名・仕入先・現在価格）が自動セットされる。

#### PriceChangeDialog

| フィールド | 型 | 必須 | 説明 |
|-----------|----|----|------|
| 商品コード | - | - | 表示のみ（選択行から自動セット） |
| 商品名 | - | - | 表示のみ |
| 仕入先 | - | - | 表示のみ |
| 変更前価格 | - | - | 表示のみ（現在のgoodsPrice） |
| 変更後価格 | number | ○ | 入力 |
| 変更予定日 | date | ○ | 入力 |
| 変更理由 | select | ○ | PU:値上 / PD:値下 / ES:販売終了 |

#### API
- `GET /api/v1/purchase-prices?shopNo={}&goodsName={}&goodsCode={}&supplierNo={}`
- `POST /api/v1/purchase-price-changes` （Dialog登録時）

---

### 3.3 仕入価格変更一覧（`/purchase-prices/changes`）

#### 概要
仕入価格変更予定（MPurchasePriceChangePlan）の検索・一覧表示。反映ステータスをBadgeで表示。

#### 検索条件

| フィールド | 型 | コンポーネント | 備考 |
|-----------|----|-----------|----|
| 店舗 | Integer | Select | admin時のみ表示 |
| 仕入先コード | String | Input | |
| 商品コード | String | Input | 部分一致 |
| JANコード | String | Input | 部分一致 |
| 変更理由 | String | Select | PU/PD/ES |
| 変更予定日（From） | date | Input[date] | |
| 変更予定日（To） | date | Input[date] | |

#### テーブル列

| 列名 | フィールド | ソート | 備考 |
|------|-----------|--------|------|
| 商品コード | goodsCode | ○ | |
| 商品名 | goodsName | ○ | |
| 仕入先 | supplierName | - | MSupplier経由 |
| 変更前価格 | beforePrice | - | 金額フォーマット |
| 変更後価格 | afterPrice | - | 金額フォーマット |
| 変更予定日 | changePlanDate | ○ | |
| 変更理由 | changeReason | - | ラベル表示（値上/値下/販売終了） |
| 反映 | purchasePriceReflect | - | Badge: 反映済(secondary) / 未反映(outline) |

#### アクション
- 「一括入力」ボタン → `/purchase-prices/changes/bulk-input` に遷移

#### API
- `GET /api/v1/purchase-price-changes?shopNo={}&supplierCode={}&goodsCode={}&janCode={}&changeReason={}&changePlanDateFrom={}&changePlanDateTo={}`

---

### 3.4 仕入価格変更一括入力（`/purchase-prices/changes/bulk-input`）

#### 概要
複数商品の仕入価格変更予定を一括登録する。ヘッダー部（共通情報）＋明細部（商品ごとの価格変更）の2段構成。

#### ヘッダー部

| フィールド | 型 | コンポーネント | 必須 | 備考 |
|-----------|----|-----------|----|------|
| 店舗 | Integer | Select | ○ | admin時のみ表示 |
| 仕入先 | Integer | SearchableSelect | ○ | clearable=false |
| 変更予定日 | date | Input[date] | ○ | |
| 変更理由 | String | Select | ○ | PU/PD/ES |

#### 明細部（動的行追加テーブル）

| 列名 | 型 | 入力 | 備考 |
|------|----|----|------|
| 商品コード | String | Input | blur時にAPI呼び出しで商品名・変更前価格を自動取得 |
| 商品名 | String | 表示のみ | 商品コードから自動取得 |
| 変更前価格 | number | 表示のみ | 商品コードから自動取得 |
| 変更後価格 | number | Input | 必須入力 |
| 削除 | - | Button | 行削除（1行以上は必須） |

#### 商品コード入力時の自動取得
商品コードのblurイベントで `GET /api/v1/purchase-prices?shopNo={}&goodsCode={}&supplierNo={}` を呼び出し、該当の商品名・現在価格を明細行に自動セットする。

#### アクション
- 「行追加」ボタン → 明細行を1行追加
- 「一括登録」ボタン → `POST /api/v1/purchase-price-changes/bulk` で一括送信
- 「変更一覧に戻る」ボタン → `/purchase-prices/changes` に遷移

#### API
- `POST /api/v1/purchase-price-changes/bulk`

リクエストボディ:
```json
{
  "shopNo": 1,
  "supplierCode": "SUP001",
  "changePlanDate": "2026-05-01",
  "changeReason": "PU",
  "details": [
    {
      "goodsCode": "SG001",
      "goodsName": "テスト商品A",
      "beforePrice": 100,
      "afterPrice": 120
    }
  ]
}
```

---

## 4. API一覧

### 仕入トランザクション

| メソッド | エンドポイント | 説明 |
|---------|-------------|------|
| GET | `/api/v1/purchases` | 仕入一覧（shopNo, companyNo） |
| GET | `/api/v1/purchases/{purchaseNo}` | 仕入詳細 |

### 仕入価格マスタ

| メソッド | エンドポイント | 説明 |
|---------|-------------|------|
| GET | `/api/v1/purchase-prices` | 仕入価格一覧（shopNo, goodsName, goodsCode, supplierNo） |

### 仕入価格変更予定

| メソッド | エンドポイント | 説明 |
|---------|-------------|------|
| GET | `/api/v1/purchase-price-changes` | 変更予定一覧（shopNo, supplierCode, goodsCode, janCode, changeReason, changePlanDateFrom/To） |
| POST | `/api/v1/purchase-price-changes` | 変更予定登録（単品） |
| POST | `/api/v1/purchase-price-changes/bulk` | 変更予定一括登録 |
| DELETE | `/api/v1/purchase-price-changes/{id}` | 変更予定論理削除 |

### 発注

| メソッド | エンドポイント | 説明 |
|---------|-------------|------|
| GET | `/api/v1/send-orders` | 発注一覧（shopNo） |

---

## 5. データモデル

### MPurchasePrice（仕入価格マスタ）
テーブル: `m_purchase_price`

| カラム | 型 | 説明 |
|--------|----|----|
| purchase_price_no | Integer (PK) | 仕入価格番号 |
| supplier_no | Integer | 仕入先番号 |
| shop_no | Integer | 店舗番号 |
| goods_no | Integer | 商品番号 |
| partner_no | Integer | 得意先番号（0=基本価格） |
| destination_no | Integer | 届先番号（0=基本価格） |
| goods_price | BigDecimal | 仕入価格（税抜） |
| include_tax_goods_price | BigDecimal | 仕入価格（税込） |
| tax_rate | BigDecimal | 税率 |
| tax_category | int | 税区分 |
| include_tax_flg | String | 税込フラグ |
| last_purchase_date | LocalDate | 直近仕入日 |
| period_from / period_to | LocalDate | 有効期間 |

**ユニークキー**: (supplier_no, shop_no, goods_no, partner_no, destination_no)

**リレーション**: MGoods, MShop, MSupplier, MPartner, WSalesGoods

### MPurchasePriceChangePlan（仕入価格変更予定マスタ）
テーブル: `m_purchase_price_change_plan`

| カラム | 型 | 説明 |
|--------|----|----|
| purchase_price_change_plan_no | Integer (PK) | 変更予定番号 |
| shop_no | Integer | 店舗番号 |
| goods_code | String | 商品コード |
| goods_name | String | 商品名（非正規化） |
| jan_code | String | JANコード |
| supplier_code | String | 仕入先コード |
| before_price | BigDecimal | 変更前価格 |
| after_price | BigDecimal | 変更後価格 |
| change_plan_date | LocalDate | 変更予定日 |
| change_reason | String | 変更理由（PU/PD/ES） |
| change_contain_num | BigDecimal | 入数変更 |
| partner_no | Integer | 得意先番号 |
| destination_no | Integer | 届先番号 |
| partner_price_change_plan_created | boolean | 得意先価格連動済み |
| purchase_price_reflect | boolean | 価格反映済み |

**リレーション**: MSupplier（shop_no + supplier_code で結合）

### MPurchasePriceLog（仕入価格変更履歴）
テーブル: `m_purchase_price_log`

MPurchasePriceと同構造。価格変更時に自動記録される。

---

## 6. 変更理由コード

| コード | 表示名 | 説明 |
|--------|--------|------|
| PU | 値上 | Price Up |
| PD | 値下 | Price Down |
| ES | 販売終了 | End of Sale |

---

## 7. バッチ処理との連携

### 価格反映フロー

```
① 画面で変更予定を登録（MPurchasePriceChangePlan）
     ↓
② バッチ: PartnerPriceChangePlanCreateTasklet
   → 得意先商品価格変更予定を自動作成
   → partner_price_change_plan_created = true
     ↓
③ バッチ: PurchasePriceChangeReflectTasklet（日次想定）
   → change_plan_date <= 今日 のレコードをMPurchasePriceに反映
   → purchase_price_reflect = true
   → MPurchasePriceLogに履歴記録
```

### 仕入実績からの価格自動作成

```
仕入トランザクション（TPurchaseDetail）
     ↓
バッチ: PurchasePriceCreateTasklet
     ↓
MPurchasePrice に自動登録（purchase_price_reflect = Y）
```

---

## 8. ファイル構成

### フロントエンド

```
frontend/
├── app/(authenticated)/
│   ├── purchases/page.tsx
│   ├── purchase-prices/
│   │   ├── page.tsx
│   │   └── changes/
│   │       ├── page.tsx
│   │       └── bulk-input/page.tsx
├── components/pages/
│   ├── purchase/
│   │   └── index.tsx              # 仕入入力
│   └── purchase-price/
│       ├── index.tsx              # 仕入価格一覧
│       ├── change-list.tsx        # 仕入価格変更一覧
│       ├── bulk-input.tsx         # 仕入価格変更一括入力
│       └── PriceChangeDialog.tsx  # 変更予定入力Dialog
├── types/
│   └── purchase-price.ts         # 型定義・定数
└── e2e/
    └── purchase-price.spec.ts    # E2Eテスト（15テスト）
```

### バックエンド

```
backend/src/main/java/jp/co/oda32/
├── api/purchase/
│   ├── PurchaseController.java
│   ├── PurchasePriceController.java
│   ├── PurchasePriceChangePlanController.java
│   └── SendOrderController.java
├── dto/purchase/
│   ├── PurchaseResponse.java
│   ├── PurchasePriceResponse.java
│   ├── PurchasePriceChangePlanResponse.java
│   ├── PurchasePriceChangePlanCreateRequest.java
│   ├── PurchasePriceChangePlanBulkRequest.java
│   └── SendOrderResponse.java
├── domain/model/purchase/
│   ├── MPurchasePrice.java
│   ├── MPurchasePriceChangePlan.java
│   ├── MPurchasePriceLog.java
│   ├── TPurchase.java
│   ├── TPurchaseDetail.java
│   ├── TSendOrder.java
│   └── TSendOrderDetail.java
├── domain/repository/purchase/  (8 files)
├── domain/service/purchase/     (8 files)
├── domain/specification/purchase/ (7 files)
└── batch/purchase/
    ├── PurchasePriceCreateTasklet.java
    └── PurchasePriceChangeReflectTasklet.java
```

---

## 9. 未実装・今後の課題

| # | 項目 | 優先度 | 備考 |
|---|------|--------|------|
| 1 | 発注入力画面（`/send-orders`） | 高 | メニューに存在するが画面未実装 |
| 2 | 仕入価格一覧のページネーション | 中 | データ件数増加時のパフォーマンス対策 |
| 3 | 仕入価格変更予定の編集・更新機能 | 中 | 現在は登録・削除のみ |
| 4 | 仕入価格変更の重複チェック | 中 | 同一商品・仕入先の重複登録防止 |
| 5 | SendOrderControllerのService層化 | 低 | 現在Repository直接注入 |
| 6 | 仕入詳細画面（入力・編集） | 高 | 旧システムの多段階フォーム相当 |
