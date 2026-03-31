# 商品管理機能 仕様書

**対象システム:** stock-app (小田光株式会社 社内システム)
**作成日:** 2026-02-23
**対象ドメイン:** goods（商品管理）

---

## 目次

1. [商品管理の概要](#1-商品管理の概要)
2. [画面一覧](#2-画面一覧)
3. [エンティティ定義](#3-エンティティ定義)
4. [各画面の詳細仕様](#4-各画面の詳細仕様)
5. [ビジネスロジック](#5-ビジネスロジック)
6. [バッチ処理（商品ファイル取込）](#6-バッチ処理商品ファイル取込)
7. [画面間の関連と遷移フロー](#7-画面間の関連と遷移フロー)
8. [サービス・リポジトリ一覧](#8-サービスリポジトリ一覧)

---

## 1. 商品管理の概要

### 1.1 目的

商品管理ドメインは、小田光株式会社が取り扱う全商品の情報を管理する中核ドメインである。以下の機能を提供する。

- **商品マスタ管理**: 全社共通の商品基本情報（`m_goods`）の登録・修正・論理削除
- **販売商品管理**: ショップ別の販売用商品情報（`w_sales_goods` / `m_sales_goods`）の管理。ワークテーブル経由でマスタへ反映するフローを採用
- **得意先商品管理**: 得意先・届け先ごとの個別価格設定（`m_partner_goods`）の管理
- **外部システム連携**: SMILEからの商品ファイル取込バッチによる自動マスタ更新
- **商品単位管理**: 「ケース」など取引単位の入数管理（`m_goods_unit`）

### 1.2 アーキテクチャ上の位置づけ

商品管理は以下のドメインから参照される依存先ドメインである。

- **order（注文管理）**: 注文明細から `ISalesGoods` を参照して商品情報取得
- **purchase（仕入管理）**: 仕入価格算出に `m_sales_goods` / `w_sales_goods` を使用
- **estimate（見積管理）**: 見積明細から商品情報取得
- **bcart（B-CART連携）**: EC在庫・価格同期に販売商品情報を使用
- **finance（財務連携）**: 仕訳生成に商品情報を使用

### 1.3 販売商品のデュアルテーブル構成

販売商品は「ワークテーブル（`w_sales_goods`）」と「マスタテーブル（`m_sales_goods`）」の二層構成を採用している。

- `w_sales_goods`: 社内担当者が編集・調整する作業領域。`getIsWork() = true` を返す
- `m_sales_goods`: 実際の販売に使用する確定情報。`getIsWork() = false` を返す
- 両テーブルは `ISalesGoods` インターフェースで統一的に扱われる
- `w_sales_goods` が存在する場合、優先的に使用される

---

## 2. 画面一覧

| 画面名 | URL | HTTPメソッド | テンプレート | 説明 |
|--------|-----|-------------|------------|------|
| 商品マスタ一覧 | `/goodsMaster` | GET / POST | `goods/goods_master.html` | 商品マスタ検索・一覧表示 |
| 商品マスタ登録 | `/goodsCreate` | GET / POST | `goods/goods_create.html` | 商品マスタ新規登録 |
| 商品マスタ修正 | `/goodsModifyForm` | GET / POST | `goods/goods_modify_form.html` | 商品マスタ修正・削除 |
| 商品マスタポップアップ | `/goodsMasterPop` | GET / POST | `goods/goods_master_pop.html` | 他画面からの商品選択用ポップアップ |
| 販売商品WORK一覧 | `/salesGoodsWork` | GET / POST | `goods/w_sales_goods.html` | 販売商品ワーク検索・一覧表示 |
| 販売商品作成可能リスト | `/wSalesGoodsCreateList` | GET / POST | `goods/w_sales_goods_create_list.html` | 販売商品WORK未登録の商品マスタ一覧 |
| 販売商品WORK登録 | `/wSalesGoodsCreateForm` | GET / POST | `goods/w_sales_goods_create_form.html` | 販売商品WORKの新規登録・マスタ反映 |
| 販売商品WORK詳細・修正 | `/wSalesGoodsModifyForm` | GET / POST | `goods/w_sales_goods_modify_form.html` | 販売商品WORKの修正・マスタ反映 |
| 販売商品マスタ一覧 | `/salesGoodsMaster` | GET / POST | `goods/m_sales_goods.html` | 販売商品マスタ検索・一覧表示 |
| 得意先商品マスタ一覧 | `/partnerGoodsMaster` | GET / POST | `goods/m_partner_goods.html` | 得意先商品検索・一覧表示 |
| 得意先商品詳細・修正 | `/partnerGoodsModifyForm` | GET / POST | `goods/m_partner_goods_modify_form.html` | 得意先商品の価格修正 |
| 販売商品検索ポップアップ | `/salesGoodsPop` | POST | `goods/sales_goods_pop.html` | 他画面からの販売商品選択用ポップアップ |

---

## 3. エンティティ定義

### 3.1 m_goods（商品マスタ）

**物理テーブル名:** `m_goods`
**クラス:** `jp.co.oda32.domain.model.goods.MGoods`
**主キー:** `goods_no`（シーケンス `m_goods_goods_no_seq` で自動採番）
**継承戦略:** `SINGLE_TABLE`

#### フィールド定義

| カラム名 | フィールド名 | 型 | 説明 | 備考 |
|---------|------------|-----|------|------|
| `goods_no` | `goodsNo` | `Integer` | 商品番号（PK） | シーケンス自動採番 |
| `goods_name` | `goodsName` | `String` | 商品名 | NFKC正規化処理あり |
| `jan_code` | `janCode` | `String` | JANコード | 8桁または13桁 |
| `maker_no` | `makerNo` | `Integer` | メーカー番号（FK: `m_maker`) | |
| `del_flg` | `delFlg` | `String` | 削除フラグ | '0'=有効, '1'=削除 |
| `keyword` | `keyword` | `String` | キーワード（索引語） | カンマ区切りで複数設定可能 |
| `tax_category` | `taxCategory` | `Integer` | 税区分コード | 0=通常、1=軽減税率、2=非課税 |
| `tax_category_name` | `taxCategoryName` | `String` | 税区分名称 | |
| `specification` | `specification` | `String` | 仕様・規格 | |
| `discontinued_flg` | `discontinuedFlg` | `String` | 廃番フラグ | |
| `case_contain_num` | `caseContainNum` | `BigDecimal` | ケース入数 | 1未満の場合は1を設定（バッチ処理） |
| `is_apply_reduced_tax_rate` | `isApplyReducedTaxRate` | `boolean` | 軽減税率適用フラグ | |
| `smile_goods_name` | `smileGoodsName` | `String` | SMILEシステム上の商品名 | 商品ファイル取込時に設定 |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 | |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 | |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 最終更新日時 | |
| `modify_user_no` | `modifyUserNo` | `Integer` | 最終更新ユーザー番号 | |

#### リレーション

| リレーション名 | 種別 | 結合カラム | 参照エンティティ |
|-------------|------|----------|--------------|
| `maker` | `@OneToOne` | `maker_no` | `MMaker`（メーカーマスタ） |

---

### 3.2 w_sales_goods（販売商品ワーク）

**物理テーブル名:** `w_sales_goods`
**クラス:** `jp.co.oda32.domain.model.goods.WSalesGoods`
**主キー:** 複合キー `(shop_no, goods_no)` — `WSalesGoodsPK` クラスで定義
**実装インターフェース:** `ISalesGoods`（`getIsWork() = true` を返す）
**継承戦略:** `SINGLE_TABLE`

#### フィールド定義

| カラム名 | フィールド名 | 型 | 説明 | 備考 |
|---------|------------|-----|------|------|
| `shop_no` | `shopNo` | `Integer` | ショップ番号（PK1） | |
| `goods_no` | `goodsNo` | `Integer` | 商品番号（PK2） | FK: `m_goods.goods_no` |
| `goods_code` | `goodsCode` | `String` | 商品コード | SMILEの商品コード。特殊商品（コード99999999）はMD5ハッシュに変換 |
| `goods_sku_code` | `goodsSkuCode` | `String` | 商品SKUコード | EC用SKUコード |
| `goods_name` | `goodsName` | `String` | 商品名（販売用） | `@`以降の価格情報を自動除去 |
| `category_no` | `categoryNo` | `Integer` | カテゴリ番号 | ※現在UI上で入力不可（TODO状態） |
| `reference_price` | `referencePrice` | `BigDecimal` | 参考価格 | |
| `purchase_price` | `purchasePrice` | `BigDecimal` | 標準仕入単価 | 0円以下の場合はnull設定（バッチ） |
| `goods_price` | `goodsPrice` | `BigDecimal` | 標準売単価 | 0円以下の場合はnull設定（バッチ） |
| `supplier_no` | `supplierNo` | `Integer` | 仕入先番号 | FK: `m_supplier` |
| `catchphrase` | `catchphrase` | `String` | キャッチフレーズ | EC表示用 |
| `goods_introduction` | `goodsIntroduction` | `String` | 商品概要 | EC表示用 |
| `goods_description1` | `goodsDescription1` | `String` | 商品説明1 | EC表示用 |
| `goods_description2` | `goodsDescription2` | `String` | 商品説明2 | EC表示用 |
| `del_flg` | `delFlg` | `String` | 削除フラグ | '0'=有効, '1'=削除 |
| `keyword` | `keyword` | `String` | キーワード | |
| `direct_shipping_flg` | `directShippingFlg` | `String` | 直送フラグ | |
| `lead_time` | `leadTime` | `Integer` | リードタイム（日数） | |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 | |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 | |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 最終更新日時 | |
| `modify_user_no` | `modifyUserNo` | `Integer` | 最終更新ユーザー番号 | |

#### リレーション

| リレーション名 | 種別 | 結合カラム | 参照エンティティ |
|-------------|------|----------|--------------|
| `mGoods` | `@OneToOne` | `goods_no` | `MGoods`（商品マスタ） |
| `mShop` | `@OneToOne` | `shop_no` | `MShop`（ショップマスタ） |
| `mSupplier` | `@OneToOne` | `supplier_no` | `MSupplier`（仕入先マスタ） |

---

### 3.3 m_sales_goods（販売商品マスタ）

**物理テーブル名:** `m_sales_goods`
**クラス:** `jp.co.oda32.domain.model.goods.MSalesGoods`
**主キー:** 複合キー `(shop_no, goods_no)` — `MSalesGoodsPK` クラスで定義
**実装インターフェース:** `ISalesGoods`（`getIsWork() = false` を返す）
**継承戦略:** `SINGLE_TABLE`

フィールド構成は `w_sales_goods` と完全に同一。`ISalesGoods` インターフェースを共有することで、サービス層では両者を透過的に扱える。

#### 主キークラス（MSalesGoodsPK）

```java
// クラス: jp.co.oda32.domain.model.embeddable.MSalesGoodsPK
// shop_no (Integer) + goods_no (Integer) の複合主キー
```

---

### 3.4 m_partner_goods（得意先商品マスタ）

**物理テーブル名:** `m_partner_goods`
**クラス:** `jp.co.oda32.domain.model.goods.MPartnerGoods`
**主キー:** 複合キー `(partner_no, goods_no, destination_no)` — `MPartnerGoodsPK` クラスで定義

#### フィールド定義

| カラム名 | フィールド名 | 型 | 説明 | 備考 |
|---------|------------|-----|------|------|
| `partner_no` | `partnerNo` | `Integer` | 得意先番号（PK1） | |
| `goods_no` | `goodsNo` | `Integer` | 商品番号（PK2） | FK: `m_goods.goods_no` |
| `destination_no` | `destinationNo` | `Integer` | 届け先番号（PK3） | 届け先別に異なる価格設定が可能 |
| `company_no` | `companyNo` | `Integer` | 会社番号 | FK: `m_company` |
| `shop_no` | `shopNo` | `Integer` | ショップ番号 | |
| `goods_price` | `goodsPrice` | `BigDecimal` | 得意先向け売単価 | 正の値のみ更新（BigDecimalUtil.isPositive チェック） |
| `order_num_per_year` | `orderNumPerYear` | `BigDecimal` | 年間注文数量 | 年次リセット機能あり |
| `goods_name` | `goodsName` | `String` | 得意先向け商品名 | NFKC正規化処理あり |
| `goods_code` | `goodsCode` | `String` | 商品コード | |
| `keyword` | `keyword` | `String` | キーワード | |
| `last_sales_date` | `lastSalesDate` | `LocalDate` | 最終売上日 | 注文処理時に更新 |
| `last_price_update_date` | `lastPriceUpdateDate` | `LocalDate` | 最終価格更新日 | |
| `reflected_estimate_no` | `reflectedEstimateNo` | `Integer` | 反映元見積番号 | 見積から価格を反映した際に設定 |
| `reflected_estimate_detail_no` | `reflectedEstimateDetailNo` | `Integer` | 反映元見積明細番号 | |
| `del_flg` | `delFlg` | `String` | 削除フラグ | '0'=有効, '1'=削除 |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 | |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 | |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 最終更新日時 | |
| `modify_user_no` | `modifyUserNo` | `Integer` | 最終更新ユーザー番号 | |

#### リレーション

| リレーション名 | 種別 | 結合カラム | 参照エンティティ |
|-------------|------|----------|--------------|
| `mGoods` | `@OneToOne` | `goods_no` | `MGoods`（商品マスタ） |
| `mShop` | `@OneToOne` | `shop_no` | `MShop`（ショップマスタ） |
| `mCompany` | `@OneToOne` | `company_no` | `MCompany`（会社マスタ） |

#### 主キークラス（MPartnerGoodsPK）

```java
// クラス: jp.co.oda32.domain.model.embeddable.MPartnerGoodsPK
// partner_no (Integer) + goods_no (Integer) + destination_no (Integer) の複合主キー
```

---

### 3.5 m_goods_unit（商品単位マスタ）

**物理テーブル名:** `m_goods_unit`
**クラス:** `jp.co.oda32.domain.model.goods.MGoodsUnit`
**主キー:** `unit_no`（シーケンス `m_goods_unit_unit_no_seq` で自動採番）

#### フィールド定義

| カラム名 | フィールド名 | 型 | 説明 | 備考 |
|---------|------------|-----|------|------|
| `unit_no` | `unitNo` | `Integer` | 単位番号（PK） | シーケンス自動採番 |
| `goods_no` | `goodsNo` | `Integer` | 商品番号（FK） | `m_goods.goods_no` |
| `unit` | `unit` | `String` | 単位種別 | `UnitType.CASE` など定数で管理 |
| `contain_num` | `containNum` | `BigDecimal` | 入数 | |
| `parent_unit_no` | `parentUnitNo` | `Integer` | 親単位番号 | 単位の階層構造（ケース→個など） |
| `del_flg` | `delFlg` | `String` | 削除フラグ | '0'=有効, '1'=削除 |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 | |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 | |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 最終更新日時 | |
| `modify_user_no` | `modifyUserNo` | `Integer` | 最終更新ユーザー番号 | |

---

### 3.6 m_partner_goods_price_change_plan（得意先商品価格変更予定マスタ）

**物理テーブル名:** `m_partner_goods_price_change_plan`
**クラス:** `jp.co.oda32.domain.model.goods.MPartnerGoodsPriceChangePlan`
**主キー:** `partner_goods_price_change_plan_no`（シーケンス自動採番）

#### フィールド定義（主要項目）

| カラム名 | フィールド名 | 型 | 説明 |
|---------|------------|-----|------|
| `partner_goods_price_change_plan_no` | `partnerGoodsPriceChangePlanNo` | `Integer` | 価格変更予定番号（PK） |
| `shop_no` | `shopNo` | `Integer` | ショップ番号 |
| `company_no` | `companyNo` | `Integer` | 会社番号 |
| `partner_no` | `partnerNo` | `Integer` | 得意先番号 |
| `partner_code` | `partnerCode` | `String` | 得意先コード |
| `goods_no` | `goodsNo` | `Integer` | 商品番号 |
| `goods_code` | `goodsCode` | `String` | 商品コード |
| `jan_code` | `janCode` | `String` | JANコード |
| `before_price` | `beforePrice` | `BigDecimal` | 変更前売単価 |
| `after_price` | `afterPrice` | `BigDecimal` | 変更後売単価 |
| `goods_name` | `goodsName` | `String` | 商品名 |
| `change_contain_num` | `changeContainNum` | `BigDecimal` | 変更入数 |
| `destination_no` | `destinationNo` | `Integer` | 届け先番号（届け先別価格の場合に使用） |
| `change_plan_date` | `changePlanDate` | `LocalDate` | 価格変更予定日 |
| `change_reason` | `changeReason` | `String` | 変更理由 |
| `before_purchase_price` | `beforePurchasePrice` | `BigDecimal` | 変更前仕入単価 |
| `after_purchase_price` | `afterPurchasePrice` | `BigDecimal` | 変更後仕入単価 |
| `estimate_created` | `estimateCreated` | `boolean` | 見積作成済みフラグ |
| `estimate_no` | `estimateNo` | `Integer` | 関連見積番号 |
| `estimate_detail_no` | `estimateDetailNo` | `Integer` | 関連見積明細番号 |
| `partner_price_reflect` | `partnerPriceReflect` | `boolean` | 得意先価格反映済みフラグ |
| `parent_change_plan_no` | `parentChangePlanNo` | `Integer` | 親変更予定番号 |
| `deficit_flg` | `deficitFlg` | `boolean` | 赤字フラグ |
| `note` | `note` | `String` | 備考 |
| `del_flg` | `delFlg` | `String` | 削除フラグ |

---

## 4. 各画面の詳細仕様

### 4.1 商品マスタ一覧画面

**タイトル:** 商品マスタ
**URL:** `/goodsMaster`
**テンプレート:** `goods/goods_master.html`
**コントローラー:** `GoodsMasterController`

#### リクエスト仕様

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/goodsMaster` | 初期表示（空フォーム） |
| POST | `/goodsMaster` | 検索実行 |

#### 検索フォーム（GoodsMasterForm）

| フィールド名 | 型 | バリデーション | 説明 |
|-----------|-----|------------|------|
| `goodsName` | `String` | `@NotNull` | 商品名（部分一致：LIKE検索） |
| `keyword` | `String` | なし | キーワード（部分一致：LIKE検索） |
| `janCode` | `String` | なし | JANコード（完全一致） |
| `makerNo` | `Integer` | なし | メーカー番号（完全一致） |

#### 検索バリデーション（CommonGoodsMasterLogic#validateSearch）

- `goodsName`、`janCode`、`keyword`、`makerNo` の全てが空・null の場合、「検索条件を入力してください。」のwarningメッセージを表示して検索を実行しない
- これはデータが多すぎる場合の全件検索を防ぐ制御である

#### 検索条件の詳細（GoodsSpecification）

| 条件 | 検索方式 |
|------|---------|
| 商品名 | `LIKE '%goodsName%'`（部分一致） |
| キーワード | `LIKE '%keyword%'`（部分一致） |
| JANコード | `= janCode`（完全一致） |
| メーカー番号 | `= makerNo`（完全一致） |
| 削除フラグ | `= '0'`（有効レコードのみ） |

#### 表示項目（一覧テーブル）

| カラム | データソース | 説明 |
|--------|------------|------|
| 商品番号 | `goods.goodsNo` | |
| メーカー名 | `goods.maker.makerName` | nullの場合「登録なし」を表示 |
| 商品名 | `goods.goodsName` | |
| JANコード | `goods.janCode` | nullの場合「登録なし」を表示 |
| キーワード | `goods.keyword` | |
| がっつり編集 | リンク | `/goodsModifyForm?goodsNo={goodsNo}` へ遷移 |

#### ボタン

- **商品マスタ新規登録**: `/goodsCreate` へ遷移
- **検索**: POST送信
- **クリア**: フォームリセット

---

### 4.2 商品マスタ登録画面

**タイトル:** 商品マスタ登録
**URL:** `/goodsCreate`
**テンプレート:** `goods/goods_create.html`
**コントローラー:** `GoodsCreateController`

#### リクエスト仕様

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/goodsCreate` | 初期表示（空フォーム） |
| POST | `/goodsCreate` | 登録処理 |

#### 登録フォーム（GoodsCreateForm）

| フィールド名 | 型 | バリデーション | 説明 | UI表示 |
|-----------|-----|------------|------|--------|
| `goodsNo` | `Integer` | — | 商品番号（自動採番） | 表示のみ（disabled） |
| `goodsName` | `String` | `@NotBlank` | 商品名 | 入力必須 |
| `makerNo` | `Integer` | `@NotNull` | メーカー番号 | プルダウン選択（必須） |
| `janCode` | `String` | `@NotBlank` + `@Size(min=8, max=13)` | JANコード | 入力必須、8〜13桁 |
| `caseContainNum` | `BigDecimal` | なし | ケース入数 | 数値入力 |
| `specification` | `String` | なし | 仕様・規格 | テキスト入力 |
| `keyword` | `String` | なし | キーワード | テキスト入力 |
| `applyReducedTaxRateFlg` | `boolean` | なし | 軽減税率適用フラグ | チェックボックス |

#### 処理フロー

1. バリデーション実行
2. `MGoodsService#insert(GoodsCreateForm)` 呼び出し
3. `BeanUtils.copyProperties` でフォーム→Entityに変換
4. `isApplyReducedTaxRateFlg` → `isApplyReducedTaxRate` にマッピング
5. 登録成功時: 「登録しました」を表示、商品番号を画面に表示
6. 失敗時: エラーメッセージを表示

---

### 4.3 商品マスタ修正画面

**タイトル:** 商品マスタ修正（テンプレート名は `goods_modify_form.html`）
**URL:** `/goodsModifyForm`
**コントローラー:** `GoodsModifyController`

#### リクエスト仕様

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/goodsModifyForm?goodsNo={id}` | 修正フォーム初期表示 |
| POST | `/goodsModifyForm` | 修正処理（フォーム送信） |
| POST | `/goodsModify` | 修正・削除処理（Ajax） |

#### 修正フォーム（GoodsModifyForm）

| フィールド名 | 型 | バリデーション | 説明 |
|-----------|-----|------------|------|
| `goodsNo` | `Integer` | — | 商品番号 |
| `goodsName` | `String` | `@NotBlank` | 商品名 |
| `makerNo` | `Integer` | `@NotNull` | メーカー番号 |
| `janCode` | `String` | なし | JANコード |
| `caseContainNum` | `BigDecimal` | なし | ケース入数 |
| `keyword` | `String` | なし | キーワード |
| `action` | `String` | なし | 操作種別（Ajax用）: `"edit"` or `"delete"` |
| `specification` | `String` | なし | 仕様 |
| `applyReducedTaxRateFlg` | `boolean` | なし | 軽減税率適用フラグ |

#### Ajax処理仕様（`/goodsModify` POST）

**編集（action="edit"）の場合:**
1. `Normalizer.normalize(goodsName, Normalizer.Form.NFKC)` で商品名を正規化
2. 同名商品の重複チェック（自分自身を除く）
3. 重複あり: `errorMessage` を返す
4. 重複なし: `MGoodsService#update` 実行、`successMessage` を返す

**削除（action="delete"）の場合:**
1. `MGoodsService#delete` 実行（`del_flg = '1'` に更新）
2. `successMessage` を返す

**レスポンス形式:** `Map<String, String>` をJSON返却

---

### 4.4 商品マスタポップアップ画面

**URL:** `/goodsMasterPop`
**テンプレート:** `goods/goods_master_pop.html`
**コントローラー:** `GoodsMasterController`

#### リクエスト仕様

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/goodsMasterPop?janCode={code}` | JANコードを初期値に設定して表示 |
| POST | `/goodsMasterPop` | 検索実行 |

他画面（発注入力など）からJANコードを渡してポップアップで商品を検索・選択するためのモーダル。フォームとバリデーション仕様は商品マスタ一覧と同一。

---

### 4.5 販売商品WORK一覧画面

**タイトル:** 販売商品WORK
**URL:** `/salesGoodsWork`
**テンプレート:** `goods/w_sales_goods.html`
**コントローラー:** `WSalesGoodsController`

#### リクエスト仕様

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/salesGoodsWork` | 初期表示 |
| POST | `/salesGoodsWork` | 検索実行 |

#### 検索フォーム（WSalesGoodsForm）

| フィールド名 | 型 | バリデーション | 検索方式 | 説明 |
|-----------|-----|------------|---------|------|
| `shopNo` | `Integer` | なし | 完全一致 | ショップ番号（プルダウン） |
| `goodsName` | `String` | なし | OR/部分一致 | 半角スペース区切りで複数ワードOR検索 |
| `notLikeGoodsName` | `String` | なし | AND/NOT LIKE | 含めたくない商品名（スペース区切りでAND除外） |
| `goodsCode` | `String` | なし | 後方一致 | `LIKE '%goodsCode'` |
| `keyword` | `String` | なし | AND/部分一致 | スペース区切りでAND検索 |
| `supplierNo` | `Integer` | なし | 完全一致 | 仕入先番号（仕入先コード検索後に設定） |
| `supplierCode` | `String` | なし | — | 仕入先コード（表示用） |
| `supplierName` | `String` | なし | — | 仕入先名（表示用） |

#### 特殊な検索仕様（WSalesGoodsSpecification）

- **商品名の複数ワード検索（OR）**: スペース（半角・全角）で分割し、各ワードを `LIKE '%word%'` で OR 結合
- **除外商品名の複数ワード検索（AND NOT LIKE）**: スペース分割し、各ワードを `NOT LIKE '%word%'` で AND 結合
- **キーワードの複数ワード検索（AND）**: スペース分割し、各ワードを `LIKE '%word%'` で AND 結合

#### 表示項目（一覧テーブル）

| カラム | データソース | 説明 |
|--------|------------|------|
| ショップ | `salesGoods.mShop?.shopName` | |
| メーカー | `salesGoods.mGoods.maker.makerName` | nullの場合「登録してください」を表示 |
| 商品名 | `salesGoods.goodsName` | |
| 商品コード | `salesGoods.goodsCode` | |
| JANコード | `salesGoods.mGoods?.janCode` | nullの場合「登録してください」を表示 |
| 仕入先 | `salesGoods.mSupplier.supplierName` | nullの場合「登録なし」を表示 |
| 仕入価格 | `salesGoods.purchasePrice` | 数値フォーマット表示、nullの場合「未設定」 |
| 標準売価 | `salesGoods.goodsPrice` | 数値フォーマット表示、nullの場合「未設定」 |
| 詳細 | リンク | `/wSalesGoodsModifyForm?shopNo=&goodsNo=` へ遷移 |

#### ボタン

- **販売商品WORK新規作成**: `/wSalesGoodsCreateList` へ遷移

---

### 4.6 販売商品作成可能リスト画面

**タイトル:** 販売商品作成可能リスト
**URL:** `/wSalesGoodsCreateList`
**テンプレート:** `goods/w_sales_goods_create_list.html`
**コントローラー:** `WSalesGoodsCreateListController`

#### 機能説明

`m_goods` に登録済みで、ログインユーザーのショップにおける `w_sales_goods` に未登録の商品を一覧表示する。販売商品WORKを新規作成する際の起点画面。

#### リクエスト仕様

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/wSalesGoodsCreateList` | 初期表示 |
| POST | `/wSalesGoodsCreateList` | 検索実行 |

#### 検索フォーム（WSalesGoodsCreateListForm、IGoodsFormを実装）

| フィールド名 | 型 | バリデーション | 説明 |
|-----------|-----|------------|------|
| `goodsName` | `String` | `@NotNull` | 商品名（部分一致） |
| `keyword` | `String` | なし | キーワード（部分一致） |
| `janCode` | `String` | なし | JANコード（完全一致） |
| `makerNo` | `Integer` | なし | メーカー番号 |

**空検索防止**: `goodsName`、`janCode`、`keyword`、`makerNo` の全てが空の場合は検索不可（商品マスタ一覧と同じ制御）

#### クエリの特徴

ログインユーザーのショップ番号を取得し、以下のSQLで未登録商品を検索する:

```sql
SELECT g.*
FROM m_goods g
LEFT JOIN w_sales_goods wsg ON g.goods_no = wsg.goods_no AND wsg.shop_no = {loginUserShopNo}
WHERE wsg.goods_no IS NULL
  AND g.goods_name LIKE '%{goodsName}%'  -- 条件あれば
  AND g.keyword LIKE '%{keyword}%'        -- 条件あれば
  AND g.jan_code = '{janCode}'            -- 条件あれば
  AND g.maker_no = {makerNo}             -- 条件あれば
```

#### 表示項目（一覧テーブル）

| カラム | 説明 |
|--------|------|
| 商品番号 | |
| メーカー | nullの場合「登録なし」 |
| 商品名 | |
| JANコード | |
| キーワード | |
| 新規作成 | リンク: `/wSalesGoodsCreateForm?goodsNo={goodsNo}` へ遷移 |

---

### 4.7 販売商品WORK登録画面

**タイトル:** 販売商品WORK登録
**URL:** `/wSalesGoodsCreateForm`
**テンプレート:** `goods/w_sales_goods_create_form.html`
**コントローラー:** `WSalesGoodsCreateController`

#### リクエスト仕様

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/wSalesGoodsCreateForm?goodsNo={id}` | 登録フォーム初期表示（商品マスタ情報を引き継ぎ） |
| POST | `/wSalesGoodsCreateForm` | WORKテーブルへ登録のみ |
| POST | `/wSalesGoodsCreateReflect` | WORKテーブルへ登録 + マスタへ反映 |

#### 初期表示時の処理

1. `goodsNo` で `MGoods` を取得し、`BeanUtils.copyProperties` でフォームに設定
2. ログインユーザーのショップ番号を取得し、ショップ情報と供給者マップをモデルに設定

#### 登録フォーム（WSalesGoodsCreateForm）

**【商品基本情報タブ】**

| フィールド名 | 型 | バリデーション | 説明 |
|-----------|-----|------------|------|
| `shopNo` | `Integer` | `@NotNull` | ショップ番号（hidden） |
| `goodsNo` | `Integer` | `@NotNull` | 商品番号（hidden） |
| `goodsCode` | `String` | `@NotBlank` | 商品コード |
| `goodsSkuCode` | `String` | なし | 商品SKUコード |
| `goodsName` | `String` | `@NotBlank` | 商品名 |
| `keyword` | `String` | なし | キーワード |
| `supplierNo` | `Integer` | `@NotNull` | 仕入先番号（プルダウン必須） |
| `categoryNo` | `Integer` | なし | カテゴリ番号（現在UI非表示・TODO） |

**【価格情報タブ】**

| フィールド名 | 型 | バリデーション | 説明 |
|-----------|-----|------------|------|
| `referencePrice` | `BigDecimal` | なし | 参考価格 |
| `purchasePrice` | `BigDecimal` | `@NotNull` | 標準仕入単価 |
| `goodsPrice` | `BigDecimal` | `@NotNull` | 標準売単価 |

**【商品説明タブ】**

| フィールド名 | 型 | バリデーション | 説明 |
|-----------|-----|------------|------|
| `catchphrase` | `String` | なし | キャッチフレーズ |
| `goodsIntroduction` | `String` | なし | 商品概要 |
| `goodsDescription1` | `String` | なし | 商品説明1 |
| `goodsDescription2` | `String` | なし | 商品説明2 |

#### ボタン

| ボタン名 | アクション | 処理 |
|---------|-----------|------|
| 登録 | POST `/wSalesGoodsCreateForm` | `w_sales_goods` のみに登録 |
| 販売商品マスタに反映 | POST `/wSalesGoodsCreateReflect` | `w_sales_goods` に登録後、`m_sales_goods` にも反映 |

---

### 4.8 販売商品WORK詳細・修正画面

**タイトル:** 販売商品WORK詳細
**URL:** `/wSalesGoodsModifyForm`
**テンプレート:** `goods/w_sales_goods_modify_form.html`
**コントローラー:** `WSalesGoodsModifyController`

#### リクエスト仕様

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/wSalesGoodsModifyForm?shopNo={n}&goodsNo={n}` | 修正フォーム初期表示 |
| POST | `/wSalesGoodsModifyForm` | WORKテーブルのみ更新 |
| POST | `/wSalesGoodsModifyReflect` | WORKテーブル更新 + マスタへ反映 |

#### 初期表示時の処理

1. `WSalesGoodsService#getByPK(shopNo, goodsNo)` でデータ取得
2. `BeanUtils.copyProperties` でEntity→フォームに変換
3. `CommonSalesGoodsService#getCaseGoodsUnitNum` でケース入数を取得（`unit = 'CASE'` のレコードから）
4. `CommonSalesGoodsController#getGoodsImagePath` で商品画像パスリストを取得

#### 修正フォーム（WSalesGoodsModifyForm）

登録フォームと同一フィールド構成（バリデーション制約は緩い）に加え、以下を含む:

| フィールド名 | 型 | 説明 |
|-----------|-----|------|
| `goodsImage` | `String` | 商品画像（表示用） |
| `caseUnitNum` | `BigDecimal` | ケース入数（表示のみ） |

#### 商品画像表示の仕様

- パス構成: `image/{supplierNo}/{goodsCode}/{goodsCode}_{連番}.jpg`
- 存在チェックしながら連番0から順次読み込む
- 存在しない場合は `image/no_image.png` を表示

#### ボタン（登録画面と同一構成）

| ボタン名 | アクション | 処理 |
|---------|-----------|------|
| 更新 | POST `/wSalesGoodsModifyForm` | `w_sales_goods` のみ更新 |
| 販売商品マスタに反映 | POST `/wSalesGoodsModifyReflect` | `w_sales_goods` 更新後、`m_sales_goods` に反映 |

---

### 4.9 販売商品マスタ一覧画面

**タイトル:** 販売商品マスタ
**URL:** `/salesGoodsMaster`
**テンプレート:** `goods/m_sales_goods.html`
**コントローラー:** `MSalesGoodsController`

#### 検索フォーム（MSalesGoodsForm）

| フィールド名 | 型 | 検索方式 | 説明 |
|-----------|-----|---------|------|
| `shopNo` | `Integer` | 完全一致 | ショップ番号（プルダウン） |
| `goodsName` | `String` | OR/部分一致 | 商品名（スペース区切りOR検索） |
| `goodsCode` | `String` | 後方一致 | 商品コード |
| `keyword` | `String` | AND/部分一致 | キーワード |
| `supplierNo` | `Integer` | 完全一致 | 仕入先（プルダウン、ショップ選択後に動的更新） |

#### 表示項目（一覧テーブル）

| カラム | 説明 |
|--------|------|
| ショップ | `mShop?.shopName` |
| 商品コード | |
| 商品名 | |
| 仕入先 | nullの場合「登録なし」 |
| 標準仕入価格 | 数値フォーマット、nullの場合「未設定」 |
| 標準売価 | 数値フォーマット、nullの場合「未設定」 |
| キーワード | |
| 詳細 | リンク: `/wSalesGoodsModifyForm?shopNo=&goodsNo=`（WORKテーブルの修正画面へ） |

---

### 4.10 得意先商品マスタ一覧画面

**タイトル:** 得意先商品マスタ
**URL:** `/partnerGoodsMaster`
**テンプレート:** `goods/m_partner_goods.html`
**コントローラー:** `MPartnerGoodsController`

#### 検索フォーム（MPartnerGoodsForm）

| フィールド名 | 型 | 検索方式 | 説明 |
|-----------|-----|---------|------|
| `shopNo` | `Integer` | 完全一致 | ショップ番号 |
| `companyNo` | `Integer` | 完全一致 | 会社番号 |
| `partnerCode` | `String` | 後方一致 | 得意先コード（`mCompany.partner.partnerCode` に対してLIKE） |
| `partnerNo` | `Integer` | 完全一致（hidden） | 得意先番号（得意先検索ポップアップで設定） |
| `partnerName` | `String` | — | 得意先名（表示用） |
| `destinationNo` | `Integer` | 完全一致 | 届け先番号（プルダウン）|
| `goodsName` | `String` | OR/部分一致 | 商品名 |
| `goodsCode` | `String` | 後方一致 | 商品コード |
| `keyword` | `String` | AND/部分一致 | キーワード |
| `companyType` | `String` | — | 会社種別（hidden、`"partner"` 固定） |

#### 特記事項

- 得意先番号は得意先検索ポップアップで検索・入力する（JavaScript連携）
- 届け先プルダウンは得意先選択後に動的に更新される（`destinationMap`）
- 検索結果は **最終売上日の降順** でソートして表示

#### 表示項目（一覧テーブル）

| カラム | データソース | 説明 |
|--------|------------|------|
| 商品番号 | `partnerGoods.goodsNo` | |
| ショップ | `partnerGoods.mShop?.shopName` | |
| 得意先 | `partnerGoods.mCompany?.companyName` | |
| 商品コード | `partnerGoods.goodsCode` | |
| 商品名 | `partnerGoods.goodsName` | |
| 売価 | `partnerGoods.goodsPrice` | 数値フォーマット表示 |
| 対応見積 | `partnerGoods.reflectedEstimateNo` | 見積番号が存在する場合「見積明細」リンク表示 |
| 最終売上日 | `partnerGoods.lastSalesDate` | |
| 詳細 | リンク | `/partnerGoodsModifyForm?partnerNo=&destinationNo=&goodsNo=` へ遷移（別タブ） |
| id（非表示） | `goodsNo + ',' + partnerNo + ',' + destinationNo` | Ajax更新で使用するキー |

---

### 4.11 得意先商品詳細・修正画面

**タイトル:** 得意先商品詳細
**URL:** `/partnerGoodsModifyForm`
**テンプレート:** `goods/m_partner_goods_modify_form.html`
**コントローラー:** `PartnerGoodsModifyController`

#### リクエスト仕様

| メソッド | パス | 説明 |
|---------|------|------|
| GET | `/partnerGoodsModifyForm?partnerNo=&destinationNo=&goodsNo=` | 詳細画面初期表示 |
| POST | `/mPartnerGoodsModifyReflect` | 売単価更新 |
| POST | `/partnerGoodsModify` | Ajax による更新・削除 |

#### 初期表示時の処理

1. 複合PK（`partnerNo`, `goodsNo`, `destinationNo`）で `MPartnerGoods` を取得
2. 注文明細（`TOrderDetail`）と返品明細（`TReturnDetail`）の両方を検索して注文履歴リストを作成
3. 注文履歴は注文日の降順でソート
4. 返品分は数量をマイナスで表示

#### 修正フォーム（PartnerGoodsModifyForm）

| フィールド名 | 型 | バリデーション | 説明 |
|-----------|-----|------------|------|
| `shopNo` | `Integer` | なし | ショップ番号（hidden） |
| `companyNo` | `Integer` | なし | 会社番号（hidden） |
| `goodsNo` | `Integer` | なし | 商品番号（hidden） |
| `goodsCode` | `String` | なし | 商品コード（hidden） |
| `keyword` | `String` | なし | キーワード（hidden） |
| `goodsPrice` | `BigDecimal` | なし | 現在の売単価（編集可能） |
| `action` | `String` | なし | Ajax操作種別: `"edit"` or `"delete"` |
| `partnerNo` | `Integer` | なし | 得意先番号 |
| `destinationNo` | `Integer` | なし | 届け先番号 |
| `id` | `String` | なし | Ajax用キー（`goodsNo,partnerNo,destinationNo` のカンマ区切り） |

#### 更新制限事項

- `/mPartnerGoodsModifyReflect` POST: **売単価（`goodsPrice`）のみ更新可能**
- `/partnerGoodsModify` Ajax（action="edit"）: 商品名、キーワード、売単価（正の値のみ）を更新

#### Ajax処理仕様（`/partnerGoodsModify` POST）

**編集（action="edit"）の場合:**
1. `id` フィールドをカンマ分割して `goodsNo`, `partnerNo`, `destinationNo` を取得
2. `Normalizer.normalize(goodsName, Normalizer.Form.NFKC)` で商品名を正規化
3. 同名商品の重複チェック（自分自身を除く）
4. 更新対象: 商品名（`!StringUtil.isEmpty` の場合）、キーワード、売単価（`BigDecimalUtil.isPositive` の場合のみ）

**削除（action="delete"）の場合:**
1. `del_flg = '1'` に更新

#### 注文履歴表示

| カラム | 説明 |
|--------|------|
| 注文日 | `orderHistoryEntity.orderDateTime` |
| 商品コード | |
| 商品名 | |
| 売価 | 数値フォーマット |
| 数量 | 返品の場合はマイナス表示 |

---

### 4.12 販売商品検索ポップアップ画面

**URL:** `/salesGoodsPop`, `/salesGoodsPopSearch`
**テンプレート:** `goods/sales_goods_pop.html`
**コントローラー:** `SalesGoodsPopController`

#### 機能説明

注文入力や見積入力など他の画面から呼び出すポップアップ。`w_sales_goods` と `m_sales_goods` を統合検索して販売商品を選択できる。

#### リクエスト仕様

| メソッド | パス | 説明 |
|---------|------|------|
| POST | `/salesGoodsPop` | ポップアップ初期表示（`ISalesGoodsSearchForm` を受取） |
| POST | `/salesGoodsPopSearch` | 販売商品検索実行 |

#### 検索フォーム（SalesGoodsPopListForm）

| フィールド名 | 型 | 検索方式 | 説明 |
|-----------|-----|---------|------|
| `shopNo` | `Integer` | 完全一致 | ショップ番号 |
| `goodsCode` | `String` | — | 商品コード |
| `goodsName` | `String` | — | 商品名 |

#### 検索ロジック（CommonSalesGoodsService#findSalesGoods）

1. `WSalesGoodsService#find` で `w_sales_goods` を検索
2. `MSalesGoodsService#find` で `m_sales_goods` を検索
3. `w_sales_goods` に存在するものを優先し、`m_sales_goods` のみのものを追記
4. 結果を `CustomSalesGoods` に変換（`caseContainNum` を `MGoods` から取得して追加）

---

## 5. ビジネスロジック

### 5.1 ワーク→マスタ反映フロー

販売商品は必ず「ワークテーブル（`w_sales_goods`）」を経由してから「マスタテーブル（`m_sales_goods`）」に反映される。

#### 新規登録フロー

```
[ユーザー]
  │
  ├─ /wSalesGoodsCreateList → 商品マスタから未登録商品を選択
  │
  ├─ /wSalesGoodsCreateForm (GET) → 選択商品のm_goods情報を引き継ぎ
  │
  ├─ [登録ボタン] POST /wSalesGoodsCreateForm
  │     └─ WSalesGoodsService#insert → w_sales_goodsのみ登録
  │
  └─ [販売商品マスタに反映ボタン] POST /wSalesGoodsCreateReflect
        ├─ WSalesGoodsService#insert → w_sales_goodsに登録
        └─ MSalesGoodsService#save   → m_sales_goodsに登録（存在すればupdate）
```

#### 修正フロー

```
[ユーザー]
  │
  ├─ /salesGoodsWork または /salesGoodsMaster → 一覧から商品を選択
  │
  ├─ /wSalesGoodsModifyForm (GET) → w_sales_goodsの情報を表示
  │
  ├─ [更新ボタン] POST /wSalesGoodsModifyForm
  │     └─ WSalesGoodsService#update → w_sales_goodsのみ更新
  │
  └─ [販売商品マスタに反映ボタン] POST /wSalesGoodsModifyReflect
        ├─ WSalesGoodsService#update → w_sales_goodsを更新
        └─ MSalesGoodsService#save   → m_sales_goodsに反映（存在すればupdate、なければinsert）
```

#### MSalesGoodsService#save のUPSERTロジック

```java
public MSalesGoods save(ISalesGoods iSalesGoods) throws Exception {
    MSalesGoods salesGoods = this.getByPK(iSalesGoods.getShopNo(), iSalesGoods.getGoodsNo());
    if (salesGoods == null) {
        return this.insert(iSalesGoods);  // 存在しない → INSERT
    }
    return this.update(iSalesGoods);  // 存在する → UPDATE
}
```

---

### 5.2 商品名の自動正規化・クリーニング

#### NFKC正規化（商品マスタ・得意先商品）

`GoodsModifyController` および `PartnerGoodsModifyController` の Ajax処理では、商品名を保存前に Unicode NFKC 正規化する:

```java
String goodsName = Normalizer.normalize(goodsModifyForm.getGoodsName(), Normalizer.Form.NFKC);
```

これにより全角英数字→半角、ひらがな←→カタカナなどの表記揺れを統一する。

#### 価格情報の除去（GoodsUtil#removePriceFromName）

`w_sales_goods` および `m_sales_goods` の商品名保存時に自動適用される:

```java
// 商品名に "@" が含まれる場合、"@" 以降を除去
if (goodsName.contains("@")) {
    goodsName = goodsName.substring(0, goodsName.indexOf("@"));
}
```

例: `"商品名@980"` → `"商品名"` に変換される。これは SMILEシステムが商品名に価格を付加する場合があるため、それを自動除去する処理。

**適用箇所:**
- `WSalesGoodsService#insert` / `WSalesGoodsService#update`
- `MGoodsService#insert(MGoods)` / `MGoodsService#update(MGoods)`（バッチ経由の場合）

---

### 5.3 重複チェック

#### 商品マスタの商品名重複チェック

`GoodsModifyController` の Ajax 編集処理で、同名商品の登録を防ぐ:

```java
private boolean isExist(GoodsModifyForm form) {
    List<MGoods> goodsList = this.goodsService.findByGoodsName(form.getGoodsName());
    // 更新前の自身の商品名と被った場合は警告を出さない
    return !CollectionUtil.isEmpty(goodsList)
        && goodsList.stream().anyMatch(goods -> !goods.getGoodsNo().equals(form.getGoodsNo()));
}
```

#### 得意先商品の商品名重複チェック

`PartnerGoodsModifyController` でも同様の処理を実施（`MPartnerGoodsService#findByGoodsName` を使用）。

---

### 5.4 得意先商品価格の更新制限

`PartnerGoodsModifyController#update` において、売単価の更新は `BigDecimalUtil.isPositive` チェックを通過した場合のみ実行する:

```java
if (goodsModifyForm.getGoodsPrice() != null
        && BigDecimalUtil.isPositive(goodsModifyForm.getGoodsPrice())) {
    updateGoods.setGoodsPrice(goodsModifyForm.getGoodsPrice());
}
```

0円以下の価格は無効とみなし、更新しない。

---

### 5.5 年間注文数量のリセット

`MPartnerGoodsService#updateAllClearOrderNumPerYear` により、`m_partner_goods` の全レコードの `order_num_per_year` を0にリセットできる。年次処理として実行される。

---

### 5.6 ケース入数の管理

`CommonSalesGoodsService#getCaseGoodsUnitNum` により、商品番号からケース入数を取得する:

```java
// m_goods_unit から unit = 'CASE' のレコードを検索し、contain_num を返す
// 存在しない場合は BigDecimal.ZERO を返す
```

`w_sales_goods` の詳細画面では、このケース入数を参照情報として表示する（編集不可）。

---

## 6. バッチ処理（商品ファイル取込）

### 6.1 ジョブ概要

| 項目 | 内容 |
|------|------|
| ジョブBean名 | `goodsFileImportJob` |
| ジョブ名（Spring Batch） | `goodsFileImport` |
| 処理方式 | Chunk型（チャンクサイズ: 10） |
| 設定クラス | `jp.co.oda32.batch.goods.config.GoodsFileImportConfig` |

#### 構成コンポーネント

| コンポーネント | クラス | 役割 |
|-------------|-------|------|
| Reader | `GoodsFileReader` | SMILEからのCSVファイル読込 |
| Processor | `GoodsFileProcessor` | 入力データの前処理・フィルタリング |
| Writer | `GoodsFileWriter` | DB書き込み（新規/既存で処理分岐） |

---

### 6.2 入力ファイル形式（GoodsFile）

SMILEシステムから出力される商品マスタCSVファイル。主要フィールド:

| フィールド名（日本語） | 説明 |
|---------------------|------|
| 商品コード | SMILEの商品コード |
| 商品名 | 商品名称 |
| 商品名索引 | キーワード（索引語） |
| 単位 | 取引単位 |
| 入数 | ケース入数 |
| 主仕入先コード / 主仕入先名 | 主要仕入先情報 |
| 標準売上単価 | 標準売価 |
| 標準仕入単価 | 標準仕入原価 |
| メーカーコード / メーカー名 | メーカー情報 |
| ＪＡＮ | JANコード |
| 非課税区分 | 非課税フラグ（1=非課税） |
| 新税分類 | 税区分（0=通常、1=軽減税率） |
| 商品分類コード〜商品分類９名 | カテゴリ情報（9階層） |

---

### 6.3 Processor の前処理ロジック（GoodsFileProcessor）

以下の条件に基づいてデータを加工・フィルタリングする:

| 条件 | 処理 |
|------|------|
| 商品名が空白・null | スキップ（null返却でDB書き込み対象外） |
| 商品名に「休止」を含む | スキップ |
| 商品コードが「99999999」（手打ち商品） | 商品名をUTF-8でMD5ハッシュ化して商品コードを生成 |
| 入数が1未満 | 1を設定 |
| JANコードが8桁・13桁以外 | JANコードをnullに設定 |
| 標準売上単価が0円以下 | nullに設定 |
| 標準仕入単価が0円以下 | nullに設定 |

---

### 6.4 Writer の処理分岐（GoodsFileWriterFactory）

SMILEの商品コードで `m_goods` の既存データを検索し、新規・既存で処理ロジックを切り替える:

- **新規商品（NewGoodsWriterLogic）**: `m_goods` → `m_goods_unit` → `w_sales_goods` の順で INSERT
- **既存商品（ExistGoodsWriterLogic）**: 差分がある場合のみ UPDATE

#### 新規商品の処理順序（NewGoodsWriterLogic#register）

1. メーカー保存（`saveMaker`）: SMILEのメーカー名からメーカーマスタを登録・取得
2. 商品マスタ保存（`saveMGoods`）: `m_goods` にINSERT（税区分も設定）
3. 商品単位保存（`saveMGoodsUnit`）: `m_goods_unit` に「ケース」単位でINSERT
4. 販売商品保存（`saveSalesGoods`）: `w_sales_goods` にINSERT

#### 既存商品の更新チェック項目（ExistGoodsWriterLogic#saveMGoods）

| 項目 | 更新条件 |
|------|---------|
| キーワード | 既存に含まれていない索引語を追記（カンマ区切りで追加） |
| 商品名 | 変更されていた場合（警告ログ出力） |
| SMILE商品名 | 変更されていた場合（警告ログ出力） |
| JANコード | 変更されていた場合（警告ログ出力） |
| メーカー番号 | 変更されていた場合（警告ログ出力） |
| ケース入数 | 変更されていた場合（警告ログ出力） |
| 税区分 | 変更されていた場合（infoログ出力） |

#### 既存商品の販売商品更新（ExistGoodsWriterLogic#saveSalesGoods）

- 仕入単価・売単価・商品名に差分があれば更新（警告ログ出力）
- `m_sales_goods` のみ存在する場合（`isWork = false`）: `w_sales_goods` にINSERT
- `w_sales_goods` が存在する場合（`isWork = true`）: `w_sales_goods` をUPDATE

---

## 7. 画面間の関連と遷移フロー

### 7.1 商品マスタ系の遷移

```
商品マスタ一覧 (/goodsMaster)
  │
  ├─[商品マスタ新規登録]→ 商品マスタ登録 (/goodsCreate)
  │                            └─ 登録成功 → 同画面（商品番号表示）
  │
  └─[がっつり編集]→ 商品マスタ修正 (/goodsModifyForm?goodsNo=)
                        └─ 更新・削除成功 → 同画面
```

### 7.2 販売商品WORK系の遷移

```
販売商品WORK一覧 (/salesGoodsWork)
  │
  ├─[販売商品WORK新規作成]→ 販売商品作成可能リスト (/wSalesGoodsCreateList)
  │                              └─[登録]→ 販売商品WORK登録 (/wSalesGoodsCreateForm?goodsNo=)
  │                                              ├─[登録]→ w_sales_goods登録 → 同画面
  │                                              └─[反映]→ w_sales_goods + m_sales_goods登録 → 同画面
  │
  └─[詳細]→ 販売商品WORK詳細 (/wSalesGoodsModifyForm?shopNo=&goodsNo=)
                ├─[更新]→ w_sales_goods更新 → 同画面
                ├─[反映]→ w_sales_goods更新 + m_sales_goods更新 → 同画面
                └─[商品マスタ修正リンク]→ 商品マスタ修正 (/goodsModifyForm?goodsNo=)
```

### 7.3 販売商品マスタ系の遷移

```
販売商品マスタ一覧 (/salesGoodsMaster)
  │
  └─[詳細]→ 販売商品WORK詳細 (/wSalesGoodsModifyForm?shopNo=&goodsNo=)
              ※ マスタ一覧から開いても、編集はWORKテーブルの修正画面へ遷移
```

### 7.4 得意先商品マスタ系の遷移

```
得意先商品マスタ一覧 (/partnerGoodsMaster)
  │
  ├─[検索]→ 一覧結果（最終売上日降順）
  │
  └─[詳細]→ 得意先商品詳細 (/partnerGoodsModifyForm?partnerNo=&destinationNo=&goodsNo=) [別タブ]
                ├─[対応見積リンク]→ 見積明細 (/estimateDetailList?estimateNo=) [別タブ]
                └─[更新]→ goodsPrice のみ更新 → 同画面
```

### 7.5 ポップアップ連携

```
他画面（注文入力、見積入力など）
  │
  ├─ POST /goodsMasterPop (JANコード渡し) → 商品マスタポップアップ
  │                                          └─[商品選択]→ 呼び出し元画面に商品情報を返す
  │
  └─ POST /salesGoodsPop (ショップ番号・商品コード渡し) → 販売商品検索ポップアップ
                                                             └─[商品選択]→ 呼び出し元画面に返す
```

---

## 8. サービス・リポジトリ一覧

### 8.1 サービスクラス

| クラス名 | パッケージ | 役割 |
|---------|----------|------|
| `MGoodsService` | `domain.service.goods` | 商品マスタ（m_goods）のCRUD操作 |
| `WSalesGoodsService` | `domain.service.goods` | 販売商品ワーク（w_sales_goods）のCRUD操作 |
| `MSalesGoodsService` | `domain.service.goods` | 販売商品マスタ（m_sales_goods）のCRUD + UPSERT |
| `MPartnerGoodsService` | `domain.service.goods` | 得意先商品マスタ（m_partner_goods）のCRUD操作 |
| `MGoodsUnitService` | `domain.service.goods` | 商品単位マスタ（m_goods_unit）のCRUD操作 |
| `CommonSalesGoodsService` | `domain.service.goods` | 販売商品系テーブルの横断的処理（w/m統合検索等） |
| `MPartnerGoodsPriceChangePlanService` | `domain.service.goods` | 得意先商品価格変更予定の管理 |

### 8.2 リポジトリクラス

| クラス名 | パッケージ | 対象テーブル |
|---------|----------|-----------|
| `GoodsRepository` | `domain.repository.goods` | `m_goods` |
| `WSalesGoodsRepository` | `domain.repository.goods` | `w_sales_goods` |
| `MSalesGoodsRepository` | `domain.repository.goods` | `m_sales_goods` |
| `MPartnerGoodsRepository` | `domain.repository.goods` | `m_partner_goods` |
| `GoodsUnitRepository` | `domain.repository.goods` | `m_goods_unit` |
| `MPartnerGoodsPriceChangePlanRepository` | `domain.repository.goods` | `m_partner_goods_price_change_plan` |

### 8.3 Specificationクラス（検索条件定義）

| クラス名 | 対象エンティティ |
|---------|--------------|
| `GoodsSpecification` | `MGoods` |
| `WSalesGoodsSpecification` | `WSalesGoods` |
| `MSalesGoodsSpecification` | `MSalesGoods` |
| `MPartnerGoodsSpecification` | `MPartnerGoods` |
| `MGoodsUnitSpecification` | `MGoodsUnit` |

### 8.4 共通インターフェース

| インターフェース | 実装クラス | 用途 |
|--------------|----------|------|
| `ISalesGoods` | `WSalesGoods`, `MSalesGoods` | 販売商品系テーブルの共通抽象化 |
| `IGoodsForm` | `GoodsMasterForm`, `GoodsModifyForm`, `WSalesGoodsCreateListForm` | 商品マスタ系フォームの共通抽象化 |
| `ISalesGoodsSearchForm` | — | 販売商品検索用フォームの共通インターフェース |

---

*本仕様書は `C:\project\stock-app` のソースコードを解析して作成した。*
