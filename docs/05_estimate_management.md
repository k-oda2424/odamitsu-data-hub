# 05 見積管理（estimate_management）仕様書

## 1. 概要

見積管理は、得意先への価格提示（御見積書の作成・管理）を行う機能である。
得意先商品価格変更予定（`m_partner_goods_price_change_plan`）から自動的に見積を生成するバッチ処理と、担当者が手動で見積を作成・修正するWeb画面の両方を備える。

### 主要機能

- 見積の新規作成・修正・削除（Webから手動操作）
- 得意先価格変更予定からの見積自動生成（バッチ）
- 親子得意先間の見積自動同期
- 通常価格・特値（得意先・届け先別特別価格）の自動反映
- 利益・粗利率のリアルタイム計算
- 御見積書の印刷出力（PDF印刷対応HTML）
- 見積ステータス管理（提出・修正・反映 等）

---

## 2. 画面一覧

| 画面名 | URL | HTTPメソッド | コントローラ | テンプレート |
|--------|-----|------------|------------|------------|
| 見積一覧 | `/estimateList` | GET / POST | `EstimateListController` | `estimate/estimate_list.html` |
| 見積入力（新規・修正） | `/estimateInput` | GET | `EstimateInputController` | `estimate/estimate_input.html` |
| 見積確認 | `/estimateConfirm` | POST | `EstimateInputController` | `estimate/estimate_confirm.html` |
| 見積明細一覧（御見積書） | `/estimateDetailList` | GET | `EstimateDetailListController` | `estimate/estimate_detail_list.html` |
| 商品検索ポップアップ | （見積入力内Ajax） | GET / POST | （共通） | `estimate/estimate_goods_pop.html` |

---

## 3. 各画面の詳細仕様

### 3.1 見積一覧（`/estimateList`）

**概要**
見積の検索・一覧表示を行う画面。ステータスの一括変更（Ajax）が可能。

**URL / HTTPメソッド**
- `GET /estimateList` : 初期表示（フォーム初期値付き）
- `POST /estimateList` : 検索実行

#### 検索条件フォーム（`EstimateListForm`）

| フィールド名 | 型 | バリデーション | 説明 |
|------------|-----|--------------|------|
| `shopNo` | `Integer` | `@NotNull` | ショップ番号（必須） |
| `estimateNo` | `Integer` | - | 見積番号（完全一致） |
| `partnerNo` | `Integer` | - | 得意先番号（hidden） |
| `partnerCode` | `String` | - | 得意先コード（検索UI用） |
| `partnerName` | `String` | - | 得意先名（表示用） |
| `goodsNo` | `Integer` | - | 商品番号 |
| `goodsName` | `String` | - | 商品名（部分一致、スペース区切りでOR検索） |
| `goodsCode` | `String` | - | 商品コード（後方一致） |
| `estimateStatus` | `String[]` | - | 見積ステータス（複数チェックボックス） |
| `estimateDateFrom` | `String` | - | 見積作成日FROM（yyyy/MM/dd） |
| `estimateDateTo` | `String` | - | 見積作成日TO（yyyy/MM/dd） |
| `priceChangeDateFrom` | `String` | - | 価格変更日FROM（yyyy/MM/dd） |
| `priceChangeDateTo` | `String` | - | 価格変更日TO（yyyy/MM/dd） |
| `profitRate` | `BigDecimal` | - | 粗利率（指定値以下の明細を含む見積を抽出） |

**初期値**
- `estimateStatus` : `["00"（作成）, "20"（修正）]` をデフォルト選択

#### 検索結果テーブルカラム

| カラム | 内容 | データソース |
|--------|------|------------|
| 見積番号 | `estimate_no` | `TEstimate.estimateNo` |
| 見積日付 | `estimate_date` | `TEstimate.estimateDate` |
| 価格変更日付 | `price_change_date` | `TEstimate.priceChangeDate` |
| 【コード】得意先 | `【partnerCode】companyName` | `MPartner.partnerCode` + `MCompany.companyName` |
| 見積ステータス | セレクトボックス（Ajax更新） | `TEstimate.estimateStatus` |
| 見積明細リンク | 別タブで見積明細画面を開く | - |

**ソート順**: 見積日付の降順（画面表示時に Java 側でソート）

**Ajax更新機能**
- URL: `POST /updateEstimateStatusForAjax`
- パラメータ: `estimateNo`（見積番号）、`estimateStatus`（更新後ステータスコード）
- 戻り値: 成功時 `1`、失敗時 `0`

---

### 3.2 見積入力（`/estimateInput`）

**概要**
新規見積の作成、または既存見積の修正入力を行う画面。

