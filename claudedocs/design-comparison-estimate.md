# 設計書: 比較見積機能（複数基準品 × 代替提案グループ構造）

- **作成日**: 2026-04-10
- **作成者**: kazuki + Claude
- **ステータス**: レビュー反映済み

---

## 1. Background / Problem

### 現状の課題
- 既存の比較見積画面（`/estimates/compare`）は sessionStorage 上の一時検討ツールで、データを保存・提出できない
- 「基準品1つ + 代替品9つ」のフラットリスト構造であり、実業務の「**複数の商品それぞれに対して代替提案を並べて比較検討する**」ニーズと合致しない
- 見積作成後に「この見積の各商品に対する代替候補を検討して得意先に提案したい」という導線がない

### あるべき姿
- **複数基準品 × 各代替提案のグループ構造** で比較検討できる
- **DB に永続化** し、見積と同様にステータス管理・提出ができる
- 作成済み見積から各明細行を基準品として比較見積を生成できる

### 用語定義

| 用語 | 意味 |
|---|---|
| **比較見積** | 複数の基準品グループをまとめた1つのドキュメント |
| **基準品グループ** | 1つの基準品（現行商品）と、そのN個の代替提案をまとめた単位 |
| **基準品** | グループの起点となる商品。見積明細の各行がこれに相当 |
| **代替提案** | 基準品に対する代替候補商品。粗利は基準品との差分で表示 |

---

## 2. Requirements

### 機能要件

| # | 要件 |
|---|---|
| FR-1 | 比較見積を新規作成できる（空 or 見積からの引継ぎ） |
| FR-2 | 見積詳細画面から「比較見積を作成」で各明細行を基準品として一括生成できる |
| FR-3 | 各基準品グループに対して、商品検索ダイアログから代替提案を追加できる |
| FR-4 | 代替提案の粗利は**その所属する基準品との差分**で表示する |
| FR-5 | 比較見積を DB に保存できる（作成・更新・論理削除） |
| FR-6 | 比較見積にステータスを持ち、提出フロー（作成→提出済→修正→…）を管理できる |
| FR-7 | 比較見積の一覧画面で検索・閲覧できる |
| FR-8 | 比較見積の詳細画面で各グループの比較表をセクション縦並びで表示する |
| FR-9 | 基準品グループの追加・削除ができる |
| FR-10 | 基準品・代替提案ともに未登録商品（goodsNo=null）をサポートする |
| FR-11 | 比較見積そのものが得意先に提出する見積書である。印刷/PDF出力で比較検討用のフォーマットで出力する |
| FR-12 | 基準品グループ数・代替提案数に上限なし |
| FR-13 | 印刷対応（得意先向け表示: 仕入情報を非表示にするモード） |

### 非機能要件
- **パフォーマンス**: 比較見積の取得は 50 グループ × 10 代替提案 = 500 行程度を想定。N+1 回避
- **セキュリティ**: 既存の店舗権限チェック（CustomService）を適用
- **データ整合性**: 比較見積と見積の関連は参照のみ（FK ではなく source_estimate_no で追跡）。見積が削除されても比較見積は残る

---

## 3. Constraints

### 技術制約
- Flyway は未使用。SQL スクリプトは `backend/src/main/resources/sql/` に手動配置し、手動実行
- Sequence 戦略: `SERIAL` (PostgreSQL auto-increment) を使用（`t_quote_import_header` と同パターン）
- `TEstimateDetail` に `supplier_no` カラムが無いため、見積からの引継ぎ時に仕入先情報は復元不可
- エンティティは `AbstractCompanyEntity` を継承し `ICompanyEntity` を実装する既存パターンに従う
- `CustomService` の insert/update/delete で監査フィールド・`del_flg` を自動管理

### ビジネス制約
- 既存の見積機能（`t_estimate` / `t_estimate_detail`）には一切変更を加えない
- 既存の `/estimates/compare` 画面は**廃止**し、新機能で差し替える

---

## 4. Proposed Solution

### アーキテクチャ概要

