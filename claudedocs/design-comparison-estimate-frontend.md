# 詳細設計: 比較見積 Phase 2 フロントエンド実装

- **作成日**: 2026-04-10
- **親設計書**: `claudedocs/design-comparison-estimate.md` §6-2
- **ステータス**: レビュー反映済み

---

## 1. 実装スコープ

| # | 項目 | 説明 |
|---|---|---|
| F-1 | 型定義 | `types/estimate-comparison.ts` — API レスポンス/リクエスト型 + ステータス定数 |
| F-2 | 一覧画面 | `/estimate-comparisons` — 見積一覧と同パターン |
| F-3 | 詳細画面 | `/estimate-comparisons/{comparisonNo}` — グループ×比較表セクション縦並び |
| F-4 | 作成/編集フォーム | `/estimate-comparisons/create`, `/{comparisonNo}/edit` — グループ追加、代替提案追加 |
| F-5 | 見積→比較見積生成 | 見積詳細画面に「比較見積を作成」ボタン追加 |
| F-6 | 印刷対応 | 得意先向け（仕入情報非表示）の `print:` レイアウト |
| F-7 | サイドバー差し替え | 「比較見積」→ `/estimate-comparisons` に変更、旧ページ廃止 |

---

## 2. ファイル構成

```
frontend/
├── types/
│   └── estimate-comparison.ts              # [新規] 型定義
├── app/(authenticated)/estimate-comparisons/
│   ├── page.tsx                             # [新規] 一覧ルート
│   ├── create/page.tsx                      # [新規] 作成ルート
│   └── [comparisonNo]/
│       ├── page.tsx                         # [新規] 詳細ルート
│       └── edit/page.tsx                    # [新規] 編集ルート
├── components/pages/estimate-comparison/
│   ├── index.tsx                            # [新規] 一覧ページ
│   ├── detail.tsx                           # [新規] 詳細ページ
│   ├── form.tsx                             # [新規] 作成/編集フォーム
│   ├── ComparisonGroupSection.tsx           # [新規] グループ表示セクション
│   └── ComparisonGroupForm.tsx              # [新規] グループ編集フォーム
├── components/pages/estimate/detail.tsx     # [修正] 「比較見積を作成」ボタン追加
├── components/layout/Sidebar.tsx            # [修正] メニュー href 変更
└── app/(authenticated)/estimates/compare/page.tsx  # [削除]
```

---

## 3. 型定義（`types/estimate-comparison.ts`）

### レスポンス型

```ts
// API: GET /estimate-comparisons (一覧) — ComparisonResponse を共用
// 一覧では groups は空リスト、groupCount でグループ数を取得
// ※ B-1 対応: バックエンド ComparisonResponse に groupCount フィールド追加済み

// API: GET /estimate-comparisons/{no} (詳細) + GET /estimate-comparisons (一覧)
export interface ComparisonResponse {
  comparisonNo: number
  shopNo: number
  partnerNo: number | null
  partnerName: string | null
  destinationNo: number | null
  destinationName: string | null
  comparisonDate: string
  comparisonStatus: string
  sourceEstimateNo: number | null
  title: string | null
  note: string | null
  groupCount: number            // B-1: 一覧用（groups が空でも正確な数）
  groups: ComparisonGroupResponse[]
}

export interface ComparisonGroupResponse {
  groupNo: number
  baseGoodsNo: number | null
  baseGoodsCode: string | null
  baseGoodsName: string
  baseSpecification: string | null
  basePurchasePrice: number | null
  baseGoodsPrice: number | null
  baseContainNum: number | null
  displayOrder: number
  groupNote: string | null
  details: ComparisonDetailResponse[]
}

export interface ComparisonDetailResponse {
  detailNo: number
  goodsNo: number | null
  goodsCode: string | null
  goodsName: string
  specification: string | null
  purchasePrice: number | null
  proposedPrice: number | null
  containNum: number | null
  profitRate: number | null
  detailNote: string | null
  displayOrder: number
  supplierNo: number | null
}
```

### リクエスト型

```ts
export interface ComparisonCreateRequest {
  shopNo: number
  partnerNo: number | null
  destinationNo: number | null
  comparisonDate: string
  title: string | null
  note: string | null
  groups: ComparisonGroupCreateRequest[]
}

export interface ComparisonGroupCreateRequest {
  baseGoodsNo: number | null
  baseGoodsCode: string | null
  baseGoodsName: string
  baseSpecification: string | null
  basePurchasePrice: number | null
  baseGoodsPrice: number | null
  baseContainNum: number | null
  displayOrder: number
  groupNote: string | null
  details: ComparisonDetailCreateRequest[]
}

export interface ComparisonDetailCreateRequest {
  goodsNo: number | null
  goodsCode: string | null
  goodsName: string
  specification: string | null
  purchasePrice: number | null
  proposedPrice: number | null
  containNum: number | null
  detailNote: string | null
  displayOrder: number
  supplierNo: number | null
}
```

