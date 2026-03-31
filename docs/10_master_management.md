# マスタ管理 機能仕様書

## 目次

1. [概要](#概要)
2. [画面一覧](#画面一覧)
3. [メーカーマスタ（MMaker）](#メーカーマスタ)
4. [倉庫マスタ（MWarehouse）](#倉庫マスタ)
5. [会社マスタ（MCompany）](#会社マスタ)
6. [仕入先マスタ（MSupplier）](#仕入先マスタ)
7. [得意先（取引先）マスタ（MPartner）](#得意先取引先マスタ)
8. [店舗マスタ（MShop）](#店舗マスタ)
9. [仕入先店舗マッピング（MSupplierShopMapping）](#仕入先店舗マッピング)
10. [ログインユーザーマスタ（MLoginUser）](#ログインユーザーマスタ)
11. [補助マスタ](#補助マスタ)
12. [定数定義](#定数定義)

---

## 概要

マスタ管理機能は、株式会社小田光の社内システム（stock-app）において、業務上の基幹データを一元管理する機能群である。在庫管理・発注・売上・財務連携の各ドメインで参照されるマスタデータを登録・更新・検索・論理削除する画面群から構成される。

### 共通設計方針

- **論理削除**: 全マスタテーブルに `del_flg` カラムを持ち、`'0'` = 有効、`'1'` = 削除済み。物理削除は行わない。
- **監査フィールド**: 全テーブルに `add_date_time`、`add_user_no`、`modify_date_time`、`modify_user_no` を持つ。
- **シーケンス採番**: 主キーはPostgreSQLシーケンスで自動採番する（`IDENTITY` 戦略）。
- **マルチショップ**: 多くのマスタが `shop_no` または `company_no` によって店舗・会社単位のデータ分離を行う。
- **認証**: Spring Security による認証済みセッションが必要。全マスタ画面はログイン後のみアクセス可能。

---

## 画面一覧

| 画面名 | URL | HTTPメソッド | テンプレートファイル | コントローラクラス |
|--------|-----|-------------|---------------------|------------------|
| メーカー一覧・検索 | `/makerMaster` | GET / POST | `master/maker_master.html` | `MakerMasterController` |
| メーカー新規登録 | `/makerCreate` | GET / POST | `master/maker_create.html` | `MakerCreateController` |
| メーカー更新・削除 | `/makerModify` | GET / POST | `master/maker_modify.html` | `MakerModifyController` |
| メーカーポップアップ検索 | `/makerMasterPop` | GET / POST | `master/maker_master_pop.html` | `MakerMasterPopController` |
| 倉庫一覧・検索 | `/warehouseMaster` | GET / POST | `master/warehouse_master.html` | `WarehouseMasterController` |
| 倉庫新規登録 | `/warehouseCreate` | GET / POST | `master/warehouse_create.html` | `WarehouseCreateController` |
| 倉庫更新・削除 | `/warehouseModify` | GET / POST | `master/warehouse_modify.html` | `WarehouseModifyController` |
| 会社ポップアップ検索 | `/companyMasterPop` (POST) / `/companyMasterPopSearch` (POST) | POST | `master/company_master_pop.html` | `CompanyMasterPopController` |
| 仕入先ポップアップ検索 | `/supplierMasterPop` (POST) / `/supplierMasterPopSearch` (POST) | POST | `master/supplier_master_pop.html` | `SupplierMasterPopController` |
| 仕入先店舗マッピング一覧・登録 | `/master/supplierShopMapping` | GET / POST | `master/supplierShopMapping/list.html` | `SupplierShopMappingController` |
| 仕入先店舗マッピング削除 | `/master/supplierShopMapping/{mappingId}/delete` | POST | （リダイレクト） | `SupplierShopMappingController` |
| 仕入先店舗マッピング CSVインポート画面 | `/master/supplierShopMapping/import` | GET | `master/supplierShopMapping/import.html` | `SupplierShopMappingController` |
| 仕入先店舗マッピング CSVインポート処理 | `/master/supplierShopMapping/import` | POST | （リダイレクト） | `SupplierShopMappingController` |
| 仕入先店舗マッピング CSVテンプレートDL | `/master/supplierShopMapping/template` | GET | （CSVダウンロード） | `SupplierShopMappingController` |

---

## メーカーマスタ

### 概要

商品に紐付くメーカー情報を管理するマスタ。同一メーカー名の重複登録を防止する。メーカー番号は全ショップ共通（shop_noは常にnullを返す実装）。

### テーブル定義

**テーブル名**: `m_maker`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `maker_no` | `makerNo` | `Integer` | メーカー番号（主キー、シーケンス自動採番） |
| `maker_code` | `makerCode` | `String` | メーカーコード |
| `shop_no` | `shopNo` | `Integer` | 店舗番号（※`getShopNo()`は常にnullを返す） |
| `maker_name` | `makerName` | `String` | メーカー名 |
| `del_flg` | `delFlg` | `String` | 削除フラグ（`'0'`=有効、`'1'`=削除） |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |

**シーケンス**: `m_maker_maker_no_seq`

**Entityクラス**: `jp.co.oda32.domain.model.master.MMaker`

### メーカー一覧・検索画面

- **URL**: `/makerMaster`
- **GET**: 検索フォームを空で表示する。一覧は表示しない。
- **POST**: メーカー名による検索を実行し、結果を一覧表示する。

#### 検索フォーム（`MakerMasterForm`）

| フィールド名 | 型 | 説明 | バリデーション |
|------------|-----|------|--------------|
| `makerNo` | `Integer` | メーカー番号 | なし |
| `makerName` | `String` | メーカー名（部分一致） | なし |

#### 検索仕様

- メーカー名が空の場合: `del_flg='0'` の全件を取得する（`findAll()`）。
- メーカー名が指定された場合: JPA Specificationによる部分一致検索を行い、`del_flg='0'` のものを返す。
- 一覧表示項目: メーカー番号（No.）、メーカー名。
- DataTables（`makerMasterTable`）によるクライアントサイドのソート・ページングに対応。

#### 画面遷移

- 「メーカーマスタ新規登録」ボタン: `/makerCreate` に遷移。

---

### メーカー新規登録画面

- **URL**: `/makerCreate`
- **GET**: 空の登録フォームを表示。メーカー番号フィールドは読み取り専用（採番前は空）。
- **POST**: バリデーション後にメーカーを新規登録する。

#### 登録フォーム（`MakerCreateForm`）

| フィールド名 | 型 | 説明 | バリデーション |
|------------|-----|------|--------------|
| `makerNo` | `Integer` | メーカー番号（画面表示のみ、入力不可） | なし |
| `makerName` | `String` | メーカー名 | `@NotBlank`（必須） |

#### 登録処理の流れ

1. バリデーションエラーがある場合: フォームを再表示する。
2. メーカー名をUnicode正規化（NFKC）する。
3. 同名のメーカーが既に存在する場合: 「既に登録されているメーカー名です。」の警告メッセージを表示する。
4. 重複がない場合: `m_maker` テーブルにINSERTし、「登録しました」のメッセージを表示する。

#### メッセージ

| 種別 | 内容 |
|------|------|
| 成功 | 「登録しました」 |
| 警告（重複） | 「既に登録されているメーカー名です。メーカー名:{メーカー名}」 |
| エラー | 「エラーが発生しました。エラー:{原因}」 |

---

### メーカー更新・削除画面

- **URL**: `/makerModify`
- **GET**: フォームを表示（更新対象の選択はAjaxで行う想定）。
- **POST**: AjaxによるJSON形式のレスポンスを返す（`@ResponseBody`）。

#### 更新フォーム（`MakerModifyForm`）

| フィールド名 | 型 | 説明 | バリデーション |
|------------|-----|------|--------------|
| `makerNo` | `Integer` | メーカー番号（主キー） | なし |
| `makerName` | `String` | メーカー名 | なし |
| `action` | `String` | アクション区分（`"edit"` または `"delete"`） | なし |

#### アクション区分

| アクション値 | 処理内容 |
|-----------|---------|
| `"edit"` | メーカー名を更新する |
| `"delete"` | `del_flg` を `'1'` に更新する（論理削除） |

#### 更新処理の流れ（action=edit）

1. バリデーションエラーがある場合: `{"errorMessage": "..."}` を返す。
2. メーカー名をUnicode正規化（NFKC）する。
3. 自身以外の同名メーカーが存在する場合: `{"errorMessage": "既に登録されているメーカー名です。..."}` を返す。
4. 正常時: `{"successMessage": "更新しました。"}` を返す。

#### 削除処理の流れ（action=delete）

1. 指定されたメーカー番号のエンティティを取得する。
2. `del_flg` を `'1'` に設定して保存する。
3. 正常時: `{"successMessage": "削除しました。"}` を返す。

---

### メーカーポップアップ検索画面

- **URL**: `/makerMasterPop`
- **用途**: 商品登録・編集画面などからモーダルウィンドウとして呼び出されるメーカー選択ダイアログ。
- **GET**: `makerNo` をクエリパラメータで受け取り、既存のメーカー情報を初期表示する。
- **POST**: 検索フォームによる絞り込み検索を行う。

#### Javaファイル

- コントローラ: `jp.co.oda32.app.master.MakerMasterPopController`
- テンプレート: `src/main/resources/templates/master/maker_master_pop.html`

---

## 倉庫マスタ

### 概要

社内の物理的な倉庫・保管場所を管理するマスタ。会社（`company_no`）に紐付く。住所・連絡先情報を保持する。

### テーブル定義

**テーブル名**: `m_warehouse`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `warehouse_no` | `warehouseNo` | `Integer` | 倉庫番号（主キー、シーケンス自動採番） |
| `company_no` | `companyNo` | `Integer` | 会社番号（外部キー: `m_company.company_no`） |
| `warehouse_name` | `warehouseName` | `String` | 倉庫名 |
| `zip_code` | `zipCode` | `String` | 郵便番号 |
| `tel_no` | `telNo` | `String` | 電話番号 |
| `fax_no` | `faxNo` | `String` | FAX番号 |
| `address1` | `address1` | `String` | 住所1 |
| `address2` | `address2` | `String` | 住所2 |
| `address3` | `address3` | `String` | 住所3 |
| `address4` | `address4` | `String` | 住所4 |
| `del_flg` | `delFlg` | `String` | 削除フラグ（`'0'`=有効、`'1'`=削除） |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |

**シーケンス**: `m_warehouse_warehouse_no_seq`

**リレーション**: `company_no` → `m_company`（`@OneToOne`）

**クラス**: `jp.co.oda32.domain.model.master.MWarehouse`
（`AbstractCompanyEntity` を継承、`@CompanyEntity` アノテーション付き）

### 倉庫一覧・検索画面

- **URL**: `/warehouseMaster`
- **GET**: 会社プルダウンを含む検索フォームを表示する。
- **POST**: 会社・倉庫名による検索を実行する。

#### 検索フォーム（`WarehouseMasterForm`）

| フィールド名 | 型 | 説明 | バリデーション |
|------------|-----|------|--------------|
| `companyNo` | `Integer` | 会社番号（プルダウン選択） | なし |
| `warehouseNo` | `Integer` | 倉庫番号 | なし |
| `warehouseName` | `String` | 倉庫名（部分一致） | なし |

#### 検索仕様

- JPA Specificationにより倉庫番号・倉庫名・会社番号・削除フラグの組み合わせで検索する。
- 会社情報は `MCompanyService.findAll()` から取得し `Map<Integer, String>（company_no → company_name）` としてモデルに渡す。
- 一覧表示項目: 倉庫番号（No.）、倉庫名。
- DataTables（`warehouseMasterTable`）対応。

---

### 倉庫新規登録画面

- **URL**: `/warehouseCreate`
- **GET**: 空の登録フォームと会社プルダウンを表示する。
- **POST**: バリデーション後に倉庫を新規登録する。

#### 登録フォーム（`WarehouseCreateForm`）

| フィールド名 | 型 | 説明 | バリデーション |
|------------|-----|------|--------------|
| `warehouseNo` | `Integer` | 倉庫番号（画面表示のみ、入力不可） | なし |
| `warehouseName` | `String` | 倉庫名 | なし（実質必須） |
| `companyNo` | `Integer` | 会社番号（プルダウン選択） | なし |

#### 登録処理の流れ

1. バリデーションエラーがある場合: フォームを再表示する。
2. 倉庫名をUnicode正規化（NFKC）する。
3. 同名の倉庫が既に存在する場合: 「既に登録されている倉庫名です。」の警告メッセージを表示する。
4. 正常時: `m_warehouse` テーブルにINSERTし、「登録しました」のメッセージを表示する。

---

### 倉庫更新・削除画面

- **URL**: `/warehouseModify`
- **GET**: フォームを表示する。
- **POST**: AjaxによるJSON形式のレスポンスを返す（`@ResponseBody`）。

#### 更新フォーム（`WarehouseModifyForm`）

| フィールド名 | 型 | 説明 | バリデーション |
|------------|-----|------|--------------|
| `warehouseNo` | `Integer` | 倉庫番号（主キー） | なし |
| `warehouseName` | `String` | 倉庫名 | なし |
| `action` | `String` | アクション区分（`"edit"` または `"delete"`） | なし |

#### アクション区分

| アクション値 | 処理内容 |
|-----------|---------|
| `"edit"` | 倉庫名を更新する |
| `"delete"` | `del_flg` を `'1'` に更新する（論理削除） |

#### レスポンス

AjaxによるJSON形式。メーカー更新・削除と同じパターン（`successMessage` / `errorMessage`）。

---

## 会社マスタ

### 概要

小田光株式会社のグループ各社・取引先各社を管理するマスタ。ショップ（`m_shop`）および得意先（`m_partner`）に紐付く。会社種別（`company_type`）によってログインユーザーのアクセス権限スコープが決まる。

### テーブル定義

**テーブル名**: `m_company`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `company_no` | `companyNo` | `Integer` | 会社番号（主キー、シーケンス自動採番） |
| `shop_no` | `shopNo` | `Integer` | 店舗番号（外部キー: `m_shop.shop_no`） |
| `partner_no` | `partnerNo` | `Integer` | 得意先番号（外部キー: `m_partner.partner_no`） |
| `company_name` | `companyName` | `String` | 会社名 |
| `abbreviated_company_name` | `abbreviatedCompanyName` | `String` | 会社名略称 |
| `company_type` | `companyType` | `String` | 会社種別（`"admin"` / `"shop"` / `"partner"`） |
| `tax_pattern` | `taxPattern` | `String` | 税パターン |
| `mat_api_key` | `matApiKey` | `String` | スマートマット APIキー |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |
| `del_flg` | `delFlg` | `String` | 削除フラグ（`'0'`=有効、`'1'`=削除） |

**シーケンス**: `m_company_company_no_seq`

**リレーション**:
- `shop_no` → `m_shop`（`@OneToOne`、`@NotFound(IGNORE)`）
- `partner_no` → `m_partner`（`@OneToOne`、`@NotFound(IGNORE)`）

**クラス**: `jp.co.oda32.domain.model.master.MCompany`
（`@ShopEntity` アノテーション付き）

### 会社ポップアップ検索画面

- **URL**: `/companyMasterPop`（POST）、`/companyMasterPopSearch`（POST）
- **用途**: 注文入力などの画面から得意先会社を選択するためのポップアップダイアログ。
- コントローラ: `jp.co.oda32.app.master.CompanyMasterPopController`

#### 検索フォーム（`CompanyMasterForm`）

| フィールド名 | 型 | 説明 | バリデーション |
|------------|-----|------|--------------|
| `companyNo` | `Integer` | 会社番号 | なし |
| `companyName` | `String` | 会社名（部分一致） | なし |
| `companyType` | `String` | 会社種別 | なし |

#### `companyMasterPop` エンドポイントの動作

1. `companyType` パラメータが未指定の場合は `"partner"` として扱う。
2. `company_type` で会社を絞り込む。
3. さらに「2年以内に最終注文がある得意先」のみを絞り込む（`lastOrderDate > 現在日 - 2年`）。
4. 得意先コード（`partnerCode`）昇順でソートして表示する。

#### サービスメソッド（`MCompanyService`）

| メソッド | 引数 | 説明 |
|---------|------|------|
| `findAll()` | なし | 削除フラグ考慮全件取得 |
| `findByCompanyType(companyType)` | `String` | 会社種別で絞り込み |
| `findByShopNoAndCompanyType(shopNo, companyType)` | `Integer, String` | 店舗番号と会社種別で検索 |
| `find(companyNo, companyName, companyType, delFlg)` | 各条件 | Specificationによる複合条件検索 |
| `getByCompanyNo(companyNo)` | `Integer` | 主キー検索 |
| `getByShopNo(shopNo)` | `Integer` | 店舗番号でSHOP種別会社を取得 |
| `getByPartnerNo(partnerNo)` | `Integer` | 得意先番号で会社を取得 |

---

## 仕入先マスタ

### 概要

商品の仕入れ元となるサプライヤー（問屋・メーカー等）を管理するマスタ。ショップ（`shop_no`）単位で管理され、仕入先コードがユニークキーとなる。支払先（`m_payment_supplier`）と紐付く。

### テーブル定義

**テーブル名**: `m_supplier`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `supplier_no` | `supplierNo` | `Integer` | 仕入先番号（主キー、シーケンス自動採番） |
| `shop_no` | `shopNo` | `Integer` | 店舗番号（外部キー: `m_shop.shop_no`） |
| `supplier_code` | `supplierCode` | `String` | 仕入先コード（ユニークキー、`shop_no` との複合） |
| `supplier_name` | `supplierName` | `String` | 仕入先名 |
| `supplier_name_display` | `supplierNameDisplay` | `String` | 仕入先表示名 |
| `standard_lead_time` | `standardLeadTime` | `Integer` | 標準リードタイム（日数） |
| `payment_supplier_no` | `paymentSupplierNo` | `Integer` | 支払先番号（外部キー: `m_payment_supplier.payment_supplier_no`） |
| `del_flg` | `delFlg` | `String` | 削除フラグ（`'0'`=有効、`'1'`=削除） |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |

**シーケンス**: `m_supplier_supplier_no_seq`

**リレーション**: `payment_supplier_no` → `m_payment_supplier`（`@OneToOne`）

**クラス**: `jp.co.oda32.domain.model.master.MSupplier`
（`@ShopEntity` アノテーション付き）

### 仕入先ポップアップ検索画面

- **URL**: `/supplierMasterPop`（POST）、`/supplierMasterPopSearch`（POST）
- **用途**: 仕入処理・発注処理の画面から仕入先を選択するポップアップダイアログ。
- コントローラ: `jp.co.oda32.app.master.SupplierMasterPopController`

#### 検索フォーム（`SupplierMasterForm`）

| フィールド名 | 型 | 説明 | バリデーション |
|------------|-----|------|--------------|
| `shopNo` | `Integer` | 店舗番号 | なし |
| `supplierNo` | `Integer` | 仕入先番号 | なし |
| `supplierName` | `String` | 仕入先名（部分一致） | なし |
| `supplierCode` | `String` | 仕入先コード（部分一致） | なし |

#### `/supplierMasterPop` の動作

- 全件取得（`del_flg='0'`、`payment_supplier_no IS NOT NULL`）し、仕入先コード昇順でソートして表示する。

#### `/supplierMasterPopSearch` の動作

- `supplierNo`・`supplierName`・`supplierCode` の複合条件で検索する。
- 結果を仕入先コード昇順でソートして返す。

#### サービスメソッド（`MSupplierService`）

| メソッド | 引数 | 説明 |
|---------|------|------|
| `findAll()` | なし | 全件取得（削除フラグ考慮なし） |
| `find(supplierNo, supplierName, supplierCode, paymentSupplierNo, delFlg)` | 各条件 | Specificationによる複合条件検索 |
| `getBySupplierNo(supplierNo)` | `Integer` | 主キー検索 |
| `findByShopNo(shopNo)` | `Integer` | 店舗番号で仕入先一覧取得 |
| `getByUniqueKey(shopNo, supplierCode)` | `Integer, String` | ユニークキー（店舗番号 + 仕入先コード）で取得 |
| `findBySupplierCodeList(shopNo, supplierCodeList)` | `Integer, List<String>` | 仕入先コードリストで一括取得 |

---

## 得意先（取引先）マスタ

### 概要

売上先となる得意先（取引先）を管理するマスタ。ショップ単位で管理され、得意先コードがユニークキー。会社（`m_company`）に紐付く。親子関係（`parent_partner_no`）による階層構造に対応する。締め日設定により支払タイプが決まる。

### テーブル定義

**テーブル名**: `m_partner`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `partner_no` | `partnerNo` | `Integer` | 得意先番号（主キー、シーケンス自動採番） |
| `company_no` | `companyNo` | `Integer` | 会社番号（外部キー: `m_company.company_no`） |
| `shop_no` | `shopNo` | `Integer` | 店舗番号 |
| `partner_name` | `partnerName` | `String` | 得意先名 |
| `abbreviated_partner_name` | `abbreviatedPartnerName` | `String` | 得意先名略称 |
| `partner_code` | `partnerCode` | `String` | 得意先コード（ユニークキー、`shop_no` との複合） |
| `last_order_date` | `lastOrderDate` | `LocalDate` | 最終注文日 |
| `cutoff_date` | `cutoffDate` | `Integer` | 締め日（0=月末、15=15日、20=20日、-1=都度現金） |
| `note` | `note` | `String` | 備考 |
| `partner_category_code` | `partnerCategoryCode` | `String` | 得意先カテゴリコード |
| `parent_partner_no` | `parentPartnerNo` | `Integer` | 親得意先番号（階層構造用） |
| `is_include_tax_display` | `isIncludeTaxDisplay` | `boolean` | 税込み表示フラグ |
| `invoice_partner_code` | `invoicePartnerCode` | `String` | 請求書用得意先コード |
| `del_flg` | `delFlg` | `String` | 削除フラグ（`'0'`=有効、`'1'`=削除） |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |

**シーケンス**: `m_partner_partner_no_seq`

**リレーション**: `company_no` → `m_company`（`@OneToOne`、`FetchType.EAGER`）

**クラス**: `jp.co.oda32.domain.model.master.MPartner`
（`@ShopEntity` アノテーション付き）

#### 締め日と支払タイプの対応

`cutoffDate` の値により `PaymentType` が決定される:

| `cutoffDate` 値 | `PaymentType` | 説明 |
|--------------|-------------|------|
| `0` または `null` | `MONTH_END` | 月末締め |
| `15` | `DAY_15` | 15日締め |
| `20` | `DAY_20` | 20日締め |
| `-1` | `CASH_ON_DELIVERY` | 都度現金払い |
| `28` 以上 | `MONTH_END` | 月末締め |

#### 固定コード定数

| 定数名 | 値 | 説明 |
|--------|-----|------|
| `Constants.FIXED_PARTNER_CODE` | `"99999999"` | 手打ち得意先コードの固定値 |

#### サービスメソッド（`MPartnerService`）

| メソッド | 引数 | 説明 |
|---------|------|------|
| `findAll()` | なし | 削除フラグ考慮全件取得 |
| `getByPartnerNo(partnerNo)` | `Integer` | 主キー検索 |
| `findByPartnerName(partnerName)` | `String` | 得意先名で検索 |
| `getByUniqueKey(shopNo, partnerCode)` | `Integer, String` | ユニークキーで取得 |
| `find(partnerNo, partnerName, partnerCode, lastOrderDateFrom, lastOrderDateTo, delFlg)` | 各条件 | Specification複合条件検索 |
| `findByPartnerNoList(partnerNoList)` | `List<Integer>` | 得意先番号リストで一括取得 |
| `findChildrenPartner(parentPartnerNo)` | `Integer` | 指定した親の子得意先を取得 |
| `findHasParentPartner()` | なし | 親を持つ子得意先の全件取得 |
| `findByPartnerCodeList(shopNo, partnerCodeList)` | `Integer, List<String>` | 得意先コードリストで一括取得 |

---

## 店舗マスタ

### 概要

小田光グループの各事業部（店舗）を管理するマスタ。会社（`m_company`）に紐付く。`shop_no=0` は管理者用の特別な店舗番号として扱われ、通常の検索から除外される（`@Where(clause = "shop_no <> 0")`）。

### テーブル定義

**テーブル名**: `m_shop`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `shop_no` | `shopNo` | `Integer` | 店舗番号（主キー、シーケンス自動採番） |
| `company_no` | `companyNo` | `Integer` | 会社番号（外部キー: `m_company.company_no`） |
| `shop_name` | `shopName` | `String` | 店舗名 |
| `del_flg` | `delFlg` | `String` | 削除フラグ（`'0'`=有効、`'1'`=削除） |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |

**シーケンス**: `m_shop_shop_no_seq`

**Hibernateフィルタ**: `@Where(clause = "shop_no <> 0")` により、管理者用の `shop_no=0` は自動的に除外される。

**クラス**: `jp.co.oda32.domain.model.master.MShop`
（`@ShopEntity` アノテーション付き）

#### サービスメソッド（`MShopService`）

| メソッド | 引数 | 説明 |
|---------|------|------|
| `findAll()` | なし | 全件取得（shop_no=0を除く） |
| `findByShopNoList(shopNoList)` | `List<Integer>` | 店舗番号リストで取得 |
| `getByShopNo(shopNo)` | `Integer` | 主キー検索 |
| `findByShopName(shopName)` | `String` | 店舗名で検索 |

---

## 仕入先店舗マッピング

### 概要

異なる店舗間（`shop_no`）で仕入先コードが異なる場合の対応関係を管理するマスタ。SMILE連携バッチで、ある店舗（ソース）の仕入先コードを別の店舗（ターゲット）の仕入先コードに変換するために使用される。現在は主に `shop_no=2`（第2事業部）から `shop_no=1`（第1事業部）へのマッピングに利用される。

### テーブル定義

**テーブル名**: `m_supplier_shop_mapping`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `mapping_id` | `mappingId` | `Integer` | マッピングID（主キー、シーケンス自動採番） |
| `source_shop_no` | `sourceShopNo` | `Integer` | ソース店舗番号（変換元） |
| `source_supplier_code` | `sourceSupplierCode` | `String` | ソース仕入先コード（変換元） |
| `target_shop_no` | `targetShopNo` | `Integer` | ターゲット店舗番号（変換先） |
| `target_supplier_code` | `targetSupplierCode` | `String` | ターゲット仕入先コード（変換先） |
| `del_flg` | `delFlg` | `String` | 削除フラグ（`'0'`=有効、`'1'`=削除） |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |

**シーケンス**: `m_supplier_shop_mapping_mapping_id_seq`

**クラス**: `jp.co.oda32.domain.model.master.MSupplierShopMapping`
（`getShopNo()` は `sourceShopNo` を返す）

### 仕入先店舗マッピング一覧・登録画面

- **URL**: `/master/supplierShopMapping`
- **GET**: マッピング一覧を表示する。新規登録フォームも同一画面に配置される。
- **POST**: 新規マッピングを登録または既存マッピングを更新する。

#### 一覧表示項目

| 列名 | フィールド | 説明 |
|------|-----------|------|
| ID | `mappingId` | マッピングID |
| ソースショップ | `sourceShopNo` | 変換元店舗番号 |
| ソース仕入先コード | `sourceSupplierCode` | 変換元仕入先コード |
| ターゲットショップ | `targetShopNo` | 変換先店舗番号 |
| ターゲット仕入先コード | `targetSupplierCode` | 変換先仕入先コード |
| 操作 | - | 削除ボタン |

※ `del_flg = '1'` のマッピングはテンプレート側で表示しない（`th:if="${mapping.delFlg != '1'}"`）。

#### 新規登録フォームの入力項目

| フィールド名 | 入力形式 | 説明 |
|------------|---------|------|
| `sourceShopNo` | セレクトボックス（固定: `shop_no=2`） | ソース店舗番号 |
| `sourceSupplierCode` | セレクトボックス（`shop_no=2` の仕入先一覧） | ソース仕入先コード |
| `targetShopNo` | セレクトボックス（固定: `shop_no=1`） | ターゲット店舗番号 |
| `targetSupplierCode` | セレクトボックス（`shop_no=1` の仕入先一覧） | ターゲット仕入先コード |
| `loginUserNo` | 隠しフィールド（セッション値） | 操作ユーザー番号 |

#### 登録・更新処理の流れ

1. `sourceShopNo` + `sourceSupplierCode` の組み合わせで既存マッピングを検索する（`del_flg='0'` のもの）。
2. 既存マッピングがある場合: `targetShopNo`・`targetSupplierCode` を更新し、`del_flg='0'` を設定する（復活対応）。
3. 既存マッピングがない場合: 新規作成する（`del_flg='0'`）。

---

### 仕入先店舗マッピング削除

- **URL**: `/master/supplierShopMapping/{mappingId}/delete`
- **POST**: 指定した `mappingId` のマッピングを論理削除する（`del_flg='1'`）。
- 削除確認ダイアログ（JavaScriptの `confirm()`）を表示する。
- 処理後: `/master/supplierShopMapping` にリダイレクト。

---

### CSVインポート画面

- **URL（表示）**: `/master/supplierShopMapping/import` (GET)
- **URL（実行）**: `/master/supplierShopMapping/import` (POST)

#### CSVフォーマット仕様

```
source_shop_no,source_supplier_code,target_shop_no,target_supplier_code
```

| 列番号 | ヘッダー名 | 型 | 説明 |
|--------|-----------|-----|------|
| 1列目 | `source_shop_no` | 整数 | ソース店舗番号 |
| 2列目 | `source_supplier_code` | 文字列 | ソース仕入先コード |
| 3列目 | `target_shop_no` | 整数 | ターゲット店舗番号 |
| 4列目 | `target_supplier_code` | 文字列 | ターゲット仕入先コード |

#### CSVインポート処理仕様

- **エンコーディング**: UTF-8
- **1行目**: ヘッダー行としてスキップする。
- **フィールド数チェック**: 4フィールドでない行はスキップする。
- **数値変換失敗**: `NumberFormatException` が発生した行はスキップする。
- **UPSERTロジック**: `sourceShopNo` + `sourceSupplierCode` の組み合わせが既存の場合は更新、ない場合は新規登録する。
- **論理削除の復活**: 既存マッピングが論理削除済みの場合でも、同じキーがCSVに含まれていれば `del_flg='0'` に戻して更新する。
- **トランザクション**: メソッド全体が `@Transactional` で制御される。

#### CSVテンプレートダウンロード

- **URL**: `/master/supplierShopMapping/template` (GET)
- **Content-Type**: `text/csv`
- **ファイル名**: `supplier_shop_mapping_template.csv`
- **内容**: ヘッダー行 + サンプル1行

```csv
source_shop_no,source_supplier_code,target_shop_no,target_supplier_code
2,仕入先コード,1,対応する仕入先コード
```

---

## ログインユーザーマスタ

### 概要

システムにログインするユーザーを管理するマスタ。会社（`m_company`）に紐付き、会社種別（`company_type`）によってアクセス可能なデータの店舗スコープが決定される。パスワードはBCryptでハッシュ化して保存される。

### テーブル定義

**テーブル名**: `m_login_user`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `login_user_no` | `loginUserNo` | `Integer` | ログインユーザー番号（主キー、シーケンス自動採番） |
| `user_name` | `userName` | `String` | ユーザー名（表示名） |
| `password` | `password` | `String` | パスワード（BCryptハッシュ） |
| `login_id` | `loginId` | `String` | ログインID（認証に使用） |
| `company_no` | `companyNo` | `Integer` | 会社番号（外部キー: `m_company.company_no`） |
| `company_type` | `companyType` | `String` | 会社種別（`"admin"` / `"shop"` / `"partner"`） |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |
| `del_flg` | `delFlg` | `String` | 削除フラグ（`'0'`=有効、`'1'`=削除） |

**シーケンス**: `m_login_user_login_user_no_seq`

**リレーション**: `company_no` → `m_company`（`@OneToOne`）

**クラス**: `jp.co.oda32.domain.model.master.MLoginUser`
（`@ShopEntity` アノテーション付き）

### 店舗番号（shopNo）の解決ロジック

`getShopNo()` メソッドは `company_type` の値に応じて以下の店舗番号を返す:

| `company_type` 値 | 返却する `shopNo` |
|----------------|---------------|
| `"admin"` | `0`（`OfficeShopNo.ADMIN.getValue()`） |
| `"shop"` | ユーザーが所属する会社の `shop_no` |
| `"partner"` | ユーザーが所属する会社の得意先が所属する `shop_no` |
| 上記以外 / null | `-1`（該当なし） |

### 認証設定（Spring Security）

- **ログインURL**: `/loginForm`（GET）
- **認証処理URL**: `/login`（POST）
- **パラメータ名**: ユーザーID = `login_id`、パスワード = `password`
- **ログイン失敗URL**: `/loginForm?error`
- **ログイン成功後の遷移**: `/dashboard`
- **ログアウトURL**: `/logout`
- **ログアウト成功URL**: `/loginForm`
- **セッションタイムアウト**: 1時間（アプリ設定）
- **同時セッション数**: 1（新しいログインで古いセッションを無効化）
- **パスワードエンコーダ**: BCrypt
- **認証サービスクラス**: `LoginUserService`（Spring Security の `UserDetailsService` 実装）

---

## 補助マスタ

### 支払先マスタ（MPaymentSupplier）

仕入先の支払先（`m_supplier.payment_supplier_no` で参照）を管理するマスタ。

**テーブル名**: `m_payment_supplier`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `payment_supplier_no` | `paymentSupplierNo` | `Integer` | 支払先番号（主キー） |
| `payment_supplier_code` | `paymentSupplierCode` | `String` | 支払先コード |
| `payment_supplier_name` | `paymentSupplierName` | `String` | 支払先名 |
| `shop_no` | `shopNo` | `Integer` | 店舗番号 |
| `tax_timing_code` | `taxTimingCode` | `BigDecimal` | 税タイミングコード |
| `tax_timing` | `taxTiming` | `String` | 税タイミング |
| `cutoff_date` | `cutoffDate` | `Integer` | 締め日 |
| `del_flg` | `delFlg` | `String` | 削除フラグ |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |

---

### 得意先カテゴリマスタ（MPartnerCategory）

得意先を分類するカテゴリを管理するマスタ。

**テーブル名**: `m_partner_category`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `partner_category_no` | `partnerCategoryNo` | `Integer` | 得意先カテゴリ番号（主キー） |
| `partner_category_name` | `partnerCategoryName` | `String` | カテゴリ名 |
| `partner_category_code` | `partnerCategoryCode` | `String` | カテゴリコード |
| `shop_no` | `shopNo` | `Integer` | 店舗番号 |
| `del_flg` | `delFlg` | `String` | 削除フラグ |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |

---

### 消費税率マスタ（MTaxRate）

消費税率の適用期間を管理するマスタ。標準税率と軽減税率を別途保持する。

**テーブル名**: `m_tax_rate`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `tax_rate_no` | `taxRateNo` | `Integer` | 税率番号（主キー） |
| `tax_rate` | `taxRate` | `BigDecimal` | 標準消費税率 |
| `reduced_tax_rate` | `reducedTaxRate` | `BigDecimal` | 軽減消費税率 |
| `period_from` | `periodFrom` | `LocalDate` | 適用開始日 |
| `period_to` | `periodTo` | `LocalDate` | 適用終了日 |
| `del_flg` | `delFlg` | `String` | 削除フラグ |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |

---

### 店舗連携ファイルマスタ（MShopLinkedFile）

各店舗がSMILE・B-CART等の外部システムと連携する際のファイル名・パス設定を管理するマスタ。`shop_no` を主キーとする（シーケンス不使用）。

**テーブル名**: `m_shop_linked_file`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `shop_no` | `shopNo` | `Integer` | 店舗番号（主キー） |
| `company_no` | `companyNo` | `Integer` | 会社番号 |
| `smile_order_input_file_name` | `smileOrderInputFileName` | `String` | SMILE受注取込ファイル名 |
| `smile_purchase_file_name` | `smilePurchaseFileName` | `String` | SMILE仕入ファイル名 |
| `smile_order_output_file_name` | `smileOrderOutputFileName` | `String` | SMILE受注出力ファイル名 |
| `b_cart_logistics_import_file_name` | `bCartLogisticsImportFileName` | `String` | B-CART物流取込ファイル名 |
| `smile_partner_output_file_name` | `smilePartnerOutputFileName` | `String` | SMILE得意先出力ファイル名 |
| `smile_destination_output_file_name` | `smileDestinationOutputFileName` | `String` | SMILE納品先出力ファイル名 |
| `smile_goods_import_file_name` | `smileGoodsImportFileName` | `String` | SMILE商品取込ファイル名 |
| `invoice_file_path` | `invoiceFilePath` | `String` | 請求書ファイルパス |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |
| `del_flg` | `delFlg` | `String` | 削除フラグ |

---

### スマートマット管理マスタ（MSmartMat）

IoTデバイス「スマートマット」と商品・会社の紐付けを管理するマスタ。`mat_id` を主キーとする（シーケンス不使用）。

**テーブル名**: `m_smart_mat`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `mat_id` | `matId` | `String` | マットID（主キー） |
| `company_no` | `companyNo` | `Integer` | 会社番号 |
| `goods_no` | `goodsNo` | `Integer` | 商品番号 |
| `goods_code` | `goodsCode` | `String` | 商品コード |
| `del_flg` | `delFlg` | `String` | 削除フラグ |
| `add_date_time` | `addDateTime` | `Timestamp` | 登録日時 |
| `add_user_no` | `addUserNo` | `Integer` | 登録ユーザー番号 |
| `modify_date_time` | `modifyDateTime` | `Timestamp` | 更新日時 |
| `modify_user_no` | `modifyUserNo` | `Integer` | 更新ユーザー番号 |

---

### 配達担当者マスタ（MDeliveryPerson）

配達担当者と得意先・配達コードの対応を管理するマスタ。複合主キーを持つ。

**テーブル名**: `m_delivery_person`

| カラム名 | Javaフィールド名 | 型 | 説明 |
|---------|---------------|-----|------|
| `partner_code` | `partnerCode` | `String` | 得意先コード（主キー1） |
| `delivery_code` | `deliveryCode` | `String` | 配達コード（主キー2） |
| `user_id` | `userId` | `BigDecimal` | ユーザーID（m_asanaのuser_idと共通） |

---

## 定数定義

### OfficeShopNo（店舗番号定数）

パッケージ: `jp.co.oda32.constant.OfficeShopNo`

各事業部に割り当てられた `shop_no` の定数定義。

| 定数名 | 値 | 説明 |
|--------|-----|------|
| `ADMIN` | `0` | 管理者（全店舗横断） |
| `DAIICHI` | `1` | 第1事業部 |
| `DAINI` | `2` | 第2事業部 |
| `CLEAN_LABO` | `3` | クリーンラボ事業部 |
| `INNER_PURCHASE` | `1` | 社内仕入（第1事業部に紐付く） |
| `INNER_ORDER` | `1` | 社内売上（第1事業部に紐付く） |
| `B_CART_ORDER` | `1` | B-Cart売上（第1事業部に紐付く） |

---

### OfficeCode（営業所コード定数）

パッケージ: `jp.co.oda32.constant.OfficeCode`

SMILEシステムで使用される8桁の営業所コード定数定義。

| 定数名 | 値 | 説明 |
|--------|-----|------|
| `DAINI` | `"00000002"` | 第2事業部 |
| `CLEAN_LABO` | `"00000001"` | クリーンラボ |
| `DAIICHI` | `"00000003"` | 第1事業部 |
| `INNER_PURCHASE` | `"00000004"` | 社内仕入 |
| `INNER_ORDER` | `"00000005"` | 社内売上 |
| `BCART_ORDER` | `"00000005"` | B-Cart売上（社内売上と同値） |

---

### CompanyType（会社種別定数）

パッケージ: `jp.co.oda32.constant.CompanyType`

`m_company.company_type` および `m_login_user.company_type` で使用される会社種別の定数定義。

| 定数名 | 文字列値 | 説明 |
|--------|---------|------|
| `ADMIN` | `"admin"` | 管理者（全ショップにアクセス可能） |
| `SHOP` | `"shop"` | ショップ担当者（自ショップのみ） |
| `PARTNER` | `"partner"` | 取引先担当者（得意先の所属ショップのみ） |

---

### Flag（削除フラグ定数）

パッケージ: `jp.co.oda32.constant.Flag`

全マスタテーブルの `del_flg` カラムで使用される定数定義。

| 定数名 | 文字列値 | 説明 |
|--------|---------|------|
| `YES` | `"1"` | 削除済み |
| `NO` | `"0"` | 有効（未削除） |

---

### Action（アクション定数）

パッケージ: `jp.co.oda32.constant.Action`

メーカー・倉庫等の更新・削除処理でAjaxのアクション区分として使用される定数定義。

| 定数名 | 文字列値 | 説明 |
|--------|---------|------|
| `EDIT` | `"edit"` | 更新 |
| `DELETE` | `"delete"` | 削除 |

---

### Constants（汎用定数）

パッケージ: `jp.co.oda32.constant.Constants`

| 定数名 | 値 | 説明 |
|--------|-----|------|
| `FIXED_PRODUCT_CODE` | `"99999999"` | 手打ち商品コードの固定値 |
| `FIXED_PARTNER_CODE` | `"99999999"` | 手打ち得意先コードの固定値 |
| `SHIPPING_FEE_PRODUCT_CODE` | `"00000015"` | 送料の商品コードの固定値 |

---

### PaymentType（支払タイプ定数）

パッケージ: `jp.co.oda32.constant.PaymentType`

得意先（`m_partner`）の `cutoffDate` から導出される支払タイプの定数定義。

| 定数名 | `cutoffCode` | 説明 |
|--------|------------|------|
| `MONTH_END` | `0` | 月末締め |
| `DAY_15` | `15` | 15日締め |
| `DAY_20` | `20` | 20日締め |
| `CASH_ON_DELIVERY` | `-1` | 都度現金払い |

---

*本仕様書は、`C:\project\stock-app` のソースコードを解析して作成した。最終更新: 2026-02-23。*
