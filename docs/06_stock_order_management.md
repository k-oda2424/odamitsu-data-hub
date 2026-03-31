# 在庫管理・注文管理 機能仕様書

**対象システム**: stock-app (小田光株式会社 社内システム)
**作成日**: 2026-02-23
**対象バージョン**: Spring Boot 2.1.1 / PostgreSQL 9.6

---

## 目次

1. [在庫管理](#在庫管理)
   1. [ドメインモデル概要](#在庫ドメインモデル概要)
   2. [エンティティ定義](#在庫エンティティ定義)
   3. [在庫一覧画面](#在庫一覧画面)
   4. [在庫ログ（履歴）画面](#在庫ログ履歴画面)
   5. [在庫新規登録画面](#在庫新規登録画面)
   6. [3単位在庫モデル](#3単位在庫モデル)
   7. [在庫ログの理由コード](#在庫ログの理由コード)
   8. [適正在庫計算ロジック](#適正在庫計算ロジック)
2. [注文管理](#注文管理)
   1. [ドメインモデル概要](#注文ドメインモデル概要)
   2. [エンティティ定義](#注文エンティティ定義)
   3. [注文一覧画面](#注文一覧画面)
   4. [取引先商品発注入力画面](#取引先商品発注入力画面)
   5. [注文チェーン](#注文チェーン)
   6. [注文ステータスコード](#注文ステータスコード)
   7. [IOrderDetailEntity インターフェース](#iorderdetailentity-インターフェース)
   8. [SMILE / B-CART 連携キー](#smile--b-cart-連携キー)
3. [リポジトリ一覧](#リポジトリ一覧)
4. [サービス一覧](#サービス一覧)
5. [仕様クラス（Specification）一覧](#仕様クラスspecification一覧)

---

## 在庫管理

### 在庫ドメインモデル概要

在庫管理は以下のエンティティ群で構成される。

| エンティティ | テーブル | 役割 |
|---|---|---|
| `TStock` | `t_stock` | 現在の在庫数量（商品×倉庫単位） |
| `TStockLog` | `t_stock_log` | 在庫変動履歴 |
| `TShopAppropriateStock` | `t_shop_appropriate_stock` | ショップ単位の適正在庫 |
| `TWarehouseAppropriateStock` | `t_warehouse_appropriate_stock` | 倉庫単位の適正在庫 |

インターフェース `IStockEntity` が `TStock` および `TStockLog` に実装されており、3単位の在庫数量操作を統一的に扱う。

---

### 在庫エンティティ定義

#### TStock（在庫テーブル: `t_stock`）

複合主キー: `goods_no` + `warehouse_no`（`TStockPK` クラスで定義）

| カラム名 | 型 | 説明 |
|---|---|---|
| `goods_no` | Integer (PK) | 商品番号 |
| `warehouse_no` | Integer (PK) | 倉庫番号 |
| `company_no` | Integer | 会社番号 |
| `shop_no` | Integer | ショップ番号 |
| `unit1_no` | Integer | 単位1番号（通常: バラ/個） |
| `unit1_stock_num` | BigDecimal | 単位1の在庫数量 |
| `unit2_no` | Integer | 単位2番号（通常: ケース） |
| `unit2_stock_num` | BigDecimal | 単位2の在庫数量 |
| `unit3_no` | Integer | 単位3番号（第3単位、現在は非表示） |
| `unit3_stock_num` | BigDecimal | 単位3の在庫数量 |
| `lead_time` | Integer | リードタイム（日数） |
| `del_flg` | String | 削除フラグ（'0'=有効、'1'=削除） |
| `add_date_time` | Timestamp | 登録日時 |
| `add_user_no` | Integer | 登録ユーザー番号 |
| `modify_date_time` | Timestamp | 更新日時 |
| `modify_user_no` | Integer | 更新ユーザー番号 |

**関連エンティティ（JPA Join）**:
- `mGoods`: 商品マスタ（`MGoods`）← `goods_no`
- `mWarehouse`: 倉庫マスタ（`MWarehouse`）← `warehouse_no`
- `goodsUnit1`, `goodsUnit2`, `goodsUnit3`: 商品単位（`MGoodsUnit`）← 各 unit_no
- `tShopAppropriateStock`: ショップ適正在庫（`TShopAppropriateStock`）← `goods_no` + `shop_no`

**計算メソッド**:

- `getPieceNum()`: 全単位の在庫を個（バラ）数に換算した合計数量を返す
  - 計算式: `unit1StockNum × unit1.containNum + unit2StockNum × unit2.containNum + unit3StockNum × unit3.containNum`
  - `containNum` が null の場合、その単位の貢献分は 0 とする

- `getEnoughStock()`: 在庫過不足数量を返す（在庫一覧画面で使用）
  - 計算式: `getPieceNum() - tShopAppropriateStock.appropriateStock`
  - `tShopAppropriateStock` が null または `appropriateStock` が null の場合は null を返す

---

#### TStockLog（在庫履歴テーブル: `t_stock_log`）

主キー: `stock_log_no`（シーケンス自動採番: `t_stock_log_stock_log_no_seq`）

| カラム名 | 型 | 説明 |
|---|---|---|
| `stock_log_no` | Integer (PK) | 在庫ログ番号（シーケンス） |
| `goods_no` | Integer | 商品番号 |
| `warehouse_no` | Integer | 倉庫番号 |
| `move_time` | LocalDateTime | 在庫移動日時 |
| `company_no` | Integer | 会社番号 |
| `shop_no` | Integer | ショップ番号 |
| `unit1_no` | Integer | 単位1番号 |
| `unit1_stock_num` | BigDecimal | 単位1の変動数量 |
| `unit2_no` | Integer | 単位2番号 |
| `unit2_stock_num` | BigDecimal | 単位2の変動数量 |
| `unit3_no` | Integer | 単位3番号 |
| `unit3_stock_num` | BigDecimal | 単位3の変動数量 |
| `reason` | String | 変動理由（StockLogReason のコード値） |
| `delivery_no` | Integer | 出荷番号（出荷に連動した変動の場合） |
| `destination_warehouse_no` | Integer | 移動先倉庫番号（倉庫間移動の場合） |
| `purchase_no` | Integer | 仕入番号（仕入に連動した変動の場合） |
| `del_flg` | String | 削除フラグ |
| `add_date_time` | Timestamp | 登録日時 |
| `add_user_no` | Integer | 登録ユーザー番号 |
| `modify_date_time` | Timestamp | 更新日時 |
| `modify_user_no` | Integer | 更新ユーザー番号 |

**関連エンティティ（JPA Join）**:
- `mGoods`: 商品マスタ（`MGoods`）← `goods_no`
- `mWarehouse`: 倉庫マスタ（`MWarehouse`）← `warehouse_no`
- `goodsUnit1`, `goodsUnit2`, `goodsUnit3`: 商品単位（`MGoodsUnit`）← 各 unit_no

**ユニークキー検索**: `goods_no` + `warehouse_no` + `move_time` + `delivery_no` + `purchase_no`

---

#### TShopAppropriateStock（ショップ適正在庫テーブル: `t_shop_appropriate_stock`）

複合主キー: `goods_no` + `shop_no`（`TShopAppropriateStockPK` クラスで定義）

| カラム名 | 型 | 説明 |
|---|---|---|
| `goods_no` | Integer (PK) | 商品番号 |
| `shop_no` | Integer (PK) | ショップ番号 |
| `appropriate_stock` | BigDecimal | 適正在庫数量（個換算） |
| `safety_stock` | BigDecimal | 安全在庫数量 |
| `del_flg` | String | 削除フラグ |
| `add_date_time` | Timestamp | 登録日時 |
| `add_user_no` | Integer | 登録ユーザー番号 |
| `modify_date_time` | Timestamp | 更新日時 |
| `modify_user_no` | Integer | 更新ユーザー番号 |

---

#### TWarehouseAppropriateStock（倉庫適正在庫テーブル: `t_warehouse_appropriate_stock`）

複合主キー: `goods_no` + `warehouse_no`（`TWarehouseAppropriateStockPK` クラスで定義）

| カラム名 | 型 | 説明 |
|---|---|---|
| `goods_no` | Integer (PK) | 商品番号 |
| `warehouse_no` | Integer (PK) | 倉庫番号 |
| `appropriate_stock` | BigDecimal | 適正在庫数量 |
| `safety_stock` | BigDecimal | 安全在庫数量 |
| `del_flg` | String | 削除フラグ |
| `add_date_time` | Timestamp | 登録日時 |
| `add_user_no` | Integer | 登録ユーザー番号 |
| `modify_date_time` | Timestamp | 更新日時 |
| `modify_user_no` | Integer | 更新ユーザー番号 |

---

#### IStockEntity インターフェース

`IStockEntity` は `IEntity` を継承し、`TStock` および `TStockLog` の両エンティティに実装される。
3単位の在庫数量を一元的に操作するための抽象化レイヤー。

```java
public interface IStockEntity extends IEntity {
    Integer getGoodsNo();
    Integer getWarehouseNo();
    Integer getUnit1No();
    BigDecimal getUnit1StockNum();
    void setUnit1StockNum(BigDecimal unit1StockNum);
    void setUnit1No(Integer unit1No);
    Integer getUnit2No();
    BigDecimal getUnit2StockNum();
    void setUnit2No(Integer unit2No);
    void setUnit2StockNum(BigDecimal unit2StockNum);
    Integer getUnit3No();
    BigDecimal getUnit3StockNum();
    void setUnit3No(Integer unit3No);
    void setUnit3StockNum(BigDecimal unit3StockNum);
}
```

---

### 在庫一覧画面

#### 基本情報

| 項目 | 内容 |
|---|---|
| 画面タイトル | 在庫一覧 |
| URL | `/stockList`（GET）、`/stockListSearch`（POST） |
| テンプレート | `src/main/resources/templates/stock/t_stock_list.html` |
| コントローラ | `TStockListController` |

#### 検索条件フォーム（`TStockListForm`）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `companyNo` | Integer | `@NotNull`（必須） | 会社番号 |
| `warehouseNo` | Integer | 任意 | 倉庫番号 |
| `goodsNo` | Integer | 任意 | 商品番号 |
| `goodsCode` | String | 任意 | 商品コード |
| `goodsName` | String | 任意 | 商品名（部分一致） |

#### 検索ロジック

`TStockService.find(goodsNo, companyNo, warehouseNo, goodsName, Flag.NO)` を呼び出す。
検索結果は `goodsNo` の昇順でソート。

Specification による絞り込み:
- `goodsNo`: 完全一致
- `companyNo`: 完全一致（必須）
- `warehouseNo`: 完全一致（任意）
- `goodsName`: `m_goods.goods_name` の部分一致（`%goodsName%`）
- `del_flg`: `Flag.NO`（'0'）で固定（論理削除除外）

#### 表示項目（一覧テーブル）

| 表示名 | 取得元 | 備考 |
|---|---|---|
| 倉庫 | `mWarehouse.warehouseName` | |
| 商品番号 | `mGoods.goodsNo` | |
| 商品名 | `mGoods.goodsName` | |
| JANコード | `mGoods.janCode` | null の場合「登録なし」を表示 |
| 数量1 | `unit1StockNum` | バラ数量 |
| 単位1 | `goodsUnit1.unit` | 例: "piece" |
| 数量2 | `unit2StockNum` | ケース数量（unit2 が null の場合 0 を表示） |
| 単位2 | `goodsUnit2.unit` | 例: "case"（null の場合「登録なし」） |
| 入数2 | `goodsUnit2.containNum` | ケース入数（null の場合「登録なし」） |
| 適正在庫 | `tShopAppropriateStock.appropriateStock` | null の場合 0 を表示 |
| 過不足 | `getEnoughStock()` | 現在庫(個換算) - 適正在庫。null の場合 0 を表示 |

**注意**: 単位3（`unit3StockNum`, `goodsUnit3.unit`, `goodsUnit3.containNum`）は現在テンプレートでコメントアウト済みであり、非表示。

#### 画面機能

- 検索ボタン: `POST /stockListSearch`
- クリアボタン: フォームのリセット
- 在庫新規登録ボタン: `GET /stockCreate` に遷移（JavaScript による遷移）

---

### 在庫ログ（履歴）画面

#### 基本情報

| 項目 | 内容 |
|---|---|
| 画面タイトル | 在庫履歴 |
| URL | `/stockLog`（GET）、`/stockLogSearch`（POST） |
| テンプレート | `src/main/resources/templates/stock/t_stock_log.html` |
| コントローラ | `TStockLogController` |

#### 検索条件フォーム（`TStockLogForm`）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `companyNo` | Integer | `@NotNull`（必須） | 会社番号 |
| `warehouseNo` | Integer | 任意 | 倉庫番号 |
| `goodsNo` | Integer | 任意（隠しフィールド） | 商品番号（商品選択ポップアップから設定） |
| `goodsName` | String | 任意（読み取り専用表示） | 商品名（表示用） |
| `janCode` | String | 任意（最大13桁） | JANコード（商品検索に使用） |

#### 検索ロジック

`TStockLogService.find(goodsNo, companyNo, warehouseNo, Flag.NO)` を呼び出す。
検索結果は `moveTime` 降順、同一日時の場合は `goodsNo` 昇順でソート。

#### 表示項目（一覧テーブル）

| 表示名 | 取得元 | 備考 |
|---|---|---|
| 倉庫 | `mWarehouse.warehouseName` | |
| 商品番号 | `mGoods.goodsNo` | |
| 商品名 | `mGoods.goodsName` | |
| JANコード | `mGoods.janCode` | null の場合「登録なし」 |
| 数量1 | `unit1StockNum` | バラ変動数量 |
| 単位1 | `goodsUnit1.unit` | |
| 数量2 | `unit2StockNum` | ケース変動数量 |
| 単位2 | `goodsUnit2.unit` | |
| 入数2 | `goodsUnit2.containNum` | |
| 理由 | `reason` | StockLogReason のコード値 |
| 移動時刻 | `moveTime` | `DateTimeUtil` でフォーマット |

---

### 在庫新規登録画面

#### 基本情報

| 項目 | 内容 |
|---|---|
| 画面タイトル | 在庫新規登録 |
| URL | `/stockCreate`（GET・POST） |
| テンプレート | `src/main/resources/templates/stock/t_stock_create.html` |
| コントローラ | `TStockCreateController` |

#### 入力フォーム（`TStockCreateForm`）

| フィールド | 型 | 説明 |
|---|---|---|
| `companyNo` | Integer | 会社番号（プルダウン） |
| `warehouseNo` | Integer | 倉庫番号（プルダウン、会社選択後に動的ロード） |
| `janCode` | String | JANコード（入力後に onblur イベントで商品マスタを検索） |
| `goodsNo` | Integer | 商品番号（JANコード検索または商品検索ポップアップから設定） |
| `goodsName` | String | 商品名（表示用、読み取り専用） |
| `caseNum` | BigDecimal | ケース数量 |
| `pieceNum` | BigDecimal | バラ数量 |

#### 登録処理フロー（`POST /stockCreate`）

1. バリデーション（`@Validated`）
2. 追加バリデーション:
   - `companyNo` が null → エラー「会社が選択されていません。」
   - `goodsNo` が null → エラー「商品が選択されていません。」
   - `warehouseNo` が null → エラー「倉庫が選択されていません。」
3. 重複チェック: `t_stock` に同じ `goodsNo` + `companyNo` + `warehouseNo` が存在する場合 → 警告「すでに在庫登録されています。」
4. 商品単位チェック: `m_goods_unit` からケース単位（`unit = 'case'`）およびバラ単位（`unit = 'piece'`）を取得。どちらかが存在しない場合 → 警告「商品入数が登録されていません。」
5. `t_stock` に INSERT:
   - `unit1No` = バラ単位の `unit_no`
   - `unit1StockNum` = 入力 `pieceNum`
   - `unit2No` = ケース単位の `unit_no`
   - `unit2StockNum` = 入力 `caseNum`
6. `t_stock_log` に INSERT: `reason = 'new'`（`StockLogReason.NEW`）、`moveTime` = 登録日時
7. 成功メッセージ表示: 「在庫登録しました。商品:{goodsName},ケース数:{caseNum},バラ:{pieceNum}」

#### AJAX エンドポイント

| URL | メソッド | 説明 |
|---|---|---|
| `/getGoodsByJanCode` | POST | JANコードから `MGoods` を返す（`@ResponseBody`） |

---

### 3単位在庫モデル

`TStock` および `TStockLog` は最大3種類の在庫単位を管理できる設計になっている。

| 位置 | フィールド | UnitType 値 | 説明 |
|---|---|---|---|
| 単位1 | `unit1_no`, `unit1_stock_num` | `piece` | 個（バラ）単位 |
| 単位2 | `unit2_no`, `unit2_stock_num` | `case` | ケース単位 |
| 単位3 | `unit3_no`, `unit3_stock_num` | （将来拡張用） | 第3単位（現在非表示） |

**`UnitType` 列挙型**:

| 定数 | getValue() | 説明 |
|---|---|---|
| `CASE` | `"case"` | ケース |
| `PIECE` | `"piece"` | 枚数・個数 |

**商品単位マスタ（`m_goods_unit` / `MGoodsUnit`）**:

| カラム名 | 型 | 説明 |
|---|---|---|
| `unit_no` | Integer (PK) | 単位番号（シーケンス） |
| `goods_no` | Integer | 商品番号 |
| `unit` | String | 単位種別（'case' または 'piece'） |
| `contain_num` | BigDecimal | 入数（例: 1ケース = 30個なら 30） |
| `parent_unit_no` | Integer | 親単位番号 |

**在庫の個換算ロジック**（`TStock.getPieceNum()`）:

```
個換算在庫 = unit1StockNum × unit1.containNum
           + unit2StockNum × unit2.containNum
           + unit3StockNum × unit3.containNum
```

各単位の `containNum` が null の場合はその単位分を 0 とする。

---

### 在庫ログの理由コード

`StockLogReason` 列挙型（`jp.co.oda32.constant.StockLogReason`）で定義。
`t_stock_log.reason` カラムに格納される文字列値。

| 定数名 | getValue()（DBコード） | 説明 |
|---|---|---|
| `NEW` | `"new"` | 新規登録（在庫新規作成時） |
| `ARRIVED` | `"arrived"` | 仕入前入荷 |
| `PURCHASE` | `"purchase"` | 仕入 |
| `ALLOCATE` | `"allocate"` | 在庫引当 |
| `SHIPMENT` | `"shipment"` | 出荷 |
| `MOVE_IN` | `"move_in"` | 移動入庫（倉庫間移動の入庫側） |
| `MOVE_OUT` | `"move_out"` | 移動出庫（倉庫間移動の出庫側） |
| `RETURN` | `"return"` | 返品 |
| `USE` | `"use"` | 使用 |
| `DISPOSE` | `"dispose"` | 処分（破棄） |
| `ADJUST` | `"adjust"` | 在庫調整 |
| `INVENTORY` | `"inventory"` | 棚卸 |

**棚卸時の特殊処理**: `TStockLogRepository.deleteForInventory(moveTime)` により、同一 `moveTime` かつ `reason = 'inventory'` のレコードを物理削除した上で再登録する。

---

### 適正在庫計算ロジック

適正在庫の計算は `AppropriateStockCalculateTasklet` の抽象クラスを継承した2種類のタスクレットで実行される。

#### 種別

| タスクレット | 対象テーブル | 基準データ |
|---|---|---|
| `ShopAppropriateStockCalculateTasklet` | `t_shop_appropriate_stock` | 注文明細（`t_order_detail`）の出荷実績 |
| `WarehouseAppropriateStockCalculateTasklet` | `t_warehouse_appropriate_stock` | 倉庫単位の出荷実績 |

#### 計算ステップ（ショップ適正在庫の場合）

1. **対象データ抽出**: ジョブパラメータ `spanMonths` 月以内の注文明細（ステータス: 在庫引当(`10`) または 納品済(`20`)）を抽出。直送フラグ（`direct_shipping_flg`）が立っているものは除外する。

2. **商品毎・日付毎に集計**: 注文数量 - キャンセル数量 - 返品数量を日次集計する。

3. **リードタイム取得**: 販売商品マスタ（`m_sales_goods`）または販売商品ワーク（`w_sales_goods`）から取得。未登録の場合はデフォルト **7日**。

4. **標準偏差の計算**: 日次出荷数量（ゼロ日を補完した配列）の標準偏差。

   ```
   標準偏差 = StaticsCalculator.getStandardDeviation(日次出荷数量配列)
   ※ 出荷実績のない日は 0 として配列を period_days の長さに補完する
   ```

5. **安全在庫の計算**:

   ```
   安全在庫 = 安全係数(1.645) × 標準偏差 × √リードタイム
   ```

   安全係数 `1.645` は95%サービスレベルに対応する値。

6. **適正在庫の計算**:

   ```
   適正在庫 = リードタイム × 1日平均出荷数量 + 安全在庫
   ```

   1日平均出荷数量 = 期間内合計出荷数量 ÷ period_days（小数点以下32桁精度）

7. **登録**: 既存の適正在庫テーブルを TRUNCATE した後に一括 INSERT する（`truncateAppropriateStock()` → INSERT ループ）。

#### ジョブパラメータ

| パラメータ名 | 型 | 説明 |
|---|---|---|
| `spanMonths` | Integer | 過去何ヶ月分のデータで計算するか |

---

## 注文管理

### 注文ドメインモデル概要

注文管理は以下のエンティティ群で構成される。受注から出荷、返品までを一貫したチェーンで管理する。

```
TOrder（注文）
  └─ TOrderDetail（注文明細） ← IOrderDetailEntity
       └─ TDelivery（出荷）
             └─ TDeliveryDetail（出荷明細） ← IOrderDetailEntity
                  └─ TReturn（返品）
                        └─ TReturnDetail（返品明細） ← IOrderDetailEntity
```

---

### 注文エンティティ定義

#### TOrder（注文テーブル: `t_order`）

主キー: `order_no`（シーケンス自動採番: `t_order_order_no_seq`）

| カラム名 | 型 | 説明 |
|---|---|---|
| `order_no` | Integer (PK) | 注文番号（シーケンス） |
| `shop_no` | Integer | 注文を受けたショップ番号 |
| `company_no` | Integer | 注文した会社番号（得意先） |
| `company_name` | String | 得意先会社名（スナップショット） |
| `order_status` | String | 注文ステータス（OrderStatus コード） |
| `order_date_time` | LocalDateTime | 注文日時 |
| `total_price` | BigDecimal | 合計金額（税抜） |
| `tax_total_price` | BigDecimal | 合計金額（税込） |
| `note` | String | 備考 |
| `order_route` | String | 注文方法（OrderRoute コード） |
| `destination_no` | Integer | 届け先番号（`m_delivery_destination` FK） |
| `payment_method` | String | 支払方法（PaymentMethod コード） |
| `b_cart_order_id` | Long | B-CART 注文 ID（B-CART 連携キー） |
| `b_cart_order_code` | Long | B-CART 受注番号（最大255桁の整数） |
| `processing_serial_number` | Long | SMILE 処理連番（SMILE 連携キー） |
| `partner_no` | Integer | 得意先番号 |
| `partner_code` | String | 得意先コード |
| `del_flg` | String | 削除フラグ |
| `add_date_time` | Timestamp | 登録日時 |
| `add_user_no` | Integer | 登録ユーザー番号 |
| `modify_date_time` | Timestamp | 更新日時 |
| `modify_user_no` | Integer | 更新ユーザー番号 |

**関連エンティティ**:
- `company`: 会社マスタ（`MCompany`）← `company_no`
- `orderDetailList`: 注文明細リスト（`List<TOrderDetail>`）← `order_no`

---

#### TOrderDetail（注文明細テーブル: `t_order_detail`）

複合主キー: `order_no` + `order_detail_no`（`TOrderDetailPK` クラスで定義）
実装インターフェース: `IOrderDetailEntity`

| カラム名 | 型 | 説明 |
|---|---|---|
| `order_no` | Integer (PK) | 注文番号 |
| `order_detail_no` | Integer (PK) | 注文明細番号 |
| `shop_no` | Integer | 注文を受けたショップ番号 |
| `company_no` | Integer | 注文した会社番号 |
| `order_detail_status` | String | 注文明細ステータス（OrderDetailStatus コード） |
| `goods_no` | Integer | 商品番号 |
| `goods_code` | String | 商品コード（スナップショット） |
| `unit_no` | Integer | 単位番号 |
| `unit_num` | Integer | ケース換算数量 |
| `unit_contain_num` | Integer | 入数 |
| `unit_name` | String | 単位名 |
| `order_num` | BigDecimal | 注文数量 |
| `cancel_num` | BigDecimal | キャンセル数量（null の場合 0.0 として扱う） |
| `return_num` | BigDecimal | 返品数量（null の場合 0.0 として扱う） |
| `goods_price` | BigDecimal | 商品単価（スナップショット） |
| `goods_name` | String | 商品名（スナップショット） |
| `tax_type` | String | 税種別 |
| `tax_rate` | BigDecimal | 税率 |
| `delivery_no` | Integer | 出荷番号（出荷後に設定） |
| `delivery_detail_no` | Integer | 出荷明細番号（出荷後に設定） |
| `note` | String | 備考 |
| `purchase_price` | BigDecimal | 仕入単価 |
| `markup_ratio` | BigDecimal | 粗利率 |
| `processing_serial_number` | Long | SMILE 処理連番 |
| `del_flg` | String | 削除フラグ |
| `add_date_time` | Timestamp | 登録日時 |
| `add_user_no` | Integer | 登録ユーザー番号 |
| `modify_date_time` | Timestamp | 更新日時 |
| `modify_user_no` | Integer | 更新ユーザー番号 |

**関連エンティティ**:
- `tOrder`: 注文（`TOrder`）← `order_no`
- `tDelivery`: 出荷（`TDelivery`）← `delivery_no`

**計算メソッド**:
- `getGoodsNum()`: `orderNum` を返す（`IOrderDetailEntity` 実装）
- `setGoodsNum(BigDecimal)`: `orderNum` をセットする（`IOrderDetailEntity` 実装）
- `getOrderDateTime()`: `tOrder.orderDateTime` を返す（`IOrderDetailEntity` 実装）
- `getOrderDate()`: `getOrderDateTime().toLocalDate()` を返す
- `getTotalAmount()`: `goodsPrice × (orderNum - cancelNum - returnNum)` を返す

---

#### TDelivery（出荷テーブル: `t_delivery`）

主キー: `delivery_no`（シーケンス自動採番: `t_delivery_delivery_no_seq`）

| カラム名 | 型 | 説明 |
|---|---|---|
| `delivery_no` | Integer (PK) | 出荷番号（シーケンス） |
| `shop_no` | Integer | ショップ番号 |
| `company_no` | Integer | 会社番号 |
| `partner_code` | String | 得意先コード（SMILE 連携用ユニークキーの一部） |
| `slip_no` | String | 伝票番号（SMILE 連携用ユニークキーの一部） |
| `slip_date` | LocalDate | 伝票日付 |
| `delivery_status` | String | 出荷ステータス（DeliveryStatus コード） |
| `destination_no` | Integer | 届け先番号 |
| `destination_name` | String | 届け先名（スナップショット） |
| `delivery_plan_date` | LocalDate | 出荷予定日 |
| `delivery_date` | LocalDate | 実際の出荷日（null の場合は `delivery_plan_date` で補完可） |
| `direct_shipping_flg` | String | 直送フラグ |
| `total_price` | BigDecimal | 合計金額（税抜） |
| `tax_total_price` | BigDecimal | 合計金額（税込） |
| `tracking_number` | String | 追跡番号 |
| `processing_serial_number` | Long | SMILE 処理連番（SMILE 連携キー） |
| `del_flg` | String | 削除フラグ |
| `add_date_time` | Timestamp | 登録日時 |
| `add_user_no` | Integer | 登録ユーザー番号 |
| `modify_date_time` | Timestamp | 更新日時 |
| `modify_user_no` | Integer | 更新ユーザー番号 |

**関連エンティティ**:
- `deliveryDetailList`: 出荷明細リスト（`List<TDeliveryDetail>`）← `delivery_no`

**ユニークキー（SMILE 連携用）**: `shop_no` + `partner_code` + `slip_no`
**SMILE 連携キー**: `shop_no` + `processing_serial_number`

---

#### TDeliveryDetail（出荷明細テーブル: `t_delivery_detail`）

複合主キー: `delivery_no` + `delivery_detail_no`（`TDeliveryDetailPK` クラスで定義）
実装インターフェース: `IOrderDetailEntity`

| カラム名 | 型 | 説明 |
|---|---|---|
| `delivery_no` | Integer (PK) | 出荷番号 |
| `delivery_detail_no` | Integer (PK) | 出荷明細番号 |
| `shop_no` | Integer | ショップ番号 |
| `company_no` | Integer | 会社番号 |
| `slip_no` | String | 伝票番号 |
| `order_no` | Integer | 注文番号（注文明細との紐付け） |
| `order_detail_no` | Integer | 注文明細番号（注文明細との紐付け） |
| `delivery_detail_status` | String | 出荷明細ステータス（DeliveryDetailStatus コード） |
| `warehouse_no` | Integer | 出荷元倉庫番号 |
| `goods_no` | Integer | 商品番号 |
| `goods_code` | String | 商品コード |
| `goods_price` | BigDecimal | 商品単価 |
| `tax_type` | String | 税種別 |
| `tax_price` | BigDecimal | 税額 |
| `unit_no` | Integer | 単位番号 |
| `unit_num` | Integer | ケース換算数量 |
| `unit_contain_num` | Integer | 入数 |
| `unit_name` | String | 単位名 |
| `delivery_num` | BigDecimal | 出荷数量 |
| `return_num` | Integer | 返品数量（null の場合 0 として扱う） |
| `mat_api_flg` | String | SmartMat API フラグ |
| `processing_serial_number` | Long | SMILE 処理連番 |
| `del_flg` | String | 削除フラグ |
| `add_date_time` | Timestamp | 登録日時 |
| `add_user_no` | Integer | 登録ユーザー番号 |
| `modify_date_time` | Timestamp | 更新日時 |
| `modify_user_no` | Integer | 更新ユーザー番号 |

**関連エンティティ**:
- `tDelivery`: 出荷（`TDelivery`）← `delivery_no`
- `tOrderDetail`: 注文明細（`TOrderDetail`）← `order_no` + `order_detail_no`

**計算メソッド**:
- `getGoodsNum()`: `deliveryNum` を返す（`IOrderDetailEntity` 実装）
- `setGoodsNum(BigDecimal)`: `deliveryNum` をセットする（`IOrderDetailEntity` 実装）
- `getGoodsName()`: `null` を返す（現在商品名カラム未保持のため、使用時は追加が必要）
- `getOrderDateTime()`: `tDelivery.deliveryDate.atStartOfDay()` を返す（`IOrderDetailEntity` 実装）

---

#### TReturn（返品テーブル: `t_return`）

主キー: `return_no`（シーケンス自動採番: `t_return_return_no_seq`）

| カラム名 | 型 | 説明 |
|---|---|---|
| `return_no` | Integer (PK) | 返品番号（シーケンス） |
| `order_no` | Integer | 対応する注文番号 |
| `shop_no` | Integer | ショップ番号 |
| `company_no` | Integer | 会社番号 |
| `return_status` | String | 返品ステータス（ReturnStatus コード） |
| `return_date_time` | LocalDateTime | 返品日時 |
| `return_finish_date_time` | LocalDateTime | 返品完了日時 |
| `return_total_price` | BigDecimal | 返品合計金額（税抜） |
| `return_tax_total_price` | BigDecimal | 返品合計金額（税込） |
| `slip_no` | String | 元伝票番号 |
| `return_slip_no` | String | 返品伝票番号（ユニークキーの一部） |
| `note` | String | 備考 |
| `slip_date` | LocalDate | 伝票日付 |
| `processing_serial_number` | Long | SMILE 処理連番 |
| `del_flg` | String | 削除フラグ |
| `add_date_time` | Timestamp | 登録日時 |
| `add_user_no` | Integer | 登録ユーザー番号 |
| `modify_date_time` | Timestamp | 更新日時 |
| `modify_user_no` | Integer | 更新ユーザー番号 |

**関連エンティティ**:
- `returnDetailList`: 返品明細リスト（`List<TReturnDetail>`）← `return_no`

**ユニークキー**: `shop_no` + `return_slip_no`

---

#### TReturnDetail（返品明細テーブル: `t_return_detail`）

複合主キー: `return_no` + `return_detail_no`（`TReturnDetailPK` クラスで定義）
実装インターフェース: `IOrderDetailEntity`

| カラム名 | 型 | 説明 |
|---|---|---|
| `return_no` | Integer (PK) | 返品番号 |
| `return_detail_no` | Integer (PK) | 返品明細番号 |
| `order_no` | Integer | 注文番号 |
| `order_detail_no` | Integer | 注文明細番号 |
| `delivery_no` | Integer | 出荷番号 |
| `delivery_detail_no` | Integer | 出荷明細番号 |
| `shop_no` | Integer | ショップ番号 |
| `company_no` | Integer | 会社番号 |
| `return_detail_status` | String | 返品明細ステータス（ReturnDetailStatus コード） |
| `goods_no` | Integer | 商品番号 |
| `goods_code` | String | 商品コード |
| `unit_no` | Integer | 単位番号 |
| `unit_num` | Integer | ケース換算数量 |
| `return_num` | BigDecimal | 返品数量 |
| `goods_price` | BigDecimal | 商品単価 |
| `goods_name` | String | 商品名 |
| `subtotal` | BigDecimal | 小計 |
| `tax_type` | String | 税種別 |
| `tax_price` | BigDecimal | 税額 |
| `note` | String | 備考 |
| `processing_serial_number` | Long | SMILE 処理連番 |
| `del_flg` | String | 削除フラグ |
| `add_date_time` | Timestamp | 登録日時 |
| `add_user_no` | Integer | 登録ユーザー番号 |
| `modify_date_time` | Timestamp | 更新日時 |
| `modify_user_no` | Integer | 更新ユーザー番号 |

**関連エンティティ**:
- `tReturn`: 返品（`TReturn`）← `return_no`

**計算メソッド**:
- `getGoodsNum()`: `returnNum` を返す（`IOrderDetailEntity` 実装）
- `setGoodsNum(BigDecimal)`: `returnNum` をセットする（`IOrderDetailEntity` 実装）
- `getOrderDateTime()`: `tReturn.returnDateTime` を返す（`IOrderDetailEntity` 実装）

---

#### MDeliveryDestination（届け先マスタテーブル: `m_delivery_destination`）

主キー: `destination_no`（シーケンス自動採番: `m_delivery_destination_destination_no_seq`）

| カラム名 | 型 | 説明 |
|---|---|---|
| `destination_no` | Integer (PK) | 届け先番号（シーケンス） |
| `shop_no` | Integer | ショップ番号 |
| `company_no` | Integer | 会社番号 |
| `partner_no` | Integer | 得意先番号 |
| `destination_code` | String | 届け先コード |
| `destination_name` | String | 届け先名称 |
| `address1` | String | 住所1 |
| `address2` | String | 住所2 |
| `address3` | String | 住所3 |
| `tel_number` | String | 電話番号 |
| `fax_number` | String | FAX番号 |
| `del_flg` | String | 削除フラグ |
| `add_date_time` | Timestamp | 登録日時 |
| `add_user_no` | Integer | 登録ユーザー番号 |
| `modify_date_time` | Timestamp | 更新日時 |
| `modify_user_no` | Integer | 更新ユーザー番号 |

---

### 注文一覧画面

#### 基本情報

| 項目 | 内容 |
|---|---|
| 画面タイトル | 注文一覧 |
| URL | `/orderList`（GET・POST） |
| テンプレート | `src/main/resources/templates/order/order_list.html` |
| コントローラ | `OrderListController` |

#### 検索条件フォーム（`OrderListForm`）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `shopNo` | Integer | `@NotNull`（必須） | ショップ番号 |
| `partnerNo` | Integer | 任意（隠しフィールド） | 得意先番号（得意先検索から設定） |
| `partnerCode` | String | 任意 | 得意先コード（手入力後に会社情報を自動取得） |
| `partnerName` | String | 任意（表示のみ） | 得意先名（自動表示） |
| `goodsNo` | Integer | 任意 | 商品番号 |
| `goodsName` | String | 任意 | 商品名（部分一致） |
| `goodsCode` | String | 任意 | 商品コード（後方一致） |
| `slipNo` | String | 任意 | 伝票番号（完全一致） |
| `orderDetailStatus` | String | 任意 | 注文明細ステータス（プルダウン） |
| `orderDateTimeFrom` | String | 任意 | 注文日時FROM（書式: `uuuu/MM/dd HH:mm`） |
| `orderDateTimeTo` | String | 任意 | 注文日時TO（書式: `uuuu/MM/dd HH:mm`） |
| `slipDateFrom` | String | 任意 | 伝票日付FROM |
| `slipDateTo` | String | 任意 | 伝票日付TO |
| `line` | String | 任意 | 注文明細商品検索用パラメータ |

#### 画面初期表示

- ショップマップをプルダウンで表示
- 注文明細ステータスマップ（`OrderDetailStatus.toMap()`）をプルダウンで表示
- `orderDateTimeFrom` の初期値: 現在日時の1ヶ月前

#### 検索ロジック

`TOrderDetailService.find(...)` を呼び出す。`shopNo` + `partnerNo`（会社番号に変換）+ その他条件で絞り込み。
検索結果は `orderDateTime`（= `tOrder.orderDateTime`）降順でソート。

`partnerCode` 入力時に `CommonService.getMCompanyByPartnerNo()` で会社情報を取得し `companyNo` を設定する。

#### 表示項目（一覧テーブル「注文商品明細」）

| 表示名 | 取得元 | 説明 |
|---|---|---|
| 注文番号 | `orderNo + '-' + orderDetailNo` | ハイフン結合の複合キー表示 |
| 伝票日付 | `tDelivery.slipDate` | 出荷の伝票日付 |
| 得意先 | `tOrder.companyName` | 注文の得意先名 |
| 伝票番号 | `tDelivery.slipNo` | 出荷の伝票番号 |
| 商品コード | `goodsCode` | |
| 商品名 | `goodsName` | |
| 単価 | `goodsPrice` | |
| 数量 | `orderNum` | |
| 入数 | `unitContainNum` | |
| ケース換算 | `unitNum` | |
| 小計 | `subtotal`（`TOrderDetail` の計算値） | |
| 受注日時 | `tOrder.orderDateTime` | |
| 更新ボタン | - | 行ごとの更新操作（JavaScript 経由） |

---

### 取引先商品発注入力画面

#### 基本情報

| 項目 | 内容 |
|---|---|
| 画面タイトル | 得意先商品マスタ（取引先商品から注文入力） |
| URL | `/partnerGoodsMaster`（GET・POST） |
| テンプレート | `src/main/resources/templates/order/partner_goods_order_input.html` |

#### 検索条件

| フィールド | 説明 |
|---|---|
| 得意先コード | 得意先コードを直接入力 |
| 得意先 | 得意先プルダウン（`partnerMap` から生成、`companyType = "partner"`） |
| 商品名 | 部分一致検索 |
| 商品コード | 部分一致検索 |
| キーワード | 部分一致検索 |

#### 表示項目（一覧テーブル「販売商品マスタ一覧」）

| 表示名 | 説明 |
|---|---|
| 商品番号 | `goodsNo` |
| ショップ | `mShop.shopName` |
| 得意先 | `mCompany.companyName` |
| 商品コード | `goodsCode` |
| 商品名 | `goodsName` |
| 売価 | `goodsPrice`（書式付き） |
| 最終売上日 | `lastSalesDate` |
| 年間注文数量 | `orderNumPerYear`（null の場合 0 表示） |
| キーワード | `keyword` |
| 詳細リンク | `/partnerGoodsModifyForm?partnerNo=*&destinationNo=*&goodsNo=*` |

---

### 注文チェーン

`TOrder → TDelivery → TReturn` の関係はフラット構造（外部キー参照）で管理される。
JPA のリレーション定義は以下の通り。

```
TOrder（order_no）
  ↓ @OneToMany (order_no)
TOrderDetail（order_no, order_detail_no）
  ├─ tOrder: TOrder を参照（@OneToOne, order_no）
  └─ tDelivery: TDelivery を参照（@OneToOne, delivery_no）

TDelivery（delivery_no）
  ↓ @OneToMany (delivery_no)
TDeliveryDetail（delivery_no, delivery_detail_no）
  ├─ tDelivery: TDelivery を参照（@OneToOne, delivery_no）
  └─ tOrderDetail: TOrderDetail を参照（@ManyToOne, order_no + order_detail_no）

TReturn（return_no）※ order_no で TOrder に紐付く
  ↓ @OneToMany (return_no)
TReturnDetail（return_no, return_detail_no）
  └─ tReturn: TReturn を参照（@OneToOne, return_no）
```

`TOrderDetail.delivery_no` / `TOrderDetail.delivery_detail_no` は出荷後に設定される（初期は null）。
`TReturnDetail` は `order_no`, `order_detail_no`, `delivery_no`, `delivery_detail_no` を全て保持し、4方向に連携している。

---

### 注文ステータスコード

#### OrderStatus（注文ステータス）

`t_order.order_status` カラムに格納される。

| 定数名 | コード | 説明 |
|---|---|---|
| `RECEIPT` | `"00"` | 注文受付 |
| `WAIT_SHIPPING` | `"10"` | 出荷待ち |
| `DELIVERED` | `"20"` | 納品済 |
| `CANCEL` | `"90"` | 全キャンセル |
| `RETURN` | `"99"` | 全返品 |

---

#### OrderDetailStatus（注文明細ステータス）

`t_order_detail.order_detail_status` カラムに格納される。
`OrderDetailStatus.toMap()` で `Map<String, String>` に変換可能（画面のプルダウン生成に使用）。

| 定数名 | コード | 表示名 |
|---|---|---|
| `RECEIPT` | `"00"` | 注文受付 |
| `BACK_ORDERED` | `"01"` | 入荷待ち |
| `ALLOCATION` | `"10"` | 在庫引当 |
| `DELIVERED` | `"20"` | 納品済 |
| `CANCEL` | `"90"` | キャンセル |
| `RETURN` | `"99"` | 返品 |

---

#### DeliveryStatus（出荷ステータス）

`t_delivery.delivery_status` カラムに格納される。

| 定数名 | コード | 説明 |
|---|---|---|
| `NOT_INPUT` | `"00"` | 伝票未入力 |
| `WAIT_SHIPPING` | `"10"` | 出荷待ち |
| `DELIVERED` | `"20"` | 納品済 |
| `CANCEL` | `"90"` | 全キャンセル |
| `RETURN` | `"99"` | 全返品 |

---

#### DeliveryDetailStatus（出荷明細ステータス）

`t_delivery_detail.delivery_detail_status` カラムに格納される。

| 定数名 | コード | 説明 |
|---|---|---|
| `NOT_INPUT` | `"00"` | 伝票未入力 |
| `WAIT_SHIPPING` | `"10"` | 出荷待ち |
| `DELIVERED` | `"20"` | 納品済 |
| `CANCEL` | `"90"` | 全キャンセル |
| `RETURN` | `"99"` | 全返品 |

**補助メソッド**: `getNotCancelStatus()` → `[WAIT_SHIPPING, DELIVERED]` のリストを返す。

---

#### ReturnStatus（返品ステータス）

`t_return.return_status` カラムに格納される。

| 定数名 | コード | 説明 |
|---|---|---|
| `RECEIPT` | `"00"` | 返品受付 |
| `WAIT_RETURN` | `"10"` | 回収待ち |
| `RETURNED` | `"20"` | 返品済 |

---

#### ReturnDetailStatus（返品明細ステータス）

`t_return_detail.return_detail_status` カラムに格納される。

| 定数名 | コード | 説明 |
|---|---|---|
| `RECEIPT` | `"00"` | 返品受付 |
| `WAIT_RETURN` | `"10"` | 回収待ち |
| `RETURNED` | `"20"` | 返品済 |

---

#### OrderRoute（注文方法）

`t_order.order_route` カラムに格納される。

| 定数名 | コード | 説明 |
|---|---|---|
| `WEB` | `"web"` | Web |
| `TEL` | `"tel"` | 電話 |
| `FAX` | `"fax"` | FAX |
| `MAIL` | `"mail"` | メール |
| `B_CART` | `"b_cart"` | B-CART（EC経由） |
| `OTHER` | `"other"` | その他 |

---

#### PaymentMethod（支払方法）

`t_order.payment_method` カラムに格納される。

| 定数名 | コード | 説明 |
|---|---|---|
| `ACCOUNTS_RECEIVABLE` | `"0"` | 売掛 |
| `CASH` | `"1"` | 現金 |

---

### IOrderDetailEntity インターフェース

`IOrderDetailEntity` は `IEntity` を継承し、注文明細・出荷明細・返品明細の3エンティティが実装するポリモーフィズムのためのインターフェース。

```java
public interface IOrderDetailEntity extends IEntity {
    // 商品コード
    String getGoodsCode();
    void setGoodsCode(String goodsCode);

    // 商品番号
    Integer getGoodsNo();
    void setGoodsNo(Integer goodsNo);

    // 商品名
    String getGoodsName();

    // 数量（注文数 / 出荷数 / 返品数）
    BigDecimal getGoodsNum();
    void setGoodsNum(BigDecimal goodsNum);

    // 単価
    BigDecimal getGoodsPrice();
    void setGoodsPrice(BigDecimal goodsPrice);

    // 注文・返品・配送日時
    LocalDateTime getOrderDateTime();
}
```

| 実装クラス | `getGoodsNum()` の実態 | `getOrderDateTime()` の実態 |
|---|---|---|
| `TOrderDetail` | `orderNum` を返す | `tOrder.orderDateTime` を返す |
| `TDeliveryDetail` | `deliveryNum` を返す | `tDelivery.deliveryDate.atStartOfDay()` を返す |
| `TReturnDetail` | `returnNum` を返す | `tReturn.returnDateTime` を返す |

このインターフェースにより、注文・出荷・返品の明細データを共通の処理（売上集計、在庫計算など）に渡すことができる。

---

### SMILE / B-CART 連携キー

#### SMILE 連携

`processing_serial_number`（SMILE 処理連番）がシステム間の同一レコードを識別するためのキー。

| エンティティ | カラム | 説明 |
|---|---|---|
| `TOrder` | `processing_serial_number` | `w_smile_order_output_file.shori_renban` と対応 |
| `TDelivery` | `processing_serial_number` | `w_smile_order_output_file.shori_renban` と対応 |
| `TOrderDetail` | `processing_serial_number` | SMILE 明細の連番 |
| `TDeliveryDetail` | `processing_serial_number` | SMILE 明細の連番 |
| `TReturn` | `processing_serial_number` | SMILE 返品の連番 |
| `TReturnDetail` | `processing_serial_number` | SMILE 返品明細の連番 |

**ユニークキー検索**: `getByShopNoAndProcessingSerialNumber(shopNo, processingSerialNumber)`
- `TOrderRepository.getByShopNoAndProcessingSerialNumber(int, long)`
- `TDeliveryRepository.getByShopNoAndProcessingSerialNumber(int, long)`

**SMILE 削除検知**: `DeletedOrderRepository.findDeletedDeliveries(shopNo)` が `w_smile_order_output_file` との差分を検出する。現在日付以前の伝票日付を持ち、`w_smile_order_output_file` に存在しない `t_delivery` レコードを「SMILE 側で削除されたデータ」として抽出する。

#### B-CART 連携

| エンティティ | カラム | 説明 |
|---|---|---|
| `TOrder` | `b_cart_order_id` | B-CART 注文 ID（Long） |
| `TOrder` | `b_cart_order_code` | B-CART 受注番号（Long、最大255桁の整数） |

B-CART 連携では `OrderRoute.B_CART`（コード: `"b_cart"`）が `order_route` に設定され、これにより B-CART 経由の注文を識別できる。

---

## リポジトリ一覧

### 在庫系リポジトリ

| リポジトリ名 | 対象テーブル | 特記事項 |
|---|---|---|
| `TStockRepository` | `t_stock` | `getByGoodsNoAndWarehouseNo()` でユニーク検索 |
| `TStockLogRepository` | `t_stock_log` | `deleteForInventory(moveTime)` で棚卸ログの物理削除 |
| `TShopAppropriateStockRepository` | `t_shop_appropriate_stock` | `truncateTShopAppropriateStock()` でTRUNCATE |
| `TWarehouseAppropriateStockRepository` | `t_warehouse_appropriate_stock` | `getByGoodsNoAndWarehouseNo()` でユニーク検索 |

### 注文系リポジトリ

| リポジトリ名 | 対象テーブル | 特記事項 |
|---|---|---|
| `TOrderRepository` | `t_order` | `updateOrderStatusByOrderNoList()` で一括ステータス更新 |
| `TOrderDetailRepository` | `t_order_detail` | `findByDeliveryNo()`, `findByOrderNo()` |
| `TDeliveryRepository` | `t_delivery` | `updateDeliveryStatusByDeliveryNoList()`, `updateDeliveryDateByDeliveryNoList()` |
| `TDeliveryDetailRepository` | `t_delivery_detail` | |
| `TReturnRepository` | `t_return` | `getByShopNoAndReturnSlipNo()` でユニーク検索 |
| `TReturnDetailRepository` | `t_return_detail` | |
| `DeletedOrderRepository` | `t_delivery` | SMILE 削除検知用ネイティブクエリ |

---

## サービス一覧

### 在庫系サービス

| サービス名 | 説明 |
|---|---|
| `TStockService` | 在庫の CRUD 操作。`find()` で多条件検索 |
| `TStockLogService` | 在庫ログの CRUD 操作。`save()` で存在確認後に insert/update を分岐 |
| `TShopAppropriateStockService` | ショップ適正在庫の CRUD。`truncateTShopAppropriateStock()` で全件削除 |
| `TWarehouseAppropriateStockService` | 倉庫適正在庫の CRUD |

### 注文系サービス

| サービス名 | 説明 |
|---|---|
| `TOrderService` | 注文の CRUD 操作。`getByUniqKey()` で SMILE 連携 |
| `TOrderDetailService` | 注文明細の CRUD 操作。多条件検索 `find()` を提供 |
| `TDeliveryService` | 出荷の CRUD 操作。ステータス一括更新と出荷日補完を提供 |
| `TDeliveryDetailService` | 出荷明細の CRUD 操作 |
| `TReturnService` | 返品の CRUD 操作 |
| `TReturnDetailService` | 返品明細の CRUD 操作 |
| `MDeliveryDestinationService` | 届け先マスタの CRUD 操作 |
| `VSalesMonthlySummaryService` | 月次売上サマリビューのサービス |

---

## 仕様クラス（Specification）一覧

### 在庫系 Specification

#### TStockSpecification

| メソッド名 | 検索カラム | 検索方式 |
|---|---|---|
| `goodsNoContains(Integer)` | `goods_no` | 完全一致 |
| `goodsNoListContains(List<Integer>)` | `goods_no` | IN 句 |
| `goodsNameContains(String)` | `m_goods.goods_name` | 部分一致（`%value%`） |
| `warehouseNoContains(Integer)` | `warehouse_no` | 完全一致 |
| `companyNoContains(Integer)` | `company_no` | 完全一致 |
| `delFlgContains(Flag)` | `del_flg` | 完全一致（継承） |

#### TStockLogSpecification

| メソッド名 | 検索カラム | 検索方式 |
|---|---|---|
| `goodsNoContains(Integer)` | `goods_no` | 完全一致 |
| `warehouseNoContains(Integer)` | `warehouse_no` | 完全一致 |
| `companyNoContains(Integer)` | `company_no` | 完全一致 |
| `goodsNameContains(String)` | `m_goods.goods_name` | 部分一致 |
| `reasonListContains(List<StockLogReason>)` | `reason` | OR 結合の複数一致 |
| `moveTimeContains(LocalDateTime, LocalDateTime)` | `move_time` | 範囲検索（FROM/TO の両方・片方に対応） |
| `delFlgContains(Flag)` | `del_flg` | 完全一致（継承） |

### 注文系 Specification

#### TOrderDetailSpecification

| メソッド名 | 検索カラム | 検索方式 |
|---|---|---|
| `shopNoContains(Integer)` | `shop_no` | 完全一致 |
| `orderNoContains(Integer)` | `order_no` | 完全一致 |
| `orderNoListContains(List<Integer>)` | `order_no` | IN 句 |
| `orderDetailNoContains(Integer)` | `order_detail_no` | 完全一致 |
| `slipNoContains(String)` | `t_delivery.slip_no` | 完全一致（JOIN） |
| `companyNoContains(Integer)` | `company_no` | 完全一致 |
| `orderDetailStatusContains(String)` | `order_detail_status` | 完全一致 |
| `orderDetailStatusListContains(String...)` | `order_detail_status` | IN 句 |
| `goodsNoContains(Integer)` | `goods_no` | 完全一致 |
| `goodsCodeContains(String)` | `goods_code` | 後方一致（`%value`） |
| `goodsNameContains(String)` | `goods_name` | 部分一致（`%value%`） |
| `orderDateTimeContains(LocalDateTime, LocalDateTime)` | `t_order.order_date_time` | 範囲検索（JOIN） |
| `slipDateContains(LocalDate, LocalDate)` | `t_delivery.slip_date` | 範囲検索（JOIN） |
| `partnerNoListContains(List<Integer>)` | `t_order.partner_no` | IN 句（JOIN） |
| `delFlgContains(Flag)` | `del_flg` | 完全一致（継承） |

#### TDeliverySpecification

| メソッド名 | 検索カラム | 検索方式 |
|---|---|---|
| `shopNoContains(Integer)` | `shop_no` | 完全一致 |
| `deliveryNoListContains(List<Integer>)` | `delivery_no` | IN 句 |
| `companyNoContains(Integer)` | `company_no` | 完全一致 |
| `companyNameContains(String)` | `company_name` | 部分一致 |
| `orderStatusContains(String)` | `order_status` | 完全一致 |
| `deliveryDateContains(LocalDate, LocalDate)` | `delivery_date` | 範囲検索 |
| `slipDateContains(String)` | `slip_date` | 完全一致 |
| `slipNoListContains(List<String>)` | `slip_no` | IN 句 |
| `delFlgContains(Flag)` | `del_flg` | 完全一致（継承） |

---

*以上が在庫管理・注文管理の全機能仕様です。*