**URL / HTTPメソッド**
- `GET /estimateInput` : 新規作成（フォーム初期化）
- `GET /modifyEstimate?estimateNo=xxx` : 修正モード（既存データをフォームに展開）
- `GET /deleteEstimate?estimateNo=xxx` : 論理削除（del_flg='1'）
- `POST /estimateInputAddEstimateDetail` : 明細行追加
- `POST /estimateConfirm` : 確認画面へ進む

#### 入力フォーム（`EstimateInputForm`）

**ヘッダ部**

| フィールド名 | 型 | バリデーション | 説明 |
|------------|-----|--------------|------|
| `estimateNo` | `Integer` | - | 見積番号（修正時のみ）、`hidden` |
| `shopNo` | `Integer` | `@NotNull` | ショップ番号（セレクトボックス） |
| `companyNo` | `Integer` | - | 会社番号（自動設定） |
| `destinationNo` | `Integer` | - | 納品先番号（セレクトボックス、得意先選択後に動的ロード） |
| `partnerNo` | `Integer` | - | 得意先番号（hidden、得意先コード入力後にAjax設定） |
| `partnerCode` | `String` | - | 得意先コード（テキスト入力、検索ボタン付き） |
| `partnerName` | `String` | - | 得意先名（表示用、readonly） |
| `estimateDate` | `String` | `@NotNull` | 見積日（yyyy/MM/dd形式、datepicker） |
| `priceChangeDate` | `String` | `@NotNull` | 価格改定日（yyyy/MM/dd形式、datepicker） |
| `note` | `String` | - | 見積要件・備考（テキスト） |
| `estimateStatusCode` | `String` | - | 見積ステータスコード（hidden） |
| `estimateStatusDisplay` | `String` | - | 見積ステータス表示名（readonly） |

**明細部（`estimateDetailList`）**

明細は動的に行追加が可能。各明細行（`EstimateDetailForm`）のフィールド:

| フィールド名 | 型 | 入力可否 | 説明 |
|------------|-----|---------|------|
| `goodsCode` | `String` | 入力（テキスト＋検索ボタン） | 商品コード。フォーカスアウト時にAjaxで商品情報自動取得 |
| `goodsNo` | `Integer` | hidden（自動設定） | 商品番号 |
| `goodsName` | `String` | 表示のみ（自動設定） | 商品名 |
| `purchasePrice` | `BigDecimal` | 表示のみ（自動設定） | 原価（仕入価格） |
| `purchasePriceChangePlan` | `String` | 表示のみ（自動設定） | 原価改訂予定（「YYYY/MM/DDより旧価格→新価格」形式） |
| `goodsPrice` | `BigDecimal` | 入力（数値） | 見積単価（担当者入力） |
| `containNum` | `BigDecimal` | 表示のみ（自動設定） | ケース入数 |
| `profit` | `BigDecimal` | 自動計算・表示 | 粗利（= 見積単価 - 原価） |
| `profitRate` | `BigDecimal` | 自動計算・表示 | 粗利率（%） |
| `caseProfit` | `BigDecimal` | 自動計算・表示 | ケース粗利（= 粗利 × 入数） |
| `detailNote` | `String` | 入力（テキスト） | 明細備考 |
| `displayOrder` | `int` | 入力（数値） | 表示順（初期値: 1） |

**バリデーション（カスタム）**
- ショップ未選択 → 「ショップが設定されていません。」
- 見積日未入力 → 「見積日が設定されていません。」
- 見積日フォーマット不正（`uuuu/MM/dd` 厳密モード） → 「見積日時のフォーマットが間違っています。」
- 価格改定日未入力 → 「価格改定日が設定されていません。」
- 価格改定日フォーマット不正 → 「価格改定日時のフォーマットが間違っています。」
- 得意先未選択 → 「得意先が設定されていません。」

**商品情報Ajax取得**
- URL: `POST /getEstimateGoods`
- パラメータ: `goodsCode`（商品コード）、`shopNo`（ショップ番号）、`partnerNo`（得意先番号）、`destinationNo`（納品先番号）
- 動作: `v_estimate_goods`（通常価格）と `v_estimate_goods_special`（特値）の両方を検索し、適切な価格情報を `EstimateDetailForm` として返却する

**修正モード動作**
- `GET /modifyEstimate?estimateNo=xxx`で既存見積を取得
- 既存明細をフォームに展開
- `estimateStatusCode` を `"20"（修正）` に設定して表示

**削除動作**
- `GET /deleteEstimate?estimateNo=xxx`で実行
- `t_estimate_detail` の `del_flg` を `'1'` に更新（論理削除）
- `t_estimate` の `del_flg` を `'1'` に更新（論理削除）

---

### 3.3 見積確認（`/estimateConfirm`）

**概要**
見積入力内容の最終確認画面。ヘッダ情報・明細を読み取り専用で表示し、登録またはキャンセル（戻る）を選択できる。

