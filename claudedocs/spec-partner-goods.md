# 得意先商品マスタ 機能仕様書

## 1. 概要

得意先（取引先）ごとの販売商品マスタを管理する機能。同一商品でも得意先・納品先ごとに異なる売価を設定でき、注文履歴の参照や売価の更新が可能。

### 対象テーブル
- `m_partner_goods` — 得意先別販売商品マスタ（主テーブル）

### 旧システム対応
| 旧画面 | 旧URL | 新URL（案） |
|--------|--------|-------------|
| 得意先商品マスタ一覧 | `/partnerGoodsMaster` | `/partner-goods` |
| 得意先商品詳細 | `/partnerGoodsModifyForm` | `/partner-goods/{partnerNo}/{destinationNo}/{goodsNo}` |

---

## 2. データモデル

### 2.1 テーブル: `m_partner_goods`

| カラム | 型 | PK | 説明 |
|--------|-----|-----|------|
| `partner_no` | Integer | PK | 得意先番号 |
| `goods_no` | Integer | PK | 商品番号 |
| `destination_no` | Integer | PK | 納品先番号 |
| `company_no` | Integer | | 会社番号（FK → m_company） |
| `shop_no` | Integer | | 店舗番号（FK → m_shop） |
| `goods_price` | BigDecimal | | 売価 |
| `goods_code` | String | | 商品コード |
| `goods_name` | String | | 商品名 |
| `keyword` | String | | 検索キーワード |
| `order_num_per_year` | BigDecimal | | 年間注文数 |
| `last_sales_date` | LocalDate | | 最終売上日 |
| `last_price_update_date` | LocalDate | | 最終単価更新日 |
| `reflected_estimate_no` | Integer | | 反映元見積番号 |
| `reflected_estimate_detail_no` | Integer | | 反映元見積明細番号 |
| `del_flg` | String | | 削除フラグ（'0'=有効, '1'=削除） |
| `add_date_time` | Timestamp | | 登録日時 |
| `add_user_no` | Integer | | 登録ユーザー |
| `modify_date_time` | Timestamp | | 更新日時 |
| `modify_user_no` | Integer | | 更新ユーザー |

**複合主キー**: (`partner_no`, `goods_no`, `destination_no`)

### 2.2 リレーション
- `m_partner_goods.company_no` → `m_company.company_no`（得意先情報）
- `m_partner_goods.goods_no` → `m_goods.goods_no`（商品マスタ）
- `m_partner_goods.shop_no` → `m_shop.shop_no`（店舗マスタ）
- `m_partner_goods.partner_no` → 納品先テーブル（納品先情報）

---

## 3. 画面仕様

### 3.1 得意先商品マスタ一覧画面

**パス**: `GET /partner-goods`

#### 検索フォーム

| 項目 | 入力方式 | 必須 | 検索条件 | 備考 |
|------|----------|------|----------|------|
| ショップ | セレクト | - | 完全一致 | `GET /api/v1/masters/shops` で取得 |
| 得意先コード | テキスト + 検索ボタン | - | 部分一致（LIKE %code%） | SearchableSelect を使用。選択後に得意先番号・得意先名を表示 |
| 納品先 | セレクト | - | 完全一致 | 得意先選択後に動的に取得 |
| 商品名 | テキスト | - | スペース区切りOR検索 | 「A B」→ goodsName LIKE '%A%' OR goodsName LIKE '%B%' |
| 商品コード | テキスト | - | 後方一致（LIKE %code） | |
| キーワード | テキスト | - | スペース区切りAND検索 | 「A B」→ keyword LIKE '%A%' AND keyword LIKE '%B%' |

**初期表示**: 検索フォームのみ表示（検索未実行時はテーブル非表示）

#### 検索結果テーブル

| # | カラム | ソート | 説明 |
|---|--------|--------|------|
| 1 | 商品番号 | - | `goodsNo` |
| 2 | ショップ | - | `mShop.shopName` |
| 3 | 得意先 | - | `mCompany.companyName` |
| 4 | 商品コード | - | `goodsCode` |
| 5 | 商品名 | - | `goodsName`（インライン編集可能※後述） |
| 6 | 売価 | - | `goodsPrice`（カンマ区切り、右寄せ） |
| 7 | 対応見積 | - | `reflectedEstimateNo` がある場合、見積明細へのリンク |
| 8 | 最終売上日 | デフォルト降順 | `lastSalesDate` |
| 9 | 詳細 | - | 詳細画面へのリンク |

