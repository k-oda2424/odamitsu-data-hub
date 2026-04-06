# B-CART商品マスタ同期・更新機能 設計書

## 1. 背景 / 課題

### 現状
- B-CART商品データはバッチで取込済み（`b_cart_products`, `b_cart_product_sets`）
- 価格変更はDBを直接更新→バッチで`PATCH /api/v1/product_sets/{id}`に反映
- **商品説明の更新機能がない**（現行バッチは価格のみ送信）
- 販売商品マスタ(`m_sales_goods`)とB-CART商品に**関連がない**
- 変更前データの**バックアップ（履歴）がない**
- B-CARTカテゴリマスタがDB上に存在しない（`b_cart_products.category_id`のみ）

### 解決したいこと
1. 本システムのUIからB-CART商品の説明・価格を更新できるようにする
2. 販売商品マスタの中にB-CART商品を含む関係性を構築する
3. 「何月何日から価格変更」の予約機能
4. AI生成の商品説明をB-CARTに反映する
5. 変更前の情報を必ずバックアップとして保持する
6. B-CARTカテゴリマスタを本システムで管理する

---

## 2. 要件

### 機能要件
| # | 要件 | 優先度 |
|---|------|--------|
| F1 | B-CART商品マスタの定期同期（API→DB） | 必須 |
| F2 | 販売商品マスタとB-CART商品の紐付け | 必須 |
| F3 | B-CART商品の説明文を本システムから更新 | 必須 |
| F4 | B-CART商品の価格を本システムから更新 | 必須 |
| F5 | 変更前データのバックアップ（変更履歴） | 必須 |
| F6 | 価格変更予定日の設定と自動反映 | 必須 |
| F7 | AI生成の商品説明を編集・反映 | 高 |
| F8 | B-CARTカテゴリマスタの同期・管理 | 必須 |

### 非機能要件
- B-CART APIレート制限: 300リクエスト/5分 → 250件ごとに5分待機（既存パターン踏襲）
- 変更履歴は無期限保持
- B-CART API障害時のリトライ・エラー通知

---

## 3. 制約

### 技術的制約（APIテスト結果 2026-04-06）
- **`PATCH /api/v1/products/{id}`**: 商品説明更新 **動作確認済み**
  - リクエスト形式: `application/x-www-form-urlencoded`（JSON形式は不可）
  - 日本語: UTF-8でURLエンコードが必要
  - `description`, `catch_copy`, `prepend_text` 等すべてのフィールドが更新可能
- **`PATCH /api/v1/product_sets/{id}`**: 価格更新（既存バッチで確認済み）
- **`GET /api/v1/categories`**: カテゴリ一覧取得 **動作確認済み**（49件、2階層構造）
- JWT トークン: ソースコード上のexpは2024年3月だが**実際は有効**（ローカル運用）
- `b_cart_products.description` は65535文字まで

### ビジネス制約
- 価格変更は必ずバックアップ後に実行
- B-CARTへの反映はバッチ実行（リアルタイムではない）
- 販売商品マスタの既存ワークフロー（ワーク→マスタ反映）を壊さない

---

## 4. 提案するソリューション

### 全体アーキテクチャ

```
┌───────────────────────────────────────────────────────────┐
│  フロントエンド                                              │
│  ┌─────────────┐ ┌────────────┐ ┌────────────┐ ┌────────┐│
│  │B-CARTカテゴリ│ │B-CART商品   │ │価格変更予定 │ │販売商品 ││
│  │マスタ画面    │ │説明編集画面 │ │一覧/登録   │ │(既存)  ││
│  └──────┬──────┘ └─────┬──────┘ └─────┬──────┘ └───┬────┘│
└─────────┼──────────────┼──────────────┼─────────────┼─────┘
          │              │              │             │
          ▼              ▼              ▼             ▼
┌───────────────────────────────────────────────────────────┐
│  バックエンド API                                           │
│  ┌───────────────────┐ ┌──────────────────────────────┐   │
│  │BCartCategoryCtrl   │ │BCartProductController(新)    │   │
│  │- GET/PUT カテゴリ  │ │- GET/PUT 商品説明            │   │
│  │- POST 新規作成     │ │- GET/PUT 価格変更予定        │   │
│  └───────────────────┘ └──────────────────────────────┘   │
└──────────────────────────┬────────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────────┐
│ B-CART同期     │  │ 価格変更      │  │ 商品説明反映     │
│ バッチ(拡張)   │  │ 反映バッチ    │  │ バッチ(新規)     │
│ + Categories  │  │ (既存拡張)    │  │ PATCH products  │
│ + Products    │  │ + 履歴保存    │  │ + 履歴保存      │
│ + ProductSets │  │              │  │                 │
└──────────────┘  └──────────────┘  └──────────────────┘
          │                │                │
          ▼                ▼                ▼
┌───────────────────────────────────────────────────────────┐
│  PostgreSQL                                                 │
│  ┌──────────────┐ ┌──────────────┐ ┌────────────────────┐ │
│  │b_cart_        │ │b_cart_*      │ │b_cart_change_      │ │
│  │categories(新) │ │(既存テーブル) │ │history(新)         │ │
│  └──────────────┘ └──────────────┘ └────────────────────┘ │
│  ┌────────────────────────┐ ┌─────────────────────┐       │
│  │b_cart_scheduled_change │ │m_sales_goods        │       │
│  │(新規: 価格変更予定)     │ │+b_cart_product_set_id│       │
│  └────────────────────────┘ └─────────────────────┘       │
└───────────────────────────────────────────────────────────┘
```