**URL / HTTPメソッド**
- `POST /estimateConfirm` : 入力画面からの遷移
- `POST /backEstimateInputForm` : 入力画面へ戻る

#### 確認フォーム（`EstimateConfirmForm`）

| フィールド名 | 説明 |
|------------|------|
| `estimateNo` | 見積番号（hidden） |
| `shopNo` | ショップ番号（hidden） |
| `shopName` | ショップ名（表示用） |
| `destinationNo` | 納品先番号（hidden） |
| `destinationName` | 納品先名（表示用） |
| `partnerNo` | 得意先番号（hidden） |
| `partnerCode` | 得意先コード（表示用） |
| `partnerName` | 得意先名（表示用） |
| `estimateDate` | 見積日（hidden + 表示） |
| `priceChangeDate` | 価格改定日（hidden + 表示） |
| `note` | 備考（hidden + 表示） |
| `estimateStatusCode` | 見積ステータスコード（hidden） |
| `estimateDetailList` | 明細リスト（全フィールドhidden + 表示） |

**明細フィルタリング**
確認フォームへの遷移時、以下の条件を満たす明細行を自動除去する:
- `goodsCode` が空の行
- `goodsPrice`（見積単価）が 0 または null の行

**表示カラム**: 商品コード、商品名、原価、原価改訂予定、見積単価、入数、粗利、粗利率、ケース粗利、備考

**アクション**
- 「見積登録」ボタン → `POST /estimateCreate` でDB登録処理へ
- 「戻る」ボタン → `POST /backEstimateInputForm` で入力画面へ戻る

---

### 3.4 見積明細一覧（御見積書）（`/estimateDetailList`）

**概要**
単一見積の御見積書形式での表示画面。印刷出力用として使用される。

**URL / HTTPメソッド**
- `GET /estimateDetailList?estimateNo=xxx` : 指定の見積を表示

#### 表示内容

**ヘッダ部**
- 見積日時 (`estimate_date`)
- 見積書番号 (`estimate_no`)
- 得意先名 (`company.companyName`) + 「御中」
- 小田光 株式会社（発行元固定情報）
- 本店営業部、住所、TEL、FAX（固定情報）
- 見積担当者（ログインユーザー名）
- 納品場所（`destinationNo` が設定されている場合：納品先名・住所・TEL）
- 有効期限（`price_change_date` == `estimate_date` の場合「御見積日より1ヵ月」、異なる場合「YYYY-MM-DD 納品分より」）
- 見積要件・備考（`note` が設定されている場合のみ表示）

**明細テーブルカラム**

| カラム | 内容 |
|--------|------|
| コード | `goods_code`（商品コード） |
| 商品名 | `goods_name` |
| 単価 | `goods_price`（税込フラグに応じて税込変換済み） |
| 入数 | `contain_num` |
| ケース価格 | `contain_num × goods_price`（計算値） |
| 備考 | `detail_note` |

**明細のソート順**: `display_order` 昇順、同値の場合は `goods_code` 昇順

**税込表示フラグ**
- `TEstimate.isIncludeTaxDisplay` が `true` の場合、単価は税込計算済みで表示
- 税率は `MTaxRate.taxRate`（標準税率）または `MTaxRate.reducedTaxRate`（軽減税率）を商品の `applyReducedTaxRate` フラグで選択
- 計算式: `goodsPrice × (1 + taxRate / 100)`、小数点以下切り捨て（`RoundingMode.DOWN`）
- 消費税表示: 税込の場合「上記価格は税込です。」、税抜の場合「上記価格に消費税は含まれておりません。」

**アクション（印刷前）**
- 「このページを印刷する」→ `GET /notifiedEstimate?estimateNo=xxx` を呼び出してステータスを「提出済」に更新後、印刷ダイアログを開く
- 「見積修正」→ `GET /modifyEstimate?estimateNo=xxx`（見積入力画面へ）
- 「見積削除」→ `GET /deleteEstimate?estimateNo=xxx`（論理削除）
- ※削除済み（`del_flg='1'`）の見積ではこれらボタンを非表示

---

## 4. エンティティ定義

### 4.1 見積ヘッダテーブル（`t_estimate`）

**Entityクラス**: `jp.co.oda32.domain.model.estimate.TEstimate`