### ステータス定数

```ts
// 見積と同じ ESTIMATE_STATUS_OPTIONS を再利用
// estimate.ts からインポートして使用
```

---

## 4. 一覧画面（`ComparisonListPage`）

### パターン: 見積一覧と同一

- **検索フォーム**: shopNo（admin のみ）, partnerNo, status[], dateFrom, dateTo, title
- **初期表示**: 検索前は空テーブル（`enabled: searchParams !== null`）
- **テーブルカラム**: 比較見積番号, 作成日, 得意先, タイトル, グループ数, ステータス
- **行クリック**: `/estimate-comparisons/{comparisonNo}` に遷移
- **デフォルトステータス**: `['00', '20']`（作成 + 修正）
- **TanStack Query キー**: `['estimate-comparisons', searchParams]`

### カラム定義

| key | header | sortable | render |
|---|---|---|---|
| comparisonNo | 比較見積番号 | ✓ | 数値 |
| comparisonDate | 作成日 | ✓ | 日付フォーマット |
| partnerName | 得意先 | ✓ | 文字列 |
| title | タイトル | ✓ | 文字列 |
| groupCount | グループ数 | ✓ | 数値 |
| comparisonStatus | ステータス | - | Badge + getEstimateStatusLabel |

---

## 5. 詳細画面（`ComparisonDetailPage`）

### レイアウト

```
PageHeader: "比較見積 #{comparisonNo}"  [編集] [削除] [印刷] [戻る]
Card: メタ情報（番号, 日付, 得意先, 納品先, ステータスSelect, 元見積リンク, タイトル, メモ）

{groups.map(group => <ComparisonGroupSection />)}
```

### ComparisonGroupSection

- **ヘッダ**: `グループ{groupNo}: {baseGoodsName}`
- **比較表**: 横に基準品 + 代替提案を並べた表
- **行構成**: 商品コード, 商品名, 規格, 仕入単価(admin), 販売単価, 入数, 粗利額(admin), 粗利率(admin), ケース粗利(admin)
- **差分表示**: 代替提案の粗利額/粗利率/ケース粗利は基準品との差分（↑/↓矢印）表示

### 印刷レイアウト（`print:` Tailwind modifier）

- 画面用コンテンツ: `print:hidden`
- 印刷用コンテンツ: `hidden print:block`
- 印刷時は仕入単価・粗利額・粗利率・ケース粗利を非表示
- 表示項目: 商品コード, 商品名, 規格, 販売単価, 入数

### ステータス更新

- `<Select>` で直接変更 → `PUT /estimate-comparisons/{no}/status`
- 見積詳細と同パターン

### 削除

- `DELETE /estimate-comparisons/{no}` → 一覧に戻る

---

## 6. 作成/編集フォーム（`ComparisonFormPage`）

### Props

```ts
interface Props {
  comparisonNo?: number  // undefined = 新規, number = 編集
}
```

### ヘッダフィールド

| フィールド | コンポーネント | 必須 |
|---|---|---|
| shopNo | SearchableSelect (admin) / hidden (非admin) | ✓ |
| partnerNo | SearchableSelect (得意先) | |
| destinationNo | SearchableSelect (納品先) | |
| comparisonDate | `<input type="date">` | ✓ |
| title | `<input type="text">` | |
| note | `<textarea>` | |

### グループリスト

- ローカル state: `GroupRow[]` with `id: string` (UUID)
- 各グループは `<ComparisonGroupForm>` で描画
- 「グループ追加」ボタン: 空の GroupRow を追加
- グループ内に「代替提案を追加」ボタン: 空の DetailRow を追加

### ComparisonGroupForm

```ts
interface GroupRow {
  id: string                    // crypto.randomUUID()
  baseGoodsNo: number | null
  baseGoodsCode: string
  baseGoodsName: string
  baseSpecification: string
  basePurchasePrice: number | null
  baseGoodsPrice: number | null
  baseContainNum: number | null
  groupNote: string
  details: DetailRow[]
}

interface DetailRow {
  id: string                    // crypto.randomUUID()
  goodsNo: number | null
  goodsCode: string
  goodsName: string
  specification: string
  purchasePrice: number | null
  proposedPrice: number | null
  containNum: number | null
  detailNote: string
  supplierNo: number | null
}
```

### 基準品の商品検索