---

## 5. データモデル / DB変更

### 5-1. b_cart_categories テーブル（新規）

B-CART APIから取得したカテゴリマスタを保持する。2階層構造（親カテゴリ→子カテゴリ）。

```sql
-- V__create_bcart_categories.sql
CREATE TABLE b_cart_categories (
  id                  INTEGER      PRIMARY KEY,  -- B-CART側のカテゴリID
  name                VARCHAR(255) NOT NULL,      -- カテゴリ名
  description         TEXT,                       -- カテゴリ説明（PC）
  rv_description      TEXT,                       -- カテゴリ説明（レスポンシブ）
  parent_category_id  INTEGER,                    -- 親カテゴリID（NULLなら親カテゴリ）
  header_image        VARCHAR(255),               -- ヘッダー画像パス
  banner_image        VARCHAR(255),               -- バナー画像パス
  menu_image          VARCHAR(255),               -- メニュー画像パス
  meta_title          VARCHAR(255),               -- META title
  meta_keywords       VARCHAR(255),               -- META keywords
  meta_description    VARCHAR(255),               -- META description
  priority            INTEGER      NOT NULL DEFAULT 0,  -- 表示優先度
  flag                INTEGER      NOT NULL DEFAULT 1,  -- 1=表示, 0=非表示
  b_cart_reflected     BOOLEAN     NOT NULL DEFAULT TRUE,  -- B-CARTへの反映済みフラグ
  updated_at          TIMESTAMP                   -- B-CART側更新日時
);

CREATE INDEX idx_bcart_categories_parent ON b_cart_categories(parent_category_id);

COMMENT ON TABLE b_cart_categories IS 'B-CARTカテゴリマスタ';
```

**現在のカテゴリ構造（49件: 親11 + 子38）:**
```
紙製品 (id=129)
  └─ ペーパータオル・ハンドタオル, トイレットペーパー, ティッシュ, キッチンペーパー・拭き取り, その他紙製品
洗剤・清掃用品 (id=130)
  └─ 台所用洗剤, トイレ・バス洗剤, 住居・床用洗剤, 漂白・カビ取り, 清掃用具
衛生・感染対策 (id=131)
  └─ 消毒・除菌剤, ハンドソープ・石けん, 除菌シート・ワイプ, ボディ・ヘアケア, 害虫対策
介護・排泄ケア (id=133)
  └─ 紙パンツ, パッド・尿取り, 介護・ケア用品, とろみ剤
袋・ゴミ袋・梱包資材 (id=132)
  └─ ゴミ袋, ポリ袋・レジ袋, チャック袋・小分け袋, 梱包テープ
PPE・手袋・マスク (id=134)
  └─ 使い捨て手袋, マスク, エプロン・ガウン・その他防護具
厨房消耗品・食品包装 (id=136)
  └─ ラップ, アルミホイル, クッキングシート, おしぼり
使い捨て容器・カトラリー (id=135)
  └─ 弁当容器・フードパック, コップ・カップ, カトラリー
トイレ用品 (id=137) └─ 消臭・芳香
事務・備品 (id=138) └─ 事務用品
その他 (id=139) └─ 未分類, 季節用品, 帳票・書類
```

### 5-2. m_sales_goods テーブル変更（既存テーブル拡張）