**デフォルトソート**: 最終売上日の降順（null は末尾）

#### インライン編集（一覧画面上での操作）

旧システムではjQuery Tableditプラグインで一覧画面上の「商品名」をインライン編集可能にしていた。

**新システムでの方針（要検討）**:
- **Option A**: インライン編集を廃止し、詳細画面に統一（シンプル）
- **Option B**: TanStack Table のセル編集で再現

旧システムの挙動:
- 商品名セルをクリック → 入力フィールドに変化
- 編集確定時に `POST /partnerGoodsModify` (AJAX) で更新
- 削除ボタンで論理削除（確認ダイアログあり）
- 更新前に商品名の重複チェック（自レコード以外で同名が存在する場合エラー）

---

### 3.2 得意先商品詳細画面

**パス**: `GET /partner-goods/{partnerNo}/{destinationNo}/{goodsNo}`

#### 得意先商品情報セクション

| 項目 | 表示/編集 | 説明 |
|------|-----------|------|
| 現在の売単価 | 編集可能（テキスト入力） | `goodsPrice` |

**更新ボタン**: 売単価のみ更新する

#### 注文履歴セクション

注文明細（`t_order_detail`）と返品明細（`t_return_detail`）を統合して表示。

| # | カラム | 説明 |
|---|--------|------|
| 1 | 注文日 | `orderDateTime` |
| 2 | 商品コード | `goodsCode` |
| 3 | 商品名 | `goodsName` |
| 4 | 売価 | `goodsPrice`（null の場合「未設定」と表示） |
| 5 | 数量 | `goodsNum`（返品は負の値で表示） |

**ソート**: 注文日の降順

**データ取得ロジック**:
1. `t_order_detail` から shopNo, companyNo, goodsNo で検索（delFlg='0'）
2. `t_return_detail` から同条件で検索（delFlg='0'）
3. 返品明細の数量を -1 倍
4. 両方を結合して注文日降順ソート

---

## 4. API 設計

### 4.1 一覧検索

```
GET /api/v1/partner-goods?shopNo=1&partnerCode=ABC&goodsName=test&goodsCode=123&keyword=key
```

**クエリパラメータ**:
| パラメータ | 型 | 説明 |
|-----------|-----|------|
| shopNo | Integer | 店舗番号 |
| companyNo | Integer | 会社番号 |
| partnerCode | String | 得意先コード（部分一致） |
| goodsName | String | 商品名（スペース区切りOR） |
| goodsCode | String | 商品コード（後方一致） |
| keyword | String | キーワード（スペース区切りAND） |
| destinationNo | Integer | 納品先番号 |

**レスポンス**: `List<PartnerGoodsResponse>`

```json
{
  "partnerNo": 1,
  "goodsNo": 100,
  "destinationNo": 1,
  "shopName": "本店",
  "companyName": "テスト得意先",
  "goodsCode": "ABC-001",
  "goodsName": "商品A",
  "goodsPrice": 1500.00,
  "reflectedEstimateNo": 10,
  "lastSalesDate": "2025-12-01"
}
```

**注意**: 旧システムでは `delFlg='0'`（有効レコード）のみ返却。ソートは `lastSalesDate` 降順（null末尾）。

### 4.2 詳細取得

```
GET /api/v1/partner-goods/{partnerNo}/{destinationNo}/{goodsNo}
```

**レスポンス**: `PartnerGoodsDetailResponse`

```json
{
  "partnerNo": 1,
  "goodsNo": 100,
  "destinationNo": 1,
  "shopNo": 1,
  "companyNo": 5,
  "goodsCode": "ABC-001",
  "goodsName": "商品A",
  "goodsPrice": 1500.00,
  "keyword": "衛生用品",
  "orderHistory": [
    {
      "orderDateTime": "2025-11-15T10:30:00",
      "goodsCode": "ABC-001",
      "goodsName": "商品A",
      "goodsPrice": 1500.00,
      "goodsNum": 10
    },
    {
      "orderDateTime": "2025-10-01T09:00:00",
      "goodsCode": "ABC-001",
      "goodsName": "商品A",
      "goodsPrice": 1500.00,
      "goodsNum": -3
    }
  ]
}
```