| カラム名 | Javaフィールド | 型 | 説明 |
|---------|-------------|-----|------|
| `estimate_no` | `estimateNo` | `Integer` | 見積番号（PK、シーケンス自動採番: `t_estimate_estimate_no_seq`） |
| `estimate_date` | `estimateDate` | `LocalDate` | 見積作成日 |
| `price_change_date` | `priceChangeDate` | `LocalDate` | 価格変更日（有効期限の基準日） |
| `shop_no` | `shopNo` | `Integer` | ショップ番号 |
| `partner_no` | `partnerNo` | `Integer` | 得意先番号（`m_partner.partner_no`参照） |
| `estimate_status` | `estimateStatus` | `String` | 見積ステータスコード（詳細は「ステータス管理」参照） |
| `destination_no` | `destinationNo` | `Integer` | 納品先番号（`m_delivery_destination.destination_no`参照。未設定時は `0`） |
| `company_no` | `companyNo` | `Integer` | 会社番号（`m_company.company_no`参照） |
| `note` | `note` | `String` | 見積要件・備考 |
| `is_include_tax_display` | `isIncludeTaxDisplay` | `boolean` | 税込表示フラグ（得意先マスタの設定値を使用） |
| `del_flg` | `delFlg` | `String` | 削除フラグ（'0'=有効、'1'=削除） |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録者番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新者番号 |

**リレーション**
- `tEstimateDetailList` → `TEstimateDetail`（1対多、`estimate_no`結合）
- `company` → `MCompany`（1対1、`company_no`結合）
- `mPartner` → `MPartner`（1対1、`partner_no`結合）
- `mDeliveryDestination` → `MDeliveryDestination`（1対1、`destination_no`結合）

**備考**
- `getTEstimateDetailList()` は `del_flg='0'` の明細のみを返す（論理削除された明細を除外）

---

### 4.2 見積明細テーブル（`t_estimate_detail`）

**Entityクラス**: `jp.co.oda32.domain.model.estimate.TEstimateDetail`
**複合PK**: `TEstimateDetailPK`（`estimate_no` + `estimate_detail_no`）

| カラム名 | Javaフィールド | 型 | 説明 |
|---------|-------------|-----|------|
| `estimate_no` | `estimateNo` | `Integer` | 見積番号（PK、`t_estimate.estimate_no`参照） |
| `estimate_detail_no` | `estimateDetailNo` | `Integer` | 見積明細番号（PK、1から連番） |
| `shop_no` | `shopNo` | `Integer` | ショップ番号 |
| `company_no` | `companyNo` | `Integer` | 会社番号 |
| `goods_no` | `goodsNo` | `Integer` | 商品番号（`m_goods.goods_no`参照） |
| `goods_code` | `goodsCode` | `String` | 商品コード |
| `goods_price` | `goodsPrice` | `BigDecimal` | 見積単価（担当者が設定する売価） |
| `contain_num` | `containNum` | `BigDecimal` | ケース入数（`m_goods.case_contain_num`より取得） |
| `change_contain_num` | `changeContainNum` | `BigDecimal` | 変更入数（入数変更がある場合のみ設定、ない場合 null） |
| `estimate_case_num` | `estimateCaseNum` | `BigDecimal` | 見積ケース数 |
| `estimate_num` | `estimateNum` | `BigDecimal` | 見積数量 |
| `goods_name` | `goodsName` | `String` | 商品名 |
| `specification` | `specification` | `String` | 仕様（`m_goods.specification`より取得） |
| `detail_note` | `detailNote` | `String` | 明細備考 |
| `profit_rate` | `profitRate` | `BigDecimal` | 粗利率（%） |
| `purchase_price` | `purchasePrice` | `BigDecimal` | 原価（仕入価格） |
| `display_order` | `displayOrder` | `int` | 表示順 |
| `partner_goods_reflect` | `partnerGoodsReflect` | `boolean` | 得意先商品価格反映済みフラグ |
| `del_flg` | `delFlg` | `String` | 削除フラグ（'0'=有効、'1'=削除） |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録者番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新者番号 |

**リレーション**
- `tEstimate` → `TEstimate`（1対1、`estimate_no`結合）
- `mGoods` → `MGoods`（1対1、`goods_no`結合）
- `company` → `MCompany`（1対1、`company_no`結合）

**削除方式の二種類**
- **物理削除**: `deleteByEstimateNo(estimateNo)` → 見積修正時の明細再登録に使用
- **論理削除**: `deleteByDelFlg(estimateNo)` → 見積削除時に `del_flg='1'` に更新

---

### 4.3 見積商品情報収集ビュー（`v_estimate_goods`）

**Entityクラス**: `jp.co.oda32.domain.model.estimate.VEstimateGoods`
**用途**: 通常価格の見積商品情報を取得するDBビュー

**複合PK**: `VEstimateGoodsPK`（`shop_no` + `goods_no`）