```sql
-- V__add_bcart_link_to_sales_goods.sql
ALTER TABLE m_sales_goods ADD COLUMN b_cart_product_set_id BIGINT;
ALTER TABLE w_sales_goods ADD COLUMN b_cart_product_set_id BIGINT;

COMMENT ON COLUMN m_sales_goods.b_cart_product_set_id IS 'B-CART商品セットID（b_cart_product_sets.id）';
COMMENT ON COLUMN w_sales_goods.b_cart_product_set_id IS 'B-CART商品セットID（b_cart_product_sets.id）';

CREATE INDEX idx_m_sales_goods_bcart ON m_sales_goods(b_cart_product_set_id)
  WHERE b_cart_product_set_id IS NOT NULL;
```

**設計判断**: 外部キー制約は**設けない**。B-CART同期タイミングと販売商品登録タイミングが異なるため、参照整合性はアプリケーション層で担保する。

### 5-3. b_cart_change_history テーブル（新規）

```sql
-- V__create_bcart_change_history.sql
CREATE TABLE b_cart_change_history (
  id              BIGSERIAL PRIMARY KEY,
  target_type     VARCHAR(30)  NOT NULL,  -- 'PRODUCT', 'PRODUCT_SET', 'CATEGORY'
  target_id       BIGINT       NOT NULL,
  change_type     VARCHAR(20)  NOT NULL,  -- 'PRICE', 'DESCRIPTION', 'STATUS', 'CATEGORY', 'BULK'
  field_name      VARCHAR(100),
  before_value    TEXT,
  after_value     TEXT,
  before_snapshot JSONB,
  change_reason   VARCHAR(500),
  changed_by      INTEGER      NOT NULL,
  changed_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  b_cart_reflected BOOLEAN     NOT NULL DEFAULT FALSE,
  b_cart_reflected_at TIMESTAMP
);

CREATE INDEX idx_bcart_history_target ON b_cart_change_history(target_type, target_id);
CREATE INDEX idx_bcart_history_unreflected ON b_cart_change_history(b_cart_reflected) WHERE b_cart_reflected = FALSE;

COMMENT ON TABLE b_cart_change_history IS 'B-CART商品/カテゴリ変更履歴（バックアップ）';
```

### 5-4. b_cart_scheduled_change テーブル（新規）

```sql
-- V__create_bcart_scheduled_change.sql
CREATE TABLE b_cart_scheduled_change (
  id                  BIGSERIAL PRIMARY KEY,
  product_set_id      BIGINT       NOT NULL,
  change_type         VARCHAR(20)  NOT NULL,  -- 'UNIT_PRICE', 'GROUP_PRICE', 'SPECIAL_PRICE'
  target_group_id     VARCHAR(50),
  target_customer_id  BIGINT,
  current_price       DECIMAL(12,2),
  new_price           DECIMAL(12,2) NOT NULL,
  effective_date      DATE         NOT NULL,
  status              VARCHAR(10)  NOT NULL DEFAULT 'PENDING',  -- PENDING / APPLIED / CANCELLED
  note                VARCHAR(500),
  created_by          INTEGER      NOT NULL,
  created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  applied_at          TIMESTAMP,
  del_flg             VARCHAR(1)   NOT NULL DEFAULT '0'
);

CREATE INDEX idx_bcart_scheduled_pending ON b_cart_scheduled_change(effective_date, status)
  WHERE status = 'PENDING';
```

### 5-5. テーブル関連図

```
b_cart_categories (カテゴリマスタ) ←── 2階層: parent_category_id
  │
  └─── category_id ←── b_cart_products (商品マスタ)
                          │
m_goods (商品マスタ)        └─── b_cart_product_sets (SKU/価格)
  │                               │        │
  └─ m_sales_goods ──bcart_id──→  │        ├── b_cart_group_price
                                  │        ├── b_cart_special_price
                                  │        └── b_cart_volume_discount
  m_partner_goods (仕入先商品)
    └─ 見積取込で仕入価格変更予定を管理（既存）

b_cart_change_history ←── 商品・カテゴリの全変更時に自動記録
b_cart_scheduled_change ←── 予定日にバッチで適用
```

---

## 6. API / UI 変更

### 6-1. BCartCategoryController（新規）