### 4.3 売価更新

```
PUT /api/v1/partner-goods/{partnerNo}/{destinationNo}/{goodsNo}/price
```

**リクエスト**:
```json
{
  "goodsPrice": 1600.00
}
```

**バリデーション**: goodsPrice は正の値であること

### 4.4 インライン更新（Option B の場合）

```
PATCH /api/v1/partner-goods/{partnerNo}/{destinationNo}/{goodsNo}
```

**リクエスト**:
```json
{
  "goodsName": "更新後の商品名",
  "keyword": "新キーワード",
  "goodsPrice": 1600.00
}
```

**バリデーション**:
- goodsName: 更新時に他レコードとの重複チェック（自レコード除外）
- goodsPrice: 正の値
- goodsName は NFKC 正規化

### 4.5 論理削除

```
DELETE /api/v1/partner-goods/{partnerNo}/{destinationNo}/{goodsNo}
```

`del_flg` を '1' に更新。

### 4.6 納品先一覧取得（得意先選択後の動的取得）

```
GET /api/v1/masters/destinations?partnerNo=1
```

**レスポンス**: `List<DestinationResponse>`
```json
[
  { "destinationNo": 1, "destinationName": "本社" },
  { "destinationNo": 2, "destinationName": "第二倉庫" }
]
```

---

## 5. バックエンド実装方針

### 5.1 パッケージ構成

```
backend/src/main/java/jp/co/oda32/
├── api/partner-goods/
│   └── PartnerGoodsController.java        # REST Controller
├── domain/
│   ├── model/MPartnerGoods.java           # JPA Entity
│   ├── model/MPartnerGoodsPK.java         # 複合PK（@Embeddable）
│   ├── repository/MPartnerGoodsRepository.java
│   ├── service/PartnerGoodsService.java
│   └── specification/MPartnerGoodsSpecification.java
└── dto/
    ├── PartnerGoodsResponse.java          # 一覧用レスポンス
    ├── PartnerGoodsDetailResponse.java    # 詳細用レスポンス
    ├── PartnerGoodsPriceUpdateRequest.java
    └── PartnerGoodsUpdateRequest.java
```

### 5.2 検索ロジック（Specification パターン）

| 検索項目 | Specification | 条件 |
|----------|---------------|------|
| shopNo | `shopNoEquals` | `= ?` |
| companyNo | `companyNoEquals` | `= ?` |
| partnerCode | `partnerCodeLike` | `LIKE %?%`（m_company 経由） |
| goodsName | `goodsNamesLike` | スペース区切り → 各語に `LIKE %?%` → OR結合 |
| goodsCode | `goodsCodeSuffix` | `LIKE %?` |
| keyword | `keywordsLike` | スペース区切り → 各語に `LIKE %?%` → AND結合 |
| destinationNo | `destinationNoEquals` | `= ?` |
| delFlg | `delFlgEquals` | 常に `= '0'` |

### 5.3 注文履歴取得ロジック

```
1. t_order_detail から (shopNo, companyNo, goodsNo, delFlg='0') で検索
2. t_return_detail から (shopNo, companyNo, goodsNo, delFlg='0') で検索
3. 返品の goodsNum を × (-1)
4. 統合して orderDateTime 降順ソート
```

---

## 6. フロントエンド実装方針

### 6.1 ファイル構成

```
frontend/src/
├── components/features/partner-goods/
│   ├── PartnerGoodsSearchForm.tsx     # 検索フォーム
│   ├── PartnerGoodsTable.tsx          # 検索結果テーブル
│   ├── PartnerGoodsDetail.tsx         # 詳細表示
│   └── OrderHistoryTable.tsx          # 注文履歴テーブル
├── pages/partner-goods/
│   ├── page.tsx                       # 一覧ページ
│   └── [partnerNo]/[destinationNo]/[goodsNo]/
│       └── page.tsx                   # 詳細ページ
└── types/
    └── partner-goods.ts               # 型定義
```

### 6.2 一覧画面の動作フロー