```
[見積詳細画面]                         [比較見積機能（新規）]
/estimates/{no}                        /estimate-comparisons
  │                                       │
  │ 「比較見積を作成」ボタン               │
  └──── POST /from-estimate/{no} ─────────┘
                                          │
                                    ┌─────┴──────┐
                                    │  バックエンド │
                                    ├────────────┤
                                    │ Controller  │  EstimateComparisonController
                                    │ Service     │  EstimateComparisonService
                                    │ Repository  │  TEstimateComparisonRepository
                                    │             │  TComparisonGroupRepository
                                    │             │  TComparisonDetailRepository
                                    │ Entity      │  TEstimateComparison (ヘッダ)
                                    │             │  TComparisonGroup (基準品グループ)
                                    │             │  TComparisonDetail (代替提案)
                                    └─────────────┘
                                          │
                                    ┌─────┴──────┐
                                    │  DB (3テーブル) │
                                    │ t_estimate_comparison │
                                    │ t_comparison_group    │
                                    │ t_comparison_detail   │
                                    └──────────────────────┘
```

### Layer 構成
- **Controller**: リクエスト受付、バリデーション、レスポンス変換
- **Service**: ビジネスロジック（見積からの生成、ステータス遷移、粗利計算用データ取得）
- **Repository**: JPA Repository（Spring Data JPA）
- **Entity**: DB テーブルと1:1 マッピング

### Entity PK 戦略（レビュー ID-4 対応）
- `TEstimateComparison`: `@SequenceGenerator(sequenceName="t_estimate_comparison_comparison_no_seq")` + `@GeneratedValue(strategy=GenerationType.IDENTITY)` — `TEstimate` と同パターン
- `TComparisonGroup`: `@IdClass(TComparisonGroupPK.class)` 複合PK `(comparison_no, group_no)` — `TEstimateDetail` と同パターン。`group_no` は Service 層で `groupNo++` 連番
- `TComparisonDetail`: `@IdClass(TComparisonDetailPK.class)` 複合PK `(comparison_no, group_no, detail_no)` — `detail_no` は Service 層で `detailNo++` 連番

### 更新時の削除戦略（レビュー ID-5 対応）
PUT `/estimate-comparisons/{no}` は**全置換方式**:
1. 既存の全 `t_comparison_detail`（該当 comparison_no）を論理削除
2. 既存の全 `t_comparison_group`（該当 comparison_no）を論理削除
3. リクエストの groups/details を `groupNo=1` / `detailNo=1` から再 insert
4. ヘッダ (`t_estimate_comparison`) は update
- これは `EstimateCreateService.updateEstimate()` と同じパターン

### 粗利計算の責務（レビュー ID-6 対応）
- **保存値**: `t_comparison_detail.profit_rate` に保存時点の粗利率を記録（参考値）
- **表示時**: フロントで基準品の `basePurchasePrice`/`baseGoodsPrice` と代替提案の `purchasePrice`/`proposedPrice` から動的に再計算し差分表示
- 既存の `lib/estimate-calc.ts`（`calcProfit`, `calcProfitRate`, `calcCaseProfit`）をそのまま再利用

---

## 5. Data Model / DB Changes

### 5-1. テーブル設計

#### `t_estimate_comparison`（比較見積ヘッダ）

| カラム | 型 | PK | NOT NULL | デフォルト | 説明 |
|---|---|---|---|---|---|
| comparison_no | SERIAL | ✓ | ✓ | auto | 比較見積番号 |
| shop_no | INTEGER | | ✓ | | 店舗番号 |
| partner_no | INTEGER | | | | 得意先番号（任意） |
| destination_no | INTEGER | | | | 納品先番号（任意） |
| comparison_date | DATE | | ✓ | | 比較見積作成日 |
| comparison_status | VARCHAR(2) | | ✓ | '00' | ステータス |
| source_estimate_no | INTEGER | | | | 元見積番号（見積から作成時） |
| title | VARCHAR(200) | | | | タイトル（任意） |
| note | TEXT | | | | 社内メモ |
| company_no | INTEGER | | ✓ | | 会社番号 |
| del_flg | VARCHAR(1) | | ✓ | '0' | 論理削除 |
| add_date_time | TIMESTAMP | | | CURRENT_TIMESTAMP | 作成日時 |
| add_user_no | INTEGER | | | | 作成者 |
| modify_date_time | TIMESTAMP | | | | 更新日時 |
| modify_user_no | INTEGER | | | | 更新者 |

#### `t_comparison_group`（基準品グループ）