```
GET  /api/v1/bcart/categories
     - カテゴリ一覧（ツリー構造で返却）
     - Response: List<BCartCategoryTreeResponse>
       { id, name, priority, flag, children: [...] }

GET  /api/v1/bcart/categories/{categoryId}
     - カテゴリ詳細 + 所属商品数
     - Response: BCartCategoryDetailResponse

PUT  /api/v1/bcart/categories/{categoryId}
     - カテゴリ情報の更新（name, description, priority, flag, meta_*）
     - Request: BCartCategoryUpdateRequest
     - 処理: ① 変更前スナップショットを履歴に保存 ② DBを更新 ③ b_cart_reflected=false

POST /api/v1/bcart/categories
     - カテゴリ新規作成
     - Request: BCartCategoryCreateRequest { name, parentCategoryId, priority, flag }
     - 処理: ① B-CART APIに直接POST（即時反映） ② DB同期

PUT  /api/v1/bcart/categories/{categoryId}/priority
     - 表示順の変更（ドラッグ&ドロップ対応）
     - Request: { priority: number }

GET  /api/v1/bcart/categories/{categoryId}/products
     - カテゴリに属する商品一覧
```

### 6-2. BCartProductController（新規）

```
GET  /api/v1/bcart/products
     - B-CART商品一覧（検索: name, categoryId, flag）
     - Response: BCartProductResponse（products + sets + カテゴリ名結合）

GET  /api/v1/bcart/products/{productId}
     - B-CART商品詳細（商品情報 + 全セット + 紐付き販売商品 + カテゴリ情報）

PUT  /api/v1/bcart/products/{productId}/description
     - 商品説明の更新（description, catchCopy, prependText等）
     - 処理: ① 変更前スナップショットを履歴保存 ② DB更新 ③ b_cart_reflected=false

PUT  /api/v1/bcart/products/{productId}/category
     - 商品のカテゴリ変更
     - Request: { categoryId, subCategoryId }

PUT  /api/v1/bcart/product-sets/{setId}/price
     - 価格の更新
     - 処理: ① 変更前スナップショットを履歴保存 ② DB更新 ③ b_cart_price_reflected=false

POST /api/v1/bcart/product-sets/{setId}/scheduled-changes
     - 価格変更予定の登録

GET  /api/v1/bcart/product-sets/{setId}/scheduled-changes
     - 価格変更予定一覧

GET  /api/v1/bcart/products/{productId}/history
     - 変更履歴の取得

POST /api/v1/bcart/products/{productId}/link
     - 販売商品マスタとの紐付け
```

### 6-3. バッチ（新規・拡張）

#### 新規: B-CARTカテゴリ同期バッチ
- ジョブ名: `bCartCategorySync`
- トリガー: 手動 or 商品同期バッチの前ステップ
- 処理:
  1. `GET /api/v1/categories?limit=100` で全カテゴリ取得
  2. `b_cart_categories`テーブルにUPSERT
  3. B-CART側に存在しないレコードは`flag=0`に更新

#### 新規: B-CARTカテゴリ反映バッチ
- ジョブ名: `bCartCategoryUpdate`
- トリガー: 手動（バッチ管理画面から）
- 処理:
  1. `b_cart_categories`で`b_cart_reflected=false`のレコードを取得
  2. `PATCH /api/v1/categories/{id}` でB-CARTに送信（form-urlencoded形式）
  3. 成功したら`b_cart_reflected=true`に更新

#### 新規: B-CART商品説明反映バッチ
- ジョブ名: `bCartProductDescriptionUpdate`
- トリガー: 手動（バッチ管理画面から）
- 処理:
  1. `b_cart_change_history`で`b_cart_reflected=false` かつ `target_type='PRODUCT'`のレコードを取得
  2. 対象の`b_cart_products`を取得
  3. `PATCH /api/v1/products/{id}` で商品説明をB-CARTに送信（form-urlencoded形式）
  4. 成功したら`b_cart_reflected=true`に更新
  5. レート制限: 250件ごとに5分待機

#### 新規: 価格変更予定適用バッチ
- ジョブ名: `bCartScheduledPriceApply`
- トリガー: 日次（毎朝6:00など）
- 処理:
  1. `b_cart_scheduled_change`で`status='PENDING'` かつ `effective_date <= today`のレコード取得
  2. 変更前スナップショットを`b_cart_change_history`に保存
  3. `b_cart_product_sets`の該当価格を更新 + `b_cart_price_reflected=false`
  4. `b_cart_scheduled_change.status='APPLIED'`に更新
  5. 既存の`bCartGoodsPriceUpdate`バッチでB-CARTに反映

