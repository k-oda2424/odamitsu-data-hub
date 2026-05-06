# B-CART連携仕様書

## 目次

1. [概要](#1-概要)
2. [Web画面：出荷情報入力](#2-web画面出荷情報入力)
3. [B-CART REST API連携](#3-b-cart-rest-api連携)
4. [バッチ処理](#4-バッチ処理)
5. [データ変換・マッピング](#5-データ変換マッピング)
6. [エンティティ定義](#6-エンティティ定義)
7. [エラーハンドリング](#7-エラーハンドリング)

---

## 1. 概要

### 1.1 システム位置づけ

B-CART（ECシステム）と本社内システムを双方向に連携させる機能群。主な目的は以下の通り：

- B-CARTで受注した注文データを本システム（SMILE基幹システム）へ取り込む
- 本システムの商品価格をB-CARTへ反映する
- 本システムで管理する出荷情報（送り状番号・出荷日等）をB-CARTへ連携する
- B-CARTの会員データを本システムの得意先マスタと同期する

### 1.2 連携フロー全体図

```
B-CART (ECサイト)
    |
    | REST API (Bearer Token認証)
    |
    v
本システム（バッチ処理）
    |
    |-- 注文取込        --> b_cart_order / b_cart_order_product / b_cart_logistics
    |-- 会員取込        --> b_cart_member / b_cart_member_other_addresses
    |-- 商品取込        --> b_cart_products / b_cart_product_sets
    |-- 価格更新        --> B-CART API PATCH /product_sets/{id}
    |-- 出荷CSV出力     --> B-CART取込用CSVファイル（Shift_JIS）
    |
    v
SMILE（基幹システム）
    |-- 得意先マスタCSV --> smile_partner_output_file
    |-- 納品先マスタCSV --> smile_destination_output_file
    |-- 売上取込CSV    --> smile_order_output_file
```

### 1.3 技術スタック

| 項目 | 内容 |
|------|------|
| HTTPクライアント | OkHttp |
| JSONパース | Gson |
| バッチフレームワーク | Spring Batch |
| 対象API | B-CART REST API v1 |
| 認証方式 | Bearer Token（JWT） |

---

## 2. Web画面：出荷情報入力

### 2.1 画面概要

| 項目 | 内容 |
|------|------|
| 画面名 | B-Cart出荷情報入力 |
| URL（GET） | `/bcart/shippingInputForm` |
| テンプレート | `src/main/resources/templates/bcart/bcart_shipping_info_input.html` |
| コントローラ | `jp.co.oda32.app.bcart.BCartShippingInputController` |

### 2.2 エンドポイント一覧

| HTTP メソッド | URL | 説明 |
|--------------|-----|------|
| GET | `/bcart/shippingInputForm` | 画面初期表示（未発送・発送指示の一覧を表示） |
| POST | `/bcart/bcart_shipping_search` | ステータス条件による検索 |
| POST | `/bcart/bcart_shipping_update` | 出荷情報の個別更新 |
| POST | `/bcart/bcart_shipping_bulk_update` | チェックボックスで選択した行の一括ステータス更新 |

### 2.3 画面表示項目

画面はテーブル形式で1件の出荷（`b_cart_logistics`）を2行で表示する。

#### 第1行

| 列 | 内容 | 入力/表示 |
|----|------|----------|
| 選択チェックボックス | 一括操作対象の選択 | チェックボックス |
| 得意先コード | SMILEの得意先コード | 表示 |
| b-cart LogisticsID | `b_cart_logistics.id` | 表示 |
| 届け先 | `b_cart_logistics.comp_name` | 表示（2行span） |
| 商品コード：商品名：数量 | SMILE商品コード、商品名、数量のリスト | 表示（2行span） |
| 送り状番号 | `b_cart_logistics.delivery_code` | 入力（text） |
| 出荷ステータス | `BcartShipmentStatus`のセレクト | セレクト（2行span） |
| メモ | `b_cart_logistics.memo` | テキストエリア |

#### 第2行

| 列 | 内容 | 入力/表示 |
|----|------|----------|
| 得意先名 | SMILEの得意先名 | 表示 |
| smile伝票番号 | `t_smile_order_import_file.slip_number` のリスト | 表示 |
| 出荷日 | `b_cart_logistics.shipment_date` | 入力（datepicker） |
| 得意先へ連絡事項 | `b_cart_order.admin_message` | テキストエリア |

### 2.4 出荷ステータス定義

`jp.co.oda32.constant.BcartShipmentStatus`

| 列挙値 | 表示名 | 説明 |
|--------|--------|------|
| `NOT_SHIPPED` | 未発送 | 出荷指示前の初期状態 |
| `SHIPPING_INSTRUCTED` | 発送指示 | 出荷指示を出した状態。このステータスでSMILE連携バッチが対象として取込む |
| `SHIPPED` | 発送済 | B-CARTに送り状番号等を送信済みの状態 |
| `EXCLUDED` | 対象外 | 一覧から除外する不要な出荷項目。通常の検索条件には表示されない |

#### デフォルト表示対象

初期表示・ステータス未選択検索時は「未発送」と「発送指示」のみ表示する。

```java
List<String> statuses = Arrays.asList(
    BcartShipmentStatus.NOT_SHIPPED.getDisplayName(),    // 未発送
    BcartShipmentStatus.SHIPPING_INSTRUCTED.getDisplayName() // 発送指示
);
```

### 2.5 更新処理の制約

出荷済みかつB-CART CSV出力済み（`b_cart_csv_exported = true`）の行は更新対象から除外される。

```java
.filter(logistics -> !(BcartShipmentStatus.SHIPPED.getDisplayName().equals(logistics.getStatus())
        && logistics.isBCartCsvExported()))
```

### 2.6 注文ステータスの自動更新

出荷情報を更新した際、関連する`b_cart_order`のステータスも以下のルールで自動更新される。

`jp.co.oda32.constant.BCartOrderStatus`

| 状況 | B-CART注文ステータス | 内容 |
|------|---------------------|------|
| 全出荷が発送済 | `完了` | 全ての`b_cart_logistics`が「発送済」になった場合 |
| 一部のみ発送済 | `カスタム1`（処理中） | 一部のみ「発送済」の場合 |
| 新規注文 | `新規注文` | B-CARTからの初期ステータス |
| キャンセル | `カスタム2` | キャンセル処理 |

### 2.7 商品コード解決ロジック（画面）

`BCartShippingInputController.getSmileProductCode()` により、B-CARTの品番をSMILEの商品コードに変換する。

| 条件 | 変換内容 |
|------|----------|
| 商品名が「送料」 | `Constants.SHIPPING_FEE_PRODUCT_CODE`（固定の送料コード） |
| 品番が`case_`で始まる | `case_`を除去した残りを品番とする |
| 品番に`_`を含む | `_`で分割し最後の要素を品番とする |
| `w_sales_goods`に登録なし | `Constants.FIXED_PRODUCT_CODE`（手入力商品コード） |

### 2.8 一括ステータス更新

一覧のチェックボックスで選択した行をまとめて指定のステータスに変更できる。

- エンドポイント: `POST /bcart/bcart_shipping_bulk_update`
- パラメータ:
  - `selectedItems`: 選択行のインデックスリスト
  - `status`: 更新先ステータス（`BcartShipmentStatus`）
- B-CART CSV出力済みの発送済行は除外される
- 更新後は未発送・発送指示の一覧を再取得して表示する

---

## 3. B-CART REST API連携

### 3.1 基本情報

| 項目 | 内容 |
|------|------|
| ベースURL | `https://api.bcart.jp/api/v1` |
| 認証方式 | Bearer Token（JWT形式） |
| 認証ヘッダー | `Authorization: Bearer {accessToken}` |
| レスポンス形式 | JSON |
| アクセストークン管理 | `jp.co.oda32.constant.BCartApiConfig`（シングルトン、ハードコード） |

### 3.2 レート制限

B-CART APIには以下のレート制限が設定されている。

| 制限内容 | 値 |
|----------|-----|
| 最大リクエスト数 | 250回/5分（他のAPIも含む合計） |
| 実装上の制御 | 250件ごとに5分間スリープ |

```java
if (++count % 250 == 0) {
    // 5分300回制限があるため250件ずつに分割(他のAPIが叩けなくなるため)
    Thread.sleep(1000 * 60 * 5); // 5分待機
}
```

### 3.3 APIアクセストークン

`BCartApiConfig`クラスにJWTトークンがハードコードされている。トークンにはスコープが付与されており、対象APIの読み書き権限を保持する。

主なスコープ:
- `products-read` / `products-write`
- `product_sets-read` / `product_sets-write`
- `product_stock-read` / `product_stock-write`
- `customers-read` / `customers-write`
- `other_addresses-read`
- `orders-read` / `orders-write`
- `order_products-read` / `order_products-write`
- `logistics-read` / `logistics-write`

### 3.4 APIエンドポイント一覧

#### 3.4.1 customers（会員）API

| 項目 | 内容 |
|------|------|
| エンドポイント | `GET https://api.bcart.jp/api/v1/customers` |
| 用途 | 会員情報の取得（会員インポートバッチ） |
| レスポンスキー | `customers`（配列） |
| ページング | `limit`（最大100）, `offset` |
| 時刻フィルタ | `created_at_min` または `updated_at_min`（前回実行時刻） |
| 除外条件 | ステータスが「未承認」または「無効」の会員は除外 |

クエリパラメータ例:
```
GET /api/v1/customers?limit=100&offset=0&created_at_min=2024-01-01+00:00:00
```

#### 3.4.2 other_addresses（別配送先）API

| 項目 | 内容 |
|------|------|
| エンドポイント | `GET https://api.bcart.jp/api/v1/other_addresses` |
| 用途 | 会員の別配送先情報取得 |
| レスポンスキー | `other_addresses`（配列） |
| ページング | なし（全件取得） |

#### 3.4.3 orders（注文）API

| 項目 | 内容 |
|------|------|
| エンドポイント | `GET https://api.bcart.jp/api/v1/orders` |
| 用途 | 新規注文の取得 |
| レスポンスキー | `orders`（配列） |
| フィルタパラメータ | `status=新規注文`, `complete=1` |

クエリパラメータ例:
```
GET /api/v1/orders?status=新規注文&complete=1
```

#### 3.4.4 products（商品）API

| 項目 | 内容 |
|------|------|
| エンドポイント | `GET https://api.bcart.jp/api/v1/products` |
| 用途 | 商品マスタ情報の取得 |
| レスポンスキー | `products`（配列） |
| ページング | `limit`（最大100）, `offset` |
| 終了条件 | レスポンス配列が空になったら終了 |

クエリパラメータ例:
```
GET /api/v1/products?limit=100&offset=0
GET /api/v1/products?limit=100&offset=100
...
```

#### 3.4.5 product_sets（商品セット）API

| 項目 | 内容 |
|------|------|
| エンドポイント（取得） | `GET https://api.bcart.jp/api/v1/product_sets` |
| エンドポイント（更新） | `PATCH https://api.bcart.jp/api/v1/product_sets/{id}` |
| 用途（取得） | 商品セット情報（品番・単価・在庫等）の取得 |
| 用途（更新） | 本システムの価格をB-CARTへ反映 |
| レスポンスキー | `product_sets`（配列） |
| ページング | `limit`（最大100）, `offset` |

PATCH リクエストボディ例:
```json
{
  "unit_price": 1000,
  "special_price": {
    "会員ID": {
      "unit_price": 900
    }
  },
  "volume_discount": {
    "10": 950,
    "20": 900
  },
  "customs": [
    {
      "field_id": 9,
      "value": "500"
    }
  ]
}
```

`customs[field_id=9]`は仕入価格（下代）を格納するカスタムフィールド。

---

## 4. バッチ処理

### 4.1 バッチジョブ一覧

| ジョブ名（Bean名） | クラス | 説明 | 実行方式 |
|-------------------|--------|------|----------|
| `bCartOrderImportJob` | `BCartOrderImportBatch` | 新規注文取込・SMILE連携CSV出力 | CLIバッチ |
| `bCartOrderImportJobForWEB` | `BCartOrderImportBatchForWEB` | 同上（Web画面からの実行用） | Web起動 |
| `bCartMemberUpdateJob` | `BCartMemberUpdateBatch` | 会員・配送先取込・SMILE連携CSV出力 | CLIバッチ |
| `bCartMemberUpdateJobForWEB` | `BCartMemberUpdateBatchForWEB` | 同上（Web画面からの実行用） | Web起動 |
| `bCartLogisticsCsvExportJob` | `BCartLogisticsCsvExportBatch` | 出荷実績CSV出力 | CLIバッチ |
| `bCartLogisticsCsvExportJobForWEB` | `BCartLogisticsCsvExportBatchForWEB` | 同上（Web画面からの実行用） | Web起動 |
| `smileOrderImportAndBCartOrderUpdateJob` | `SmileOrderImportAndBCartOrderUpdateBatch` | SMILE注文取込後・B-CART処理連番更新 | Web起動 |
| `bCartProductsImportJob`（旧`job`） | `BCartProductsImportBatch` | 商品・商品セット取込 | CLIバッチ |
| ~~`bCartPriceUpdateJob`~~ | ~~`BCartPriceUpdateBatch`~~ | 価格更新 → 2026-05-01 廃止し REST 同期 API (`/api/v1/bcart/pending-changes/reflect`) に置換 | — |

### 4.2 bCartOrderImportJob（注文取込ジョブ）

#### ジョブフロー

```
Step1: bCartOrderRegisterStep        (BCartOrderRegisterTasklet)
Step2: bCartOrderConvertSmileOrderFileStep  (BCartOrderConvertSmileOrderFileTasklet)
Step3: bSmileDestinationFileOutPutStep      (SmileDestinationFileOutPutTasklet)
Step4: smileOrderFileOutPutStep             (SmileOrderFileOutPutTasklet)
```

#### Step1: BCartOrderRegisterTasklet（注文情報登録）

- B-CART `/orders` APIから`status=新規注文`の注文を取得する
- `b_cart_order`、`b_cart_order_product`、`b_cart_logistics`テーブルに保存する
- 既存レコードはカスタムSQLで更新（JSONB型カラムはNULLに設定）
- APIレスポンスのGsonデシリアライズには`CustomNumberDeserializer`を使用し型変換エラーを防ぐ

#### Step2: BCartOrderConvertSmileOrderFileTasklet（SMILE連携データ変換）

対象条件:
- `b_cart_logistics.status = 発送指示`
- `b_cart_logistics.arrival_date`（納品日）が設定されている
- `t_smile_order_import_file`に未登録のもの

処理内容:
1. 消費税率をマスタから取得（取得失敗時は例外をスローしジョブ停止）
2. 出荷指示済みの`b_cart_logistics`を取得し、既存登録済みを除外
3. 各`b_cart_logistics`について`t_smile_order_import_file`データを生成
4. 送料行は`b_cart_logistics`に紐づかない場合も追加対応
5. 重複チェック後DBに保存
6. 行番号とシーケンス採番による処理連番（仮）を付与

SMILE連携レコードのフィールド設定:

| フィールド | 設定内容 |
|-----------|----------|
| `slip_number` | `b_cart_logistics.id`（整数） |
| `customer_code` | `b_cart_member.ext_id`（SMILEの得意先コード） |
| `customer_comp_name` | `b_cart_order.customer_comp_name`（最大48文字、半角変換） |
| `delivery_code` | DeliveryMappingによる変換コード（異なる納品先の場合のみ） |
| `product_code` | SMILEの商品コード（品番変換後） |
| `product_name` | SMILEのgoods_name（最大36文字、半角変換） |
| `quantity` | セット入数 × 注文数 |
| `unit_price` | `b_cart_order_product.unit_price` |
| `original_unit_price` | `b_cart_order_product.set_custom3`（仕入価格） |
| `tax_rate` | `b_cart_order_product.tax_rate × 100`（例：10.0） |
| `remarks` | admin_message または customer_message（最大36文字） |
| `person_in_charge_code` | 固定値`"5"`（小田一輝） |
| `detail_type` | `SmileOrderDetailType.NORMAL` |
| `processing_serial_number` | シーケンス採番（`w_smile_order_import_file_processing_serial_number_seq`） |

軽減税率対応:
- 消費税区分が軽減税率の場合、伝票番号の先頭を「8」に変換する
- 例：`23456789` → `83456789`（8桁で先頭が2の場合）

#### Step3: SmileDestinationFileOutPutTasklet（納品先マスタCSV出力）

- `x_delivery_mapping`から`smile_csv_outputted = false`のレコードを取得
- SMILE納品先マスタインポート用CSVを出力（UTF-8）
- 出力後に`smile_csv_outputted = true`に更新

CSVヘッダー:
```
得意先コード, 納品先コード, 納品先名, 納品先索引, 荷受け人名１, 荷受け人名２,
郵便番号, 住所１, 住所２, 住所３, 電話番号, ＦＡＸ番号, マスター検索表示区分
```

#### Step4: SmileOrderFileOutPutTasklet（SMILE注文CSV出力）

- `t_smile_order_import_file`から`csv_exported = false`のレコードを取得
- 伝票番号・行番号でソートして出力（軽減税率混在時のSMILE連番を考慮）
- SMILE売上取込用CSVを出力（UTF-8）
- 出力後に`csv_exported = true`に更新

CSVヘッダー（SmileOrderImportCsvHeader.CSV_HEADERS）:
```
伝票日付, 伝票№, 処理連番, 明細区分, 行, 得意先コード, 得意先名１, 得意先名２,
納品先コード, 納品先名, 担当者コード, 請求区分, 売掛区分, 取引区分, 取引区分属性,
商品コード, 商品名, 入数, 個数, 個数単位, 数量, 数量単位, 単価, 金額, 原単価, 原価金額,
粗利, 単価掛率, 課税区分, 消費税率, 内消費税等, 行摘要コード, 行摘要１, 行摘要２,
備考コード, 備考, 受注№, 受注行, 見積処理連番, 見積行, 自動生成区分,
伝票消費税計算区分, データ発生区分, 相手処理連番, 入力ﾊﾟﾀｰﾝ№, 伝票番号,
相手伝票番号, コード, コード, 社店コード, 分類コード, 伝票区分, 取引先コード,
売単価, 相手商品コード, チェックマーク区分, 消費税分類
```
（計58列）

### 4.3 bCartMemberUpdateJob（会員更新ジョブ）

#### ジョブフロー

```
Step1: bCartMemberImportStep          (BCartMemberImportTasklet)
Step2: bCartMemberDeliveryImportStep  (BCartMemberDeliveryImportTasklet)
Step3: smilePartnerFileOutPutStep     (SmilePartnerFileOutPutTasklet)
Step4: registerBCartMemberStep        (RegisterBCartMemberTasklet)
```

#### Step1: BCartMemberImportTasklet（会員情報取込）

- B-CART `/customers` APIから会員情報を取得
- `created_at_min`と`updated_at_min`の2パターンで実行（新規登録・更新）
- 前回のジョブ実行時刻を`JobExplorer`から取得してフィルタ条件に使用
- 「未承認」「無効」ステータスの会員は除外
- `smilePartnerMasterLinked = false`に設定して保存
- `ext_id`がAPIから取得できない場合は既存のext_idを保持する

ページング処理:
- `limit=100`, `offset`を100ずつ増加
- レスポンスが`limit`未満になったら終了

#### Step2: BCartMemberDeliveryImportTasklet（別配送先情報取込）

- B-CART `/other_addresses` APIから全会員の別配送先を取得
- `b_cart_member_other_addresses`テーブルにUPSERTする
- ページングなし（全件取得）

#### Step3: SmilePartnerFileOutPutTasklet（SMILE得意先マスタCSV出力）

- `b_cart_member`から`smile_partner_master_linked = false`のレコードを取得
- SMILE得意先マスタインポート用CSVを出力（UTF-8）
- 出力後に`smile_partner_master_linked = true`に更新

CSVの主要フィールド（SmilePartnerImportCsvHeader.CSV_HEADERS、計69列）:

| SMILE項目 | B-CART会員フィールド | 備考 |
|-----------|---------------------|------|
| 得意先コード | `ext_id` | 6桁 |
| 得意先名１ | `comp_name` | 48桁 |
| 得意先名索引 | `comp_name_kana`（半角変換） | 10桁 |
| 郵便番号 | `zip` | 10桁 |
| 住所１ | `pref` + `address1` | 48桁 |
| 住所２ | `address2` | 48桁 |
| 住所３ | `address3` | 48桁 |
| 電話番号 | `tel` | 15桁 |
| ＦＡＸ番号 | `fax` | 15桁 |
| 担当者コード | 固定値`"000005"` | |
| 相手先担当者名 | `tanto_last_name` + `tanto_first_name` | 32桁 |

#### Step4: RegisterBCartMemberTasklet（得意先マスタ登録）

- `b_cart_member`の`ext_id`が設定されており、`m_partner`テーブルに存在しない会員を取得
- `m_company`に会社情報を新規登録
- `m_partner`に得意先情報を新規登録（`partner_code = ext_id`）
- `m_company.partner_no`を更新

### 4.4 bCartLogisticsCsvExportJob（出荷実績CSV出力ジョブ）

#### ジョブフロー

```
Step1: bCartLogisticsCsvOutputStep  (BCartLogisticsCsvOutputTasklet)
```

#### BCartLogisticsCsvOutputTasklet（出荷実績CSV出力）

対象条件（いずれか）:
- `b_cart_csv_exported = false`かつステータスが「発送指示」または「発送済」
- `b_cart_csv_exported = true`かつ`is_updated = true`（更新あり）

出力ファイル形式: Shift_JIS（B-CAR取込用）

CSVヘッダー（`BCartLogisticsCsv.CSV_HEADERS`）:
```
Bカート発送ID, 送り状番号, 発送日, 出荷管理番号, 発送状況, 発送メモ, お客様への連絡事項, 対応状況
```

| CSV列 | ソースフィールド |
|-------|----------------|
| Bカート発送ID | `b_cart_logistics.id` |
| 送り状番号 | `b_cart_logistics.delivery_code` |
| 発送日 | `b_cart_logistics.shipment_date` |
| 出荷管理番号 | `b_cart_logistics.shipment_code`（SMILEの処理連番） |
| 発送状況 | `b_cart_logistics.status` |
| 発送メモ | `b_cart_logistics.memo` |
| お客様への連絡事項 | `b_cart_order.admin_message` |
| 対応状況 | `b_cart_order.status` |

出力後の更新:
- `is_updated = false`
- `b_cart_csv_exported = true`

### 4.5 bCartProductsImportJob（商品取込ジョブ）

#### ジョブフロー

```
Step1: bCartProductsImportStep     (BCartProductsImportTasklet)
Step2: bCartProductSetsImportStep  (BCartProductSetsImportTasklet)
```

#### Step1: BCartProductsImportTasklet（商品マスタ取込）

- B-CART `/products` APIから全商品を取得
- `b_cart_products`テーブルに保存
- `limit=100`でオフセットをインクリメント、レスポンスが空になったら終了

#### Step2: BCartProductSetsImportTasklet（商品セット取込）

- B-CART `/product_sets` APIから全商品セットを取得
- `b_cart_product_sets`テーブルに保存
- 数量割引（`volume_discount`）、特別価格（`special_price`）、グループ価格（`group_price`）も合わせて処理

特別価格（`b_cart_special_price`）処理:
- キーが会員ID（カンマ区切りで複数可）
- 本システムからB-CARTへ価格反映済み（`b_cart_price_reflected = true`）のもののみ更新
- インポートデータにないものは削除

グループ価格（`b_cart_group_price`）処理:
- キーがグループID
- グループ名・割引率・単価・固定価格・数量割引を管理

価格更新フラグ管理:
- `b_cart_product_sets.b_cart_price_reflected = false`の場合、APIからの単価で上書きしない（本システムで変更した価格を保持）

### 4.6 価格・配送サイズ反映 — REST 同期 API（旧 bCartPriceUpdateJob を置換）

> **2026-05-01 更新**: 旧 `BCartGoodsPriceTableUpdateTasklet` は空スタブのまま長らく未実装だった。Phase 3-A で **同期 REST API に置換** した。バッチではなく UI からの即時反映で、結果が画面に直接返るため UX が良い。詳細は `claudedocs/design-bcart-pending-changes.md` 参照。

#### エンドポイント

- `PUT /api/v1/bcart/product-sets/{setId}/pricing` — `unit_price` / `shipping_size` をローカル DB 編集 + 履歴記録（`b_cart_change_history`、`b_cart_price_reflected=false`）
- `GET /api/v1/bcart/pending-changes` — 未反映商品セットの diff（最古 before / 最新 after に集約）一覧
- `POST /api/v1/bcart/pending-changes/reflect` — 指定セット or 全件を `PATCH /api/v1/product_sets/{id}` でB-CART反映（最大 200 件、超過時 400）
- `GET /api/v1/bcart/pending-changes/count` — サイドバーバッジ用件数

#### スコープ

- 今回反映対象: `unit_price`, `shipping_size` の 2 フィールドのみ
- `group_price` / `special_price` / `volume_discount` は **Phase 3-B 以降**（複雑度高、JSON 構造で別エンドポイント想定）
- `purchase_price` は社内データのため B-CART 反映対象外

#### UI

- 編集: `/bcart/products/{id}` の「セット一覧」タブにインライン編集 UI（行ごとの 💾 ボタン）
- 反映: サイドバー「B-CART」 →「B-CART変更点一覧」(`/bcart/pending-changes`) でチェック選択 or 全件反映

### 4.7 smileOrderImportAndBCartOrderUpdateJob（SMILE注文取込・処理連番更新ジョブ）

#### ジョブフロー（主要ステップのみ）

```
Step1: WSmileOrderFileTruncateStep
Step2: smileOrderFileImportStep
Step3: smileOrderImportStep
Step4: orderStatusUpdateStep
Step5: bCartOrderProcessingSerialNumberUpdateStep  (BCartOrderProcessingSerialNumberUpdateTasklet)
Step6: vSalesMonthlySummaryRefreshStep
```

#### BCartOrderProcessingSerialNumberUpdateTasklet（処理連番更新）

- SMILEが売上取込後に採番した実処理連番を`t_smile_order_import_file`に反映
- `b_cart_logistics.shipment_code`を`t_smile_order_import_file.processing_serial_number`で更新（SMILE処理連番をB-CART出荷管理番号に設定）

---

## 5. データ変換・マッピング

### 5.1 B-CART注文からSMILE連携ファイルへの変換

`BCartOrderConvertSmileOrderFileTasklet.convertSmileOrderImportFile()`

#### 変換概要

```
BCartLogistics (b_cart_logistics)
  └── BCartOrderProduct[] (b_cart_order_product)
        └── BCartOrder (b_cart_order)
              └── BCartMember (b_cart_member) → ext_id → SMILE得意先コード
```

#### 得意先コードマッピング（customerCodeMapper）

1. キャッシュ（`bCartMemberMap`）に`bCartCustomerId`が存在すれば`ext_id`を返す
2. キャッシュにない場合は`b_cart_member`テーブルを検索
3. `ext_id`が未設定の場合は例外をスローしてジョブを停止させる

#### 品番変換ルール（getSmileProductCode）

| 条件 | 変換内容 |
|------|----------|
| `product_name == "送料"` | `SHIPPING_FEE_PRODUCT_CODE`（固定送料コード） |
| `product_no`が`"case_"`で始まる | `"case_"`プレフィックスを除去 |
| `product_no`に`"_"`を含む | `"_"`で分割し最後の要素 |
| `w_sales_goods`に登録なし | `FIXED_PRODUCT_CODE`（手入力商品コード）、商品名をbcartの商品名から設定 |

#### 数量計算

- セット販売（`set_name`に「単品」を含まない場合）:
  - `set_quantity`を入数として設定
  - `order_pro_count`をケース数として設定
  - `quantity = set_quantity × order_pro_count`
- バラ単品の場合:
  - `set_quantity = null`

#### 原価（仕入価格）設定

`b_cart_order_product.set_custom3`フィールドに格納された仕入価格を使用。数値変換に失敗した場合はゼロを設定してログ出力。

#### 行摘要2の設定

`b_cart_member.need_smile_order_file_goods_code = true`の会員の場合、行摘要2に商品コードを設定する（ゆうあい等の特定取引先向け対応）。

### 5.2 DeliveryMapping（納品先コード変換）

`BCartOrderConvertSmileOrderFileTasklet.deliveryCodeMapper()`

B-CARTの配送先情報をSMILEの納品先コードに変換・管理するテーブル（`x_delivery_mapping`）。

#### 変換ロジック

1. 注文者の会社名+部署名と配送先の会社名+部署名が同一の場合は納品先コード不要（スキップ）
2. 異なる場合は`b_cart_customer_id`で既存マッピングを検索
3. `b_cart_logistics.destination_code`が設定されている場合、`smile_delivery_code`と一致するマッピングを返す
4. `delivery_name`（会社名+部署名）が一致するマッピングを検索
5. 既存マッピングが見つかった場合:
   - 内容が変化していなければそのまま`smile_delivery_code`を返す
   - 変化している場合は更新して`smile_csv_outputted = false`にセット
6. 新規の場合: 6桁の連番コード（`000001`以降）を採番して新規登録

#### マッピングテーブル（x_delivery_mapping）フィールド

| フィールド | 内容 |
|-----------|------|
| `b_cart_customer_id` | B-CARTの会員ID |
| `delivery_name` | 配送先会社名+部署名 |
| `smile_delivery_code` | SMILEの納品先コード（6桁連番） |
| `b_cart_destination_code` | B-CARTの配送先コード |
| `partner_code` | SMILEの得意先コード |
| `zip` | 郵便番号 |
| `address1` | 都道府県+市区町村 |
| `address2` | 町域・番地 |
| `address3` | ビル・建物名 |
| `recipient_name1` | 荷受け人名 |
| `phone_number` | 電話番号 |
| `smile_csv_outputted` | SMILE用CSV出力済フラグ |

### 5.3 商品マッピング（B-CART品番→SMILE商品コード）

`w_sales_goods`テーブルを使用して品番→SMILE商品コードのマッピングを行う。

```java
WSalesGoods wSalesGoods = wSalesGoodsService.getByShopNoAndGoodsCode(
    OfficeShopNo.B_CART_ORDER.getValue(),  // B-CART店舗番号
    productCode
);
```

- マスタに存在する場合: `wSalesGoods.getMGoods().getSmileGoodsName()` または `wSalesGoods.getGoodsName()`を商品名として使用
- マスタに存在しない場合: `FIXED_PRODUCT_CODE`（手入力商品コード）を設定し、品番を行摘要2に格納

### 5.4 在庫同期

B-CART商品セットには`stock`（在庫数）と`stock_flag`（在庫フラグ）フィールドがある。商品取込バッチで本テーブルに反映されるが、B-CARTへの在庫プッシュバックの実装は現時点では`b_cart_product_sets`テーブル内の`stock`カラム管理に留まる（PATCH APIで在庫を更新する機能は価格更新APIで間接的に対応）。

---

## 6. エンティティ定義

### 6.1 b_cart_order（受注情報）

テーブル: `b_cart_order`
クラス: `jp.co.oda32.domain.model.bcart.BCartOrder`

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `id` | Long | 受注ID（主キー） |
| `code` | Long | 受注番号 |
| `customer_id` | Long | 注文者会員ID |
| `customer_ext_id` | String | 貴社独自会員ID |
| `customer_parent_id` | String | 親代理店ID |
| `customer_salesman_id` | String | 営業担当者ID（カンマ区切り） |
| `customer_comp_name` | String | 注文者会社名（最大128桁） |
| `customer_department` | String | 注文者部署名 |
| `customer_name` | String | 注文者名（最大128桁） |
| `customer_tel` | String | 注文者電話番号 |
| `customer_mobile_phone` | String | 注文者携帯番号 |
| `customer_email` | String | 注文者メールアドレス |
| `customer_price_group_id` | String | 価格グループ名 |
| `customer_zip` | String | 注文者郵便番号 |
| `customer_pref` | String | 注文者都道府県 |
| `customer_address1` | String | 注文者市区町村 |
| `customer_address2` | String | 注文者町域・番地 |
| `customer_address3` | String | 注文者ビル・建物名 |
| `customer_customs` | text[] | 注文者カスタム項目（配列） |
| `payment` | String | 決済方法 |
| `payment_at` | LocalDate | 決済確定日 |
| `total_price` | BigDecimal(25,2) | 商品合計金額 |
| `tax` | BigDecimal | 消費税 |
| `tax_rate` | BigDecimal(25,2) | 税率 |
| `cod_cost` | BigDecimal | 決済手数料 |
| `shipping_cost` | BigDecimal | 送料 |
| `final_price` | BigDecimal | 受注総額 |
| `use_point` | BigDecimal | ポイント利用 |
| `get_point` | BigDecimal | ポイント取得 |
| `order_totals` | jsonb | 税率別合計金額（配列） |
| `customer_message` | TEXT | お客様からの連絡事項 |
| `admin_message` | TEXT | お客様への連絡事項 |
| `memo` | TEXT | 管理用メモ |
| `customs` | text[] | 受注カスタム項目 |
| `enquete1`～`enquete5` | String | アンケート1〜5 |
| `ordered_at` | String | 受注日（yyyy-MM-dd HH:mm:ss） |
| `affiliate_id` | String | 参照元ID |
| `estimate_id` | Long | 見積番号 |
| `status` | String | 対応状況（新規注文/カスタム1/カスタム2/完了） |

リレーション:
- `order_products`: `b_cart_order_product`（OneToMany）

### 6.2 b_cart_order_product（受注商品情報）

テーブル: `b_cart_order_product`
クラス: `jp.co.oda32.domain.model.bcart.BCartOrderProduct`
複合主キー: (`id`, `order_id`)（`BCartOrderProductPK`）

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `id` | Long | 受注商品ID |
| `order_id` | Long | 受注ID（b_cart_order参照） |
| `logistics_id` | Long | 出荷ID（b_cart_logistics参照） |
| `product_id` | Long | 商品ID |
| `main_no` | String(255) | 商品管理番号 |
| `product_no` | String(255) | 品番 |
| `jan_code` | String(255) | JANコード |
| `location_no` | String(255) | ロケーション番号 |
| `product_name` | String(255) | 商品名 |
| `product_set_id` | Long | 商品セットID |
| `set_name` | String(255) | 商品セット名 |
| `unit_price` | BigDecimal | 単価 |
| `set_quantity` | BigDecimal | 入数 |
| `set_unit` | String(255) | 単位 |
| `order_pro_count` | BigDecimal | 注文数 |
| `shipping_size` | BigDecimal | 配送サイズ |
| `tax_rate` | BigDecimal | 税率（0.1=10%, 0.08=8%） |
| `tax_type_id` | Integer | 税区分（1=標準, 2=軽減） |
| `tax_incl` | Integer | 税込フラグ（0=税別, 1=税込） |
| `item_type` | String(255) | 商品区分（product/shipping/payment_fees/point） |
| `options` | text[] | 商品オプションリスト |
| `set_custom3` | String | カスタム3（仕入価格を格納） |

### 6.3 b_cart_logistics（出荷情報）

テーブル: `b_cart_logistics`
クラス: `jp.co.oda32.domain.model.bcart.BCartLogistics`

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `id` | Long | 出荷ID（主キー） |
| `shipment_code` | String(255) | 出荷管理番号（SMILE処理連番） |
| `delivery_code` | String(255) | 送り状番号 |
| `destination_code` | String(255) | 配送先コード |
| `shipping_group_id` | String(255) | 配送グループID |
| `comp_name` | String(255) | 配送先会社名 |
| `department` | String(255) | 配送先部署名 |
| `name` | String(255) | 配送先担当者名 |
| `zip` | String(16) | 配送先郵便番号 |
| `pref` | String(32) | 配送先都道府県 |
| `address1` | String(255) | 配送先市区町村 |
| `address2` | String(255) | 配送先町域・番地 |
| `address3` | String(255) | 配送先ビル・建物名 |
| `tel` | String(255) | 配送先電話番号 |
| `due_date` | String | 配送希望日 |
| `due_time` | String(32) | 配送希望時間 |
| `memo` | TEXT | 発送メモ |
| `shipment_date` | String | 発送日 |
| `arrival_date` | String | 納品日 |
| `status` | String(4) | 発送状況（未発送/発送指示/発送済/対象外） |
| `is_updated` | boolean | 更新フラグ（CSV出力後falseに設定） |
| `b_cart_csv_exported` | boolean | B-CART出荷実績CSV出力済フラグ |

リレーション:
- `b_cart_order_product_list`: `b_cart_order_product`（OneToMany、EAGER）

### 6.4 b_cart_member（会員情報）

テーブル: `b_cart_member`
クラス: `jp.co.oda32.domain.model.bcart.BCartMember`
備考: `ext_id`にSMILEの得意先コードを設定

主要カラム:

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `id` | Long | 会員ID（主キー） |
| `ext_id` | String | SMILEの得意先コード（外部ID） |
| `agent_id` | String | 代理店ID |
| `agent_rate` | Double | 代理店割引率 |
| `parent_id` | Long | 親代理店ID |
| `destination_code` | String | 配送コード |
| `comp_name` | String | 会社名 |
| `comp_name_kana` | String | 会社名カナ |
| `ceo_last_name` | String | 代表者姓 |
| `ceo_first_name` | String | 代表者名 |
| `ceo_last_name_kana` | String | 代表者姓カナ |
| `ceo_first_name_kana` | String | 代表者名カナ |
| `department` | String | 部署名 |
| `tanto_last_name` | String | 担当者姓 |
| `tanto_first_name` | String | 担当者名 |
| `tanto_last_name_kana` | String | 担当者姓カナ |
| `tanto_first_name_kana` | String | 担当者名カナ |
| `zip` | String | 郵便番号 |
| `pref` | String | 都道府県 |
| `address1` | String | 市区町村 |
| `address2` | String | 町域・番地 |
| `address3` | String | ビル・建物名 |
| `email` | String | メールアドレス |
| `email_cc` | String | CCメールアドレス |
| `tel` | String | 電話番号 |
| `mobile_phone` | String | 携帯番号 |
| `fax` | String | FAX番号 |
| `url` | String | URL |
| `foundation` | String | 設立年 |
| `sales` | Integer | 売上規模 |
| `job` | String | 業種 |
| `memo` | String | メモ |
| `payment` | String | 支払方法 |
| `special_shipping_cost` | String | 特別送料 |
| `paid` | String | 入金情報 |
| `mm_flag` | Integer | メルマガフラグ |
| `point` | Integer | 保有ポイント |
| `price_group_id` | Long | 価格グループID |
| `view_group_id` | Long | 表示グループID |
| `salesman_id` | String | 営業担当者ID |
| `af_id` | String | アフィリエイトID |
| `credit_limit` | Integer | 与信限度額 |
| `cutoff_date` | String | 締日 |
| `payment_month` | String | 支払月 |
| `payment_date` | String | 支払日 |
| `default_other_shipping_id` | Long | デフォルト別配送先ID |
| `default_payment` | String | デフォルト支払方法 |
| `hidden_price` | Integer | 価格非表示フラグ |
| `status` | String | 会員ステータス |
| `created_at` | LocalDate | 登録日 |
| `updated_at` | LocalDate | 更新日 |
| `smile_partner_master_linked` | boolean | SMILE得意先マスタ連携済フラグ |
| `need_smile_order_file_goods_code` | boolean | SMILE注文ファイルに商品コード必要フラグ |

### 6.5 b_cart_member_other_addresses（会員別配送先）

テーブル: `b_cart_member_other_addresses`
クラス: `jp.co.oda32.domain.model.bcart.BCartMemberOtherAddresses`

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `id` | Long | 別配送先ID（主キー） |
| `customer_id` | Long | 会員ID |
| `destination_code` | String(255) | 配送コード |
| `comp_name` | String(255) | 配送先会社名 |
| `department` | String(255) | 配送先部署名 |
| `name` | String(255) | 配送先名 |
| `zip` | String(255) | 郵便番号 |
| `pref` | String(255) | 都道府県 |
| `address1` | String(50) | 市区町村 |
| `address2` | String(50) | 町域・番地 |
| `address3` | String(50) | ビル・建物名 |
| `tel` | String(255) | 電話番号 |

### 6.6 b_cart_products（商品マスタ）

テーブル: `b_cart_products`
クラス: `jp.co.oda32.domain.model.bcart.BCartProducts`

主要カラム:

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `id` | Integer | 商品ID（主キー） |
| `main_no` | String(255) | 商品管理番号 |
| `name` | String(255) | 商品名 |
| `catch_copy` | TEXT | キャッチコピー |
| `category_id` | Integer | カテゴリID |
| `sub_category_id` | String(255) | サブカテゴリID（カンマ区切り） |
| `feature_id1`〜`feature_id3` | Integer | 特集ID1〜3 |
| `made_in` | String(255) | 生産地 |
| `size` | TEXT | サイズ |
| `sozai` | TEXT | 素材 |
| `caution` | TEXT | 注意事項 |
| `tag` | String(21) | 商品特徴（new/recommend/limited等） |
| `description` | TEXT | 説明 |
| `meta_title` | String(255) | METAタイトル |
| `meta_keywords` | String(255) | METAキーワード |
| `meta_description` | String(255) | META説明 |
| `image` | String(255) | 商品画像パス |
| `customs` | text[] | カスタム項目 |
| `hanbai_start` | Date | 販売期間開始 |
| `hanbai_end` | Date | 販売期間終了 |
| `view_pattern` | Integer | 商品一覧表示パターン（0〜5） |
| `priority` | Integer | 表示順 |
| `flag` | String(3) | 状態（表示/非表示） |
| `updated_at` | Date | 更新日 |

### 6.7 b_cart_product_sets（商品セット）

テーブル: `b_cart_product_sets`
クラス: `jp.co.oda32.domain.model.bcart.BCartProductSets`

主要カラム:

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `id` | Long | 商品セットID（主キー） |
| `product_id` | Long | 商品ID |
| `product_no` | String(255) | 品番 |
| `jan_code` | String(255) | JANコード |
| `location_no` | String(255) | ロケーション番号 |
| `jodai_type` | String(50) | 参考上代タイプ |
| `jodai` | BigDecimal | 参考上代 |
| `name` | String(255) | セット名 |
| `unit_price` | BigDecimal | 単価 |
| `min_order` | BigDecimal | 最小注文数 |
| `max_order` | BigDecimal | 最大注文数 |
| `group_price` | String | グループ価格（JSON文字列、DB保存用） |
| `special_price` | String | 特別価格（JSON文字列、DB保存用） |
| `volume_discount` | String | 数量割引（JSON文字列、DB保存用） |
| `quantity` | Integer | 入数 |
| `unit` | String(255) | 単位 |
| `description` | TEXT | セット説明 |
| `stock` | Integer | 在庫 |
| `stock_flag` | Integer | 在庫フラグ |
| `stock_parent` | String | 参照在庫（DB保存用） |
| `stock_view_id` | Integer | 在庫表示パターン |
| `stock_few` | BigDecimal | 在庫わずかになる数量 |
| `view_group_filter` | String(255) | 非表示フィルタ |
| `customs` | String | カスタム項目（DB保存用） |
| `purchase_price` | BigDecimal | 仕入価格 |
| `option_ids` | String | 商品オプションID |
| `shipping_group_id` | Integer | 配送グループID |
| `shipping_size` | BigDecimal | 配送サイズ |
| `priority` | Integer | 表示優先度 |
| `set_flag` | String(3) | セットフラグ |
| `tax_type_id` | Integer | 税区分ID |
| `updated_at` | Date | 更新日時 |
| `volume_discount_ids` | String | 数量割引ID（カンマ区切り） |
| `b_cart_price_reflected` | boolean | B-CART価格反映済フラグ |

### 6.8 b_cart_group_price（グループ価格）

テーブル: `b_cart_group_price`
クラス: `jp.co.oda32.domain.model.bcart.productSets.BCartGroupPrice`
複合主キー: (`product_set_id`, `group_id`)

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `product_set_id` | Long | 商品セットID |
| `group_id` | String | グループID |
| `name` | String | グループ名 |
| `rate` | BigDecimal | 割引率 |
| `unit_price` | BigDecimal | 単価 |
| `fixed_price` | BigDecimal | 固定価格 |
| `volume_discount_ids` | String | 数量割引IDリスト |
| `b_cart_price_reflected` | boolean | B-CART価格反映済フラグ |

### 6.9 b_cart_special_price（特別価格）

テーブル: `b_cart_special_price`
クラス: `jp.co.oda32.domain.model.bcart.productSets.BCartSpecialPrice`
複合主キー: (`product_set_id`, `customer_id`)

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `product_set_id` | Long | 商品セットID |
| `customer_id` | Long | 会員ID |
| `unit_price` | BigDecimal | 特別単価 |
| `volume_discount_ids` | String | 数量割引IDリスト（カンマ区切り） |
| `b_cart_price_reflected` | boolean | B-CART価格反映済フラグ |

### 6.10 b_cart_volume_discount（数量割引）

テーブル: `b_cart_volume_discount`
クラス: `jp.co.oda32.domain.model.bcart.BCartVolumeDiscount`
主キー: シーケンス生成（`b_cart_volume_discount_seq`）

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `volume_discount_id` | Long | 数量割引ID（主キー） |
| `set_num` | BigDecimal | セット数（数量） |
| `unit_price` | BigDecimal | 単価 |
| `product_set_id` | Long | 商品セットID |
| `customer_id` | Long | 会員ID（会員別割引の場合） |
| `del_flg` | String | 削除フラグ（'0'=有効, '1'=削除） |
| `b_cart_price_reflected` | boolean | B-CART価格反映済フラグ |

### 6.11 x_delivery_mapping（納品先コードマッピング）

テーブル: `x_delivery_mapping`
クラス: `jp.co.oda32.domain.model.bcart.DeliveryMapping`

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `x_delivery_mapping_id` | Integer | ID（主キー、自動採番） |
| `b_cart_customer_id` | Long | B-CART会員ID |
| `delivery_name` | String | 配送先名（会社名+部署名） |
| `smile_delivery_code` | String | SMILEの納品先コード（6桁連番、ユニーク） |
| `b_cart_destination_code` | String | B-CARTの配送先コード |
| `partner_code` | String | SMILEの得意先コード |
| `delivery_index` | String | 納品先索引 |
| `recipient_name1` | String | 荷受け人名1 |
| `recipient_name2` | String | 荷受け人名2 |
| `zip` | String | 郵便番号 |
| `address1` | String | 住所1（都道府県+市区町村） |
| `address2` | String | 住所2 |
| `address3` | String | 住所3 |
| `phone_number` | String | 電話番号 |
| `fax_number` | String | FAX番号 |
| `smile_csv_outputted` | Boolean | SMILE CSV出力済フラグ |

### 6.12 t_smile_order_import_file（SMILE注文連携ファイル）

テーブル: `t_smile_order_import_file`
クラス: `jp.co.oda32.domain.model.bcart.TSmileOrderImportFile`

主要カラム:

| カラム名 | 型 | 説明 |
|----------|-----|------|
| `id` | Long | ID（主キー、自動採番） |
| `slip_date` | LocalDate | 伝票日付（納品日） |
| `slip_number` | Integer | 伝票番号（b_cart_logistics.idを使用） |
| `processing_serial_number` | Long | 処理連番（SMILEから採番） |
| `detail_type` | Integer | 明細区分 |
| `line_number` | Integer | 行番号 |
| `customer_code` | String | 得意先コード |
| `customer_comp_name` | String | 得意先名1 |
| `delivery_code` | String | 納品先コード |
| `delivery_comp_name` | String | 納品先名 |
| `person_in_charge_code` | String | 担当者コード |
| `product_code` | String | 商品コード |
| `product_name` | String | 商品名 |
| `set_quantity` | BigDecimal | 入数 |
| `order_pro_count` | BigDecimal | 個数（ケース数） |
| `quantity` | BigDecimal | 数量 |
| `unit_price` | BigDecimal | 単価 |
| `amount` | BigDecimal | 金額 |
| `original_unit_price` | BigDecimal | 原単価 |
| `cost_amount` | BigDecimal | 原価金額 |
| `tax_type` | Integer | 課税区分 |
| `tax_rate` | BigDecimal | 消費税率 |
| `remarks` | String | 備考 |
| `consumption_tax_classification` | Integer | 消費税分類（通常/軽減） |
| `b_cart_order_id` | Long | B-CART受注ID |
| `csv_exported` | boolean | CSV出力済フラグ |
| `b_cart_logistics_id` | Long | B-CART出荷ID |
| `psn_updated` | boolean | 処理連番更新済フラグ |
| `line_summary_2` | String | 行摘要2（手入力商品の品番、または商品コード） |

---

## 7. エラーハンドリング

### 7.1 API呼び出しエラー

| 状況 | 処理内容 |
|------|----------|
| HTTP応答が非成功（`!response.isSuccessful()`） | ログ出力し`RepeatStatus.CONTINUABLE`を返してバッチを継続 |
| レスポンスボディがnull | ログ出力し`RepeatStatus.CONTINUABLE`を返して継続 |
| IOExceptionが発生 | ログ出力し`RepeatStatus.CONTINUABLE`を返して継続 |

### 7.2 データ変換エラー

| 状況 | 処理内容 |
|------|----------|
| `ext_id`が未設定の会員（注文変換時） | 例外をスローしてジョブを停止、管理者に対応を促す |
| 消費税率が取得できない（注文変換時） | 例外をスローしてジョブを停止 |
| 仕入価格のキャスト失敗（`set_custom3`） | ログ出力し0を設定して処理継続 |
| `t_smile_order_import_file`に対応するデータが見つからない | `info`ログ出力してスキップ（SMILE連携後に再実行を促す） |
| 重複キー検出（BCartLogisticsKey） | `warn`ログ出力後、先頭データを採用 |

### 7.3 ファイル出力エラー

| 状況 | 処理内容 |
|------|----------|
| ファイルパスが空 | `error`ログ出力して処理を中断 |
| IOException | `e.printStackTrace()`を呼び出し（改善余地あり） |

### 7.4 注意事項

1. **アクセストークンのハードコード**: `BCartApiConfig`にJWTトークンが直接記述されている。トークンには有効期限が設定されており、期限切れ時はAPI呼び出しが失敗する。定期的な更新が必要。

2. **処理連番の仮採番**: `BCartOrderConvertSmileOrderFileTasklet`で採番する処理連番はSMILEへの取込前の仮番号。SMILEが実際に取り込んだ後に`BCartOrderProcessingSerialNumberUpdateTasklet`で本番号に更新する。更新前の仮番号は8桁未満（判別基準: `numDigits < 8`）。

3. **重複実行不可**: Spring Batchの仕様により、同一パラメータでのジョブ重複実行は不可。`RunIdIncrementer`を使用して毎回異なるパラメータを付与している。

4. **JSONB型カラムの扱い**: `b_cart_order`の`customer_customs`、`customs`、`order_totals`はJSONB型。更新時はHibernateの型問題を避けるためNULLに設定するカスタムSQL実装となっている。

5. **B-CART CSV出力フォーマット**: 出荷実績CSVはShift_JIS（B-CARTのインポート形式）、SMILEへの各CSVはUTF-8で出力する。