| カラム | 型 | PK | NOT NULL | デフォルト | 説明 |
|---|---|---|---|---|---|
| comparison_no | INTEGER | ✓ | ✓ | | FK → t_estimate_comparison |
| group_no | INTEGER | ✓ | ✓ | | グループ連番（手動管理） |
| base_goods_no | INTEGER | | | | 基準品の商品番号（null=未登録） |
| base_goods_code | VARCHAR(50) | | | | 基準品の商品コード |
| base_goods_name | VARCHAR(200) | | ✓ | | 基準品の商品名 |
| base_specification | VARCHAR(200) | | | | 基準品の規格 |
| base_purchase_price | DECIMAL(12,2) | | | | 基準品の仕入単価 |
| base_goods_price | DECIMAL(12,2) | | | | 基準品の現行販売単価 |
| base_contain_num | DECIMAL(12,2) | | | | 基準品の入数 |
| display_order | INTEGER | | ✓ | | 表示順 |
| group_note | TEXT | | | | グループ備考 |
| shop_no | INTEGER | | ✓ | | |
| company_no | INTEGER | | ✓ | | |
| del_flg | VARCHAR(1) | | ✓ | '0' | |
| add_date_time | TIMESTAMP | | | CURRENT_TIMESTAMP | |
| add_user_no | INTEGER | | | | |
| modify_date_time | TIMESTAMP | | | | |
| modify_user_no | INTEGER | | | | |

#### `t_comparison_detail`（代替提案）

| カラム | 型 | PK | NOT NULL | デフォルト | 説明 |
|---|---|---|---|---|---|
| comparison_no | INTEGER | ✓ | ✓ | | FK → t_estimate_comparison |
| group_no | INTEGER | ✓ | ✓ | | FK → t_comparison_group |
| detail_no | INTEGER | ✓ | ✓ | | 明細連番（手動管理） |
| goods_no | INTEGER | | | | 商品番号（null=未登録） |
| goods_code | VARCHAR(50) | | | | 商品コード |
| goods_name | VARCHAR(200) | | ✓ | | 商品名 |
| specification | VARCHAR(200) | | | | 規格 |
| purchase_price | DECIMAL(12,2) | | | | 仕入単価 |
| proposed_price | DECIMAL(12,2) | | | | 提案販売単価 |
| contain_num | DECIMAL(12,2) | | | | 入数 |
| profit_rate | DECIMAL(5,2) | | | | 粗利率 |
| detail_note | TEXT | | | | 明細備考 |
| display_order | INTEGER | | ✓ | | 表示順 |
| supplier_no | INTEGER | | | | 仕入先番号（未登録品用） |
| shop_no | INTEGER | | ✓ | | |
| company_no | INTEGER | | ✓ | | |
| del_flg | VARCHAR(1) | | ✓ | '0' | |
| add_date_time | TIMESTAMP | | | CURRENT_TIMESTAMP | |
| add_user_no | INTEGER | | | | |
| modify_date_time | TIMESTAMP | | | | |
| modify_user_no | INTEGER | | | | |

### 5-2. ER 図（テキスト）

```
t_estimate_comparison (1) ──── (*) t_comparison_group (1) ──── (*) t_comparison_detail
       │                              │
       │ source_estimate_no           │ base_goods_no → m_goods (任意)
       ▼                              │
  t_estimate (参照のみ)                ▼
                                 m_goods (任意)
```

### 5-3. ステータス定義

見積の `EstimateStatus` と同じ 10 種を採用:

| コード | ラベル | 説明 |
|---|---|---|
| 00 | 作成 | 新規作成・編集中 |
| 10 | 提出済 | 得意先に提出 |
| 20 | 修正 | 提出後の修正中 |
| 30 | 修正後提出済 | 修正版を再提出 |
| 40 | 他同グループ提出済 | 同グループの他比較見積を提出済 |
| 50 | 削除 | 論理削除 |
| 60 | 都度見積のため不要 | |
| 70 | 価格反映済 | 比較結果を価格に反映済 |
| 90 | 入札関係のため不要 | |
| 99 | 取引なし | |

### 5-4. SQL スクリプト

`backend/src/main/resources/sql/create_estimate_comparison_tables.sql`:

```sql
-- 比較見積ヘッダ
CREATE TABLE IF NOT EXISTS t_estimate_comparison (
    comparison_no SERIAL PRIMARY KEY,
    shop_no INTEGER NOT NULL,
    partner_no INTEGER,
    destination_no INTEGER,
    comparison_date DATE NOT NULL,
    comparison_status VARCHAR(2) NOT NULL DEFAULT '00',
    source_estimate_no INTEGER,
    title VARCHAR(200),
    note TEXT,
    company_no INTEGER NOT NULL,
    del_flg VARCHAR(1) NOT NULL DEFAULT '0',
    add_date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no INTEGER,
    modify_date_time TIMESTAMP,
    modify_user_no INTEGER
);

-- 基準品グループ
CREATE TABLE IF NOT EXISTS t_comparison_group (
    comparison_no INTEGER NOT NULL REFERENCES t_estimate_comparison(comparison_no),
    group_no INTEGER NOT NULL,
    base_goods_no INTEGER,
    base_goods_code VARCHAR(50),
    base_goods_name VARCHAR(200) NOT NULL,
    base_specification VARCHAR(200),
    base_purchase_price DECIMAL(12,2),
    base_goods_price DECIMAL(12,2),
    base_contain_num DECIMAL(12,2),
    display_order INTEGER NOT NULL,
    group_note TEXT,
    shop_no INTEGER NOT NULL,
    company_no INTEGER NOT NULL,
    del_flg VARCHAR(1) NOT NULL DEFAULT '0',
    add_date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no INTEGER,
    modify_date_time TIMESTAMP,
    modify_user_no INTEGER,
    PRIMARY KEY (comparison_no, group_no)
);

-- 代替提案明細
CREATE TABLE IF NOT EXISTS t_comparison_detail (
    comparison_no INTEGER NOT NULL,
    group_no INTEGER NOT NULL,
    detail_no INTEGER NOT NULL,
    goods_no INTEGER,
    goods_code VARCHAR(50),
    goods_name VARCHAR(200) NOT NULL,
    specification VARCHAR(200),
    purchase_price DECIMAL(12,2),
    proposed_price DECIMAL(12,2),
    contain_num DECIMAL(12,2),
    profit_rate DECIMAL(5,2),
    detail_note TEXT,
    display_order INTEGER NOT NULL,
    supplier_no INTEGER,
    shop_no INTEGER NOT NULL,
    company_no INTEGER NOT NULL,
    del_flg VARCHAR(1) NOT NULL DEFAULT '0',
    add_date_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    add_user_no INTEGER,
    modify_date_time TIMESTAMP,
    modify_user_no INTEGER,
    PRIMARY KEY (comparison_no, group_no, detail_no),
    FOREIGN KEY (comparison_no, group_no) REFERENCES t_comparison_group(comparison_no, group_no)
);

CREATE INDEX IF NOT EXISTS idx_comparison_group_comparison_no ON t_comparison_group(comparison_no);
CREATE INDEX IF NOT EXISTS idx_comparison_detail_comparison_group ON t_comparison_detail(comparison_no, group_no);
CREATE INDEX IF NOT EXISTS idx_estimate_comparison_shop ON t_estimate_comparison(shop_no);
CREATE INDEX IF NOT EXISTS idx_estimate_comparison_status ON t_estimate_comparison(comparison_status);
```

---

## 6. API / UI Changes

### 6-1. バックエンド API

#### エンドポイント一覧

| パス | メソッド | 用途 | リクエスト | レスポンス |
|---|---|---|---|---|
| `/api/v1/estimate-comparisons` | GET | 一覧検索 | クエリパラメータ | `List<ComparisonListResponse>` |
| `/api/v1/estimate-comparisons/{no}` | GET | 詳細取得 | - | `ComparisonResponse` |
| `/api/v1/estimate-comparisons` | POST | 新規作成 | `ComparisonCreateRequest` | `ComparisonResponse` |
| `/api/v1/estimate-comparisons/{no}` | PUT | 更新 | `ComparisonCreateRequest` | `ComparisonResponse` |
| `/api/v1/estimate-comparisons/{no}` | DELETE | 論理削除 | - | - |
| `/api/v1/estimate-comparisons/{no}/status` | PUT | ステータス更新 | `{ status }` | `ComparisonResponse` |
| `/api/v1/estimate-comparisons/from-estimate/{estimateNo}` | POST | 見積から生成 | - | `ComparisonResponse` |

#### 検索パラメータ（GET 一覧）