#### 既存拡張: BCartGoodsPriceUpdateTasklet
- 変更点: 反映成功時に`b_cart_change_history.b_cart_reflected=true`を更新

### 6-4. フロントエンド UI

#### B-CARTカテゴリマスタ画面 `/bcart-categories`

**画面レイアウト:**
```
┌─────────────────────────────────────────────────────────┐
│ B-CARTカテゴリマスタ                        [同期] [新規作成]│
├─────────────────────┬───────────────────────────────────┤
│                     │                                   │
│  カテゴリツリー       │  カテゴリ詳細                      │
│                     │                                   │
│  ▼ 紙製品 (5)        │  カテゴリ名: [ペーパータオル・ハンド] │
│    ├ ペーパータオル   │  親カテゴリ: 紙製品                 │
│    ├ トイレットペーパー│  表示優先度: [5              ]     │
│    ├ ティッシュ       │  状態: ◉表示 ○非表示               │
│    ├ キッチンペーパー  │                                   │
│    └ その他紙製品     │  説明（PC）:                       │
│  ▼ 洗剤・清掃用品 (5) │  ┌─────────────────────────┐     │
│    ├ 台所用洗剤      │  │                           │     │
│    ├ トイレ・バス洗剤 │  └─────────────────────────┘     │
│    ├ 住居・床用洗剤   │  説明（レスポンシブ）:              │
│    ├ 漂白・カビ取り   │  ┌─────────────────────────┐     │
│    └ 清掃用具        │  │                           │     │
│  ▼ 衛生・感染対策 (5) │  └─────────────────────────┘     │
│    ├ ...             │                                   │
│  ▼ 介護・排泄ケア (4) │  META情報:                        │
│  ▼ 袋・ゴミ袋 (4)    │  title:    [                    ] │
│  ▼ PPE・手袋 (3)     │  keywords: [                    ] │
│  ...                 │  description:[                  ] │
│                     │                                   │
│                     │  所属商品数: 15件  [商品一覧を見る]  │
│                     │                                   │
│                     │  [保存]  [B-CARTに反映]            │
│                     │                                   │
│                     │  ── 変更履歴 ──                    │
│                     │  2026-04-05 小田 名前変更           │
│                     │  2026-04-01 小田 新規作成           │
└─────────────────────┴───────────────────────────────────┘
```

**コンポーネント構成:**
- `app/(authenticated)/bcart-categories/page.tsx` → `components/pages/bcart-categories.tsx`
- 左ペイン: ツリービュー（shadcn/ui Collapsible + ドラッグ&ドロップで並び替え）
- 右ペイン: 選択したカテゴリの詳細編集フォーム
- ツリーの各ノード: カテゴリ名 + 子カテゴリ数バッジ + 表示/非表示アイコン

**操作フロー:**
1. ツリーでカテゴリを選択 → 右ペインに詳細が表示
2. 編集して「保存」→ DBに保存 + 変更履歴記録（`b_cart_reflected=false`）
3. 「B-CARTに反映」→ バッチ実行 or 直接API呼び出し（1件なら即時PATCH）
4. 「同期」ボタン → カテゴリ同期バッチを実行してB-CARTの最新状態を取得
5. 「新規作成」→ ダイアログ（カテゴリ名、親カテゴリ選択）

**TanStack Query設計:**
```typescript
// クエリキー
['bcart', 'categories']                    // ツリー一覧
['bcart', 'categories', categoryId]        // カテゴリ詳細
['bcart', 'categories', categoryId, 'products']  // 所属商品
['bcart', 'categories', categoryId, 'history']   // 変更履歴

// 型定義
interface BCartCategory {
  id: number
  name: string
  description: string
  rvDescription: string
  parentCategoryId: number | null
  metaTitle: string
  metaKeywords: string
  metaDescription: string
  priority: number
  flag: number  // 1=表示, 0=非表示
  bCartReflected: boolean
}

interface BCartCategoryTree extends BCartCategory {
  children: BCartCategoryTree[]
  productCount: number
}
```

#### B-CART商品一覧画面 `/bcart-products`
- 検索: 商品名、**カテゴリ（ツリーセレクト）**、表示/非表示
- テーブル: 商品名、**カテゴリ名**、セット数、基本単価、販売商品紐付き状態、最終同期日
- アクション: 詳細画面へ遷移

#### B-CART商品詳細画面 `/bcart-products/{productId}`
**タブ構成:**