```
1. 初期表示: 検索フォームのみ表示（テーブル非表示）
2. ショップ選択（セレクト）
3. 得意先入力（SearchableSelect / 得意先コード入力 + 検索）
   → 得意先選択後: 得意先名表示 + 納品先セレクト取得
4. 各検索条件入力
5. 「検索」ボタンクリック → API呼び出し → テーブル表示
6. テーブル行の「詳細」リンク → 詳細画面遷移（別タブ）
```

### 6.3 詳細画面の動作フロー

```
1. URL パラメータから (partnerNo, destinationNo, goodsNo) 取得
2. API で詳細データ + 注文履歴を取得
3. 売価入力フィールドに現在値を表示
4. 「更新」ボタン → PUT API → 成功トースト
5. 注文履歴テーブルは読み取り専用で表示
```

---

## 7. 実装決定事項

以下の検討事項はすべて推奨方針で確定・実装済み:

| # | 項目 | 決定 | 実装状況 |
|---|------|------|----------|
| 1 | インライン編集 | **廃止** → 詳細画面で商品名・キーワード・売価を編集 | 完了 |
| 2 | ページネーション | **全件取得**（クライアントサイドページネーション） | 完了 |
| 3 | 詳細画面の編集項目 | **拡張**: 商品名・キーワード・売価を編集可能 | 完了 |
| 4 | 見積明細リンク | 一覧画面に見積明細へのリンクを設置 | 完了 |
| 5 | 得意先検索方式 | `SearchableSelect` で得意先コード+名前で検索 | 完了 |
| 6 | 注文履歴上限 | 最新100件に制限（パフォーマンス考慮） | 完了 |

| 7 | 最終売上日の更新 | `OrderNumCountTasklet` で注文明細から算出しバッチ更新 | 完了 |

### 最終売上日（lastSalesDate）の更新方式
- `m_partner_goods.last_sales_date` は非正規化カラム（注文テーブルから集計した値を保持）
- `OrderNumCountTasklet` がSMILE注文取込バッチ（`smileOrderFileImportJob`）のフロー内で実行される
- 過去1年間の注文明細から商品×納品先ごとの最大注文日を算出
- 既存の `lastSalesDate` より新しい場合のみ更新（古い日付で上書きしない）
- ジョブフロー: SMILE取込 → 在庫引当 → ステータス更新 → **注文数カウント+最終売上日更新** → 適正在庫計算 → 月次サマリ更新 → ファイル移動

### 旧システムからの変更点
- フロントエンド: Thymeleaf → React + shadcn/ui
- API: @Controller → @RestController（JSON API）
- 検索: サーバーサイドレンダリング → SPA + TanStack Query
- 納品先動的取得: jQuery AJAX → TanStack Query の依存クエリ
- インライン編集(Tabledit): 廃止 → 詳細画面の編集モードに統合

## 8. 作成ファイル一覧

### バックエンド
- `api/goods/PartnerGoodsController.java` — REST API（一覧/詳細/更新/削除）
- `dto/goods/PartnerGoodsResponse.java` — 一覧レスポンスDTO
- `dto/goods/PartnerGoodsDetailResponse.java` — 詳細レスポンスDTO（注文履歴含む）
- `dto/goods/OrderHistoryResponse.java` — 注文履歴レスポンスDTO
- `dto/goods/PartnerGoodsUpdateRequest.java` — 更新リクエストDTO
- `dto/master/PartnerResponse.java` — 得意先レスポンスDTO
- `dto/master/DeliveryDestinationResponse.java` — 納品先レスポンスDTO
- `api/master/MasterController.java` — 得意先・納品先エンドポイント追加
- `batch/order/OrderNumCountTasklet.java` — lastSalesDate更新ロジック追加
- `batch/smile/config/SmileOrderFileImportConfig.java` — orderNumCountStep再有効化

### フロントエンド
- `types/partner-goods.ts` — 型定義
- `components/pages/partner-goods/index.tsx` — 一覧ページコンポーネント
- `components/pages/partner-goods/detail.tsx` — 詳細ページコンポーネント
- `app/(authenticated)/partner-goods/page.tsx` — 一覧ルート
- `app/(authenticated)/partner-goods/[partnerNo]/[destinationNo]/[goodsNo]/page.tsx` — 詳細ルート
- `hooks/use-master-data.ts` — usePartners/useDestinations フック追加
- `e2e/helpers/mock-api.ts` — モックデータ追加
- `e2e/partner-goods.spec.ts` — E2Eテスト（15件）