| パラメータ | 型 | 説明 |
|---|---|---|
| shopNo | Integer | 店舗 |
| partnerNo | Integer | 得意先 |
| comparisonStatus | String[] | ステータス（複数可） |
| comparisonDateFrom | String | 作成日 from |
| comparisonDateTo | String | 作成日 to |
| title | String | タイトル部分一致 |

#### レスポンス DTO

```java
// 一覧用（軽量）
@Data @Builder
public class ComparisonListResponse {
    private Integer comparisonNo;
    private Integer shopNo;
    private String partnerName;
    private String comparisonDate;
    private String comparisonStatus;
    private String title;
    private Integer sourceEstimateNo;
    private int groupCount; // 基準品グループ数
}

// 詳細用（全データ）
@Data @Builder
public class ComparisonResponse {
    private Integer comparisonNo;
    private Integer shopNo;
    private Integer partnerNo;
    private String partnerName;
    private Integer destinationNo;
    private String destinationName;
    private String comparisonDate;
    private String comparisonStatus;
    private Integer sourceEstimateNo;
    private String title;
    private String note;
    private List<ComparisonGroupResponse> groups;
}

@Data @Builder
public class ComparisonGroupResponse {
    private Integer groupNo;
    // 基準品情報
    private Integer baseGoodsNo;
    private String baseGoodsCode;
    private String baseGoodsName;
    private String baseSpecification;
    private BigDecimal basePurchasePrice;
    private BigDecimal baseGoodsPrice;
    private BigDecimal baseContainNum;
    private int displayOrder;
    private String groupNote;
    // 代替提案リスト
    private List<ComparisonDetailResponse> details;
}

@Data @Builder
public class ComparisonDetailResponse {
    private Integer detailNo;
    private Integer goodsNo;
    private String goodsCode;
    private String goodsName;
    private String specification;
    private BigDecimal purchasePrice;
    private BigDecimal proposedPrice;
    private BigDecimal containNum;
    private BigDecimal profitRate;
    private String detailNote;
    private int displayOrder;
    private Integer supplierNo;
}
```

#### リクエスト DTO

```java
@Data
public class ComparisonCreateRequest {
    @NotNull private Integer shopNo;
    private Integer partnerNo;
    private Integer destinationNo;
    @NotBlank private String comparisonDate;
    private String title;
    private String note;
    @NotEmpty @Valid private List<ComparisonGroupCreateRequest> groups;
}

@Data
public class ComparisonGroupCreateRequest {
    private Integer baseGoodsNo;
    private String baseGoodsCode;
    @NotBlank private String baseGoodsName;
    private String baseSpecification;
    private BigDecimal basePurchasePrice;
    private BigDecimal baseGoodsPrice;
    private BigDecimal baseContainNum;
    private int displayOrder;
    private String groupNote;
    @Valid private List<ComparisonDetailCreateRequest> details;
}

@Data
public class ComparisonDetailCreateRequest {
    private Integer goodsNo;
    private String goodsCode;
    @NotBlank private String goodsName;
    private String specification;
    private BigDecimal purchasePrice;
    private BigDecimal proposedPrice;
    private BigDecimal containNum;
    private String detailNote;
    private int displayOrder;
    private Integer supplierNo;
}
```

#### 「見積から生成」ロジック（POST `/from-estimate/{estimateNo}`）

1. `GET /estimates/{estimateNo}` で見積取得
2. 見積ヘッダ → `t_estimate_comparison` ヘッダ作成（shopNo, partnerNo, destinationNo, source_estimate_no）
3. 各 `t_estimate_detail` 行 → `t_comparison_group` 1 件作成:
   - `base_goods_no = detail.goodsNo`
   - `base_goods_code = detail.goodsCode`
   - `base_goods_name = detail.goodsName`
   - `base_purchase_price = detail.purchasePrice`
   - `base_goods_price = detail.goodsPrice`
   - `base_contain_num = detail.containNum`（changeContainNum 優先）
4. `t_comparison_detail` は空（代替提案は後から追加）
5. ステータス = `00`（作成）
6. 生成した `ComparisonResponse` を返却

### 6-2. フロントエンド UI

#### ルーティング

