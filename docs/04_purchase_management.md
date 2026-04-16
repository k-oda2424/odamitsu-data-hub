# 04. 仕入管理ドメイン 詳細仕様書

**対象システム**: stock-app
**対象ドメイン**: 仕入管理（purchase）
**作成日**: 2026-02-23
**バージョン**: 1.0

---

## 目次

1. [概要](#1-概要)
2. [画面一覧](#2-画面一覧)
3. [エンティティ定義](#3-エンティティ定義)
4. [仕入入力フロー](#4-仕入入力フロー)
5. [発注管理フロー](#5-発注管理フロー)
6. [仕入単価マスタ管理](#6-仕入単価マスタ管理)
7. [仕入価格変更予定管理](#7-仕入価格変更予定管理)
8. [バッチ処理](#8-バッチ処理)
9. [定数・区分値定義](#9-定数区分値定義)
10. [ビジネスロジック詳細](#10-ビジネスロジック詳細)
11. [システム連携](#11-システム連携)

---

## 1. 概要

仕入管理ドメインは stock-app において最も複雑なドメインの一つです。以下の主要機能を提供します。

### 1.1 機能概要

| 機能カテゴリ | 説明 |
|---|---|
| 仕入入力 | 手動による仕入伝票の登録・修正・削除 |
| 発注管理 | 仕入先への発注書作成および入荷管理 |
| 仕入単価マスタ | 商品×仕入先ごとの仕入単価マスタ管理 |
| 仕入価格変更予定 | 価格改定予定の一括登録・反映管理 |
| SMILE連携バッチ | SMILEシステムからの仕入CSVデータ自動取込 |

### 1.2 関連テーブル一覧

| テーブル名 | 説明 | 区分 |
|---|---|---|
| `t_purchase` | 仕入ヘッダ | トランザクション |
| `t_purchase_detail` | 仕入明細 | トランザクション |
| `t_send_order` | 発注ヘッダ | トランザクション |
| `t_send_order_detail` | 発注明細 | トランザクション |
| `m_purchase_price` | 仕入単価マスタ | マスタ |
| `m_purchase_price_log` | 仕入単価変更履歴 | ログ |
| `m_purchase_price_change_plan` | 仕入価格変更予定 | マスタ |
| `w_smile_purchase_output_file` | SMILE仕入出力ファイルワーク | ワーク |

---

## 2. 画面一覧

| 画面名 | URL | HTTP | テンプレート | 説明 |
|---|---|---|---|---|
| 仕入入力 | `/purchaseInput` | GET | `purchase/purchase_input.html` | 仕入情報入力画面 |
| 仕入確認 | `/purchaseConfirm` | POST | `purchase/purchase_confirm.html` | 仕入入力確認画面 |
| 仕入完了 | `/purchaseCreate` | POST | `purchase/purchase_complete.html` | 仕入登録完了画面（発注書印刷用） |
| 仕入一覧 | `/purchaseList` | GET/POST | `purchase/purchase_list.html` | 仕入明細検索・一覧・削除 |
| 発注入力 | `/sendOrderInput` | GET | `purchase/send_order_input.html` | 発注情報入力画面 |
| 発注確認 | `/sendOrderConfirm` | POST | `purchase/send_order_confirm.html` | 発注入力確認画面 |
| 発注完了 | `/sendOrderCreate` | POST | `purchase/send_order_complete.html` | 発注登録完了画面（発注書印刷用） |
| 発注一覧 | `/sendOrderList` | GET/POST | `purchase/send_order_list.html` | 発注明細検索・ステータス更新 |
| 仕入単価マスタ | `/mPurchasePriceList` | GET/POST | `purchase/m_purchase_price.html` | 仕入単価検索・一覧 |
| 仕入単価登録 | `/mPurchasePriceCreateNewForm` | GET | `purchase/m_purchase_price_create.html` | 仕入単価新規登録 |
| 仕入単価編集 | `/mPurchasePriceCreateForm` | GET | `purchase/m_purchase_price_create.html` | 仕入単価編集 |
| 仕入単価保存 | `/mPurchasePriceCreate` | POST | `purchase/m_purchase_price_create.html` | 仕入単価保存処理 |
| 仕入価格変更一覧 | `/purchasePriceChangeList` | GET/POST | `purchase/purchase_price_change_list.html` | 価格変更予定一覧・検索 |
| 仕入価格変更予定登録 | `/mPurchasePriceChangePlanCreateNewForm` | GET | `purchase/m_purchase_price_change_plan_create.html` | 価格変更予定単品登録 |
| 仕入価格変更一括入力 | `/purchasePriceChangeListInput` | GET | `purchase/purchase_price_change_list_input.html` | 価格変更予定一括入力 |
| 仕入価格変更一括確認 | `/purchasePriceChangeListInputConfirm` | POST | `purchase/purchase_price_change_list_confirm.html` | 価格変更予定一括確認 |
| 仕入価格変更一括完了 | `/purchasePriceChangeListCreate` | POST | `purchase/purchase_price_change_list_complete.html` | 価格変更予定一括登録完了 |

---

## 3. エンティティ定義

### 3.1 仕入ヘッダ（t_purchase）

**クラス**: `jp.co.oda32.domain.model.purchase.TPurchase`
**テーブル**: `t_purchase`
**主キー**: `purchase_no`（シーケンス自動採番）

| カラム名 | フィールド名 | 型 | 必須 | 説明 |
|---|---|---|---|---|
| `purchase_no` | `purchaseNo` | Integer | PK | 仕入番号（自動採番） |
| `ext_purchase_no` | `extPurchaseNo` | Long | - | SMILE処理連番（SMILE連携時に設定） |
| `purchase_code` | `purchaseCode` | String | - | 仕入コード（任意の識別コード） |
| `purchase_date` | `purchaseDate` | LocalDate | - | 仕入日 |
| `supplier_no` | `supplierNo` | Integer | - | 仕入先番号（`m_supplier`外部キー） |
| `shop_no` | `shopNo` | Integer | - | ショップ番号 |
| `company_no` | `companyNo` | Integer | - | 会社番号 |
| `warehouse_no` | `warehouseNo` | Integer | - | 倉庫番号 |
| `send_order_no` | `sendOrderNo` | Integer | - | 発注番号（発注と紐付いた場合に設定） |
| `purchase_amount` | `purchaseAmount` | BigDecimal | - | 税抜仕入合計金額 |
| `include_tax_amount` | `includeTaxAmount` | BigDecimal | - | 税込仕入合計金額 |
| `tax_amount` | `taxAmount` | BigDecimal | - | 消費税額合計 |
| `tax_type` | `taxType` | String | - | 税種別（税込・税抜） |
| `tax_timing` | `taxTiming` | String | - | 課税タイミング（`0`=伝票毎、`1`=請求時） |
| `tax_rate` | `taxRate` | BigDecimal | - | 消費税率（%、例：10） |
| `department_no` | `departmentNo` | Integer | - | 部門番号 |
| `note` | `note` | String | - | 備考 |
| `del_flg` | `delFlg` | String | - | 削除フラグ（`0`=有効、`1`=削除） |
| `add_date_time` | `addDateTime` | Timestamp | - | 登録日時 |
| `add_user_no` | `addUserNo` | Integer | - | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | Timestamp | - | 更新日時 |
| `modify_user_no` | `modifyUserNo` | Integer | - | 更新ユーザー番号 |

**アソシエーション**:
- `company`（MCompany）: `company_no` で結合
- `mSupplier`（MSupplier）: `supplier_no` で結合
- `mWarehouse`（MWarehouse）: `warehouse_no` で結合
- `purchaseDetailList`（List<TPurchaseDetail>）: `purchase_no` で結合（OneToMany）

---

### 3.2 仕入明細（t_purchase_detail）

**クラス**: `jp.co.oda32.domain.model.purchase.TPurchaseDetail`
**テーブル**: `t_purchase_detail`
**複合主キー**: `purchase_no` + `purchase_detail_no`（`TPurchaseDetailPK`クラス）

| カラム名 | フィールド名 | 型 | 必須 | 説明 |
|---|---|---|---|---|
| `purchase_no` | `purchaseNo` | Integer | PK | 仕入番号（t_purchaseへの外部キー） |
| `purchase_detail_no` | `purchaseDetailNo` | Integer | PK | 仕入明細番号（1から連番） |
| `ext_purchase_no` | `extPurchaseNo` | Long | - | SMILE処理連番 |
| `shop_no` | `shopNo` | Integer | - | ショップ番号 |
| `company_no` | `companyNo` | Integer | - | 会社番号 |
| `goods_no` | `goodsNo` | Integer | - | 商品番号 |
| `goods_code` | `goodsCode` | String | - | 商品コード（最大8桁） |
| `goods_name` | `goodsName` | String | - | 商品名（登録時にスナップショット） |
| `goods_price` | `goodsPrice` | BigDecimal | - | 仕入単価（税抜） |
| `include_tax_goods_price` | `includeTaxGoodsPrice` | BigDecimal | - | 仕入単価（税込） |
| `goods_num` | `goodsNum` | BigDecimal | - | 数量（バラ数） |
| `purchase_date` | `purchaseDate` | LocalDate | - | 仕入日 |
| `warehouse_no` | `warehouseNo` | Integer | - | 倉庫番号 |
| `tax_type` | `taxType` | String | - | 税種別（税込金額か税抜金額か） |
| `tax_category` | `taxCategory` | Integer | - | 課税種別（`0`=通常税率、`1`=軽減税率、`2`=非課税） |
| `tax_rate` | `taxRate` | BigDecimal | - | 消費税率（%） |
| `tax_price` | `taxPrice` | BigDecimal | - | 消費税額（明細単位） |
| `subtotal` | `subtotal` | BigDecimal | - | 小計（税抜）= goods_price × goods_num |
| `include_tax_subtotal` | `includeTaxSubtotal` | BigDecimal | - | 小計（税込）= include_tax_goods_price × goods_num |
| `difficult_price` | `difficultPrice` | BigDecimal | - | 難商品価格（特殊価格） |
| `note` | `note` | String | - | 備考（明細行単位） |
| `stock_process_flg` | `stockProcessFlg` | String | - | 在庫処理フラグ（`0`=未処理、`1`=処理済） |
| `send_order_no` | `sendOrderNo` | Integer | - | 発注番号（紐付け後に設定） |
| `send_order_detail_no` | `sendOrderDetailNo` | Integer | - | 発注明細番号（紐付け後に設定） |
| `contain_num` | `containNum` | BigDecimal | - | 入数（1ケースに入る数量） |
| `purchase_price_reflect` | `purchasePriceReflect` | String | - | 仕入単価マスタ反映済フラグ |
| `del_flg` | `delFlg` | String | - | 削除フラグ |
| `add_date_time` | `addDateTime` | Timestamp | - | 登録日時 |
| `add_user_no` | `addUserNo` | Integer | - | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | Timestamp | - | 更新日時 |
| `modify_user_no` | `modifyUserNo` | Integer | - | 更新ユーザー番号 |

**計算メソッド**:
- `getPurchaseCaseNum()`: ケース換算数を返す。`goodsNum ÷ containNum`（端数切捨て）。`containNum`がゼロの場合は null を返す。

**アソシエーション**:
- `company`（MCompany）: `company_no` で結合
- `tPurchase`（TPurchase）: `purchase_no` で結合（OneToOne）

---

### 3.3 発注ヘッダ（t_send_order）

**クラス**: `jp.co.oda32.domain.model.purchase.TSendOrder`
**テーブル**: `t_send_order`
**主キー**: `send_order_no`（シーケンス自動採番）

| カラム名 | フィールド名 | 型 | 必須 | 説明 |
|---|---|---|---|---|
| `send_order_no` | `sendOrderNo` | Integer | PK | 発注番号（自動採番） |
| `send_order_date_time` | `sendOrderDateTime` | LocalDateTime | - | 発注日時 |
| `desired_delivery_date` | `desiredDeliveryDate` | LocalDate | - | 希望納期（任意） |
| `shop_no` | `shopNo` | Integer | - | ショップ番号 |
| `company_no` | `companyNo` | Integer | - | 会社番号 |
| `supplier_no` | `supplierNo` | Integer | - | 仕入先番号 |
| `send_order_status` | `sendOrderStatus` | String | - | 発注ステータス（後述） |
| `warehouse_no` | `warehouseNo` | Integer | - | 入荷倉庫番号 |
| `del_flg` | `delFlg` | String | - | 削除フラグ |
| `add_date_time` | `addDateTime` | Timestamp | - | 登録日時 |
| `add_user_no` | `addUserNo` | Integer | - | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | Timestamp | - | 更新日時 |
| `modify_user_no` | `modifyUserNo` | Integer | - | 更新ユーザー番号 |

**計算メソッド**:
- `getSendOrderDisplayDate()`: `sendOrderDateTime` を表示用文字列に変換

**アソシエーション**:
- `tSendOrderDetailList`（List<TSendOrderDetail>）: `purchase_no` で結合（注: `send_order_no`と読むべき箇所だが実装上は`purchase_no`カラムを指定）
- `mSupplier`（MSupplier）: `supplier_no` で結合
- `mWarehouse`（MWarehouse）: `warehouse_no` で結合

---

### 3.4 発注明細（t_send_order_detail）

**クラス**: `jp.co.oda32.domain.model.purchase.TSendOrderDetail`
**テーブル**: `t_send_order_detail`
**複合主キー**: `send_order_no` + `send_order_detail_no`（`TSendOrderDetailPK`クラス）

| カラム名 | フィールド名 | 型 | 必須 | 説明 |
|---|---|---|---|---|
| `send_order_no` | `sendOrderNo` | Integer | PK | 発注番号 |
| `send_order_detail_no` | `sendOrderDetailNo` | Integer | PK | 発注明細番号（1から連番） |
| `shop_no` | `shopNo` | Integer | - | ショップ番号 |
| `company_no` | `companyNo` | Integer | - | 会社番号 |
| `warehouse_no` | `warehouseNo` | Integer | - | 倉庫番号 |
| `goods_no` | `goodsNo` | Integer | - | 商品番号 |
| `goods_code` | `goodsCode` | String | - | 商品コード |
| `goods_name` | `goodsName` | String | - | 商品名（スナップショット） |
| `goods_price` | `goodsPrice` | BigDecimal | - | 発注単価 |
| `send_order_num` | `sendOrderNum` | Integer | - | 発注数量（バラ数） |
| `send_order_case_num` | `sendOrderCaseNum` | BigDecimal | - | 発注ケース数 |
| `arrive_plan_date` | `arrivePlanDate` | LocalDate | - | 入荷予定日（仕入先からの納期回答後に設定） |
| `arrived_date` | `arrivedDate` | LocalDate | - | 実際の入荷日 |
| `arrived_num` | `arrivedNum` | BigDecimal | - | 入荷済数量 |
| `difference_num` | `differenceNum` | BigDecimal | - | 差異数（発注数 - 入荷済数） |
| `send_order_detail_status` | `sendOrderDetailStatus` | String | - | 発注明細ステータス（後述） |
| `contain_num` | `containNum` | Integer | - | 入数（1ケースに入る数量） |
| `del_flg` | `delFlg` | String | - | 削除フラグ |
| `add_date_time` | `addDateTime` | Timestamp | - | 登録日時 |
| `add_user_no` | `addUserNo` | Integer | - | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | Timestamp | - | 更新日時 |
| `modify_user_no` | `modifyUserNo` | Integer | - | 更新ユーザー番号 |

**計算メソッド**:
- `getSubtotalDisplay()`: 小計の表示用文字列を返す（`goodsPrice × sendOrderNum` を `"xxxxx円"` 形式でフォーマット）

**アソシエーション**:
- `tSendOrder`（TSendOrder）: `send_order_no` で結合（OneToOne）

---

### 3.5 仕入単価マスタ（m_purchase_price）

**クラス**: `jp.co.oda32.domain.model.purchase.MPurchasePrice`
**テーブル**: `m_purchase_price`
**主キー**: `purchase_price_no`（シーケンス自動採番）

| カラム名 | フィールド名 | 型 | 必須 | 説明 |
|---|---|---|---|---|
| `purchase_price_no` | `purchasePriceNo` | Integer | PK | 仕入単価番号（自動採番） |
| `supplier_no` | `supplierNo` | Integer | - | 仕入先番号 |
| `shop_no` | `shopNo` | Integer | - | ショップ番号 |
| `goods_no` | `goodsNo` | Integer | - | 商品番号 |
| `partner_no` | `partnerNo` | Integer | - | 得意先番号（`0`=直接仕入、得意先指定あり=特定得意先向け） |
| `destination_no` | `destinationNo` | Integer | - | 届け先番号（`0`=デフォルト） |
| `goods_price` | `goodsPrice` | BigDecimal | - | 仕入単価（税抜） |
| `include_tax_goods_price` | `includeTaxGoodsPrice` | BigDecimal | - | 仕入単価（税込） |
| `tax_rate` | `taxRate` | BigDecimal | - | 税率（%） |
| `tax_category` | `taxCategory` | int | - | 課税種別（`0`=通常税率、`1`=軽減税率、`2`=非課税） |
| `include_tax_flg` | `includeTaxFlg` | String | - | 税込フラグ（`0`=税抜入力、`1`=税込入力） |
| `note` | `note` | String | - | 備考 |
| `last_purchase_date` | `lastPurchaseDate` | LocalDate | - | 最終仕入日（バッチ処理時に更新） |
| `period_from` | `periodFrom` | LocalDate | - | 有効期間開始日 |
| `period_to` | `periodTo` | LocalDate | - | 有効期間終了日 |
| `del_flg` | `delFlg` | String | - | 削除フラグ |
| `add_date_time` | `addDateTime` | Timestamp | - | 登録日時 |
| `add_user_no` | `addUserNo` | Integer | - | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | Timestamp | - | 更新日時 |
| `modify_user_no` | `modifyUserNo` | Integer | - | 更新ユーザー番号 |

**ユニークキー**: `(supplier_no, shop_no, goods_no, partner_no, destination_no)`

**アソシエーション**:
- `mGoods`（MGoods）: `goods_no` で結合
- `mShop`（MShop）: `shop_no` で結合
- `mSupplier`（MSupplier）: `supplier_no` で結合
- `mPartner`（MPartner）: `partner_no` で結合
- `wSalesGoods`（WSalesGoods）: `goods_no` + `shop_no` で結合（ManyToOne、NotFoundAction.IGNORE）

---

### 3.6 仕入単価変更履歴（m_purchase_price_log）

**クラス**: `jp.co.oda32.domain.model.purchase.MPurchasePriceLog`
**テーブル**: `m_purchase_price_log`
**主キー**: `purchase_price_log_no`（シーケンス自動採番）

`m_purchase_price`の保存操作が発生するたびに自動的にログが記録されます。

| カラム名 | フィールド名 | 型 | 説明 |
|---|---|---|---|
| `purchase_price_log_no` | `purchasePriceLogNo` | Integer | ログ番号（自動採番） |
| `supplier_no` | `supplierNo` | Integer | 仕入先番号 |
| `shop_no` | `shopNo` | Integer | ショップ番号 |
| `goods_no` | `goodsNo` | Integer | 商品番号 |
| `partner_no` | `partnerNo` | Integer | 得意先番号 |
| `goods_price` | `goodsPrice` | BigDecimal | 仕入単価（税抜） |
| `include_tax_goods_price` | `includeTaxGoodsPrice` | BigDecimal | 仕入単価（税込） |
| `tax_type` | `taxType` | String | 税種別 |
| `tax_rate` | `taxRate` | BigDecimal | 税率 |
| `include_tax_flg` | `includeTaxFlg` | String | 税込フラグ |
| `note` | `note` | String | 備考 |
| `last_purchase_date` | `lastPurchaseDate` | LocalDate | 最終仕入日 |
| `del_flg` | `delFlg` | String | 削除フラグ |
| `add_date_time` | `addDateTime` | Timestamp | 登録日時 |

---

### 3.7 仕入価格変更予定マスタ（m_purchase_price_change_plan）

**クラス**: `jp.co.oda32.domain.model.purchase.MPurchasePriceChangePlan`
**テーブル**: `m_purchase_price_change_plan`
**主キー**: `purchase_price_change_plan_no`（シーケンス自動採番）

| カラム名 | フィールド名 | 型 | 必須 | 説明 |
|---|---|---|---|---|
| `purchase_price_change_plan_no` | `purchasePriceChangePlanNo` | Integer | PK | 変更予定番号（自動採番） |
| `shop_no` | `shopNo` | Integer | - | ショップ番号 |
| `goods_code` | `goodsCode` | String | - | 商品コード |
| `jan_code` | `janCode` | String | - | JANコード |
| `before_price` | `beforePrice` | BigDecimal | - | 変更前単価 |
| `after_price` | `afterPrice` | BigDecimal | - | 変更後単価（改定後の仕入単価） |
| `goods_name` | `goodsName` | String | - | 商品名（スナップショット） |
| `change_contain_num` | `changeContainNum` | BigDecimal | - | 変更後入数 |
| `change_plan_date` | `changePlanDate` | LocalDate | - | 価格改定予定日 |
| `supplier_code` | `supplierCode` | String | - | 仕入先コード |
| `change_reason` | `changeReason` | String | - | 変更理由コード（`PU`=値上、`PD`=値下、`ES`=販売終了） |
| `partner_price_change_plan_created` | `partnerPriceChangePlanCreated` | boolean | - | 得意先価格変更予定作成済フラグ |
| `partner_no` | `partnerNo` | Integer | - | 得意先番号 |
| `destination_no` | `destinationNo` | Integer | - | 届け先番号 |
| `purchase_price_reflect` | `purchasePriceReflect` | boolean | - | 仕入単価への反映完了フラグ |
| `del_flg` | `delFlg` | String | - | 削除フラグ |
| `add_date_time` | `addDateTime` | Timestamp | - | 登録日時 |
| `add_user_no` | `addUserNo` | Integer | - | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | Timestamp | - | 更新日時 |
| `modify_user_no` | `modifyUserNo` | Integer | - | 更新ユーザー番号 |

**アソシエーション**:
- `mSupplier`（MSupplier）: `shop_no` + `supplier_code` の複合カラムで結合（ManyToOne）

---

## 4. 仕入入力フロー

### 4.1 フロー概要

```
仕入入力画面 ──POST──> 仕入確認画面 ──POST──> 仕入完了画面
(GET /purchaseInput)  (/purchaseConfirm)    (/purchaseCreate)
       ^                    |
       |___(戻る)___________|
```

### 4.2 仕入入力画面（purchase_input.html）

**URL**: `GET /purchaseInput`
**コントローラー**: `PurchaseInputController#purchaseInput`
**テンプレート**: `purchase/purchase_input.html`

#### 初期表示処理

1. ショップマップ（全ショップ）を取得してセット
2. 税率タイミングマップをセット（`TaxTiming.toMap()`）
3. 空の `PurchaseInputForm` を生成（明細1行分の空フォームを含む、デフォルト税率10%）

#### フォーム項目（PurchaseInputForm）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `purchaseNo` | Integer | - | 仕入番号（修正時のみ設定） |
| `shopNo` | Integer | `@NotNull` | ショップ番号 |
| `warehouseNo` | Integer | - | 倉庫番号 |
| `supplierNo` | Integer | - | 仕入先番号 |
| `supplierCode` | String | - | 仕入先コード（表示用） |
| `supplierName` | String | - | 仕入先名（表示用） |
| `purchaseCode` | String | - | 仕入コード（任意） |
| `purchaseDate` | String | - | 仕入日（`yyyy/MM/dd`形式の文字列） |
| `purchaseAmount` | BigDecimal | - | 税抜合計金額（自動計算） |
| `taxRate` | BigDecimal | - | 消費税率（デフォルト：10） |
| `taxAmount` | BigDecimal | - | 消費税額（手動入力） |
| `taxTiming` | String | - | 課税タイミングコード |
| `note` | String | - | 備考 |
| `purchaseDetailList` | List<PurchaseDetailForm> | `@Valid` | 明細リスト |

#### 明細フォーム項目（PurchaseDetailForm）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `purchaseNo` | Integer | - | 仕入番号（修正時） |
| `purchaseDetailNo` | Integer | - | 仕入明細番号 |
| `goodsCode` | String | `@Size(max=8)` | 商品コード（最大8桁） |
| `goodsNo` | Integer | - | 商品番号（hidden） |
| `goodsName` | String | - | 商品名（表示用） |
| `purchasePrice` | BigDecimal | - | 仕入単価（税抜） |
| `includeTaxPurchasePrice` | BigDecimal | - | 仕入単価（税込） |
| `purchaseNum` | BigDecimal | `@Min(1)` | 仕入数量 |
| `containNum` | BigDecimal | - | 入数（1ケース当たり） |
| `purchaseCaseNum` | BigDecimal | - | ケース数（自動計算） |
| `subtotal` | BigDecimal | - | 小計（税抜）（hidden） |
| `includeTaxSubtotal` | BigDecimal | - | 小計（税込）（hidden） |
| `supplierNo` | Integer | - | 仕入先番号 |
| `lineNote` | String | - | 行備考 |

#### バリデーション（カスタム）

`PurchaseInputController#validate`メソッドによる追加チェック：

| チェック内容 | エラーメッセージ |
|---|---|
| ショップ未設定 | `"ショップが設定されていません。"` |
| 倉庫未設定 | `"入庫する倉庫が設定されていません。"` |
| 仕入日未設定 | `"仕入日時が設定されていません。"` |
| 仕入日フォーマット不正（`yyyy/MM/dd`） | `"仕入日時のフォーマットが間違っています。{詳細}"` |
| 仕入先未設定 | `"仕入先が設定されていません。"` |

#### 機能

**商品検索（Ajax）**:
- URL: `POST /getPurchaseGoods`
- パラメータ: `goodsCode`（商品コード）、`shopNo`（ショップ番号）、`supplierNo`（仕入先番号）
- 処理: 商品コードとショップでw_sales_goodsを検索し、直近の仕入単価も取得（`m_purchase_price`参照）
- レスポンス: `CustomSalesGoods`（商品情報 + ケース入数 + 仕入単価）

**行追加**:
- URL: `POST /purchaseInputAddOrderDetail`
- 処理: 既存の入力内容を再描画しつつ、明細リストに空行を1行追加

**税込単価の自動計算**:
- 明細再描画時に計算: `includeTaxPurchasePrice = purchasePrice × (1 + taxRate/100)`（端数切捨て）
- 小計: `subtotal = purchasePrice × purchaseNum`
- 税込小計: `includeTaxSubtotal = includeTaxPurchasePrice × purchaseNum`

#### 仕入修正（一覧から遷移）

- URL: `GET /modifyPurchaseInputForm?purchaseNo={purchaseNo}`
- 処理: `t_purchase`から該当仕入番号を取得し、フォームに変換して再描画

---

### 4.3 仕入確認画面（purchase_confirm.html）

**URL**: `POST /purchaseConfirm`
**コントローラー**: `PurchaseInputController#purchaseInputted`
**テンプレート**: `purchase/purchase_confirm.html`

#### 処理内容

1. `PurchaseInputForm` のバリデーション（`@Validated`）とカスタムバリデーション
2. エラーがある場合: 仕入入力画面に戻り、エラーメッセージを表示
3. エラーがない場合:
   - `PurchaseInputForm` → `PurchaseConfirmForm` に変換
   - 仕入日付を `String` → `LocalDate` に変換（フォーマット: `uuuu/MM/dd`、厳密モード）
   - 商品番号がnullの明細行を除外（フィルタリング）
   - ショップ名・仕入先名・倉庫名をサービスから取得してセット
   - 税抜合計・税込合計を計算してセット
4. 確認フォームをモデルにセットして確認画面を表示

#### 確認フォーム項目（PurchaseConfirmForm）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `purchaseNo` | Integer | - | 仕入番号（修正時） |
| `shopNo` | Integer | `@NotNull` | ショップ番号 |
| `shopName` | String | - | ショップ名（表示用） |
| `warehouseNo` | Integer | - | 倉庫番号 |
| `warehouseName` | String | - | 倉庫名（表示用） |
| `supplierNo` | Integer | - | 仕入先番号 |
| `supplierName` | String | - | 仕入先名（表示用） |
| `purchaseDate` | LocalDate | - | 仕入日（Date型） |
| `purchaseDateDisplay` | String | - | 仕入日（表示用文字列） |
| `purchaseCode` | String | - | 仕入コード |
| `purchaseAmount` | BigDecimal | - | 税抜合計金額 |
| `includeTaxPurchaseAmount` | BigDecimal | - | 税込合計金額 |
| `taxRate` | BigDecimal | - | 消費税率 |
| `taxAmount` | BigDecimal | - | 消費税額 |
| `taxTiming` | String | - | 課税タイミング |
| `note` | String | - | 備考 |
| `purchaseDetailList` | List<PurchaseDetailForm> | `@Valid` | 明細リスト（空行除外済） |

**戻るボタン処理**:
- URL: `POST /backPurchaseInputForm`
- 処理: `PurchaseConfirmForm` → `PurchaseInputForm` に変換して入力画面を再描画

---

### 4.4 仕入完了画面（purchase_complete.html）

**URL**: `POST /purchaseCreate`
**コントローラー**: `PurchaseCreateController#purchaseInputted`
**テンプレート**: `purchase/purchase_complete.html`

#### 処理内容

1. `PurchaseConfirmForm` のバリデーション
2. エラーがある場合: 仕入入力画面を返却してエラー表示
3. エラーがない場合:
   - `TPurchase` エンティティを生成してDBに INSERT（`t_purchase`）
   - `TPurchaseDetail` エンティティのリストを生成してDBに INSERT（`t_purchase_detail`）
   - 登録エラー時は例外をキャッチしてエラーメッセージを入力画面に返す
4. 登録した `TPurchase`（明細含む）をモデルにセットして完了画面を表示

#### 仕入ヘッダ（TPurchase）生成ロジック

```java
TPurchase.builder()
    .purchaseNo(form.getPurchaseNo())          // 修正時のみ設定
    .purchaseCode(form.getPurchaseCode())
    .purchaseDate(purchaseDate)                // LocalDate変換済
    .shopNo(mShop.getShopNo())
    .companyNo(mShop.getCompanyNo())           // ショップから会社番号を取得
    .supplierNo(mSupplier.getSupplierNo())
    .purchaseAmount(form.getPurchaseAmount())   // 税抜合計
    .taxRate(form.getTaxRate())
    .taxAmount(form.getTaxAmount())
    .includeTaxAmount(form.getIncludeTaxPurchaseAmount())
    .note(form.getNote())
    .warehouseNo(form.getWarehouseNo())
    .taxTiming(form.getTaxTiming())
    .build();
```

#### 仕入明細（TPurchaseDetail）生成ロジック

```java
TPurchaseDetail.builder()
    .goodsNo(goods.getGoodsNo())
    .goodsCode(goods.getGoodsCode())
    .goodsName(goods.getGoodsName())
    .goodsPrice(detail.getPurchasePrice())           // 税抜単価
    .includeTaxGoodsPrice(detail.getIncludeTaxPurchasePrice())  // 税込単価
    .purchaseDetailNo(purchaseDetailNo)              // 1から連番
    .purchaseNo(purchase.getPurchaseNo())
    .goodsNum(detail.getPurchaseNum())               // 数量（バラ）
    .shopNo(form.getShopNo())
    .companyNo(purchase.getCompanyNo())
    .warehouseNo(form.getWarehouseNo())
    .containNum(detail.getContainNum())              // 入数
    .subtotal(detail.getPurchasePrice().multiply(detail.getPurchaseNum()))
    .includeTaxSubtotal(detail.getIncludeTaxPurchasePrice().multiply(detail.getPurchaseNum()))
    .note(detail.getLineNote())
    .stockProcessFlg(Flag.NO.getValue())             // 初期値: 未処理
    .taxRate(form.getTaxRate())
    .taxCategory(goods.getMGoods().getTaxCategory()) // 商品マスタから取得
    .taxType(TaxType.TAX_EXCLUDE.getValue())         // 税抜
    .purchaseDate(purchase.getPurchaseDate())
    .build();
```

#### 完了画面表示内容

| 表示項目 | 説明 |
|---|---|
| 仕入日付 | 登録した仕入日 |
| 仕入書番号 | 自動採番された `purchase_no` |
| 仕入コード | 入力した `purchase_code` |
| 消費税率 | 設定した税率（%表示） |
| 仕入先名 | `mSupplier.supplierNameDisplay` |
| 納品場所 | 倉庫の会社名・住所・電話番号 |
| 仕入商品テーブル | 商品名・入数・ケース数・バラ数 |

---

### 4.5 仕入一覧（purchase_list.html）

**URL**: `GET /purchaseList`（初期表示）、`POST /purchaseList`（検索）
**コントローラー**: `PurchaseListController`
**テンプレート**: `purchase/purchase_list.html`

#### 検索フォーム（PurchaseListForm）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `shopNo` | Integer | `@NotNull` | ショップ番号（必須） |
| `warehouseNo` | Integer | - | 倉庫番号 |
| `supplierNo` | Integer | - | 仕入先番号 |
| `goodsNo` | Integer | - | 商品番号 |
| `goodsCode` | String | - | 商品コード（部分一致） |
| `goodsName` | String | - | 商品名（表示用） |
| `purchaseDateFrom` | String | - | 仕入日FROM（`yyyy/MM/dd`形式） |
| `purchaseDateTo` | String | - | 仕入日TO（`yyyy/MM/dd`形式） |

#### 検索処理

`TPurchaseDetailService.find()`を使用してデータ取得。検索結果が500件超の場合は警告メッセージを表示（"表示件数が多すぎます。条件を変更してください。件数:xxx"）。

#### 削除機能

- URL: `GET /deletePurchase?purchaseNo={purchaseNo}`
- 処理フロー:
  1. 仕入番号で `TPurchase` を取得
  2. 各明細の `stockProcessFlg` が `YES`（在庫計上済）の場合、在庫数を減少（マイナス移動）
  3. `TPurchase` および紐付く全明細を物理削除
  4. 成功時: `"仕入削除に成功しました。仕入番号:{purchaseNo}"`
  5. 失敗時: `"仕入削除に失敗しました。仕入番号:{purchaseNo} 理由:{例外メッセージ}"`

---

## 5. 発注管理フロー

### 5.1 発注明細ステータスフロー（SendOrderDetailStatus）

```
00: 発注済
  └─ (仕入先から納期回答) ──> 10: 納期回答
       └─ (物品が入荷) ──> 20: 入荷済
            └─ (仕入伝票を入力) ──> 30: 仕入入力済
                                    (いずれのステータスからも)
                                    └─ 99: キャンセル
```

**重要制約**: ステータスの後退は不可能。現在のステータスコードと同じかそれより小さいコードへの変更はシステムエラーとなる。

### 5.2 発注ヘッダステータス（SendOrderStatus）

| コード | 名称 | 説明 |
|---|---|---|
| `00` | 発注済 | 発注登録完了直後の初期ステータス |
| `10` | 納期回答 | 仕入先から納期回答あり |
| `15` | 一部入荷済 | 一部の明細が入荷済 |
| `20` | 入荷済 | 全明細が入荷済 |
| `30` | 仕入伝票入力済 | 仕入明細の入力完了 |
| `90` | 一部キャンセル | 一部の明細をキャンセル |
| `99` | 全キャンセル | 全明細をキャンセル |

---

### 5.3 発注入力画面（send_order_input.html）

**URL**: `GET /sendOrderInput`
**コントローラー**: `SendOrderInputController#sendOrderInput`
**テンプレート**: `purchase/send_order_input.html`

#### 初期表示処理

1. ショップマップを取得してセット
2. 空の `SendOrderInputForm` を生成（明細1行分の空フォームを含む）

#### フォーム項目（SendOrderInputForm）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `shopNo` | Integer | `@NotNull` | ショップ番号（必須） |
| `warehouseNo` | Integer | - | 入荷倉庫番号 |
| `supplierNo` | Integer | - | 仕入先番号 |
| `supplierCode` | String | - | 仕入先コード（表示用） |
| `supplierName` | String | - | 仕入先名（表示用） |
| `sendOrderDateTime` | String | - | 発注日時（`yyyy/MM/dd HH:mm`形式） |
| `desiredDeliveryDate` | String | - | 希望納期（`yyyy/MM/dd`形式、任意） |
| `sendOrderDetailList` | List<SendOrderDetailForm> | `@Valid` | 明細リスト |

#### 明細フォーム項目（SendOrderDetailForm）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `goodsCode` | String | `@Size(max=8)` | 商品コード |
| `goodsNo` | Integer | - | 商品番号（hidden） |
| `goodsName` | String | - | 商品名（表示用） |
| `purchasePrice` | BigDecimal | - | 発注単価 |
| `sendOrderNum` | Integer | `@Min(1)` | 発注数量 |
| `containNum` | Integer | - | 入数 |
| `sendOrderCaseNum` | BigDecimal | - | 発注ケース数 |
| `subtotal` | BigDecimal | - | 小計（purchasePrice × sendOrderNum） |
| `supplierNo` | Integer | - | 仕入先番号 |

#### バリデーション（カスタム）

`SendOrderInputController#validate`メソッド:

| チェック内容 | エラーメッセージ |
|---|---|
| ショップ未設定 | `"ショップが設定されていません。"` |
| 倉庫未設定 | `"入庫する倉庫が設定されていません。"` |
| 発注日時未設定 | `"発注日時が設定されていません。"` |
| 発注日時フォーマット不正（`yyyy/MM/dd HH:mm`） | `"発注日時のフォーマットが間違っています。{詳細}"` |
| 仕入先未設定 | `"仕入先が設定されていません。"` |

#### 商品検索（Ajax）

- URL: `POST /getSendOrderGoods`
- パラメータ: `goodsCode`、`shopNo`
- 処理: 商品コードとショップでw_sales_goodsを検索（仕入単価は取得しない）
- レスポンス: `CustomSalesGoods`（商品情報 + ケース入数）

---

### 5.4 発注確認画面（send_order_confirm.html）

**URL**: `POST /sendOrderConfirm`
**コントローラー**: `SendOrderInputController#sendOrderInputted`
**テンプレート**: `purchase/send_order_confirm.html`

#### 処理内容

1. バリデーション（`@Validated`）とカスタムバリデーション
2. 明細フォームを再描画（商品名・仕入先番号をサービスから取得して補完）
3. `SendOrderInputForm` → `SendOrderConfirmForm` に変換
4. 発注日時・希望納期を `String` → `LocalDateTime/LocalDate` に変換
5. 空行を除外（`emptyFormRemoveFilter`: 数量がnull・負・商品コードが空の行を除外）
6. ショップ名・仕入先名・倉庫名を取得してセット
7. 小計合計を計算（全明細の `purchasePrice × sendOrderNum` の合計）
8. 確認フォームをモデルにセット

#### 確認フォーム（SendOrderConfirmForm）

| フィールド | 型 | 説明 |
|---|---|---|
| `shopNo` | Integer | ショップ番号 |
| `shopName` | String | ショップ名 |
| `warehouseNo` | Integer | 倉庫番号 |
| `warehouseName` | String | 倉庫名 |
| `supplierNo` | Integer | 仕入先番号 |
| `supplierName` | String | 仕入先名 |
| `sendOrderDateTime` | LocalDateTime | 発注日時 |
| `sendOrderDateTimeDisplay` | String | 発注日時（表示用） |
| `desiredDeliveryDate` | LocalDate | 希望納期 |
| `desiredDeliveryDateDisplay` | String | 希望納期（表示用） |
| `sendOrderDetailList` | List<SendOrderDetailForm> | 明細リスト（空行除外済） |
| `subtotal` | BigDecimal | 合計金額 |

**空行除外フィルタ** (`emptyFormRemoveFilter`):
- `sendOrderNum == null` → 除外
- `sendOrderNum < 0` → 除外
- `goodsCode`が空 → 除外

---

### 5.5 発注完了画面（send_order_complete.html）

**URL**: `POST /sendOrderCreate`
**コントローラー**: `SendOrderCreateController#sendOrderInputted`
**テンプレート**: `purchase/send_order_complete.html`

#### 処理内容

1. バリデーションチェック
2. 発注ヘッダ（`TSendOrder`）を生成・INSERT
   - 初期ステータス: `SendOrderStatus.ORDERED.getValue()`（`"00"` = 発注済）
3. 発注明細（`TSendOrderDetail`）リストを生成・INSERT
   - 明細初期ステータス: `SendOrderDetailStatus.SEND_ORDER.getCode()`（`"00"` = 発注済）
4. 完了画面を表示（発注書フォーマット）

#### 発注書表示内容

| 表示項目 | 説明 |
|---|---|
| 発注日時 | 発注書発行日時 |
| 発注書番号 | 自動採番された `send_order_no` |
| 仕入先名（宛先） | `mSupplier.supplierNameDisplay` + 「御中」 |
| 発注者名 | ログインユーザー名 |
| 自社情報 | 会社名・部署・住所・TEL・FAX（固定値） |
| 納品場所 | 倉庫の会社名・住所・電話番号 |
| 希望納期 | （設定されている場合のみ表示） |
| 発注商品テーブル | 商品名・入数・ケース数・バラ数 |

---

### 5.6 発注一覧（send_order_list.html）

**URL**: `GET /sendOrderList`（初期表示）、`POST /sendOrderList`（検索）
**コントローラー**: `SendOrderListController`
**テンプレート**: `purchase/send_order_list.html`

#### 検索フォーム（SendOrderListForm）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `shopNo` | Integer | `@NotNull` | ショップ番号（必須） |
| `warehouseNo` | Integer | - | 倉庫番号 |
| `supplierNo` | Integer | - | 仕入先番号 |
| `sendOrderDetailStatus` | String | - | 発注明細ステータス |
| `sendOrderDateTimeFrom` | String | - | 発注日時FROM（`yyyy/MM/dd HH:mm`） |
| `sendOrderDateTimeTo` | String | - | 発注日時TO（`yyyy/MM/dd HH:mm`） |

#### 一覧表示項目

| カラム名 | 説明 |
|---|---|
| 発注番号 | `yyyyMMdd-{send_order_no}-{send_order_detail_no}` フォーマット |
| 商品コード | |
| 商品名 | |
| 単価 | `goods_price` |
| 数量 | `send_order_num`（バラ数） |
| 入数 | `contain_num` |
| ケース換算 | `send_order_case_num` |
| 小計 | `getSubtotalDisplay()`（`"xxxxx円"` 形式） |
| 入荷予定日 | 日付入力フィールド（更新可能） |
| 入荷日 | 日付入力フィールド（更新可能） |
| 入荷済数 | 数値入力フィールド（更新可能） |
| ステータス | プルダウン（更新可能） |

#### 発注明細ステータス更新（Ajax）

- URL: `POST /updateSendOrderDetail`
- パラメータ: `sendOrderNo`、`sendOrderDetailNo`、`arrivePlanDate`、`arrivedDate`、`arrivedNum`、`sendOrderDetailStatus`
- バリデーション:
  - ステータスの後退は不可（現在値以下には変更不可）
  - `ARRIVAL_TO_PROMISE`（10: 納期回答）に変更時: `arrivePlanDate`が必須
  - `ARRIVED`（20: 入荷済）に変更時: `arrivedNum`が必須かつ正の値
  - `PURCHASED`（30: 仕入入力済）に変更時: `arrivedDate`が必須
- 入荷済ステータスへの変更時の特別処理:
  - 在庫数を増加（`StockManager.move()`で `StockLogReason.ARRIVED` として移動）
- レスポンス: `CommonAjaxObject`（成功/失敗メッセージ）

---

## 6. 仕入単価マスタ管理

### 6.1 仕入単価マスタ一覧（m_purchase_price.html）

**URL**: `GET /mPurchasePriceList`（初期表示）、`POST /mPurchasePriceList`（検索）
**コントローラー**: `MPurchasePriceController`
**テンプレート**: `purchase/m_purchase_price.html`

#### 検索フォーム（MPurchasePriceListForm）

| フィールド | 型 | 説明 |
|---|---|---|
| `shopNo` | Integer | ショップ番号 |
| `supplierNo` | Integer | 仕入先番号 |
| `goodsCode` | String | 商品コード（部分一致） |
| `goodsName` | String | 商品名（部分一致） |
| `notLikeGoodsName` | String | 除外商品名（指定した文字を含む商品を除外） |
| `includeTaxStr` | String | 税込フラグ |

#### 検索処理

`MPurchasePriceService.find()`でフィルタリング。ショップ・商品コード・商品名・除外商品名・仕入先の複合条件検索。

---

### 6.2 仕入単価登録・編集（m_purchase_price_create.html）

**URL**:
- `GET /mPurchasePriceCreateNewForm` （新規）
- `GET /mPurchasePriceCreateForm?purchasePriceNo={no}` （編集）
- `POST /mPurchasePriceCreate` （保存）

**コントローラー**: `MPurchasePriceCreateController`
**テンプレート**: `purchase/m_purchase_price_create.html`

#### 登録フォーム（MPurchasePriceCreateForm）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `purchasePriceNo` | Integer | - | 仕入単価番号（更新時のみ） |
| `goodsNo` | Integer | `@NotNull` | 商品番号（必須） |
| `goodsCode` | String | `@NotBlank` | 商品コード（必須、商品選択必要） |
| `goodsName` | String | - | 商品名（表示用） |
| `shopNo` | Integer | - | ショップ番号 |
| `supplierNo` | Integer | `@NotNull` | 仕入先番号（必須） |
| `supplierCode` | String | `@NotBlank` | 仕入先コード（必須、仕入先選択必要） |
| `supplierName` | String | - | 仕入先名（表示用） |
| `partnerNo` | Integer | - | 得意先番号（任意） |
| `destinationNo` | Integer | - | 届け先番号（任意） |
| `periodFrom` | String | - | 有効期間開始日 |
| `periodTo` | String | - | 有効期間終了日 |
| `goodsPrice` | BigDecimal | `@NotNull` | 入力単価（必須） |
| `taxRate` | BigDecimal | - | 税率（%） |
| `includeTaxFlg` | boolean | - | 税込入力フラグ |
| `note` | String | - | 備考 |

#### 保存時の税額計算ロジック

```
税込フラグ = true（税込入力）の場合:
  goodsPrice（税抜）= 入力値 ÷ (1 + taxRate/100)  [小数2桁、切捨て]
  includeTaxGoodsPrice = 入力値

税込フラグ = false（税抜入力）の場合:
  goodsPrice = 入力値
  includeTaxGoodsPrice = 入力値 × (1 + taxRate/100)  [小数2桁、切上げ]
```

#### 保存処理フロー

1. フォームバリデーション
2. 税抜・税込単価を計算
3. `partnerNo`・`destinationNo` が null の場合は 0 を設定
4. `MPurchasePrice` エンティティを生成
5. `partnerNo != 0` の場合は強制的に 0 にリセット（直近仕入価格として登録 - TODO注記あり）
6. `MPurchasePriceService.save()` で保存
   - `m_purchase_price` テーブルへの UPSERT
   - `m_purchase_price_log` テーブルへの履歴記録
7. 成功時: `"保存に成功しました"` のメッセージを表示

---

## 7. 仕入価格変更予定管理

### 7.1 仕入価格変更一覧（purchase_price_change_list.html）

**URL**: `GET /purchasePriceChangeList`（初期表示）、`POST /purchasePriceChangeList`（検索）
**コントローラー**: `PurchasePriceChangePlanListController`
**テンプレート**: `purchase/purchase_price_change_list.html`

#### 検索フォーム（PurchasePriceChangeForm）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `shopNo` | Integer | `@NotNull` | ショップ番号（必須） |
| `supplierNo` | Integer | - | 仕入先番号 |
| `goodsCode` | String | - | 商品コード |
| `janCode` | String | - | JANコード |
| `purchasePriceChangeReason` | String | - | 変更理由コード（`PU`/`PD`/`ES`） |
| `priceChangeDateFrom` | String | - | 価格改定日FROM（`yyyy/MM/dd`） |
| `priceChangeDateTo` | String | - | 価格改定日TO（`yyyy/MM/dd`） |

---

### 7.2 仕入価格変更予定単品登録（m_purchase_price_change_plan_create.html）

**URL**:
- `GET /mPurchasePriceChangePlanCreateNewForm`（新規）
- `POST /mPurchasePriceChangePlanCreate`（保存）

**コントローラー**: `MPurchasePriceChangePlanCreateController`
**テンプレート**: `purchase/m_purchase_price_change_plan_create.html`

#### フォーム（MPurchasePriceChangePlanCreateForm）

| フィールド | 型 | 説明 |
|---|---|---|
| `goodsNo` | Integer | 商品番号 |
| `goodsCode` | String | 商品コード |
| `janCode` | String | JANコード |
| `goodsName` | String | 商品名 |
| `shopNo` | Integer | ショップ番号 |
| `supplierNo` | Integer | 仕入先番号 |
| `supplierCode` | String | 仕入先コード |
| `partnerNo` | Integer | 得意先番号 |
| `destinationNo` | Integer | 届け先番号 |
| `beforePrice` | BigDecimal | 変更前単価 |
| `afterPrice` | BigDecimal | 変更後単価 |
| `changePlanDateStr` | String | 価格改定予定日（`yyyy/MM/dd`） |
| `changeReason` | String | 変更理由コード |

#### 保存処理フロー（重複チェック付きUPSERT）

1. 同じ（shopNo、supplierCode、goodsCode、partnerNo、destinationNo、changePlanDate）の組み合わせが既存か確認
2. 既存なし → INSERT（`"登録に成功しました"`）
3. 既存あり → UPDATE（`"更新に成功しました"`）

---

### 7.3 仕入価格変更一括入力（purchase_price_change_list_input.html）

**URL**:
- `GET /purchasePriceChangeListInput`（初期表示）
- `POST /addPurchasePriceChangeDetail`（行追加）
- `POST /purchasePriceChangeListInputConfirm`（確認画面へ）
- `POST /backPurchasePriceChangeListInput`（戻る）
- `POST /purchasePriceChangeListCreate`（保存）

**コントローラー**: `PurchasePriceChangeListInputController`
**テンプレート**:
- 入力: `purchase/purchase_price_change_list_input.html`
- 確認: `purchase/purchase_price_change_list_confirm.html`
- 完了: `purchase/purchase_price_change_list_complete.html`

#### ヘッダーフォーム（PurchasePriceChangeHeaderForm）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `shopNo` | Integer | - | ショップ番号 |
| `shopName` | String | - | ショップ名 |
| `supplierNo` | Integer | - | 仕入先番号 |
| `supplierCode` | String | - | 仕入先コード |
| `supplierName` | String | - | 仕入先名 |
| `partnerNo` | Integer | - | 得意先番号 |
| `changePlanDateStr` | String | `@NotBlank` | 価格改定日（必須、`yyyy/MM/dd`） |
| `purchasePriceChangeDetailFormList` | List<PurchasePriceChangeDetailForm> | `@NotEmpty @Valid` | 明細リスト（必須、1件以上） |
| `changeReason` | String | - | 変更理由コード |

#### 明細フォーム（PurchasePriceChangeDetailForm）

| フィールド | 型 | バリデーション | 説明 |
|---|---|---|---|
| `goodsNo` | Integer | - | 商品番号 |
| `goodsCode` | String | `@NotBlank` | 商品コード（必須） |
| `janCode` | String | - | JANコード |
| `goodsName` | String | - | 商品名 |
| `containNum` | BigDecimal | - | 現在の入数 |
| `changeContainNum` | BigDecimal | - | 変更後入数 |
| `beforePrice` | BigDecimal | `@Digits(integer=5, fraction=3) @NotNull` | 変更前単価（必須、整数5桁・小数3桁以内） |
| `afterPrice` | BigDecimal | `@Digits(integer=5, fraction=3) @NotNull` | 変更後単価（必須、整数5桁・小数3桁以内） |
| `createOrUpdate` | String | - | 確認画面での処理種別表示（"登録します"/"更新します"） |

#### ヘッダーバリデーション（カスタム）

| チェック内容 | エラーメッセージ |
|---|---|
| ショップ未設定 | `"ショップが設定されていません。"` |
| 価格改定日未設定 | `"価格改定日が設定されていません。"` |
| 価格改定日フォーマット不正 | `"見積日時のフォーマットが間違っています。{詳細}"` |
| 仕入先未設定 | `"仕入先が設定されていません。"` |
| 有効明細なし | `"明細が設定されていません。"` |

#### 確認画面での処理判定

各明細行に対して既存データの有無を確認：
- 存在しない → `createOrUpdate = "登録します"`
- 存在する → `createOrUpdate = "更新します"`

#### 保存処理フロー（一括UPSERT）

1. ヘッダーバリデーション
2. フォームをエンティティリスト（`List<MPurchasePriceChangePlan>`）に変換
3. 各明細に対して重複チェック付きUPSERT実行
4. 成功メッセージのリストを構築（`"商品コード：{code} {name}を登録/更新しました。"`）
5. 完了画面に成功・失敗メッセージを表示

---

## 8. バッチ処理

### 8.1 仕入ファイル取込バッチ（purchaseFileImport）

**ジョブ名**: `purchaseFileImport`
**設定クラス**: `jp.co.oda32.batch.purchase.config.PurchaseFileImportConfig`
**実行コマンド**:
```bash
java -jar stock-app.jar --spring.profiles.active=batch,prod --spring.batch.job.name=purchaseFileImport
```

#### ステップ構成（順序）

| 順序 | ステップ名 | タイプ | 処理内容 |
|---|---|---|---|
| 1 | `wSmilePurchaseFileTruncateStep` | Tasklet | ワークテーブル（`w_smile_purchase_output_file`）のTRUNCATE |
| 2 | `purchaseFileImportStep` | Chunk（500件） | SMILE仕入CSVをワークテーブルに取込 |
| 3 | `smilePurchaseImportStep` | Tasklet | ワークテーブルから仕入・仕入明細を本テーブルにUPSERT |
| 4 | `purchaseLinkSendOrderStep` | Tasklet | 仕入明細と発注明細のマッチング・紐付け |
| 5 | `purchasePriceCreateStep` | Tasklet | 仕入実績から仕入単価マスタを自動更新 |

---

#### ステップ2: 仕入ファイル取込（Chunkステップ）

**Reader**: `ShopNoAwareItemReader`（`MultiResourceItemReader<PurchaseFile>`を拡張）
**Processor**: `PurchaseFileProcessor`
**Writer**: `PurchaseFileWriter`
**チャンクサイズ**: 500件

**Reader詳細**:
- 複数ショップ・複数ファイルに対応（shop_no=1 の `purchase_import.csv`、shop_no=2 の `purchase_import2_YYYYMMDD.csv` 等）
- `setDelegate()` を override し、delegate に `ResourceTrackingDelegate` ラッパーを被せて `setResource()` 呼び出しをフック。現在読込中リソースを追跡
- 1行ごとに `currentResource.getFilename()` を `m_shop_linked_file.smile_purchase_file_name` と照合して正しい `shop_no` をセット
- 過去のバグ修正: 旧実装は `resources[]` 先頭から検索していたため複数ファイル取込時に全行が先頭ファイルの shop_no になる問題があった（shop_no=2 の仕入が shop_no=1 にも複製される不具合）
- shori_renban は shop_no が違えば別系統。shop_no=1 は 330000台、shop_no=2 は 80000台

**入力ファイルフォーマット（PurchaseFile）**: SMILEから出力される仕入明細CSV。主なカラム:

| CSVカラム | 説明 |
|---|---|
| 伝票日付 | `yyyyMMdd`形式の文字列 → `LocalDate`に変換 |
| 伝票番号 | 仕入伝票番号 |
| 処理連番 | SMILE内の処理連番（`ext_purchase_no`に対応） |
| 仕入先コード | 仕入先識別コード |
| 商品コード | 商品識別コード（8桁） |
| 商品名 | 商品名 |
| 入数 | 1ケースの入数 |
| 数量 | 仕入数量 |
| 単価 | 仕入単価 |
| 金額 | 仕入金額 |
| 消費税率 | 消費税率（%） |
| 課税区分 | 課税種別コード |
| 消費税分類 | 課税分類（通常・軽減・非課税） |
| 発注番号 | 関連する発注番号 |

**Processor詳細** (`PurchaseFileProcessor`):
1. `shop_no` からの会社番号（`company_no`）を取得（初回のみ）
2. `PurchaseFile` → `ExtPurchaseFile` へのコピー（`goodsNo`、`companyNo`、`supplierNo`、`warehouseNo`を追加）
3. 商品コード `99999999`（手打ち商品）の場合、商品コードをMD5ハッシュ値（16進数文字列）に変換

---

#### ステップ4: 発注紐付けタスクレット（PurchaseLinkSendOrderTasklet）

**処理概要**: バッチ取込された仕入明細と手動入力された発注明細を自動マッチングして紐付けます。

**マッチングロジック**:
1. 発注明細のうちステータスが「発注済」「納期回答」「入荷済」（00、10、20）のものを取得
2. 仕入明細のうち発注番号が未紐付けかつ1ヶ月以内のものを取得
3. 以下の条件で両者をマッチング:
   - 同じ `goods_no`（商品番号）
   - 同じ `goods_num`（数量） = 発注明細の `send_order_num`
   - 仕入日 > 発注日時
4. マッチングした場合:
   - 仕入明細に `send_order_no`、`send_order_detail_no` をセット
   - 発注明細のステータスを `ARRIVED`（入荷済）の場合、仕入明細の `stock_process_flg` を `YES` にセット
   - 発注明細の入荷日・入荷済数が未設定の場合、仕入明細の仕入日・数量で補完
   - 発注明細のステータスを `PURCHASED`（30: 仕入入力済）に更新

---

#### ステップ5: 仕入単価マスタ自動更新タスクレット（PurchasePriceCreateTasklet）

**処理概要**: 仕入実績から仕入単価マスタ（`m_purchase_price`）を自動更新します。

**処理フロー**:
1. 仕入単価マスタ未反映（`purchase_price_reflect IS NULL`）の仕入明細を取得（`findLatestPurchasePrice()`）
2. 以下の条件でフィルタリング:
   - 仕入単価 > 0（値引き行は除外）
   - 数量 > 0（数量未入力行は除外）
3. 仕入明細から `MPurchasePrice` に変換してマスタをUPSERT
   - `supplier_no`: 仕入ヘッダの仕入先番号
   - `goods_no`: 仕入明細の商品番号
   - `shop_no`: 仕入明細のショップ番号
   - `partner_no`: 0（直接仕入）
   - `destination_no`: 0（デフォルト）
   - `goods_price`: 仕入明細の単価
   - `last_purchase_date`: 仕入日
   - `include_tax_flg`: `Flag.NO`（税抜）
4. 全仕入明細の `purchase_price_reflect` フラグを更新（反映済みに設定）
5. 保存時に `m_purchase_price_log` にも履歴記録

---

### 8.2 仕入価格変更予定反映バッチ（PurchasePriceChangeReflectTasklet）

**処理概要**: 価格改定予定日を過ぎた仕入価格変更予定を仕入単価マスタに自動反映します。

**処理フロー**:
1. `purchase_price_reflect = false`（未反映）の変更予定レコードを全件取得
2. `change_plan_date <= 今日` のものだけに絞り込み（当日以前の予定のみ処理）
3. 各変更予定に対して:
   - 仕入先コードから `MSupplier` を取得（見つからない場合はWARNINGログ出力してスキップ）
   - 商品コードとショップ番号から `WSalesGoods` を取得（見つからない場合はERRORログ出力してスキップ）
   - ユニークキーで既存の `MPurchasePrice` を検索
   - 既存レコードあり → 単価を `after_price` で更新
   - 既存レコードなし → 新規レコード作成（税率は `MTaxRate` から取得、軽減税率商品は `reducedTaxRate` を適用）
4. 全変更予定の `purchase_price_reflect` フラグを true に更新（`updateReflectComplete()`）

---

### 8.3 仕入先マスタファイル取込

**Reader**: `PurchaseMasterFileReader`
**Processor**: `PurchaseMasterFileWriter`
**Writer**: `PurchaseMasterFileWriter`

SMILEの仕入先マスタCSVから仕入先情報を取込みます。`PurchaseMasterFile` には仕入先の住所・電話番号・支払条件・締日等の詳細情報が含まれます。

---

## 9. 定数・区分値定義

### 9.1 発注明細ステータス（SendOrderDetailStatus）

| コード | 定数名 | 表示名 | 説明 |
|---|---|---|---|
| `00` | `SEND_ORDER` | 発注済 | 発注登録直後の初期状態 |
| `10` | `ARRIVAL_TO_PROMISE` | 納期回答 | 仕入先から入荷予定日の回答があった状態 |
| `20` | `ARRIVED` | 入荷済 | 商品が実際に入荷した状態（在庫増加処理実行） |
| `30` | `PURCHASED` | 仕入入力済 | 仕入伝票の入力が完了した状態 |
| `99` | `CANCEL` | キャンセル | 発注をキャンセルした状態 |

### 9.2 発注ヘッダステータス（SendOrderStatus）

| コード | 定数名 | 説明 |
|---|---|---|
| `00` | `ORDERED` | 発注済（初期値） |
| `10` | `ARRIVAL_TO_PROMISE` | 納期回答 |
| `15` | `PART_ARRIVED` | 一部入荷済 |
| `20` | `ARRIVED` | 入荷済 |
| `30` | `PURCHASE_INPUTTED` | 仕入伝票入力済 |
| `90` | `PART_CANCEL` | 一部キャンセル |
| `99` | `CANCEL` | 全キャンセル |

### 9.3 課税タイミング（TaxTiming）

| コード | 定数名 | 表示名 | 説明 |
|---|---|---|---|
| `0` | `SLIP` | 伝票毎 | 仕入伝票ごとに消費税を計算 |
| `1` | `PAYMENT` | 請求時 | 請求時に一括して消費税を計算 |

### 9.4 仕入価格変更理由（PurchasePriceChangeReason）

| コード | 定数名 | 表示名 |
|---|---|---|
| `PU` | `PRICE_UP` | 値上 |
| `PD` | `PRICE_DOWN` | 値下 |
| `ES` | `END_OF_SALE` | 販売終了 |

### 9.5 課税種別（taxCategory）

| 値 | 説明 |
|---|---|
| `0` | 通常税率（10%） |
| `1` | 軽減税率（8%） |
| `2` | 非課税 |

---

## 10. ビジネスロジック詳細

### 10.1 税額計算ロジック

#### 仕入入力時（手動入力）

仕入入力画面では税率を手動で入力します（デフォルト10%）。

```
税込単価 = 税抜単価 × (1 + 税率/100)  ← 小数点以下切捨て
小計（税抜）= 税抜単価 × 数量
小計（税込）= 税込単価 × 数量
合計（税抜）= Σ(明細の税抜小計)  ← goods_noがnullの行は除外
合計（税込）= Σ(明細の税込小計)  ← goods_noがnullの行は除外
```

#### 仕入単価マスタ登録時

```
税込入力（includeTaxFlg=true）の場合:
  税抜単価 = 入力値 ÷ (1 + taxRate/100)  [小数2桁、切捨て]
  税込単価 = 入力値

税抜入力（includeTaxFlg=false）の場合:
  税抜単価 = 入力値
  税込単価 = 入力値 × (1 + taxRate/100)  [小数2桁、切上げ]
```

#### ケース換算

```
ケース数 = 数量（バラ）÷ 入数  [端数切捨て（RoundingMode.DOWN）]
条件: 入数がゼロの場合は null を返す
```

### 10.2 仕入単価マスタの自動更新タイミング

仕入単価マスタ（`m_purchase_price`）は以下のタイミングで更新されます。

| タイミング | 処理 | 方法 |
|---|---|---|
| 仕入ファイル取込バッチ実行時 | SMILEから取込んだ仕入実績を元に自動更新 | バッチ（`PurchasePriceCreateTasklet`） |
| 仕入単価登録・編集画面での保存時 | 手動で単価を登録・修正 | Web画面（`MPurchasePriceCreateController`） |
| 価格変更予定反映バッチ実行時 | 価格改定予定日到来後に自動反映 | バッチ（`PurchasePriceChangeReflectTasklet`） |

更新のたびに `m_purchase_price_log` にログが自動記録されます（`MPurchasePriceService.save()`内で実行）。

### 10.3 在庫への影響

| 操作 | 在庫への影響 | 処理クラス |
|---|---|---|
| 発注明細を「入荷済」に更新 | 在庫を増加（`StockLogReason.ARRIVED`） | `SendOrderListController#updateSendOrderDetail` |
| 仕入伝票削除 | `stock_process_flg=YES`の明細の在庫を減少 | `PurchaseListController#deletePurchase` |
| 仕入ファイル取込バッチ（発注紐付け時） | 入荷済フラグが立っている場合に在庫処理フラグを設定 | `PurchaseLinkSendOrderTasklet` |

### 10.4 発注紐付けマッチングルール

バッチ処理（`PurchaseLinkSendOrderTasklet`）での自動紐付けは以下のルールで行います：

```
条件1: purchaseDetail.goods_no = sendOrderDetail.goods_no（同じ商品）
条件2: purchaseDetail.goods_num = sendOrderDetail.send_order_num（同じ数量）
条件3: purchaseDetail.purchase_date > sendOrder.send_order_date_time（仕入日が発注後）
条件4: purchaseDetail.send_order_no IS NULL（未紐付け）
条件5: purchaseDetail.purchase_date >= 1ヶ月前（直近1ヶ月の仕入明細のみ）
```

### 10.5 手打ち商品（固定商品コード 99999999）

SMILEからの仕入ファイルに商品コード `99999999` が含まれる場合（`Constants.FIXED_PRODUCT_CODE`）、商品名のMD5ハッシュ値（16進数文字列）を商品コードとして設定します。これにより、SMILE上で自由入力された商品名ベースで一意の商品コードが生成されます。

### 10.6 仕入削除時の参照整合性

仕入削除（`TPurchaseService.delete()`）では、論理削除ではなく物理削除を行います：
1. `t_purchase` レコードの削除
2. `t_purchase_detail` の関連明細を `purchase_no` で一括削除
3. 在庫計上済み（`stock_process_flg = '1'`）の明細がある場合は、削除前に在庫を減少させます

---

## 11. システム連携

### 11.1 SMILE連携（仕入ファイル取込）

```
SMILE（基幹システム）
  |
  | CSV出力（SMILEの仕入伝票データ）
  |
  v
ファイルサーバー（ショップ別ファイル配置）
  |
  | purchaseFileImportバッチ読込
  |
  v
w_smile_purchase_output_file（ワークテーブル）
  |
  | smilePurchaseImportStep
  |
  v
t_purchase（仕入ヘッダ）
t_purchase_detail（仕入明細）
  |
  | purchaseLinkSendOrderStep
  |
  v
t_send_order_detail（発注明細ステータス更新・紐付け）
  |
  | purchasePriceCreateStep
  |
  v
m_purchase_price（仕入単価マスタ更新）
m_purchase_price_log（履歴記録）
```

**ファイル配置設定**: `m_shop_linked_file` テーブルの `smile_purchase_file_name` フィールドにファイルパスを設定。複数ショップ・複数ファイルに対応。

### 11.2 SMILEと本システムの仕入データ整合性

SMILEで仕入が削除された場合の検出ロジック（`TPurchaseService.findDeletedPurchases()`）:
- `w_smile_purchase_output_file` に存在する伝票日付（当日以前）を基準に
- 本システムの `t_purchase` に存在するが `w_smile_purchase_output_file` に対応する `ext_purchase_no` がないレコードを検出
- これによりSMILE側で削除・修正された仕入伝票を特定できる

### 11.3 財務システム（マネーフォワード）への連携

仕入データは財務ドメイン（`batch/finance`）のバッチ処理を通じてマネーフォワード用の仕訳CSVに変換されます。詳細は財務ドメイン仕様書を参照。

---

## 付録：コントローラー一覧

| クラス名 | 役割 |
|---|---|
| `PurchaseInputController` | 仕入入力・確認・修正の制御 |
| `PurchaseCreateController` | 仕入登録・完了の制御 |
| `PurchaseListController` | 仕入一覧検索・削除の制御 |
| `SendOrderInputController` | 発注入力・確認の制御 |
| `SendOrderCreateController` | 発注登録・完了の制御 |
| `SendOrderListController` | 発注一覧検索・ステータス更新の制御 |
| `SendOrderGoodsPopController` | 商品選択ポップアップの制御 |
| `MPurchasePriceController` | 仕入単価マスタ一覧の制御 |
| `MPurchasePriceCreateController` | 仕入単価登録・更新の制御 |
| `MPurchasePriceChangePlanCreateController` | 仕入価格変更予定単品登録の制御 |
| `PurchasePriceChangePlanListController` | 仕入価格変更予定一覧の制御 |
| `PurchasePriceChangeListInputController` | 仕入価格変更予定一括入力・登録の制御 |

## 付録：サービスクラス一覧

| クラス名 | 役割 |
|---|---|
| `TPurchaseService` | 仕入ヘッダのCRUD |
| `TPurchaseDetailService` | 仕入明細のCRUD |
| `TSendOrderService` | 発注ヘッダのCRUD |
| `TSendOrderDetailService` | 発注明細のCRUD |
| `MPurchasePriceService` | 仕入単価マスタのCRUD（履歴記録含む） |
| `MPurchasePriceLogService` | 仕入単価変更履歴のCRUD |
| `MPurchasePriceChangePlanService` | 仕入価格変更予定のCRUD |

## 付録：バッチクラス一覧

| クラス名 | タイプ | 役割 |
|---|---|---|
| `PurchaseFileImportConfig` | Configuration | 仕入ファイル取込ジョブ定義 |
| `ShopNoAwareItemReader` | ItemReader | ショップ番号付き仕入ファイルReader |
| `PurchaseFileReader` | ItemReader | 仕入ファイルReader（基底） |
| `PurchaseFileProcessor` | ItemProcessor | 仕入ファイルの変換・会社番号付与 |
| `PurchaseFileWriter` | ItemWriter | ワークテーブルへの書込 |
| `PurchaseLinkSendOrderTasklet` | Tasklet | 仕入-発注マッチング・紐付け |
| `PurchasePriceCreateTasklet` | Tasklet | 仕入実績→仕入単価マスタ自動更新 |
| `PurchasePriceChangeReflectTasklet` | Tasklet | 価格変更予定→仕入単価マスタ反映 |
| `PurchaseMasterFileReader` | ItemReader | 仕入先マスタCSVReader |
| `PurchaseMasterFileProcessor` | ItemProcessor | 仕入先マスタの変換 |
| `PurchaseMasterFileWriter` | ItemWriter | 仕入先マスタへの書込 |