| カラム名 | Javaフィールド | 型 | 説明 |
|---------|-------------|-----|------|
| `shop_no` | `shopNo` | `Integer` | ショップ番号（PK） |
| `goods_no` | `goodsNo` | `Integer` | 商品番号（PK） |
| `goods_code` | `goodsCode` | `String` | 商品コード |
| `case_contain_num` | `caseContainNum` | `BigDecimal` | ケース入数（商品マスタの標準入数） |
| `purchase_price` | `purchasePrice` | `BigDecimal` | 現在の仕入価格（原価） |
| `company_no` | `companyNo` | `Integer` | 会社番号 |
| `now_goods_price` | `nowGoodsPrice` | `BigDecimal` | 現在の販売単価 |
| `change_plan_date` | `changePlanDate` | `LocalDate` | 価格変更予定日 |
| `before_price` | `beforePrice` | `BigDecimal` | 価格変更前の単価 |
| `after_price` | `afterPrice` | `BigDecimal` | 価格変更後の単価 |
| `goods_name` | `goodsName` | `String` | 商品名 |
| `change_contain_num` | `changeContainNum` | `BigDecimal` | 変更後の入数（入数変更がある場合のみ） |

---

### 4.4 特値見積商品情報収集ビュー（`v_estimate_goods_special`）

**Entityクラス**: `jp.co.oda32.domain.model.estimate.VEstimateGoodsSpecial`
**用途**: 得意先・届け先別の特別価格（特値）情報を取得するDBビュー

**複合PK**: `VEstimateGoodsSpecialPK`（`shop_no` + `goods_no` + `partner_no` + `destination_no`）

| カラム名 | Javaフィールド | 型 | 説明 |
|---------|-------------|-----|------|
| `shop_no` | `shopNo` | `Integer` | ショップ番号（PK） |
| `goods_no` | `goodsNo` | `Integer` | 商品番号（PK） |
| `partner_no` | `partnerNo` | `Integer` | 得意先番号（PK） |
| `destination_no` | `destinationNo` | `Integer` | 納品先番号（PK） |
| `goods_code` | `goodsCode` | `String` | 商品コード |
| `case_contain_num` | `caseContainNum` | `BigDecimal` | ケース入数 |
| `purchase_price` | `purchasePrice` | `BigDecimal` | 仕入価格 |
| `company_no` | `companyNo` | `Integer` | 会社番号 |
| `now_goods_price` | `nowGoodsPrice` | `BigDecimal` | 現在の特値単価 |
| `change_plan_date` | `changePlanDate` | `LocalDate` | 特値変更予定日 |
| `before_price` | `beforePrice` | `BigDecimal` | 変更前の特値単価 |
| `after_price` | `afterPrice` | `BigDecimal` | 変更後の特値単価 |
| `goods_name` | `goodsName` | `String` | 商品名 |
| `change_contain_num` | `changeContainNum` | `BigDecimal` | 変更後の入数 |

---

## 5. ビジネスロジック

### 5.1 利益・粗利率の計算

商品コードが入力され、見積単価（`goodsPrice`）が設定された時点でリアルタイム計算される。

```
粗利（profit） = 見積単価（goodsPrice） - 原価（purchasePrice）

粗利率（profitRate）[%] = (1 - 原価 / 見積単価) × 100
    ※ スケール: 小数点以下3桁、四捨五入（HALF_UP）

ケース粗利（caseProfit） = 粗利 × 入数（containNum or changeContainNum）
```

**実装箇所**: `EstimateUtil.fillEstimateGoods()` 内

---

### 5.2 特別価格（v_estimate_goods_special）の適用ロジック

商品コード入力時に `v_estimate_goods`（通常価格）と `v_estimate_goods_special`（特値）の両方を検索し、以下のロジックで適用する価格を決定する。

```
1. 通常価格のみ存在する場合: 通常価格を使用
2. 特値のみ存在する場合:    特値を通常価格として使用
3. 両方存在する場合:
   - 特値に changePlanDate が設定されており、かつ
     通常価格の changePlanDate が null である、
     または特値の changePlanDate >= 通常価格の changePlanDate
     → 特値を使用（過去の特値取得を防ぐ条件）
   - 上記以外: 通常価格を使用
4. 両方存在しない場合: 「商品情報が取得できませんでした。」を detailNote に設定
```

**実装箇所**: `EstimateUtil.fillEstimateGoods()`

---

### 5.3 入数（ケース入数）の選択ロジック

```
1. changeContainNum（変更入数）が設定されている場合: changeContainNum を使用
2. caseContainNum（商品マスタの標準入数）が設定されている場合: caseContainNum を使用
3. それ以外: BigDecimal.ONE（1）を使用
```

入数変更がある場合、`detailNote` に「入数変更 旧入数→新入数」の旨を自動付記する。

---

### 5.4 原価改訂予定情報の表示

`changePlanDate`（価格変更予定日）が設定されている場合、以下の形式で `purchasePriceChangePlan` に設定される:

```
"YYYY/MM/DDより beforePrice→afterPrice"
```

**例**: `"2024/04/01より 100.00→120.00"`

---

### 5.5 親子見積の自動同期

得意先マスタ（`m_partner`）の `parent_partner_no` により親子関係が定義される。
見積登録・修正時に、親子見積が自動的に同期される。

#### パターン1: 子得意先の見積を作成・修正した場合

