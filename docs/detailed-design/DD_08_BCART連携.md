# DD_08 B-CART連携 詳細プログラム設計書

| 項目 | 内容 |
|------|------|
| 文書番号 | DD_08 |
| 対象機能 | B-CART連携 |
| 対象基本設計書 | 08_bcart_integration.md |
| 作成日 | 2026-02-23 |
| 最終更新日 | 2026-02-23 |

---

## 目次

1. [B-CART API通信設計](#1-b-cart-api通信設計)
2. [出荷情報入力画面処理設計](#2-出荷情報入力画面処理設計)
3. [出荷ステータス管理設計](#3-出荷ステータス管理設計)
4. [注文取込バッチ設計](#4-注文取込バッチ設計)
5. [商品連携バッチ設計](#5-商品連携バッチ設計)
6. [会員連携バッチ設計](#6-会員連携バッチ設計)
7. [価格連携バッチ設計](#7-価格連携バッチ設計)
8. [出荷CSV出力バッチ設計](#8-出荷csv出力バッチ設計)
9. [エンティティ設計](#9-エンティティ設計)
10. [Gson変換設計](#10-gson変換設計)
11. [画面項目定義書](#11-画面項目定義書)

---

## 1. B-CART API通信設計

### 1.1 HTTPクライアント構成

B-CART REST APIとの通信には OkHttp3 を使用する。各Taskletにおいて都度 `OkHttpClient` インスタンスを生成する方式を採用している。

#### OkHttpClient生成パターン

```java
OkHttpClient client = new OkHttpClient().newBuilder().build();
```

| 項目 | 設定値 |
|------|--------|
| ライブラリ | `okhttp3` (OkHttp) |
| インスタンス管理 | 各APIコール時に新規生成（シングルトンではない） |
| タイムアウト設定 | OkHttpデフォルト（接続10秒、読取10秒、書込10秒） |
| コネクションプール | OkHttpデフォルト（最大5接続、キープアライブ5分） |

#### URL構築パターン

```java
HttpUrl url = new HttpUrl.Builder()
        .scheme("https")
        .host("api.bcart.jp")
        .addPathSegment("api")
        .addPathSegment("v1")
        .addPathSegment("{リソース名}")
        .addQueryParameter("limit", String.valueOf(API_LIMIT))
        .addQueryParameter("offset", String.valueOf(offset))
        .build();
```

#### リクエスト構築パターン（GET）

```java
Request request = new Request.Builder()
        .url(url)
        .get()
        .addHeader("Accept", "application/json")
        .addHeader("Authorization", "Bearer " + BCartApiConfig.getInstance().getAccessToken())
        .build();
```

#### リクエスト構築パターン（PATCH）

```java
RequestBody body = RequestBody.create(
    bodyJson.toString(),
    MediaType.parse("application/json; charset=utf-8")
);
Request request = new Request.Builder()
        .url(url)
        .header("Authorization", "Bearer " + BCartApiConfig.getInstance().getAccessToken())
        .header("Content-Type", "application/json")
        .patch(body)
        .build();
```

### 1.2 Bearer Token認証フロー

#### BCartApiConfig クラス設計

| 項目 | 内容 |
|------|------|
| クラス名 | `jp.co.oda32.constant.BCartApiConfig` |
| パターン | シングルトン（遅延初期化） |
| トークン管理 | JWTトークンをフィールドにハードコード |

```
[Tasklet] --getAccessToken()--> [BCartApiConfig (Singleton)]
                                    |
                                    +-- instance: BCartApiConfig (static)
                                    +-- accessToken: String (JWT)
                                    |
                                    v
                              "Bearer {JWT}" をAuthorizationヘッダーに付与
                                    |
                                    v
                              [B-CART API Server]
```

**シングルトン実装**:

```java
public class BCartApiConfig {
    private static BCartApiConfig instance;
    private final String accessToken;

    private BCartApiConfig() {
        this.accessToken = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9...";
    }

    public static BCartApiConfig getInstance() {
        if (instance == null) {
            instance = new BCartApiConfig();
        }
        return instance;
    }

    public String getAccessToken() { return accessToken; }
}
```

**JWTトークンのスコープ一覧**:

| スコープ | 読取 | 書込 |
|----------|------|------|
| products | `products-read` | `products-write` |
| product_sets | `product_sets-read` | `product_sets-write` |
| product_stock | `product_stock-read` | `product_stock-write` |
| customers | `customers-read` | `customers-write` |
| other_addresses | `other_addresses-read` | `other_addresses-write` |
| orders | `orders-read` | `orders-write` |
| order_products | `order_products-read` | `order_products-write` |
| logistics | `logistics-read` | `logistics-write` |
| categories | `categories-read` | `categories-write` |
| order_statuses | `order_statuses-read` | `order_statuses-write` |
| view_groups | `view_groups-read` | - |
| price_groups | `price_groups-read` | - |
| shipping_groups | `shipping_groups-read` | - |
| product_features | `product_features-read` | `product_features-write` |
| bookmarks | `bookmarks-read` | `bookmarks-write` |
| points | `points-read` | `points-write` |

### 1.3 レート制限制御

| 制限パラメータ | 値 |
|---------------|------|
| 最大リクエスト数 | 250回/5分（APIサーバー側の上限は300回/5分） |
| スリープ間隔 | 5分（`Thread.sleep(1000 * 60 * 5)`） |
| 適用タスクレット | `BCartGoodsPriceUpdateTasklet` |

```java
if (++count % 250 == 0) {
    Thread.sleep(1000 * 60 * 5); // 5分待機
}
```

### 1.4 エンドポイント一覧

| # | メソッド | エンドポイント | 用途 | 使用Tasklet |
|---|---------|---------------|------|-------------|
| 1 | GET | `/api/v1/orders?status=新規注文&complete=1` | 新規注文取得 | `BCartOrderRegisterTasklet` |
| 2 | GET | `/api/v1/customers?limit=100&offset={n}` | 会員情報取得 | `BCartMemberImportTasklet` |
| 3 | GET | `/api/v1/other_addresses` | 別配送先取得 | `BCartMemberDeliveryImportTasklet` |
| 4 | GET | `/api/v1/products?limit=100&offset={n}` | 商品マスタ取得 | `BCartProductsImportTasklet` |
| 5 | GET | `/api/v1/product_sets?limit=100&offset={n}` | 商品セット取得 | `BCartProductSetsImportTasklet` |
| 6 | PATCH | `/api/v1/product_sets/{id}` | 商品価格更新 | `BCartGoodsPriceUpdateTasklet` |

### 1.5 レスポンスJSON構造

#### orders APIレスポンス

```json
{
  "orders": [
    {
      "id": 12345,
      "code": 67890,
      "customer_id": 100,
      "customer_ext_id": "001234",
      "customer_comp_name": "株式会社テスト",
      "customer_department": "営業部",
      "status": "新規注文",
      "order_products": [
        {
          "id": 1001,
          "order_id": 12345,
          "logistics_id": 2001,
          "product_id": 500,
          "product_no": "ABC-001",
          "product_name": "テスト商品",
          "unit_price": 1000,
          "set_quantity": 10,
          "order_pro_count": 2,
          "tax_rate": 0.10,
          "tax_type_id": 1,
          "item_type": "product",
          "set_custom3": "800"
        }
      ],
      "logistics": [
        {
          "id": 2001,
          "comp_name": "株式会社テスト",
          "status": "未発送",
          "arrival_date": "2024-01-15"
        }
      ]
    }
  ]
}
```

#### customers APIレスポンス

```json
{
  "customers": [
    {
      "id": 100,
      "ext_id": "001234",
      "comp_name": "株式会社テスト",
      "comp_name_kana": "カブシキガイシャテスト",
      "status": "通常",
      "created_at": "2024-01-01 00:00:00",
      "updated_at": "2024-01-15 10:30:00"
    }
  ]
}
```

#### product_sets PATCHリクエストボディ

```json
{
  "unit_price": 1000,
  "special_price": {
    "123": {
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

### 1.6 エラーハンドリング方針

| 状況 | 処理 | 戻り値 |
|------|------|--------|
| HTTPレスポンスが非成功 | `log.error`でステータスコードを出力 | `RepeatStatus.CONTINUABLE` |
| レスポンスボディがnull | `log.error`で出力 | `RepeatStatus.CONTINUABLE` |
| `IOException`発生 | `log.error`でスタックトレース出力 | `RepeatStatus.CONTINUABLE` |
| PATCHリクエスト失敗 | `log.warn`でレスポンス出力 | `false`を返却（価格反映フラグ更新スキップ） |

---

## 2. 出荷情報入力画面処理設計

### 2.1 コントローラ概要

| 項目 | 内容 |
|------|------|
| クラス名 | `jp.co.oda32.app.bcart.BCartShippingInputController` |
| アノテーション | `@Controller`, `@Log4j2`, `@EnableAutoConfiguration`, `@RequiredArgsConstructor` |
| ベースURL | `/bcart` |
| テンプレート | `bcart/bcart_shipping_info_input` |

#### 依存サービス

| フィールド名 | 型 | 用途 |
|-------------|-----|------|
| `bCartLogisticsService` | `BCartLogisticsService` | 出荷情報の検索・更新 |
| `TSmileOrderImportFileService` | `TSmileOrderImportFileService` | SMILE連携ファイルデータの取得 |
| `bCartOrderService` | `BCartOrderService` | 注文情報の更新 |
| `wSalesGoodsService` | `WSalesGoodsService` | 商品コード変換 |

### 2.2 初期表示処理シーケンス

**メソッド**: `init(Model model)`
**URL**: `GET /bcart/shippingInputForm`

```
[ブラウザ] --> GET /bcart/shippingInputForm
                |
                v
[BCartShippingInputController.init()]
  |
  +--> findNonShippedItems()
  |      |
  |      +--> BcartShipmentStatus.NOT_SHIPPED.getDisplayName()  --> "未発送"
  |      +--> BcartShipmentStatus.SHIPPING_INSTRUCTED.getDisplayName() --> "発送指示"
  |      +--> bCartLogisticsService.findByStatusIn(["未発送", "発送指示"])
  |      |      |
  |      |      v
  |      |    SELECT * FROM b_cart_logistics WHERE status IN ('未発送', '発送指示')
  |      |
  |      +--> convertLogisticsToInputForm(logisticsList)
  |             |
  |             +--> [1] logistics IDリストを抽出
  |             +--> [2] TSmileOrderImportFileService.findByBCartLogisticsIdIn(idList)
  |             +--> [3] 送料行を除外 (productCode != SHIPPING_FEE_PRODUCT_CODE)
  |             +--> [4] 処理連番が8桁未満のデータのみ抽出（SMILE連携済み判定）
  |             +--> [5] BCartLogisticsKeyでグルーピング
  |             |         キー: (logisticsId, productCode, setQuantity, quantity)
  |             +--> [6] 重複キーをwarnログ出力
  |             +--> [7] MapにSTF変換（重複時は先頭要素を採用）
  |             +--> [8] 各logisticsをBCartShippingInputFormに変換
  |             |         - deliveryCode, shipmentDate, memo, status等を設定
  |             |         - 配送希望日(dueDate)があればmemoに追記
  |             |         - adminMessageをBCartOrderから取得
  |             |         - 各orderProductの商品コード変換
  |             |         - goodsInfo(商品コード:商品名:数量)リスト生成
  |             |         - slipNoList(SMILE伝票番号)リスト生成
  |             v
  +--> BCartShippingSearchForm生成
  |      - bCartShippingInputFormList = 変換結果リスト
  |
  +--> model.addAttribute("bCartShippingSearchForm", form)
  |
  +--> return "bcart/bcart_shipping_info_input"
```

### 2.3 検索処理シーケンス

**メソッド**: `search(BCartShippingSearchForm, BindingResult, Model)`
**URL**: `POST /bcart/bcart_shipping_search`

```
[ブラウザ] --> POST /bcart/bcart_shipping_search
                |
                v
[BCartShippingInputController.search()]
  |
  +--> bcartShipmentStatus = searchForm.getBcartShipmentStatus()
  |
  +--> [分岐]
  |      |
  |      +-- (null) --> statuses = ["未発送", "発送指示"]
  |      |              bCartLogisticsService.findByStatusIn(statuses)
  |      |
  |      +-- (EXCLUDED) --> bCartLogisticsService.findByStatus("対象外")
  |      |
  |      +-- (その他) --> bCartLogisticsService.findByStatus(status.getDisplayName())
  |
  +--> convertLogisticsToInputForm(searchLogisticsList)
  |
  +--> searchForm.setBCartShippingInputFormList(変換結果)
  |
  +--> model.addAttribute("bCartShippingSearchForm", searchForm)
  |
  +--> return "bcart/bcart_shipping_info_input"
```

### 2.4 個別更新処理シーケンス

**メソッド**: `update(BCartShippingSearchForm, BindingResult, Model)`
**URL**: `POST /bcart/bcart_shipping_update`

```
[ブラウザ] --> POST /bcart/bcart_shipping_update
                |
                v
[BCartShippingInputController.update()]
  |
  +--> bCartShippingInputFormList = searchForm.getBCartShippingInputFormList()
  |
  +--> [空チェック] isEmpty ? --> return テンプレート（何もしない）
  |
  +--> logisticsIdList = formListから全logisticsId抽出
  |
  +--> bCartLogisticsService.findByIdIn(logisticsIdList)
  |
  +--> [フィルタ] 出荷済み(SHIPPED) かつ CSV出力済み(bCartCsvExported=true) を除外
  |
  +--> [更新ループ] updateLogisticsList x bCartShippingInputFormList
  |      |
  |      +--> logisticsId一致のフォームを検索
  |      +--> deliveryCode, shipmentDate, status, memo を更新
  |      +--> 関連BCartOrder.adminMessage を更新
  |
  +--> bCartLogisticsService.save(updateLogisticsList)    // b_cart_logistics更新
  +--> bCartOrderService.save(updateBCartOrderList)       // b_cart_order.admin_message更新
  +--> updateBCartOrderStatus(updateLogisticsList)        // 注文ステータス自動更新
  |      |
  |      +--> 全出荷が発送済 --> BCartOrder.status = "完了"
  |      +--> 一部のみ発送済 --> BCartOrder.status = "カスタム1"（処理中）
  |
  +--> return "bcart/bcart_shipping_info_input"
```

### 2.5 一括ステータス更新処理シーケンス

**メソッド**: `bulkUpdate(List<Integer> selectedItems, BcartShipmentStatus status, BCartShippingSearchForm, Model)`
**URL**: `POST /bcart/bcart_shipping_bulk_update`

```
[ブラウザ] --> POST /bcart/bcart_shipping_bulk_update
                |  パラメータ: selectedItems=[0,2,5], status=SHIPPING_INSTRUCTED
                v
[BCartShippingInputController.bulkUpdate()]
  |
  +--> [バリデーション] selectedItems or status が空 --> return テンプレート
  |
  +--> bCartShippingInputFormList = searchForm.getBCartShippingInputFormList()
  |
  +--> [空チェック] isEmpty ? --> return テンプレート
  |
  +--> selectedItemsのインデックスに基づき対象logisticsIdを抽出
  |      例: index=0,2,5 --> formList[0], formList[2], formList[5]のlogisticsId
  |
  +--> bCartLogisticsService.findByIdIn(targetLogisticsIds)
  |
  +--> [フィルタ] 出荷済み かつ CSV出力済みの行を除外
  |
  +--> [一括更新] 全対象のstatus = 指定ステータスのdisplayName
  |
  +--> bCartLogisticsService.save(updateLogisticsList)
  |
  +--> updateBCartOrderStatus(updateLogisticsList)  // 注文ステータス自動更新
  |
  +--> findNonShippedItems()  // 未発送・発送指示一覧を再取得
  |
  +--> model.addAttribute("bCartShippingSearchForm", searchForm)
  |
  +--> return "bcart/bcart_shipping_info_input"
```

### 2.6 BCartShippingSearchForm 項目設計

| フィールド名 | Java型 | 用途 | バインド元 |
|-------------|--------|------|-----------|
| `partnerNo` | `Integer` | 得意先番号（hidden） | フォーム検索条件 |
| `partnerCode` | `String` | 得意先コード | テキスト入力 |
| `partnerName` | `String` | 得意先名 | 表示用 |
| `bcartShipmentStatus` | `BcartShipmentStatus` | 出荷ステータス検索条件 | セレクトボックス |
| `bCartShippingInputFormList` | `List<BCartShippingInputForm>` | 一覧データ | テーブル行バインド |
| `selectedItems` | `List<Integer>` | 一括更新選択行インデックス | チェックボックス |

### 2.7 BCartShippingInputForm 項目設計

| フィールド名 | Java型 | 用途 | 入力/表示 |
|-------------|--------|------|----------|
| `partnerCode` | `String` | 得意先コード（SMILE） | 表示（hidden送信） |
| `partnerName` | `String` | 得意先名 | 表示（hidden送信） |
| `bCartLogisticsId` | `Long` | B-Cart出荷ID | 表示（hidden送信） |
| `deliveryCompName` | `String` | 届け先名 | 表示（hidden送信） |
| `deliveryCode` | `String` | 送り状番号 | テキスト入力 |
| `shipmentDateStr` | `String` | 出荷日 | datepicker入力 |
| `shipmentStatus` | `BcartShipmentStatus` | 出荷ステータス | セレクトボックス |
| `memo` | `String` | メモ | テキストエリア |
| `adminMessage` | `String` | 得意先へ連絡事項 | テキストエリア |
| `goodsInfo` | `List<String>` | 商品情報リスト（コード:名:数量） | 表示（hidden送信） |
| `slipNoList` | `List<Integer>` | SMILE伝票番号リスト | 表示（hidden送信） |

### 2.8 BCartLogisticsKey 内部クラス設計

`BCartShippingInputController`の内部クラスとして定義。SMILE連携データとの突合キーとして使用する。

| フィールド | 型 | 説明 |
|-----------|------|------|
| `logisticsId` | `Long` | 出荷ID |
| `productCode` | `String` | SMILE商品コード（変換後） |
| `setQuantity` | `BigDecimal` | セット入数 |
| `quantity` | `BigDecimal` | 数量 |

- `equals()`: 4フィールドの一致を判定。BigDecimalは`compareTo`で比較。
- `hashCode()`: `stripTrailingZeros()`を適用して末尾ゼロを除去した上でハッシュ計算。

### 2.9 商品コード変換ロジック（getSmileProductCode）

コントローラとTaskletで同一のロジックを使用する。

```
入力: goodsName (商品名), goodsCode (B-CART品番)
  |
  +--> goodsName == "送料" ?
  |      YES --> return SHIPPING_FEE_PRODUCT_CODE (固定送料コード)
  |
  +--> goodsCode が "case_" で始まる ?
  |      YES --> goodsCode = "case_" 以降の文字列
  |
  +--> goodsCode に "_" を含む ?
  |      YES --> goodsCode = "_" で分割した最後の要素
  |
  +--> wSalesGoodsService.getByShopNoAndGoodsCode(B_CART_ORDER, goodsCode)
  |      |
  |      +--> null (マスタ未登録) --> return FIXED_PRODUCT_CODE (手入力商品コード)
  |      +--> 非null (マスタ登録済) --> return goodsCode
```

---

## 3. 出荷ステータス管理設計

### 3.1 BcartShipmentStatus enum定義

| クラス | `jp.co.oda32.constant.BcartShipmentStatus` |
|-------|---------------------------------------------|
| 作成日 | 2023/04/18 |
| 作成者 | k_oda |

| 列挙値 | displayName | DB格納値 | 説明 |
|--------|-------------|---------|------|
| `NOT_SHIPPED` | `"未発送"` | `"未発送"` | 出荷指示前の初期状態 |
| `SHIPPING_INSTRUCTED` | `"発送指示"` | `"発送指示"` | 出荷指示を出した状態 |
| `SHIPPED` | `"発送済"` | `"発送済"` | B-CARTに送り状番号等を送信済み |
| `EXCLUDED` | `"対象外"` | `"対象外"` | 不要な出荷項目を一覧から除外 |

**purseメソッド（文字列からenumへの変換）**:

```java
public static BcartShipmentStatus purse(String key) {
    for (BcartShipmentStatus status : values()) {
        if (status.getDisplayName().equals(key)) {
            return status;
        }
    }
    return null;
}
```

### 3.2 状態遷移図

```
                    ┌─────────────┐
                    │   未発送     │  (初期状態: B-CART注文取込時に設定)
                    │ NOT_SHIPPED │
                    └──────┬──────┘
                           │
                           │ [画面操作: ステータス変更]
                           │ [一括更新: 発送指示に変更]
                           v
                    ┌──────────────────┐
                    │    発送指示       │
                    │ SHIPPING_INSTRUCTED│
                    └──────┬───────────┘
                           │
                           │ [画面操作: ステータス変更]
                           │ [一括更新: 発送済に変更]
                           │ ※SMILE連携バッチがこのステータスを対象として取込
                           v
                    ┌─────────────┐
                    │   発送済     │
                    │   SHIPPED   │
                    └──────┬──────┘
                           │
                           │ [B-CART CSV出力バッチ実行]
                           │ b_cart_csv_exported = true
                           v
                    ┌──────────────────────┐
                    │ 発送済 + CSV出力済    │  (更新ロック状態)
                    │ SHIPPED +            │
                    │ bCartCsvExported=true │
                    └──────────────────────┘

    ※任意のステータスから「対象外」への遷移が可能（一括更新による除外操作）

                    ┌──────────────┐
    未発送/発送指示  │    対象外     │
    ─────────────>  │  EXCLUDED    │
    [画面操作]      └──────────────┘
```

### 3.3 BCartOrderStatus 注文ステータス定義

出荷ステータスの変更に連動して、関連する `b_cart_order.status` が自動更新される。

| 列挙値 | status値 | description | 遷移条件 |
|--------|---------|-------------|---------|
| `NEW_ORDER` | `"新規注文"` | 新規注文 | B-CARTからの初期ステータス |
| `PROCESSING` | `"カスタム1"` | 処理中 | 一部のみ発送済 |
| `CANCELED` | `"カスタム2"` | キャンセル | キャンセル処理 |
| `COMPLETED` | `"完了"` | 完了 | 全出荷が発送済 |

### 3.4 注文ステータス自動更新ロジック

```java
// updateBCartOrderStatus(updateLogisticsList)
for (BCartOrder bCartOrder : allBCartOrderList) {
    List<BCartLogistics> allLogisticsList = bCartOrder.orderProductList
        .stream().map(BCartOrderProduct::getBCartLogistics).collect(...);

    // 更新対象LogisticsのステータスをallLogisticsListに反映
    for (BCartLogistics updatedLogistics : updateLogisticsList) {
        // IDが一致するlogisticsのstatusを更新済みのstatusで上書き
        allLogisticsList.stream()
            .filter(l -> l.id == updatedLogistics.id)
            .peek(l -> l.setStatus(updatedLogistics.getStatus()));

        if (全logisticsが"発送済") {
            bCartOrder.status = "完了";
        } else {
            bCartOrder.status = "カスタム1"; // 処理中
        }
    }
}
```

---

## 4. 注文取込バッチ設計

### 4.1 ジョブ構成

| ジョブBean名 | ジョブクラス | 実行方式 |
|-------------|------------|---------|
| `bCartOrderImportJob` | `BCartOrderImportBatch` | CLIバッチ |
| `bCartOrderImportJobForWEB` | `BCartOrderImportBatchForWEB` | Web起動 |

#### ステップフロー

```
Step1: bCartOrderRegisterStep
  +--> BCartOrderRegisterTasklet          [B-CART APIから注文取得・DB登録]
       |
Step2: bCartOrderConvertSmileOrderFileStep
  +--> BCartOrderConvertSmileOrderFileTasklet [出荷指示データをSMILE連携形式に変換]
       |
Step3: bSmileDestinationFileOutPutStep
  +--> SmileDestinationFileOutPutTasklet  [SMILE納品先マスタCSV出力]
       |
Step4: smileOrderFileOutPutStep
  +--> SmileOrderFileOutPutTasklet        [SMILE注文CSV出力]
```

### 4.2 BCartOrderRegisterTasklet 処理フロー

| 項目 | 内容 |
|------|------|
| クラス | `jp.co.oda32.batch.bcart.BCartOrderRegisterTasklet` |
| アノテーション | `@Component`, `@StepScope`, `@Log4j2`, `@RequiredArgsConstructor` |
| 依存サービス | `BCartOrderService`, `BCartOrderProductService`, `BCartLogisticsService` |

```
[BCartOrderRegisterTasklet.execute()]
  |
  +--> executeBCartOrdersAPI()
  |      |
  |      +--> GET https://api.bcart.jp/api/v1/orders?status=新規注文&complete=1
  |      +--> Authorization: Bearer {accessToken}
  |      +--> Accept: application/json
  |
  +--> [レスポンスチェック]
  |      !response.isSuccessful() --> log.error --> return CONTINUABLE
  |      response.body() == null  --> log.error --> return CONTINUABLE
  |
  +--> Gson構築
  |      registerTypeAdapter(Integer.class, CustomNumberDeserializer)
  |      registerTypeAdapter(Double.class,  CustomNumberDeserializer)
  |      registerTypeAdapter(Float.class,   CustomNumberDeserializer)
  |      registerTypeAdapter(Long.class,    CustomNumberDeserializer)
  |
  +--> gson.fromJson(json, BCartOrdersApiResponse.class)
  |      |
  |      v
  |    bCartOrdersApiResponse.getBCartOrderList()
  |
  +--> [登録ループ] for each bCartOrder:
  |      |
  |      +--> bCartOrderService.save(bCartOrder)           // b_cart_order
  |      +--> bCartOrderProductService.save(orderProducts) // b_cart_order_product
  |      +--> bCartLogisticsService.save(logisticsList)    // b_cart_logistics
  |
  +--> return FINISHED
  |
  +--> [例外] IOException --> log.error --> return CONTINUABLE
```

### 4.3 BCartOrderConvertSmileOrderFileTasklet 処理フロー

| 項目 | 内容 |
|------|------|
| クラス | `jp.co.oda32.batch.bcart.BCartOrderConvertSmileOrderFileTasklet` |
| 定数 | `PRODUCT_NO_CASE_PREFIX = "case_"` |
| 内部キャッシュ | `bCartMemberMap: Map<Long, BCartMember>` |

```
[BCartOrderConvertSmileOrderFileTasklet.execute()]
  |
  +--> [1] mTaxRateService.getTaxRate()
  |      null --> throw Exception("消費税率を取得できませんでした")
  |
  +--> [2] bCartLogisticsService.findByStatus("発送指示")
  |      +--> arrivalDate が null のものを除外
  |      +--> 空リスト --> log.info --> return FINISHED
  |
  +--> [3] TSmileOrderImportFileService.findByBCartLogisticsIdIn(idList)
  |
  +--> [4] 既存登録済みの b_cart_logistics_id をSetに変換
  |
  +--> [5] 未登録のbCartLogisticsのみ抽出
  |      空リスト --> log.info("全て登録済み") --> return FINISHED
  |
  +--> [6] BCartOrderのマップ構築 (logisticsId -> BCartOrder)
  |
  +--> [7] 未登録出荷データをTSmileOrderImportFileに変換
  |      for each bCartLogistics:
  |        convertSmileOrderImportFile(bCartLogistics, orderMap)
  |          |
  |          +--> createBaseSmileOrderImportFile(bCartOrder)
  |          +--> setSharedProperties(bCartOrder, baseFile)
  |          |      customerCodeMapper() --> ext_id取得
  |          |      customerCompName --> 半角変換・48文字制限
  |          |      detailType, billingType等の定数設定
  |          |      personInChargeCode = "5"
  |          |      remarks = adminMessage or customerMessage (36文字制限)
  |          |
  |          +--> processOrderProductList(logistics, importList, baseFile)
  |          |      for each orderProduct:
  |          |        processOrderProduct() --> createDetailFile()
  |          |
  |          +--> addShippingFeeLineIfNeeded()
  |                 bCartLogisticsに紐づかない送料行を追加
  |
  +--> [7'] 既存レコードとの重複チェック
  |      slipNumber + productCode + quantity + bCartLogisticsId で判定
  |
  +--> [8] assignLineNumbersAndProcessingSerialNumbers()
  |      伝票番号(slipNumber)ごとに行番号を1からインクリメント
  |      新規slipNumber出現時にシーケンスから処理連番を取得
  |      SELECT nextval('w_smile_order_import_file_processing_serial_number_seq')
  |
  +--> [9] TSmileOrderImportFileService.save(finalImportFiles)
  |
  +--> return FINISHED
```

#### createDetailFile 詳細

```
createDetailFile(baseFile, orderProduct, bCartLogistics, companyName, customerId)
  |
  +--> BeanUtils.copyProperties(baseFile, detail) // ベースファイルコピー
  |
  +--> slipNumber = bCartLogistics.id.intValue()
  |
  +--> setProductProperties()
  |      getSmileProductCode(orderProduct)
  |      wSalesGoodsService.getByShopNoAndGoodsCode(B_CART_ORDER, code)
  |      +--> null: productCode=FIXED_PRODUCT_CODE, productName=bcart商品名
  |      +--> 非null: productCode=code, productName=smileGoodsName or goodsName
  |
  +--> setCalculatedProperties()
  |      セット販売判定: setName != null && !setName.contains("単品")
  |        YES: setQuantity=orderProduct.setQuantity, orderProCount設定
  |        NO:  setQuantity=null
  |      quantity = setQuantity * orderProCount
  |      originalUnitPrice = set_custom3 (数値変換失敗時は0)
  |      costAmount = originalUnitPrice * quantity
  |      unitPrice = orderProduct.unitPrice
  |      amount = unitPrice * quantity
  |      taxType = TAX_EXCLUDED
  |      taxRate = orderProduct.taxRate * 100
  |      consumptionTaxClassification = 軽減/通常判定
  |      needSmileOrderFileGoodsCode=true --> lineSummary2=productCode
  |
  +--> setLogisticsProperties()
         slipDate = arrivalDate (ハイフン区切り -> LocalDate)
         deliveryCode = deliveryCodeMapper() (会社名が異なる場合のみ)
         bCartLogisticsId = logistics.id
         軽減税率 --> slipNumber先頭を"8"に変換
           例: 23456789 --> 83456789
```

#### 得意先コードマッピング（customerCodeMapper）

```
customerCodeMapper(bCartCustomerId)
  |
  +--> [1] キャッシュ(bCartMemberMap)確認
  |      存在 && extId != null --> return extId
  |
  +--> [2] キャッシュに無い --> DB検索
  |      bCartMemberService.getByBCartCustomerId(id)
  |
  +--> [3] 未登録 or extId未設定
  |      --> throw Exception("会員情報が見つかりません...会員情報連携バッチを先に起動してください")
  |
  +--> [4] キャッシュに格納 --> return extId
```

#### 納品先コードマッピング（deliveryCodeMapper）

```
deliveryCodeMapper(bCartLogistics, bCartCustomerId, deliveryName, customerCode)
  |
  +--> deliveryMappingService.findBybCartCustomerId(bCartCustomerId)
  |
  +--> [1] bCartLogistics.destinationCode != null
  |      --> マッピングからsmileDeliveryCodeが一致するものを検索
  |      --> 見つかれば return smileDeliveryCode
  |
  +--> [2] deliveryNameが一致するマッピングを検索
  |
  +--> [3] 新しいDeliveryMappingを構築（住所等を設定）
  |
  +--> [4] 既存マッピングあり
  |      内容同一 --> return 既存smileDeliveryCode
  |      内容変更 --> 更新 (smileCsvOutputted=false) --> return smileDeliveryCode
  |
  +--> [5] 新規 --> 6桁連番採番 (String.format("%06d", size+1))
  |      --> 保存 --> return 新smileDeliveryCode
```

### 4.4 SMILE連携レコードフィールドマッピング

| TSmileOrderImportFileフィールド | 設定元 | 変換ルール |
|-------------------------------|--------|-----------|
| `slipNumber` | `bCartLogistics.id` | `.intValue()` |
| `slipDate` | `bCartLogistics.arrivalDate` | `StringHaifunToLocalDate` |
| `customerCode` | `bCartMember.extId` | customerCodeMapper経由 |
| `customerCompName` | `bCartOrder.customerCompName` | 半角変換、48文字制限 |
| `deliveryCode` | `DeliveryMapping.smileDeliveryCode` | 配送先が異なる場合のみ |
| `productCode` | `BCartOrderProduct.productNo` | getSmileProductCode変換 |
| `productName` | `WSalesGoods.mGoods.smileGoodsName` | マスタ未登録時はbcart商品名(36文字) |
| `setQuantity` | `BCartOrderProduct.setQuantity` | セット販売時のみ設定 |
| `orderProCount` | `BCartOrderProduct.orderProCount` | セット販売時のみ設定 |
| `quantity` | `setQuantity * orderProCount` | 計算値 |
| `unitPrice` | `BCartOrderProduct.unitPrice` | そのまま |
| `amount` | `unitPrice * quantity` | 計算値 |
| `originalUnitPrice` | `BCartOrderProduct.setCustom3` | 数値変換失敗時は0 |
| `costAmount` | `originalUnitPrice * quantity` | 計算値 |
| `taxType` | 固定 | `SmileTaxType.TAX_EXCLUDED` |
| `taxRate` | `BCartOrderProduct.taxRate * 100` | 例: 0.10 -> 10 |
| `consumptionTaxClassification` | 税率判定 | 軽減/通常 |
| `personInChargeCode` | 固定 | `"5"`（小田一輝） |
| `detailType` | 固定 | `SmileOrderDetailType.NORMAL` |
| `processingSerialNumber` | シーケンス | `w_smile_order_import_file_processing_serial_number_seq` |
| `lineNumber` | 計算値 | slipNumberごとに1からインクリメント |
| `remarks` | `bCartOrder.adminMessage` or `customerMessage` | 改行除去、半角変換、36文字制限 |
| `lineSummary2` | 条件付き | 手入力商品: 品番 / needSmileOrderFileGoodsCode: 商品コード |

---

## 5. 商品連携バッチ設計

### 5.1 ジョブ構成

| ジョブBean名 | ジョブクラス | 実行方式 |
|-------------|------------|---------|
| `bCartProductsImportJob` | `BCartProductsImportBatch` | CLIバッチ |

#### ステップフロー

```
Step1: bCartProductsImportStep
  +--> BCartProductsImportTasklet      [商品マスタ取込]
       |
Step2: bCartProductSetsImportStep
  +--> BCartProductSetsImportTasklet   [商品セット取込]
```

### 5.2 BCartProductsImportTasklet 処理フロー

| 項目 | 内容 |
|------|------|
| クラス | `jp.co.oda32.batch.bcart.BCartProductsImportTasklet` |
| 定数 | `API_LIMIT = 100` |
| 依存サービス | `BCartProductsService` |

```
[BCartProductsImportTasklet.execute()]
  |
  +--> i = 0
  +--> [ループ] while (true)
         |
         +--> executeBCartProductsAPI(i)
         |      GET /api/v1/products?limit=100&offset={i*100}
         |
         +--> [レスポンスチェック]
         |      !successful --> log.error --> return CONTINUABLE
         |      body == null --> log.error --> return CONTINUABLE
         |
         +--> Gson構築
         |      CustomNumberDeserializer (Integer/Double/Float/Long)
         |      CustomDateTypeAdapter ("yyyy-MM-dd HH:mm:ss")
         |
         +--> gson.fromJson(json, BCartProductsApiResponse.class)
         |
         +--> bCartProductsList が 空 --> break (ループ終了)
         |
         +--> for each bCartProducts:
         |      bCartProductsService.save(bCartProducts) // UPSERT
         |
         +--> i++
         |
  +--> return FINISHED
  |
  +--> [例外] IOException --> log.error --> return CONTINUABLE
```

### 5.3 BCartProductSetsImportTasklet 処理フロー

| 項目 | 内容 |
|------|------|
| クラス | `jp.co.oda32.batch.bcart.BCartProductSetsImportTasklet` |
| 定数 | `API_LIMIT = 100` |
| 依存サービス | `BCartProductSetsService`, `BCartVolumeDiscountService`, `BCartSpecialPriceService`, `BCartGroupPriceService` |

```
[BCartProductSetsImportTasklet.execute()]
  |
  +--> i = 0
  +--> [ループ] while (true)
         |
         +--> executeBCartProductSetsAPI(i)
         |      GET /api/v1/product_sets?limit=100&offset={i*100}
         |
         +--> [レスポンスチェック] (同上)
         |
         +--> Gson構築
         |      CustomNumberDeserializer (Integer/Double/Float/Long)
         |      CustomDateTypeAdapter ("yyyy-MM-dd HH:mm:ss")
         |      SpecialPriceDeserializer  --> Map<String, BCartSpecialPrice>
         |      GroupPriceDeserializer    --> Map<String, BCartGroupPrice>
         |      VolumeDiscountDeserializer --> Map<String, Integer>
         |      StockParentDeserializer   --> StockParent
         |
         +--> gson.fromJson(json, BCartProductSetsApiResponse.class)
         |
         +--> bCartProductSetsList が 空 --> break
         |
         +--> for each bCartProductSets:
         |      |
         |      +--> [1] saveBCartVolumeDiscount(volumeDiscountMap, setId, null)
         |      |      +--> 初回: 新規一括登録 (bCartPriceReflected=true)
         |      |      +--> 更新: 全件delFlg='1' --> マッチするものをdelFlg='0'に戻す
         |      |      +--> 新規分を追加登録
         |      |
         |      +--> [2] saveBCartSpecialPrice(bCartProductSets)
         |      |      +--> specialPriceMapのキーはカンマ区切り会員ID
         |      |      +--> 初回: 全件新規登録 (bCartPriceReflected=true)
         |      |      +--> 更新: bCartPriceReflected=trueのみ対象
         |      |      +--> インポートデータにないものは削除
         |      |
         |      +--> [3] saveBCartGroupPrice(bCartProductSets)
         |      |      +--> 初回: 全件新規登録 (bCartPriceReflected=true)
         |      |      +--> 更新: bCartPriceReflected=trueのみ対象
         |      |      +--> インポートデータにないものは削除
         |      |
         |      +--> [4] volumeDiscountIds設定（カンマ区切りID文字列）
         |      |
         |      +--> [5] 価格更新フラグ確認
         |      |      既存セットの bCartPriceReflected=false の場合
         |      |      --> 既存の unitPrice を維持（APIの値で上書きしない）
         |      |
         |      +--> [6] bCartProductSetsService.save(bCartProductSets)
         |
         +--> i++
         |
  +--> return FINISHED
```

#### 数量割引更新ロジック詳細

```
saveBCartVolumeDiscount(volumeDiscountMap, productSetId, customerId)
  |
  +--> volumeDiscountMap が null/空 --> return null
  |
  +--> 既存データ取得: findByProductSetIdAndCustomerId(productSetId, customerId)
  |
  +--> [既存なし] 初回登録
  |      volumeDiscountMap の各エントリを BCartVolumeDiscount に変換
  |      bCartPriceReflected = true
  |      saveAll --> return リスト
  |
  +--> [既存あり] 更新
         +--> 全既存レコードの delFlg = '1' (論理削除マーク)
         +--> volumeDiscountMap をループ:
         |      既存にsetNumが一致するものがない --> insertListに追加
         |      一致するものあり && bCartPriceReflected=true
         |        --> delFlg='0'に戻す、unitPriceを更新
         +--> saveAll(volumeDiscountList) // 更新
         +--> saveAll(insertList)         // 新規追加
         +--> return 結合リスト
```

---

## 6. 会員連携バッチ設計

### 6.1 ジョブ構成

| ジョブBean名 | ジョブクラス | 実行方式 |
|-------------|------------|---------|
| `bCartMemberUpdateJob` | `BCartMemberUpdateBatch` | CLIバッチ |
| `bCartMemberUpdateJobForWEB` | `BCartMemberUpdateBatchForWEB` | Web起動 |

#### ステップフロー

```
Step1: bCartMemberImportStep
  +--> BCartMemberImportTasklet          [会員情報取込]
       |
Step2: bCartMemberDeliveryImportStep
  +--> BCartMemberDeliveryImportTasklet  [別配送先情報取込]
       |
Step3: smilePartnerFileOutPutStep
  +--> SmilePartnerFileOutPutTasklet     [SMILE得意先マスタCSV出力]
       |
Step4: registerBCartMemberStep
  +--> RegisterBCartMemberTasklet        [得意先マスタ登録]
```

### 6.2 BCartMemberImportTasklet 処理フロー

| 項目 | 内容 |
|------|------|
| クラス | `jp.co.oda32.batch.bcart.BCartMemberImportTasklet` |
| 定数 | `API_LIMIT = 100` |
| 依存 | `BCartMemberService`, `JobExplorer` |

```
[BCartMemberImportTasklet.execute()]
  |
  +--> processMembers("created_at_min")   // 新規登録会員の取込
  +--> processMembers("updated_at_min")   // 更新会員の取込
  |
  +--> return FINISHED
```

#### processMembers 詳細

```
processMembers(timeParameter)
  |
  +--> accessToken = BCartApiConfig.getInstance().getAccessToken()
  +--> lastBatchRunTime = getLastBatchRunTime()
  |      |
  |      +--> jobExplorer.getJobInstances("BCartMemberUpdateBatch", 0, 2)
  |      +--> 2件未満 --> return null (フィルタなし=全件)
  |      +--> 前回ジョブのendTime をLocalDateTimeに変換
  |
  +--> offset = 0, moreData = true
  +--> [ページングループ] while (moreData)
         |
         +--> URL構築
         |      GET /api/v1/customers?limit=100&offset={offset}
         |      lastBatchRunTime != null
         |        --> &{timeParameter}={yyyy-MM-dd HH:mm:ss}
         |
         +--> APIコール --> responseBody取得
         |
         +--> Gson構築
         |      LocalDate用カスタムデシリアライザ
         |        "yyyy-MM-dd HH:mm:ss" --> LocalDate
         |
         +--> gson.fromJson(responseBody, BCartMemberApiResponse.class)
         |
         +--> [nullチェック] response or memberList が null
         |      --> log.error --> moreData = false
         |
         +--> [空チェック] memberList が 空 --> moreData = false
         |
         +--> [フィルタリング]
         |      status != "未承認" && status != "無効"
         |      smilePartnerMasterLinked = false に設定
         |      extIdが空の場合はwarnログ（既存データは保持される）
         |
         +--> [空チェック] フィルタ後が 空 --> offset += 100; continue
         |
         +--> bCartMemberService.updateMembers(processedMembers)
         |
         +--> memberList.size() < API_LIMIT --> moreData = false
         |
         +--> offset += API_LIMIT
```

### 6.3 BCartMemberDeliveryImportTasklet 処理フロー

| 項目 | 内容 |
|------|------|
| クラス | `jp.co.oda32.batch.bcart.BCartMemberDeliveryImportTasklet` |
| HTTPクライアント | フィールドとして`OkHttpClient`を保持 |
| JSONパース | `org.json.JSONObject` / `JSONArray`（Gsonではない） |
| 依存サービス | `BCartMemberOtherAddressesService` |

```
[BCartMemberDeliveryImportTasklet.execute()]
  |
  +--> GET https://api.bcart.jp/api/v1/other_addresses
  |      Authorization: Bearer {accessToken}
  |
  +--> [レスポンスチェック] !successful --> throw IOException
  |
  +--> JSONObject responseJson = new JSONObject(response.body().string())
  +--> JSONArray deliveryInfoArray = responseJson.getJSONArray("other_addresses")
  |
  +--> registerMemberDeliveryInfo(deliveryInfoArray)
  |      for i = 0 to deliveryInfoArray.length():
  |        JSONObject deliveryInfo = deliveryInfoArray.getJSONObject(i)
  |        BCartMemberOtherAddresses entity = BCartMemberOtherAddresses.builder()
  |          .id(deliveryInfo.getLong("id"))
  |          .customerId(deliveryInfo.getLong("customer_id"))
  |          .destinationCode(nullチェック付き getString)
  |          .compName(nullチェック付き getString)
  |          .department(nullチェック付き getString)
  |          .name(getString)
  |          .zip(getString)
  |          .pref(getString)
  |          .address1(getString)
  |          .address2(getString)
  |          .address3(nullチェック付き getString)
  |          .tel(getString)
  |          .build()
  |        bCartMemberOtherAddressesService.save(entity) // UPSERT
  |
  +--> return FINISHED
```

### 6.4 RegisterBCartMemberTasklet 処理フロー

| 項目 | 内容 |
|------|------|
| クラス | `jp.co.oda32.batch.bcart.RegisterBCartMemberTasklet` |
| 依存サービス | `BCartMemberService`, `MCompanyService`, `MPartnerService` |

```
[RegisterBCartMemberTasklet.execute()]
  |
  +--> bCartMemberService.fetchNonPartneredMembers()
  |      条件: ext_id IS NOT NULL
  |            AND m_partner に partner_code = ext_id のレコードが存在しない
  |
  +--> for each nonPartneredMember:
  |      |
  |      +--> [1] registerMCompany(member)
  |      |      MCompany.builder()
  |      |        .shopNo(B_CART_ORDER)
  |      |        .companyName(member.compName)
  |      |        .companyType(PARTNER)
  |      |        .abbreviatedCompanyName(member.compName)
  |      |      mCompanyService.insert(mCompany) --> return mCompany
  |      |
  |      +--> [2] registerMPartner(member, mCompany)
  |      |      MPartner.builder()
  |      |        .shopNo(B_CART_ORDER)
  |      |        .partnerCode(member.extId)
  |      |        .partnerName(member.compName)
  |      |        .abbreviatedPartnerName(member.compName)
  |      |        .isIncludeTaxDisplay(false)
  |      |        .companyNo(mCompany.companyNo)
  |      |      mPartnerService.insert(mPartner) --> return mPartner
  |      |
  |      +--> [3] updateMCompany(mCompany, mPartner.partnerNo)
  |             mCompany.setPartnerNo(mPartner.partnerNo)
  |             mCompanyService.update(mCompany)
  |
  +--> return FINISHED
```

---

## 7. 価格連携バッチ設計

### 7.1 ジョブ構成

| ジョブBean名 | ジョブクラス | 実行方式 |
|-------------|------------|---------|
| `bCartPriceUpdateJob` | `BCartPriceUpdateBatch` | CLIバッチ |

#### ステップフロー

```
Step1: bCartGoodsPriceUpdateStep
  +--> BCartGoodsPriceUpdateTasklet      [B-CART API経由で価格反映]
```

### 7.2 BCartGoodsPriceUpdateTasklet 処理フロー

| 項目 | 内容 |
|------|------|
| クラス | `jp.co.oda32.batch.bcart.BCartGoodsPriceUpdateTasklet` |
| HTTPクライアント | フィールドとして`OkHttpClient`を保持（再利用） |
| 依存サービス | `BCartProductSetsService`, `BCartVolumeDiscountService`, `BCartGroupPriceService`, `BCartSpecialPriceService` |

```
[BCartGoodsPriceUpdateTasklet.execute()]
  |
  +--> bCartProductSetsService.findNotBCartPriceReflected()
  |      条件: b_cart_price_reflected = false
  |
  +--> count = 0
  +--> for each target (BCartProductSets):
  |      |
  |      +--> groupPriceList = target.getGroupPrices()
  |      +--> specialPriceList = target.getSpecialPrices()
  |      +--> volumeDiscountIds != null
  |      |      --> IDリスト分割 --> findByVolumeDiscountIdList()
  |      |
  |      +--> [レート制限] ++count % 250 == 0 --> Thread.sleep(5分)
  |      |
  |      +--> updateProductPrices(setId, unitPrice, purchasePrice,
  |      |      groupPriceList, specialPriceList, volumeDiscountList)
  |      |      |
  |      |      +--> JSONObject bodyJson構築
  |      |      |      "unit_price": newUnitPrice
  |      |      |      "special_price": { "会員ID": {"unit_price": 値} }
  |      |      |      "volume_discount": { "数量": 単価 } (存在する場合のみ)
  |      |      |      "customs": [{"field_id": 9, "value": "仕入価格"}] (存在する場合のみ)
  |      |      |
  |      |      +--> PATCH https://api.bcart.jp/api/v1/product_sets/{setId}
  |      |      |      Content-Type: application/json
  |      |      |      Authorization: Bearer {token}
  |      |      |
  |      |      +--> response.isSuccessful()
  |      |             true --> return true
  |      |             false --> log.warn --> return false
  |      |
  |      +--> [更新成功時] updateBCartPriceReflected(target, volumeDiscountList)
  |             +--> volumeDiscount: bCartPriceReflected = true (各レコード)
  |             +--> groupPrice: bCartPriceReflected = true (各レコード)
  |             +--> specialPrice: bCartPriceReflected = true (各レコード)
  |             +--> productSets: bCartPriceReflected = true
  |             +--> [例外時] log.error
  |
  +--> return FINISHED
```

### 7.3 BCartGoodsPriceTableUpdateTasklet 処理フロー

| 項目 | 内容 |
|------|------|
| クラス | `jp.co.oda32.batch.bcart.BCartGoodsPriceTableUpdateTasklet` |
| 依存サービス | `BCartProductSetsService` |
| 備考 | 現時点では処理本体は未実装（スタブ） |

```
[BCartGoodsPriceTableUpdateTasklet.execute()]
  |
  +--> // TODO: v_b_cart_products_price_change から更新対象を取得
  +--> // TODO: b-cart用テーブルに更新をかける
  |
  +--> return FINISHED
```

---

## 8. 出荷CSV出力バッチ設計

### 8.1 ジョブ構成

| ジョブBean名 | ジョブクラス | 実行方式 |
|-------------|------------|---------|
| `bCartLogisticsCsvExportJob` | `BCartLogisticsCsvExportBatch` | CLIバッチ |
| `bCartLogisticsCsvExportJobForWEB` | `BCartLogisticsCsvExportBatchForWEB` | Web起動 |

#### ステップフロー

```
Step1: bCartLogisticsCsvOutputStep
  +--> BCartLogisticsCsvOutputTasklet    [出荷実績CSV出力]
```

### 8.2 BCartLogisticsCsvOutputTasklet 処理フロー

| 項目 | 内容 |
|------|------|
| クラス | `jp.co.oda32.batch.bcart.BCartLogisticsCsvOutputTasklet` |
| 依存サービス | `BCartLogisticsService`, `BCartOrderProductService`, `MShopLinkedFileService` |
| 文字コード | Shift_JIS |
| CSVフォーマット | Apache Commons CSV、`QuoteMode.ALL_NON_NULL` |

```
[BCartLogisticsCsvOutputTasklet.execute()]
  |
  +--> bCartLogisticsService.findExportableRecords()
  |      条件1: b_cart_csv_exported=false AND status IN ('発送指示', '発送済')
  |      条件2: b_cart_csv_exported=true AND is_updated=true
  |
  +--> logisticsIdsを抽出
  |
  +--> bCartOrderProductService.findByLogisticsIdIn(logisticsIds)
  |      --> BCartOrder マップ構築 (logisticsId -> BCartOrder)
  |
  +--> mShopLinkedFileService.getByShopNo(B_CART_ORDER)
  |      null --> throw Exception("ショップ連携ファイルEntityが取得できません")
  |
  +--> exportToCsv(list, outputFilePath, orderMap)
         |
         +--> FileUtil.renameCurrentFile(outputFilePath) // 既存ファイルリネーム
         |
         +--> CSVFormat.DEFAULT
         |      .withHeader(BCartLogisticsCsv.CSV_HEADERS)
         |      .withQuoteMode(QuoteMode.ALL_NON_NULL)
         |
         +--> FileOutputStream --> OutputStreamWriter(Shift_JIS) --> BufferedWriter
         |
         +--> for each record:
         |      orderMap から BCartOrder を取得
         |      csvPrinter.printRecord(
         |        record.id,              // Bカート発送ID
         |        record.deliveryCode,    // 送り状番号
         |        record.shipmentDate,    // 発送日
         |        record.shipmentCode,    // 出荷管理番号(SMILE処理連番)
         |        record.status,          // 発送状況
         |        record.memo,            // 発送メモ
         |        bCartOrder.adminMessage,// お客様への連絡事項
         |        bCartOrder.status       // 対応状況
         |      )
         |
         +--> csvPrinter.flush()
         |
         +--> 出力後更新:
         |      is_updated = false
         |      b_cart_csv_exported = true
         |      bCartLogisticsService.save(updatedList)
         |
         +--> [例外] IOException --> e.printStackTrace()
```

### 8.3 CSVフォーマット定義

#### BCartLogisticsCsv.CSV_HEADERS

| # | ヘッダー名 | ソースフィールド | 型 |
|---|-----------|----------------|-----|
| 1 | Bカート発送ID | `b_cart_logistics.id` | Long |
| 2 | 送り状番号 | `b_cart_logistics.delivery_code` | String |
| 3 | 発送日 | `b_cart_logistics.shipment_date` | String |
| 4 | 出荷管理番号 | `b_cart_logistics.shipment_code` | String |
| 5 | 発送状況 | `b_cart_logistics.status` | String |
| 6 | 発送メモ | `b_cart_logistics.memo` | String(TEXT) |
| 7 | お客様への連絡事項 | `b_cart_order.admin_message` | String(TEXT) |
| 8 | 対応状況 | `b_cart_order.status` | String |

| 出力属性 | 値 |
|---------|------|
| 文字コード | Shift_JIS |
| 区切り文字 | カンマ(,) |
| クォート | 非NULLフィールドは全てダブルクォート |
| 改行コード | CSVFormat.DEFAULTのデフォルト(CRLF) |
| ファイル拡張子 | .csv |
| 出力先パス | `m_shop_linked_file.b_cart_logistics_import_file_name` で管理 |

---

## 9. エンティティ設計

### 9.1 b_cart_order（受注情報）

| 項目 | 内容 |
|------|------|
| テーブル名 | `b_cart_order` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.BCartOrder` |
| 主キー | `id` (Long) |
| 特殊型定義 | `@TypeDef("list-array", ListArrayType)`, `@TypeDef("jsonb", JsonBinaryType)` |

| # | カラム名 | Java型 | JPA定義 | Gsonマッピング | 説明 |
|---|---------|--------|---------|--------------|------|
| 1 | `id` | Long | `@Id` | - | 受注ID |
| 2 | `code` | Long | `@Column` | - | 受注番号 |
| 3 | `customer_id` | Long | `@Column` | `@SerializedName("customer_id")` | 注文者会員ID |
| 4 | `customer_ext_id` | String | `@Column` | `@SerializedName("customer_ext_id")` | 貴社独自会員ID |
| 5 | `customer_parent_id` | String | `@Column` | `@SerializedName("customer_parent_id")` | 親代理店ID |
| 6 | `customer_salesman_id` | String | `@Column` | `@SerializedName("customer_salesman_id")` | 営業担当者ID(カンマ区切り) |
| 7 | `customer_comp_name` | String | `@Column` | `@SerializedName("customer_comp_name")` | 注文者会社名(最大128桁) |
| 8 | `customer_department` | String | `@Column` | `@SerializedName("customer_department")` | 注文者部署名 |
| 9 | `customer_name` | String | `@Column` | `@SerializedName("customer_name")` | 注文者名(最大128桁) |
| 10 | `customer_tel` | String | `@Column` | `@SerializedName("customer_tel")` | 注文者電話番号 |
| 11 | `customer_mobile_phone` | String | `@Column` | `@SerializedName("customer_mobile_phone")` | 注文者携帯番号 |
| 12 | `customer_email` | String | `@Column` | `@SerializedName("customer_email")` | 注文者メールアドレス |
| 13 | `customer_price_group_id` | String | `@Column` | `@SerializedName("customer_price_group_id")` | 価格グループ名 |
| 14 | `customer_zip` | String | `@Column` | `@SerializedName("customer_zip")` | 注文者郵便番号 |
| 15 | `customer_pref` | String | `@Column` | `@SerializedName("customer_pref")` | 注文者都道府県 |
| 16 | `customer_address1` | String | `@Column` | `@SerializedName("customer_address1")` | 注文者市区町村 |
| 17 | `customer_address2` | String | `@Column` | `@SerializedName("customer_address2")` | 注文者町域・番地 |
| 18 | `customer_address3` | String | `@Column` | `@SerializedName("customer_address3")` | 注文者ビル・建物名 |
| 19 | `customer_customs` | `List<Object>` | `@Type("list-array")`, `columnDefinition="text[]"` | `@SerializedName("customer_customs")` | 注文者カスタム項目 |
| 20 | `payment` | String | `@Column` | `@SerializedName("payment")` | 決済方法 |
| 21 | `payment_at` | LocalDate | `@Column` | `@SerializedName("payment_at")` | 決済確定日 |
| 22 | `total_price` | BigDecimal(25,2) | `@Column(precision=25,scale=2)` | `@SerializedName("total_price")` | 商品合計金額 |
| 23 | `tax` | BigDecimal | `@Column(length=8)` | `@SerializedName("tax")` | 消費税 |
| 24 | `tax_rate` | BigDecimal(25,2) | `@Column(precision=25,scale=2)` | `@SerializedName("tax_rate")` | 税率 |
| 25 | `COD_cost` | BigDecimal | `@Column(length=8)` | `@SerializedName("COD_cost")` | 決済手数料 |
| 26 | `shipping_cost` | BigDecimal | `@Column(length=8)` | `@SerializedName("shipping_cost")` | 送料 |
| 27 | `final_price` | BigDecimal | `@Column(length=11)` | `@SerializedName("final_price")` | 受注総額 |
| 28 | `use_point` | BigDecimal | `@Column` | `@SerializedName("use_point")` | ポイント利用 |
| 29 | `get_point` | BigDecimal | `@Column` | `@SerializedName("get_point")` | ポイント取得 |
| 30 | `order_totals` | `List<OrderTotal>` | `@Type("jsonb")`, `columnDefinition="jsonb"` | `@SerializedName("order_totals")` | 税率別合計金額 |
| 31 | `customer_message` | String | `@Column(length=65535)` | `@SerializedName("customer_message")` | お客様からの連絡事項 |
| 32 | `admin_message` | String | `@Column(length=65535)` | `@SerializedName("admin_message")` | お客様への連絡事項 |
| 33 | `memo` | String | `@Column(length=65535)` | `@SerializedName("memo")` | 管理用メモ |
| 34 | `customs` | `List<String>` | `@Type("list-array")`, `columnDefinition="text[]"` | `@SerializedName("customs")` | 受注カスタム項目 |
| 35 | `enquete1`-`enquete5` | String | `@Column` | `@SerializedName("enquete1")`等 | アンケート1-5 |
| 36 | `ordered_at` | String | `@Column` | `@SerializedName("ordered_at")` | 受注日(yyyy-MM-dd HH:mm:ss) |
| 37 | `affiliate_id` | String | `@Column` | `@SerializedName("affiliate_id")` | 参照元ID |
| 38 | `estimate_id` | Long | `@Column` | `@SerializedName("estimate_id")` | 見積番号 |
| 39 | `status` | String | `@Column` | `@SerializedName("status")` | 対応状況 |

**リレーション**:

| フィールド | 型 | アノテーション | マッピング |
|-----------|-----|--------------|-----------|
| `orderProductList` | `List<BCartOrderProduct>` | `@OneToMany(mappedBy="bCartOrder", cascade=ALL, fetch=LAZY)` | `@SerializedName("order_products")` |
| `bCartLogisticsList` | `List<BCartLogistics>` | `@Transient` | `@SerializedName("logistics")` |

**内部クラス OrderTotal**:

| フィールド | 型 | SerializedName | 説明 |
|-----------|-----|---------------|------|
| `taxRate` | BigDecimal | `"tax_rate"` | 税率 |
| `total` | BigDecimal | `"total"` | 税抜合計金額 |
| `tax` | BigDecimal | `"tax"` | 消費税 |
| `totalInclTax` | BigDecimal | `"total_incl_tax"` | 税込合計金額 |

### 9.2 b_cart_order_product（受注商品情報）

| 項目 | 内容 |
|------|------|
| テーブル名 | `b_cart_order_product` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.BCartOrderProduct` |
| 複合主キー | `BCartOrderProductPK` (`id`, `order_id`) |

| # | カラム名 | Java型 | 説明 |
|---|---------|--------|------|
| 1 | `id` | Long | 受注商品ID（複合PK） |
| 2 | `order_id` | Long | 受注ID（複合PK、b_cart_order参照） |
| 3 | `logistics_id` | Long | 出荷ID（b_cart_logistics参照） |
| 4 | `product_id` | Long | 商品ID |
| 5 | `main_no` | String(255) | 商品管理番号 |
| 6 | `product_no` | String(255) | 品番 |
| 7 | `jan_code` | String(255) | JANコード |
| 8 | `location_no` | String(255) | ロケーション番号 |
| 9 | `product_name` | String(255) | 商品名 |
| 10 | `product_set_id` | Long | 商品セットID |
| 11 | `set_name` | String(255) | 商品セット名 |
| 12 | `unit_price` | BigDecimal | 単価 |
| 13 | `set_quantity` | BigDecimal | 入数 |
| 14 | `set_unit` | String(255) | 単位 |
| 15 | `order_pro_count` | BigDecimal | 注文数 |
| 16 | `shipping_size` | BigDecimal | 配送サイズ |
| 17 | `tax_rate` | BigDecimal | 税率（0.10=10%） |
| 18 | `tax_type_id` | Integer | 税区分（1=標準, 2=軽減） |
| 19 | `tax_incl` | Integer | 税込フラグ（0=税別, 1=税込） |
| 20 | `item_type` | String(255) | 商品区分（product/shipping/payment_fees/point） |
| 21 | `options` | `text[]` | 商品オプションリスト |
| 22 | `set_custom3` | String | カスタム3（仕入価格を格納） |

**リレーション**:

| フィールド | 型 | アノテーション |
|-----------|-----|--------------|
| `bCartOrder` | `BCartOrder` | `@ManyToOne(fetch=LAZY)`, `@JoinColumn(name="order_id")` |
| `bCartLogistics` | `BCartLogistics` | `@ManyToOne(fetch=LAZY)`, `@JoinColumn(name="logistics_id")` |

### 9.3 b_cart_logistics（出荷情報）

| 項目 | 内容 |
|------|------|
| テーブル名 | `b_cart_logistics` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.BCartLogistics` |
| 主キー | `id` (Long) |

| # | カラム名 | Java型 | JPA定義 | SerializedName | 説明 |
|---|---------|--------|---------|---------------|------|
| 1 | `id` | Long | `@Id` | `"id"` | 出荷ID |
| 2 | `shipment_code` | String | `@Column` | `"shipment_code"` | 出荷管理番号(SMILE処理連番) |
| 3 | `delivery_code` | String | `@Column` | `"delivery_code"` | 送り状番号 |
| 4 | `destination_code` | String | `@Column` | `"destination_code"` | 配送先コード |
| 5 | `shipping_group_id` | String | `@Column` | `"shipping_group_id"` | 配送グループID |
| 6 | `comp_name` | String | `@Column` | `"comp_name"` | 配送先会社名 |
| 7 | `department` | String | `@Column` | `"department"` | 配送先部署名 |
| 8 | `name` | String | `@Column` | `"name"` | 配送先担当者名 |
| 9 | `zip` | String(16) | `@Column` | `"zip"` | 配送先郵便番号 |
| 10 | `pref` | String(32) | `@Column` | `"pref"` | 配送先都道府県 |
| 11 | `address1` | String | `@Column` | `"address1"` | 配送先市区町村 |
| 12 | `address2` | String | `@Column` | `"address2"` | 配送先町域・番地 |
| 13 | `address3` | String | `@Column` | `"address3"` | 配送先ビル・建物名 |
| 14 | `tel` | String | `@Column` | `"tel"` | 配送先電話番号 |
| 15 | `due_date` | String | `@Column` | `"due_date"` | 配送希望日 |
| 16 | `due_time` | String(32) | `@Column` | `"due_time"` | 配送希望時間 |
| 17 | `memo` | String | `@Column(columnDefinition="TEXT")` | `"memo"` | 発送メモ |
| 18 | `shipment_date` | String | `@Column` | `"shipment_date"` | 発送日 |
| 19 | `arrival_date` | String | `@Column` | `"arrival_date"` | 納品日 |
| 20 | `status` | String | `@Column` | `"status"` | 発送状況 |
| 21 | `is_updated` | boolean | `@Column` | - | 更新フラグ |
| 22 | `b_cart_csv_exported` | boolean | `@Column` | - | CSV出力済フラグ |

**リレーション**:

| フィールド | 型 | アノテーション |
|-----------|-----|--------------|
| `bCartOrderProductList` | `List<BCartOrderProduct>` | `@OneToMany(mappedBy="bCartLogistics", cascade=ALL, fetch=EAGER)`, `@BatchSize(size=30)` |

**便宜的メソッド**:

```java
@Transient
public BCartOrder getBCartOrder() {
    // bCartOrderProductList の先頭要素から BCartOrder を取得
}
```

### 9.4 b_cart_member（会員情報）

| 項目 | 内容 |
|------|------|
| テーブル名 | `b_cart_member` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.BCartMember` |
| 主キー | `id` (Long) |

主要カラムは基本設計書 6.4 を参照。追加の実装詳細:

| フラグフィールド | 型 | 初期値 | 用途 |
|----------------|-----|--------|------|
| `smile_partner_master_linked` | boolean | false | SMILE得意先マスタ連携済み |
| `need_smile_order_file_goods_code` | boolean | false | SMILE注文ファイルに商品コード出力が必要 |

### 9.5 b_cart_member_other_addresses（会員別配送先）

| 項目 | 内容 |
|------|------|
| テーブル名 | `b_cart_member_other_addresses` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.BCartMemberOtherAddresses` |
| 主キー | `id` (Long) |
| ビルダー | Lombokの`@Builder`使用 |

| # | カラム名 | Java型 | 説明 |
|---|---------|--------|------|
| 1 | `id` | Long | 別配送先ID |
| 2 | `customer_id` | Long | 会員ID |
| 3 | `destination_code` | String(255) | 配送コード |
| 4 | `comp_name` | String(255) | 配送先会社名 |
| 5 | `department` | String(255) | 配送先部署名 |
| 6 | `name` | String(255) | 配送先名 |
| 7 | `zip` | String(255) | 郵便番号 |
| 8 | `pref` | String(255) | 都道府県 |
| 9 | `address1` | String(50) | 市区町村 |
| 10 | `address2` | String(50) | 町域・番地 |
| 11 | `address3` | String(50) | ビル・建物名 |
| 12 | `tel` | String(255) | 電話番号 |

### 9.6 b_cart_products（商品マスタ）

| 項目 | 内容 |
|------|------|
| テーブル名 | `b_cart_products` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.BCartProducts` |
| 主キー | `id` (Integer) |

主要カラムは基本設計書 6.6 を参照。

### 9.7 b_cart_product_sets（商品セット）

| 項目 | 内容 |
|------|------|
| テーブル名 | `b_cart_product_sets` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.BCartProductSets` |
| 主キー | `id` (Long) |

主要カラムは基本設計書 6.7 を参照。追加の実装詳細:

| Transientフィールド | 型 | 用途 |
|-------------------|-----|------|
| `volumeDiscountMap` | `Map<String, BigDecimal>` | APIレスポンスの数量割引マップ（Gson用） |
| `specialPriceMap` | `Map<String, BCartSpecialPrice>` | APIレスポンスの特別価格マップ（Gson用） |
| `groupPriceMap` | `Map<String, BCartGroupPrice>` | APIレスポンスのグループ価格マップ（Gson用） |

**価格更新フラグの意味**:

| `b_cart_price_reflected` | 意味 |
|--------------------------|------|
| `true` | 本システムの価格がB-CART APIに反映済み。インポート時にAPIからの値で上書き可能 |
| `false` | 本システムで価格を変更したがB-CARTに未反映。インポート時にAPIからの値で上書きしない |

### 9.8 b_cart_group_price（グループ価格）

| 項目 | 内容 |
|------|------|
| テーブル名 | `b_cart_group_price` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.productSets.BCartGroupPrice` |
| 複合主キー | `BCartGroupPricePK` (`product_set_id`, `group_id`) |

| # | カラム名 | Java型 | 説明 |
|---|---------|--------|------|
| 1 | `product_set_id` | Long | 商品セットID（複合PK） |
| 2 | `group_id` | String | グループID（複合PK） |
| 3 | `name` | String | グループ名 |
| 4 | `rate` | BigDecimal | 割引率 |
| 5 | `unit_price` | BigDecimal | 単価 |
| 6 | `fixed_price` | BigDecimal | 固定価格 |
| 7 | `volume_discount_ids` | String | 数量割引IDリスト(カンマ区切り) |
| 8 | `b_cart_price_reflected` | boolean | B-CART価格反映済フラグ |

**Transientフィールド**: `volumeDiscount: Map<String, BigDecimal>` (Gsonデシリアライズ用)

### 9.9 b_cart_special_price（特別価格）

| 項目 | 内容 |
|------|------|
| テーブル名 | `b_cart_special_price` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.productSets.BCartSpecialPrice` |
| 複合主キー | `BCartSpecialPricePK` (`product_set_id`, `customer_id`) |

| # | カラム名 | Java型 | 説明 |
|---|---------|--------|------|
| 1 | `product_set_id` | Long | 商品セットID（複合PK） |
| 2 | `customer_id` | Long | 会員ID（複合PK） |
| 3 | `unit_price` | BigDecimal | 特別単価 |
| 4 | `volume_discount_ids` | String | 数量割引IDリスト(カンマ区切り) |
| 5 | `b_cart_price_reflected` | boolean | B-CART価格反映済フラグ |

**Transientフィールド**: `volumeDiscountMap: Map<String, BigDecimal>` (Gsonデシリアライズ用)

### 9.10 b_cart_volume_discount（数量割引）

| 項目 | 内容 |
|------|------|
| テーブル名 | `b_cart_volume_discount` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.BCartVolumeDiscount` |
| 主キー | `volume_discount_id` (Long、シーケンス: `b_cart_volume_discount_seq`) |

| # | カラム名 | Java型 | 説明 |
|---|---------|--------|------|
| 1 | `volume_discount_id` | Long | 数量割引ID（PK、自動採番） |
| 2 | `set_num` | BigDecimal | セット数（数量） |
| 3 | `unit_price` | BigDecimal | 単価 |
| 4 | `product_set_id` | Long | 商品セットID |
| 5 | `customer_id` | Long | 会員ID（会員別割引の場合） |
| 6 | `del_flg` | String | 削除フラグ（'0'=有効, '1'=削除） |
| 7 | `b_cart_price_reflected` | boolean | B-CART価格反映済フラグ |

### 9.11 x_delivery_mapping（納品先コードマッピング）

| 項目 | 内容 |
|------|------|
| テーブル名 | `x_delivery_mapping` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.DeliveryMapping` |
| 主キー | `x_delivery_mapping_id` (Integer、自動採番) |

| # | カラム名 | Java型 | 説明 |
|---|---------|--------|------|
| 1 | `x_delivery_mapping_id` | Integer | ID（PK、自動採番） |
| 2 | `b_cart_customer_id` | Long | B-CART会員ID |
| 3 | `delivery_name` | String | 配送先名（会社名+部署名） |
| 4 | `smile_delivery_code` | String | SMILEの納品先コード（6桁連番、ユニーク） |
| 5 | `b_cart_destination_code` | String | B-CARTの配送先コード |
| 6 | `partner_code` | String | SMILEの得意先コード |
| 7 | `delivery_index` | String | 納品先索引 |
| 8 | `recipient_name1` | String | 荷受け人名1 |
| 9 | `recipient_name2` | String | 荷受け人名2 |
| 10 | `zip` | String | 郵便番号 |
| 11 | `address1` | String | 住所1（都道府県+市区町村） |
| 12 | `address2` | String | 住所2 |
| 13 | `address3` | String | 住所3 |
| 14 | `phone_number` | String | 電話番号 |
| 15 | `fax_number` | String | FAX番号 |
| 16 | `smile_csv_outputted` | Boolean | SMILE CSV出力済フラグ |

### 9.12 t_smile_order_import_file（SMILE注文連携ファイル）

| 項目 | 内容 |
|------|------|
| テーブル名 | `t_smile_order_import_file` |
| Entityクラス | `jp.co.oda32.domain.model.bcart.TSmileOrderImportFile` |
| 主キー | `id` (Long、自動採番) |

主要カラムは基本設計書 6.12 を参照。

---

## 10. Gson変換設計

### 10.1 カスタムデシリアライザー/アダプター一覧

| クラス名 | パッケージ | 対象型 | 用途 |
|---------|-----------|--------|------|
| `CustomNumberDeserializer<T>` | `jp.co.oda32.util.gson` | `Integer`, `Double`, `Float`, `Long` | 文字列として返却される数値の安全な型変換 |
| `CustomDateTypeAdapter` | `jp.co.oda32.util.gson` | `Date` | `yyyy-MM-dd HH:mm:ss`形式の日付パース |
| `SpecialPriceDeserializer` | `jp.co.oda32.util.gson.bcart.productSets` | `Map<String, BCartSpecialPrice>` | 動的キー(会員ID)を持つ特別価格JSONの変換 |
| `GroupPriceDeserializer` | `jp.co.oda32.util.gson.bcart.productSets` | `Map<String, BCartGroupPrice>` | 動的キー(グループID)を持つグループ価格JSONの変換 |
| `VolumeDiscountDeserializer` | `jp.co.oda32.util.gson.bcart.productSets` | `Map<String, Integer>` | 動的キー(数量)を持つ数量割引JSONの変換 |
| `StockParentDeserializer` | `jp.co.oda32.util.gson.bcart.productSets` | `StockParent` | 参照在庫JSONの変換 |

### 10.2 CustomNumberDeserializer 設計

B-CART APIのレスポンスでは数値フィールドが文字列として返却されることがあるため、安全な型変換を行う汎用デシリアライザー。

```java
public class CustomNumberDeserializer<T extends Number> extends TypeAdapter<T> {
    private final Class<T> type;
}
```

| 入力値 | 処理 | 出力 |
|--------|------|------|
| `JsonToken.NULL` | `in.nextNull()` | `null` |
| `""` (空文字) | - | `null` |
| `"123"` (文字列数値) | 型に応じたvalueOf | `Integer(123)` / `Long(123)` 等 |
| `"abc"` (非数値文字列) | `NumberFormatException` | `RuntimeException`をスロー |

**対応型の変換ロジック**:

```java
if (type == Integer.class) return type.cast(Integer.valueOf(stringValue));
else if (type == Double.class) return type.cast(Double.valueOf(stringValue));
else if (type == Float.class) return type.cast(Float.valueOf(stringValue));
else if (type == Long.class) return type.cast(Long.valueOf(stringValue));
```

### 10.3 CustomDateTypeAdapter 設計

`java.util.Date`型のフィールドを`yyyy-MM-dd HH:mm:ss`形式でパースするデシリアライザー。`SimpleDateFormat`を使用し、スレッドセーフのため`synchronized`ブロック内でパースする。

```java
public class CustomDateTypeAdapter implements JsonDeserializer<Date> {
    private final DateFormat dateFormat;

    public CustomDateTypeAdapter(String format) {
        dateFormat = new SimpleDateFormat(format);
    }

    @Override
    public Date deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
        synchronized (dateFormat) {
            return dateFormat.parse(json.getAsString());
        }
    }
}
```

### 10.4 SpecialPriceDeserializer 設計

B-CART APIの`special_price`フィールドは動的キー（会員ID）を持つJSONオブジェクト。

**入力JSON例**:
```json
{
  "123": { "unit_price": 900, "volume_discount": {"10": 850} },
  "456,789": { "unit_price": 800 }
}
```

**処理ロジック**:
```
for each entry in jsonObject:
    key = entry.getKey()         // "123" or "456,789"
    value = context.deserialize(entry.getValue(), BCartSpecialPrice.class)
    resultMap.put(key, value)
return resultMap
```

キーにカンマ区切りの複数会員IDが含まれる場合は、呼び出し元の`BCartProductSetsImportTasklet.convertSpecialPriceMap()`で分割処理される。

### 10.5 GroupPriceDeserializer 設計

B-CART APIの`group_price`フィールドは動的キー（グループID）を持つJSONオブジェクト。

**入力JSON例**:
```json
{
  "1": { "name": "グループA", "rate": "10", "unit_price": 900, "fixed_price": null, "volume_discount": {"10": 850} }
}
```

**処理ロジック**:
```
for each entry in jsonObject:
    groupPriceObject = entry.getValue().getAsJsonObject()
    name = getString("name")                                // nullable
    rate = getString("rate") -> BigDecimal                   // nullable
    unitPrice = getBigDecimal("unit_price")                  // nullable
    fixedPrice = getDouble("fixed_price") -> BigDecimal      // nullable (null check)
    volumeDiscount = Map<String, BigDecimal>                  // nullable (nested parse)
    BCartGroupPrice = builder().name(name).rate(rate)...build()
    groupPriceMap.put(key, bCartGroupPrice)
return groupPriceMap
```

`ClassCastException`が発生した場合はログ出力してスキップする。

### 10.6 VolumeDiscountDeserializer 設計

数量（文字列キー）と単価（整数値）のマップを変換する。

**入力JSON例**:
```json
{ "10": 950, "20": 900 }
```

**処理ロジック**:
```
for each entry in jsonObject:
    volumeDiscount.put(entry.getKey(), entry.getValue().getAsInt())
return volumeDiscount
```

### 10.7 StockParentDeserializer 設計

参照在庫のJSONオブジェクトを`StockParent`エンティティに変換する。

**処理ロジック**:
```
jsonObject -> context.deserialize(jsonObject, Map<String, Integer>.class)
StockParent stockParent = new StockParent()
stockParent.setStockParentMap(map)
return stockParent
```

### 10.8 Gson構築パターン一覧

#### 注文取込用（BCartOrderRegisterTasklet）

```java
Gson gson = new GsonBuilder()
    .registerTypeAdapter(Integer.class, new CustomNumberDeserializer<>(Integer.class))
    .registerTypeAdapter(Double.class,  new CustomNumberDeserializer<>(Double.class))
    .registerTypeAdapter(Float.class,   new CustomNumberDeserializer<>(Float.class))
    .registerTypeAdapter(Long.class,    new CustomNumberDeserializer<>(Long.class))
    .create();
```

#### 商品取込用（BCartProductsImportTasklet）

```java
Gson gson = new GsonBuilder()
    .registerTypeAdapter(Integer.class, new CustomNumberDeserializer<>(Integer.class))
    .registerTypeAdapter(Double.class,  new CustomNumberDeserializer<>(Double.class))
    .registerTypeAdapter(Float.class,   new CustomNumberDeserializer<>(Float.class))
    .registerTypeAdapter(Long.class,    new CustomNumberDeserializer<>(Long.class))
    .registerTypeAdapter(Date.class,    new CustomDateTypeAdapter("yyyy-MM-dd HH:mm:ss"))
    .create();
```

#### 商品セット取込用（BCartProductSetsImportTasklet）

```java
Gson gson = new GsonBuilder()
    .registerTypeAdapter(Integer.class, new CustomNumberDeserializer<>(Integer.class))
    .registerTypeAdapter(Double.class,  new CustomNumberDeserializer<>(Double.class))
    .registerTypeAdapter(Float.class,   new CustomNumberDeserializer<>(Float.class))
    .registerTypeAdapter(Long.class,    new CustomNumberDeserializer<>(Long.class))
    .registerTypeAdapter(Date.class,    new CustomDateTypeAdapter("yyyy-MM-dd HH:mm:ss"))
    .registerTypeAdapter(new TypeToken<Map<String, BCartSpecialPrice>>(){}.getType(),
                         new SpecialPriceDeserializer())
    .registerTypeAdapter(new TypeToken<Map<String, BCartGroupPrice>>(){}.getType(),
                         new GroupPriceDeserializer())
    .registerTypeAdapter(new TypeToken<Map<String, Integer>>(){}.getType(),
                         new VolumeDiscountDeserializer())
    .registerTypeAdapter(StockParent.class, new StockParentDeserializer())
    .create();
```

#### 会員取込用（BCartMemberImportTasklet）

```java
Gson gson = new GsonBuilder()
    .registerTypeAdapter(LocalDate.class, new JsonDeserializer<LocalDate>() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        @Override
        public LocalDate deserialize(JsonElement json, Type typeOfT,
                                     JsonDeserializationContext context) {
            return LocalDate.parse(json.getAsString(), formatter);
        }
    })
    .create();
```

### 10.9 APIレスポンスモデル

| クラス | `@SerializedName` | リスト型 |
|--------|-------------------|---------|
| `BCartOrdersApiResponse` | `"orders"` | `List<BCartOrder>` |
| `BCartProductsApiResponse` | `"products"` | `List<BCartProducts>` |
| `BCartProductSetsApiResponse` | `"product_sets"` | `List<BCartProductSets>` |
| `BCartMemberApiResponse` | `"customers"` | `List<BCartMember>` |

---

## 11. 画面項目定義書

### 11.1 画面基本情報

| 項目 | 内容 |
|------|------|
| 画面名 | B-Cart出荷情報入力 |
| 画面ID | bcart_shipping_info_input |
| URL | `/bcart/shippingInputForm` (GET) |
| テンプレート | `src/main/resources/templates/bcart/bcart_shipping_info_input.html` |
| CSS | `/css/bcart/bcartShippingInput.css` |
| JavaScript | `/js/bcart/bcartShippingInput.js` |
| レイアウト | `layout:decorator="layout"` (共通レイアウト) |
| フォームオブジェクト | `bCartShippingSearchForm` (BCartShippingSearchForm) |

### 11.2 検索条件エリア

| # | 項目名 | HTML要素 | id/name | バインドフィールド | 説明 |
|---|--------|---------|---------|-----------------|------|
| 1 | 店舗番号 | `<input type="hidden">` | `shopSelect` / `shopNo` | - | 固定値: `1` |
| 2 | 得意先コード | `<input type="text" class="form-control">` | `partnerCode` | `*{partnerCode}` | 得意先コード入力 |
| 3 | 得意先番号 | `<input type="hidden">` | `partnerNo` | `*{partnerNo}` | 得意先番号(hidden) |
| 4 | 得意先名 | `<div>` | `partnerName` | `*{partnerName}` | 得意先名表示 |
| 5 | 得意先検索ボタン | `<button type="button" class="companySearch">` | - | - | 得意先検索ダイアログ起動 |
| 6 | 出荷ステータス | `<select class="form-control">` | `bcartShippingStatusSelect` / `bcartShippingStatus` | `*{bcartShipmentStatus}` | ステータス選択 |
| 7 | 検索ボタン | `<button type="submit" class="action-button">` | - | - | `data-action="/bcart_shipping_search"` |

#### 出荷ステータスセレクトボックスのオプション

| 表示値 | value | 説明 |
|--------|-------|------|
| 選択して下さい | `""` | デフォルト（ステータス未選択時は未発送+発送指示を表示） |
| 未発送 | `NOT_SHIPPED` | |
| 発送指示 | `SHIPPING_INSTRUCTED` | |
| 発送済 | `SHIPPED` | |
| 対象外 | `EXCLUDED` | |

### 11.3 出荷情報一覧テーブル

テーブルID: `input_table`
テーブルクラス: `table table-bordered table-double-striped`

1件の出荷データを2行で表示する。`th:each="inputForm, rowStat : *{bCartShippingInputFormList}"`

#### ヘッダー行（2段構成）

**第1段ヘッダー**:

| # | 列名 | rowspan | 備考 |
|---|------|---------|------|
| 1 | (全選択チェックボックス) | 1 | `<input type="checkbox" id="selectAll">` |
| 2 | 得意先コード | 1 | |
| 3 | b-cart.LogisticsID | 1 | |
| 4 | 届け先 | 2 | rowspan="2" |
| 5 | 商品コード：商品名：数量 | 2 | rowspan="2" |
| 6 | 送り状番号 | 1 | |
| 7 | 出荷ステータス | 2 | rowspan="2" |
| 8 | メモ | 1 | |

**第2段ヘッダー**:

| # | 列名 | 備考 |
|---|------|------|
| 1 | 選択 | |
| 2 | 得意先名 | |
| 3 | smile伝票番号 | |
| 4 | 出荷日 | |
| 5 | 得意先へ連絡事項 | |

#### データ行（第1行）

| # | 項目名 | HTML要素 | バインド | 入力/表示 | rowspan |
|---|--------|---------|---------|----------|---------|
| 1 | 選択チェックボックス | `<input type="checkbox" class="row-select">` | `selectedItems[{index}]` value=`{index}` | チェックボックス | 2 |
| 2 | 得意先コード | `<td>` + `<input type="hidden">` | `*{bCartShippingInputFormList[i].partnerCode}` | 表示 | 1 |
| 3 | b-cart LogisticsID | `<td>` + `<input type="hidden">` | `*{bCartShippingInputFormList[i].bCartLogisticsId}` | 表示 | 1 |
| 4 | 届け先 | `<td>` + `<input type="hidden">` | `*{bCartShippingInputFormList[i].deliveryCompName}` | 表示 | 2 |
| 5 | 商品コード：商品名：数量 | `<ul><li th:each="goods">` + `<input type="hidden">` | `*{bCartShippingInputFormList[i].goodsInfo}` | 表示(リスト) | 2 |
| 6 | 送り状番号 | `<input type="text" class="form-control">` | `*{bCartShippingInputFormList[i].deliveryCode}` | テキスト入力 | 1 |
| 7 | 出荷ステータス | `<select class="form-control">` | `*{bCartShippingInputFormList[i].shipmentStatus}` | セレクトボックス | 2 |
| 8 | メモ | `<textarea class="form-control">` | `*{bCartShippingInputFormList[i].memo}` | テキストエリア | 1 |

#### データ行（第2行）

| # | 項目名 | HTML要素 | バインド | 入力/表示 |
|---|--------|---------|---------|----------|
| 1 | 得意先名 | `<td>` + `<input type="hidden">` | `*{bCartShippingInputFormList[i].partnerName}` | 表示 |
| 2 | smile伝票番号 | `<ul><li th:each="slipNo">` + `<input type="hidden">` | `*{bCartShippingInputFormList[i].slipNoList}` | 表示(リスト) |
| 3 | 出荷日 | `<input type="text" class="datepicker form-control">` | `*{bCartShippingInputFormList[i].shipmentDateStr}` | datepicker入力 |
| 4 | 得意先へ連絡事項 | `<textarea class="form-control">` | `*{bCartShippingInputFormList[i].adminMessage}` | テキストエリア |

### 11.4 一括更新エリア

| # | 項目名 | HTML要素 | id | 説明 |
|---|--------|---------|------|------|
| 1 | 一括更新ステータス | `<select class="form-control">` | `bulkStatusSelect` | BcartShipmentStatusの全値を選択肢として表示 |
| 2 | 一括更新ボタン | `<button type="button" class="btn btn-primary">` | `bulkUpdateButton` | "選択した項目を一括更新" |

### 11.5 操作ボタン

| # | ボタン名 | HTML要素 | 送信先 | 説明 |
|---|--------|---------|--------|------|
| 1 | 検索 | `<button type="submit" class="action-button">` | `POST /bcart/bcart_shipping_search` | `data-action="/bcart_shipping_search"` |
| 2 | 出荷情報更新 | `<button type="submit" class="action-button">` | `POST /bcart/bcart_shipping_update` | `data-action="/bcart_shipping_update"` |
| 3 | 選択した項目を一括更新 | `<button type="button">` | `POST /bcart/bcart_shipping_bulk_update` | JavaScript経由で送信 |

### 11.6 画面遷移図

```
[B-Cart出荷情報入力画面]
  |
  +--> [GET /bcart/shippingInputForm] --> 初期表示（未発送+発送指示一覧）
  |
  +--> [POST /bcart/bcart_shipping_search] --> 条件検索 --> 同画面再表示
  |
  +--> [POST /bcart/bcart_shipping_update] --> 個別更新 --> 同画面再表示
  |
  +--> [POST /bcart/bcart_shipping_bulk_update] --> 一括更新 --> 同画面再表示（未発送+発送指示一覧）
```

### 11.7 更新制約

| 制約 | 条件 | 動作 |
|------|------|------|
| CSV出力済み発送済行の更新禁止 | `status == "発送済"` AND `bCartCsvExported == true` | フィルタで除外（更新対象外） |
| 空リストの更新スキップ | `bCartShippingInputFormList`が空 | 何もせずテンプレート返却 |
| 一括更新の空選択スキップ | `selectedItems`が空 or `status`がnull | 何もせずテンプレート返却 |

---

以上