1. **商品情報タブ**
   - 商品名、キャッチコピー、**カテゴリ（ツリーセレクト）**
   - 説明文（リッチテキスト編集）
   - フリースペース（上部/中部/下部、PC/レスポンシブ）
   - メタ情報（SEO）
   - 「AI生成」ボタン → 商品説明をAIで生成してプレビュー
   - 「保存」→ DB更新 + 履歴保存

2. **価格・セット一覧タブ**
   - セットごとの単価、仕入価格、グループ価格、特別価格
   - 「価格変更」→ インライン編集 or ダイアログ
   - 「価格変更予定を登録」→ 日付指定ダイアログ

3. **販売商品紐付けタブ**
   - 紐付き済みの販売商品マスタ一覧
   - 「紐付け追加」→ 販売商品検索ダイアログ

4. **変更履歴タブ**
   - 変更日時、変更者、変更種別、変更フィールド、before/after
   - B-CART反映状態（反映済み/未反映）

---

## 7. エッジケース

| ケース | 対応 |
|--------|------|
| B-CART API側で商品が削除された | 同期バッチで`flag='非表示'`に更新。物理削除しない |
| B-CART API側でカテゴリが削除された | `flag=0`に更新。商品のcategory_idは維持（参照先なし警告） |
| 親カテゴリを削除しようとした | 子カテゴリがある場合はエラー。先に子を移動/削除 |
| 価格変更予定日に同じセットの予定が複数ある | effective_dateが早い順に適用。UIでも警告表示 |
| B-CART APIがダウンしている | `b_cart_reflected=false`のまま残す。次回バッチで再試行 |
| 説明文に長大なHTMLが含まれる | 65535文字制限をバリデーション |
| 同期中にユーザーが同じ商品/カテゴリを編集 | 楽観的ロック（`updated_at`チェック）でコンフリクト検出 |

---

## 8. リスクと対策

| リスク | 影響度 | 対策 |
|--------|--------|------|
| ~~B-CART API PATCH非対応~~ | ~~高~~ | **解消済み**: APIテストで`description`, `catch_copy`等の更新を確認（2026-04-06） |
| B-CART API `PATCH /categories/{id}` の仕様未確認 | 中 | 商品PATCHと同様にform-urlencoded形式でテスト。未対応なら管理画面での直接反映に限定 |
| JWTトークンの期限切れ | 中 | ローカル運用では問題なし。将来クラウド移行時に要検討 |
| 大量の変更履歴によるDB肥大化 | 低 | `before_snapshot`はJSONB圧縮。必要に応じて古いスナップショットを定期削除 |

---

## 9. ロールアウト計画

### Phase 1: カテゴリマスタ + 基盤（1週間）
- [ ] DB マイグレーション（b_cart_categories, b_cart_change_history, b_cart_scheduled_change）
- [ ] BCartCategories Entity / Repository / Service
- [ ] カテゴリ同期バッチ（GET /api/v1/categories → DB）
- [ ] BCartCategoryController API
- [ ] カテゴリマスタ画面（ツリー + 詳細編集）

### Phase 2: 商品説明管理（1週間）
- [ ] 変更履歴保存ロジック（共通Service）
- [ ] 商品説明更新API + 履歴保存
- [ ] 商品説明反映バッチ（PATCH /products/{id}、form-urlencoded形式）
- [ ] B-CART商品一覧/詳細画面

### Phase 3: 価格管理（1週間）
- [ ] 価格更新API + 履歴保存
- [ ] 価格変更予定の登録/一覧API
- [ ] 既存`BCartGoodsPriceUpdateTasklet`の拡張（履歴反映フラグ更新）
- [ ] 価格変更予定適用バッチ
- [ ] 価格変更予定UI

### Phase 4: 販売商品連携（1週間）
- [ ] m_sales_goods / w_sales_goods への b_cart_product_set_id 追加
- [ ] 販売商品詳細画面にB-CART情報表示
- [ ] 紐付け管理UI

### Phase 5: AI連携（後日）
- [ ] AI商品説明生成エンドポイント
- [ ] 商品詳細画面に「AI生成」ボタン

### ロールバック計画
- DBマイグレーションは全て`ADD COLUMN`/`CREATE TABLE`のため、ロールバックは`DROP`で対応可能
- 既存テーブルのデータは一切変更しない
- B-CART APIへの反映はバッチの手動実行制御で停止可能