```
1. 親得意先の同じ価格変更日、提出前（CREATE/MODIFIED）の見積を検索
2. 検索結果の見積の明細を物理削除
3. 子見積の明細をコピーして親見積の明細として登録
4. 親見積の estimate_date, price_change_date を更新
5. 親見積の兄弟（他の子見積）の同じ価格変更日・提出前の見積を検索し、
   それらも同様に明細を更新し、ステータスを「他同グループ提出済み（40）」に変更
```

#### パターン2: 親得意先の見積を作成・修正した場合

```
1. 子得意先リストを取得（findChildrenPartner）
2. 各子得意先の同じ価格変更日・提出前の見積を検索
3. 各子見積の明細を物理削除
4. 親見積の明細をコピーして子見積の明細として登録
5. 子見積のステータスを「他同グループ提出済み（40）」に変更
6. 子見積が存在しない場合は新規作成
```

**見積明細番号の再編成**: コピー後、`goods_code` の昇順でソートして `1` から連番を振り直す。

**実装箇所**: `EstimateCreateController.updateParentEstimate()`, `updateChildrenEstimate()`, `createParentEstimate()`, `createChildrenEstimate()`

---

### 5.6 ステータス管理（全ステータスコード）

`EstimateStatus` 列挙型で管理される。

| コード | 定数名 | 表示名 | 説明 |
|--------|--------|--------|------|
| `00` | `CREATE` | 作成 | バッチ自動生成または手動新規作成時の初期ステータス |
| `10` | `NOTIFIED` | 提出済 | 「このページを印刷する」ボタンで初回提出（印刷）した状態 |
| `20` | `MODIFIED` | 修正 | 提出済み見積を修正した場合のステータス |
| `30` | `M_NOTIFIED` | 修正後提出済 | 修正（20）または修正後提出済（30）の見積を印刷した状態 |
| `40` | `OTHER_PARTNER_NOTIFIED` | 他同グループ提出済 | 同グループの他得意先（主に親得意先）が提出済みとなった場合に自動設定 |
| `50` | `DELETE` | 削除 | 論理削除された見積 |
| `60` | `EACH_TIME` | 都度見積のため不要 | 都度見積に該当し管理不要と判断した場合 |
| `70` | `PRICE_REFLECT` | 価格反映済 | 見積内容が得意先商品価格に反映された状態 |
| `90` | `BID` | 入札関係のため不要 | 入札案件に該当し管理不要と判断した場合 |
| `99` | `NOT_DEAL` | 取引なし | 取引が成立しなかった見積 |

**ステータス分類**
- 提出前（`getBeforeNotifiedStatusCodeList()`）: `["00"（作成）, "20"（修正）]`
- 提出済（`getNotifiedStatusCodeList()`）: `["10"（提出済）, "30"（修正後提出済）, "40"（他同グループ提出済）]`

**提出（印刷）時のステータス遷移ロジック**

```
現在ステータスが「修正（20）」または「修正後提出済（30）」の場合
    → 「修正後提出済（30）」に更新
それ以外
    → 「提出済（10）」に更新
```

**実装箇所**: `EstimateUtil.getNotifiedStatus()`

---

## 6. バッチ処理

### 6.1 得意先価格改定予定作成バッチ（`partnerPriceChangePlanCreate`）

**クラス**: `PartnerPriceChangePlanCreateBatch`

バッチは以下の5ステップを順番に実行する:

```
Step 1: partnerPriceChangePlanCreateStep
    → PartnerPriceChangePlanCreateTasklet
    → 仕入価格変更予定から得意先商品価格変更予定を作成

Step 2: parentPartnerPriceChangePlanCreateStep
    → ParentPartnerPriceChangePlanCreateTasklet
    → 子得意先の価格変更予定から親得意先の価格変更予定を自動作成

Step 3: priceChangeToEstimateCreateStep
    → PriceChangeToEstimateCreateTasklet
    → 価格変更予定から見積を自動生成（本ドキュメントの主要処理）

Step 4: parentEstimateCreatedStep
    → ParentEstimateCreatedTasklet
    → 親見積が作成された子見積のステータスを「他同グループ提出済（40）」に更新

Step 5: partnerPriceChangeReflectStep
    → PartnerPriceChangeReflectTasklet
    → 価格変更を得意先商品マスタへ反映
```

---

### 6.2 価格変更予定から見積自動生成（`PriceChangeToEstimateCreateTasklet`）

**クラス**: `jp.co.oda32.batch.estimate.PriceChangeToEstimateCreateTasklet`

**処理フロー**