| パス | ページ |
|---|---|
| `/estimate-comparisons` | 一覧画面 |
| `/estimate-comparisons/create` | 新規作成フォーム |
| `/estimate-comparisons/{comparisonNo}` | 詳細表示 |
| `/estimate-comparisons/{comparisonNo}/edit` | 編集フォーム |

#### 一覧画面（`/estimate-comparisons`）

既存の見積一覧と同パターン:
- 検索フォーム（shopNo, partnerNo, status, date range, title）
- DataTable: 比較見積番号, 作成日, 得意先, タイトル, グループ数, ステータス
- 行クリックで詳細へ遷移

#### 詳細画面（`/estimate-comparisons/{comparisonNo}`）

```
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
比較見積 #123                [修正] [削除] [印刷] [戻る]
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 比較見積番号: 123
 作成日: 2026/04/10         得意先: いしい記念病院
 ステータス: [作成]          元見積: #570
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

╔═══════════════════════════════════════════╗
║ グループ1: 花王 除菌洗浄剤 5L             ║
╠═══════════════════════════════════════════╣
║           │ 基準品      │ 代替1       │ 代替2       ║
║───────────┼────────────┼────────────┼────────────║
║ 商品コード │ KAO-001    │ LION-001   │ SARAYA-001 ║
║ 商品名     │ 花王除菌    │ ライオン除菌│ サラヤ除菌  ║
║ 規格       │ 5L         │ 4.5L       │ 5L         ║
║ 仕入単価   │ ¥1,200     │ ¥1,050     │ ¥950       ║
║ 販売単価   │ ¥1,900     │ ¥1,750     │ ¥1,600     ║
║ 入数       │ 3          │ 3          │ 4          ║
║ 粗利額     │ ¥700       │ ¥700 (±0)  │ ¥650 (↓50) ║
║ 粗利率     │ 36.8%      │ 40.0% (↑)  │ 40.6% (↑)  ║
║ ケース粗利 │ ¥2,100     │ ¥2,100(±0) │ ¥2,600 (↑) ║
╚═══════════════════════════════════════════╝

╔═══════════════════════════════════════════╗
║ グループ2: ライオン 食器洗剤 4.5L          ║
╠═══════════════════════════════════════════╣
║           │ 基準品      │ 代替1       │          ║
║ ...       │ ...        │ ...        │          ║
╚═══════════════════════════════════════════╝
```

#### 作成/編集フォーム（`/estimate-comparisons/create` `/edit`）

- ヘッダ: shopNo, partnerNo, destinationNo, comparisonDate, title, note
- グループリスト:
  - 各グループ: 基準品情報（商品コード入力 or 検索ダイアログ）
  - 各グループ内: 代替提案テーブル（行追加/削除、商品検索ダイアログ）
  - 「グループ追加」ボタンで基準品グループを追加
- 保存ボタンで `POST` / `PUT`

#### 見積詳細画面への導線（`/estimates/{estimateNo}`）

- 「比較見積を作成」ボタン追加（**見積のステータスが** `00`(作成) or `20`(修正) の場合のみ表示）
- クリック → `POST /api/v1/estimate-comparisons/from-estimate/{estimateNo}`
- 成功 → `/estimate-comparisons/{生成されたcomparisonNo}/edit` に遷移

#### コンポーネント階層

```
app/(authenticated)/estimate-comparisons/
├── page.tsx                         → ComparisonListPage
├── create/page.tsx                  → ComparisonFormPage
├── [comparisonNo]/
│   ├── page.tsx                     → ComparisonDetailPage
│   └── edit/page.tsx                → ComparisonFormPage (edit mode)

components/pages/estimate-comparison/
├── index.tsx                        → ComparisonListPage
├── detail.tsx                       → ComparisonDetailPage
├── form.tsx                         → ComparisonFormPage
├── ComparisonGroupSection.tsx       → 1つの基準品グループUI
├── ComparisonGroupTable.tsx         → グループ内の比較表
└── ComparisonGroupForm.tsx          → グループ内の編集フォーム

types/
└── estimate-comparison.ts           → 型定義
```

#### TanStack Query キー設計

```ts
['estimate-comparisons']                     // 一覧
['estimate-comparison', comparisonNo]        // 詳細
```

---

## 7. Edge Cases