- 商品コード入力 → `onBlur` で自動検索（見積フォームと同パターン）
- GoodsSearchDialog から選択も可能
- 選択時: `baseGoodsNo`, `baseGoodsCode`, `baseGoodsName`, `baseSpecification`, `basePurchasePrice`, `baseContainNum` を設定

### 代替提案の商品検索

- 同じく商品コード入力 or GoodsSearchDialog
- 選択時: `goodsNo`, `goodsCode`, `goodsName`, `specification`, `purchasePrice`, `containNum` を設定
- `proposedPrice` はユーザーが手入力（提案販売単価）

### 保存処理

1. バリデーション: shopNo 必須, comparisonDate 必須, groups 1件以上, 各 group の baseGoodsName 必須
2. `groups` を `ComparisonGroupCreateRequest[]` に変換（空の details も可）
3. 新規: `POST /estimate-comparisons` → 詳細画面に遷移
4. 編集: `PUT /estimate-comparisons/{no}` → 詳細画面に遷移

---

## 7. 見積詳細画面への導線

### 修正箇所: `components/pages/estimate/detail.tsx`

- 「比較見積を作成」ボタン追加（ステータスが `00` or `20` の場合のみ）
- クリック → `POST /api/v1/estimate-comparisons/from-estimate/{estimateNo}`
- 成功 → `router.push(`/estimate-comparisons/${response.comparisonNo}/edit`)`
- loading state 管理: `useMutation` で処理中のボタン非活性化

---

## 8. サイドバー変更

### `components/layout/Sidebar.tsx`

```diff
- { title: '比較見積', icon: ArrowLeftRight, href: '/estimates/compare' },
+ { title: '比較見積', icon: ArrowLeftRight, href: '/estimate-comparisons' },
```

### 旧ページ削除

- `app/(authenticated)/estimates/compare/page.tsx` を削除
- `components/pages/estimate/comparison.tsx` を削除

---

## 9. TanStack Query キー設計

```ts
['estimate-comparisons']                      // 一覧
['estimate-comparison', comparisonNo]         // 詳細
```

Mutation 成功時に `queryClient.invalidateQueries({ queryKey: ['estimate-comparisons'] })` で一覧を再取得。

---

## 10. 注意事項

1. **profitRate はリクエストに含めない**: API が受け付けない。表示時はフロントで `calcProfitRate` を使う
2. **ステータス定数は見積と共通**: `ESTIMATE_STATUS_OPTIONS` / `getEstimateStatusLabel` を `estimate.ts` から import
3. **GoodsSearchDialog の再利用**: `components/pages/estimate/GoodsSearchDialog.tsx` をそのまま import
4. **DataTable / SearchForm**: `components/features/common/` の共通コンポーネントを使用
5. **日付フォーマット**: ISO 文字列 → `YYYY/MM/DD` 表示は既存ユーティリティに従う
6. **差分表示**: 基準品との粗利差分は `calcProfit(proposedPrice, purchasePrice) - calcProfit(baseGoodsPrice, basePurchasePrice)` で計算
7. **comparisonDate の送信**: ISO形式文字列 `"2026-04-10"` で送信（Jackson が `LocalDate` にデシリアライズ）（C-1）
8. **ステータス更新のフィールド名**: `{ comparisonStatus: status }` を使用（見積の `estimateStatus` ではない）（C-3）
9. **代替提案の goodsName バリデーション**: 代替提案追加時、`goodsName` は必須（バックエンド `@NotBlank`）（C-2）
10. **ComparisonGroupTable は ComparisonGroupSection に統合**: 1ファイルで表示セクションと比較表を管理（M-1）
11. **displayOrder の自動採番**: `GroupRow` / `DetailRow` に `displayOrder` は持たず、保存時に配列インデックス + 1 で自動設定（m-3）
12. **編集ボタン表示条件**: ステータスが `00`(作成) or `20`(修正) の場合のみ「編集」ボタン表示（EC-8, M-4）
13. **元見積の表示**: `sourceEstimateNo` がある場合リンク表示。リンク先が404なら「（削除済）」テキスト表示（EC-3, M-2）
14. **グループ0件時のUI**: 「グループがありません。「グループ追加」ボタンから追加してください」メッセージ表示（EC-4, M-3）
15. **PDF出力**: Phase 2 ではブラウザ印刷→PDF で対応。専用PDFエンドポイントは Phase 3 以降で検討（M-6）
16. **印刷フォーマット**: 見積詳細の印刷パターンに準じて「御見積書」ヘッダ + 得意先名 + 日付 + 各グループ比較表（仕入情報非表示）を出力（M-5）