```
1. 全ショップを取得してループ
2. 各ショップで「見積未作成（estimate_created=false）」の得意先商品価格変更予定を取得
3. 得意先番号でグルーピング
4. 各得意先について:
   a. 価格変更日 + 納品先番号でグルーピング
   b. 各グループで見積を作成または更新:
      - 同一条件（ショップ・得意先・価格変更日）の既存見積が「作成（00）・修正（20）・入札（90）・取引なし（99）」ステータスで存在する場合
        → 既存見積を使用し、ステータスを「修正（20）」に変更
      - 存在しない場合
        → 新規見積を「作成（00）」ステータスで作成
        → estimate_date には当日日付を設定（提出（印刷）時に修正する前提）
   c. 見積明細の生成:
      - 既存明細を一旦全て物理削除
      - 価格変更予定から新規明細を生成
      - 既存明細がある場合は今回追加分と重複しないようマージ
      - 赤字見積（deficit_flg=true）の場合、detailNote に「【赤字です】価格修正してください。」を付記
      - detailNote に「現単価XXX円」を付記
   d. 価格変更予定の estimate_created を true に更新し、estimate_no と estimate_detail_no を設定
5. 全見積明細の商品情報を商品マスタから一括更新（contain_num, specification が null の行を更新）
```

**実装箇所**: `PriceChangeToEstimateCreateTasklet.execute()`, `createEstimate()`, `createEstimateDetail()`

---

### 6.3 子見積の「他同グループ提出済」更新（`ParentEstimateCreatedTasklet`）

**クラス**: `jp.co.oda32.batch.estimate.ParentEstimateCreatedTasklet`

**処理内容**

`m_partner_goods_price_change_plan` の親子関係を利用して、親見積が作成済み（`estimate_created=true`）の子見積のうち、ステータスが「作成（00）」または「修正（20）」のものを取得し、ステータスを「他同グループ提出済（40）」に一括更新する。

**検索SQL（ネイティブクエリ）**
```sql
WITH price_change_plan AS (
    SELECT ppc.estimate_no
    FROM m_partner_goods_price_change_plan ppc
    WHERE parent_change_plan_no IS NOT NULL
      AND EXISTS (
          SELECT 'X'
          FROM m_partner_goods_price_change_plan parent_ppc
          WHERE ppc.parent_change_plan_no = parent_ppc.partner_goods_price_change_plan_no
            AND parent_ppc.estimate_created
      )
)
SELECT e.*
FROM t_estimate e
JOIN price_change_plan pcp ON e.estimate_no = pcp.estimate_no
WHERE e.estimate_status IN ('00', '20')
```

---

### 6.4 親得意先価格変更予定作成（`ParentPartnerPriceChangePlanCreateTasklet`）

**クラス**: `jp.co.oda32.batch.estimate.ParentPartnerPriceChangePlanCreateTasklet`

**処理内容**
- 親得意先設定がある子得意先（`m_partner.parent_partner_no` が設定されている）の価格変更予定から、親得意先の価格変更予定を自動生成する
- 同一商品に複数子得意先の変更予定がある場合、`after_price` が最大かつ `change_plan_date` が最新のものを採用する
- 金額が相違する場合は子得意先の変更予定も親得意先の価格に統一し、関連する見積明細も更新する

**見積の自動更新**
- `estimate_no` が設定済みの価格変更予定について、見積明細の `goods_price` と見積ヘッダの `price_change_date` を最新の変更予定値に更新する

---

## 7. データアクセス（Repository / Specification）

### 7.1 TEstimateRepository

```java
// 子見積を検索（ネイティブクエリ）
List<TEstimate> findChildrenEstimate();
```

### 7.2 TEstimateDetailRepository

```java
// 見積番号で明細を取得
List<TEstimateDetail> findByEstimateNo(Integer estimateNo);

// 見積番号+明細番号で1件取得
TEstimateDetail getByEstimateNoAndEstimateDetailNo(Integer estimateNo, Integer estimateDetailNo);

// 商品情報の一括更新（contain_num, specification が null の行を商品マスタから補完）
int updateGoodsInfo();

// 論理削除（del_flg='1'に更新）
int deleteByDelFlg(int estimateNo);

// 物理削除
int deleteByEstimateNo(int estimateNo);
```

### 7.3 TEstimateSpecification（検索条件）

| メソッド名 | 検索条件 |
|-----------|---------|
| `shopNoContains` | `shop_no` 完全一致 |
| `estimateNoContains` | `estimate_no` 完全一致 |
| `companyNoContains` | `company_no` 完全一致 |
| `partnerNoContains` | `partner_no` 完全一致 |
| `goodsNamesContains` | 見積明細の `goods_name` 部分一致（スペース区切りでOR） |
| `goodsCodeContains` | 見積明細の `goods_code` 後方一致 |
| `profitRateContains` | 見積明細の `profit_rate` 以下 |
| `estimateStatusContains` | `estimate_status` 完全一致 |
| `estimateStatusListContains` | `estimate_status` IN句 |
| `estimateDateContains` | `estimate_date` 範囲（FROM/TO） |
| `priceChangeDateRangeContains` | `price_change_date` 範囲（FROM/TO） |
| `priceChangeDateContains` | `price_change_date` 完全一致 |
| `priceChangeDatePastContains` | `price_change_date` <= referenceDate（過去の価格変更日） |