| # | シナリオ | 挙動 |
|---|---|---|
| EC-1 | 基準品グループが0件で保存 | バリデーションエラー（最低1グループ必須） |
| EC-2 | 代替提案が0件のグループ | 許容（基準品だけ登録して後で代替追加可能） |
| EC-3 | 元見積が論理削除されている | `source_estimate_no` は参照のみなので影響なし。画面上は「元見積: #xxx（削除済）」表示 |
| EC-4 | 見積から生成時に見積明細が0件 | 空のグループ0件の比較見積を作成。ユーザーが手動でグループ追加 |
| EC-5 | 未登録商品の基準品 | `base_goods_no = null` で保存。商品名は必須 |
| EC-6 | 未登録商品の代替提案 | `goods_no = null` で保存。supplierNo は任意 |
| EC-7 | 同じ見積から2回以上比較見積を生成 | 重複チェックなし。別の比較見積として独立生成（ユースケース: 異なるパターンで検討） |
| EC-8 | 提出済（10）のステータスで編集しようとする | フロント側でボタン非表示。API 側でもステータスチェック（許可は 00/20 のみ） |
| EC-9 | 印刷/PDF出力時の得意先向けレイアウト | 仕入単価・粗利を非表示にし、商品名・規格・販売単価・入数のみ表示。見積書としてのフォーマット |

---

## 8. Risks and Mitigations

| # | リスク | 影響 | 緩和策 |
|---|---|---|---|
| R-1 | 3テーブル JOIN による N+1 | パフォーマンス劣化 | `@OneToMany(fetch = LAZY)` + Service 層で `JOIN FETCH` クエリ |
| R-2 | 基準品グループが増大して画面が長い | UX 劣化 | セクション折りたたみ（アコーディオン）を将来オプション追加 |
| R-3 | 見積と比較見積のステータス体系が乖離 | 運用混乱 | 初期は最小限のステータスのみ。必要に応じて追加 |
| R-4 | 既存 `/estimates/compare` の廃止タイミング | 移行期のユーザー混乱 | Phase 2 で新機能公開と同時に旧画面を廃止。サイドバーメニュー「比較見積」を新画面に差し替え |
| R-5 | group_no / detail_no の採番 | — | `TEstimateDetail.estimateDetailNo` と同パターン: `@Transactional` 内で 1 始まりの連番 (`groupNo++` / `detailNo++`) で全件一括 insert。更新時は全グループ・明細を論理削除→再 insert する全置換方式のため、MAX+1 方式は使わず、レースコンディションは発生しない |

---

## 9. Rollout Plan

### フェーズ分割

この機能は大きいため、**3フェーズに分割**して段階的にリリースする。

#### Phase 1: DB + バックエンド API + 見積からの生成
- SQL スクリプト実行（テーブル作成）
- Entity / Repository / Service / Controller 実装
- `POST /from-estimate/{estimateNo}` で見積→比較見積を生成
- **API レベルの動作確認まで**

#### Phase 2: フロントエンド — 一覧 + 詳細 + 編集フォーム + 印刷/PDF
- 比較見積一覧画面
- 比較見積詳細画面（セクション縦並び表示）
- 比較見積作成/編集フォーム（グループ追加、代替提案追加）
- 見積詳細画面への「比較見積を作成」ボタン追加
- 印刷対応（得意先向け表示: 仕入情報非表示モード）— 比較見積は提出用見積書なので必須
- PDF出力
- 既存 `/estimates/compare` の廃止（サイドバーメニュー「比較見積」を新機能に差し替え）

#### Phase 3: 発展機能（必要に応じて）
- セクション折りたたみ（アコーディオン UI）
- 比較見積の複製機能

### ロールバック手順
- Phase 1: テーブル DROP + Controller 削除
- Phase 2: ページ・コンポーネント削除、サイドバーメニュー削除
- Phase 3: 各機能単位で削除可能

---

## 10. 確認事項（解決済み）

| # | 質問 | 回答 |
|---|---|---|
| 1 | サイドバーメニュー名 | 「比較見積」で既存と差し替え。旧画面は廃止 |
| 2 | ステータス体系 | 見積と同じ 10 種 |
| 3 | 得意先ユーザーへの閲覧権限 | 不要（得意先ユーザーはログインしない） |
| 4 | FR-11 の意味 | 比較見積そのものが得意先に提出する見積書。採用→別見積の導線は不要 |
| 5 | 既存 `/estimates/compare` の扱い | 廃止。新機能で差し替え |