---

## 8. 画面遷移フロー

```
【手動操作フロー】

見積一覧
(/estimateList)
    |
    |--- [見積明細リンク] ----------→ 見積明細一覧（御見積書）
    |                                (/estimateDetailList?estimateNo=xxx)
    |                                    |
    |                                    |--- [このページを印刷する]
    |                                    |        → /notifiedEstimate?estimateNo=xxx
    |                                    |        → ステータス更新後、同画面で印刷
    |                                    |
    |                                    |--- [見積修正]
    |                                    |        → /modifyEstimate?estimateNo=xxx
    |                                    |        → 見積入力画面（修正モード）
    |                                    |
    |                                    |--- [見積削除]
    |                                             → /deleteEstimate?estimateNo=xxx
    |                                             → 論理削除後、見積入力画面へ
    |
    |--- [見積ステータスを変更（Ajax）]
    |        → /updateEstimateStatusForAjax (POST)
    |        → 同一画面のままステータス更新

見積入力（新規）
(/estimateInput)
    |
    |--- [商品コード入力→フォーカスアウト]
    |        → /getEstimateGoods (POST, Ajax)
    |        → 商品情報を自動取得・表示
    |
    |--- [行追加ボタン]
    |        → /estimateInputAddEstimateDetail (POST)
    |        → 同画面に空行を追加
    |
    |--- [見積確認画面ボタン]
             → /estimateConfirm (POST)
             → バリデーション後、見積確認画面へ遷移
                    |
                    |--- [戻るボタン]
                    |        → /backEstimateInputForm (POST)
                    |        → 見積入力画面へ
                    |
                    |--- [見積登録ボタン]
                             → /estimateCreate (POST)
                             → DB登録処理 + 親子同期処理
                             → 登録完了後、見積明細一覧（御見積書）へ遷移

【バッチ自動処理フロー】

m_partner_goods_price_change_plan
（得意先商品価格変更予定）
    |
    [partnerPriceChangePlanCreate バッチ]
    |
    Step1: 仕入価格変更予定から
           得意先商品価格変更予定を生成
    |
    Step2: 子得意先の変更予定から
           親得意先の変更予定を自動生成
    |
    Step3: 価格変更予定から
           t_estimate / t_estimate_detail を自動生成
           ステータス: 作成（00）または修正（20）
    |
    Step4: 親見積が作成済みの子見積を
           「他同グループ提出済（40）」に更新
    |
    Step5: 価格変更を得意先商品マスタへ反映
```

---

## 9. 補足情報

### 9.1 税込表示の判定

`TEstimate.isIncludeTaxDisplay` の値は、得意先マスタ（`m_partner.is_include_tax_display`）の設定値が見積作成時に自動的にコピーされる。見積明細一覧（御見積書）表示時に、このフラグに基づいて単価の税込変換を行う。

### 9.2 有効期限の表示ロジック

御見積書における有効期限の表示は以下のルールで決定される:
- `price_change_date == estimate_date`（価格変更日が見積日と同じ）→ 「御見積日より1ヵ月」
- それ以外 → 「{price_change_date} 納品分より」

### 9.3 見積明細番号の採番ルール

見積明細の `estimate_detail_no` は、見積修正・親子同期時に以下のルールで再採番される:
1. `estimate_no` ごとにグルーピング
2. `goods_code` の昇順でソート
3. `1` から連番を振り直す

手動入力での新規登録時は、登録フォームの順序で `1` から連番となる。

### 9.4 赤字見積の扱い

バッチによる見積自動生成時、`m_partner_goods_price_change_plan.deficit_flg = true` の場合、見積明細の `detail_note` に以下の文字列が自動付記される:

```
【赤字です】価格修正してください。現単価XXX円
```

担当者はこの見積を確認し、見積単価を修正して提出する必要がある。

### 9.5 関連マスタ

| マスタテーブル | 用途 |
|-------------|------|
| `m_partner` | 得意先情報、親子関係（`parent_partner_no`）、税込表示フラグ（`is_include_tax_display`） |
| `m_company` | 会社情報（得意先に紐づく会社） |
| `m_delivery_destination` | 納品先情報 |
| `m_goods` | 商品情報（商品コード、商品名、ケース入数、仕様、軽減税率フラグ） |
| `m_shop` | ショップ情報 |
| `m_tax_rate` | 税率情報（標準税率、軽減税率） |
| `m_partner_goods_price_change_plan` | 得意先商品価格変更予定（バッチ自動生成の起点） |
