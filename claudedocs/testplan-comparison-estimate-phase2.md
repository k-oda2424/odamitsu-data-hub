# テスト計画: 比較見積 Phase 2 フロントエンド

- **対象設計書**: `claudedocs/design-comparison-estimate-frontend.md`
- **親設計書**: `claudedocs/design-comparison-estimate.md`
- **作成日**: 2026-04-10
- **テストフレームワーク**: Playwright (Chromium headless)
- **テストファイル**: `frontend/e2e/estimate-comparison-phase2.spec.ts`

---

## 前提: テスト共通セットアップ

```ts
import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis, MOCK_USER } from './helpers/mock-api'

// 各 test.beforeEach で:
// 1. await mockAllApis(page)
// 2. 比較見積用 API モックを追加登録
// 3. await loginAndGoto(page, '/estimate-comparisons')
```

---

## モックデータ定義

### MOCK_ESTIMATE_COMPARISONS（一覧用）

```ts
export const MOCK_ESTIMATE_COMPARISONS = [
  {
    comparisonNo: 1,
    shopNo: 1,
    partnerNo: 108,
    partnerName: 'いしい記念病院',
    destinationNo: null,
    destinationName: null,
    comparisonDate: '2026-04-01',
    comparisonStatus: '00',
    sourceEstimateNo: 570,
    title: '除菌洗浄剤 比較提案',
    note: null,
    groupCount: 2,
    groups: [],
  },
  {
    comparisonNo: 2,
    shopNo: 1,
    partnerNo: 200,
    partnerName: 'クローバーハウス',
    destinationNo: 1,
    destinationName: '本社',
    comparisonDate: '2026-04-05',
    comparisonStatus: '10',
    sourceEstimateNo: null,
    title: '衛生用品 切替提案',
    note: 'テスト備考',
    groupCount: 3,
    groups: [],
  },
  {
    comparisonNo: 3,
    shopNo: 1,
    partnerNo: 108,
    partnerName: 'いしい記念病院',
    destinationNo: null,
    destinationName: null,
    comparisonDate: '2026-03-20',
    comparisonStatus: '20',
    sourceEstimateNo: 570,
    title: '修正版 除菌洗浄剤',
    note: null,
    groupCount: 1,
    groups: [],
  },
]
```

### MOCK_ESTIMATE_COMPARISON_DETAIL（詳細用）

```ts
export const MOCK_ESTIMATE_COMPARISON_DETAIL = {
  comparisonNo: 1,
  shopNo: 1,
  partnerNo: 108,
  partnerName: 'いしい記念病院',
  destinationNo: null,
  destinationName: null,
  comparisonDate: '2026-04-01',
  comparisonStatus: '00',
  sourceEstimateNo: 570,
  title: '除菌洗浄剤 比較提案',
  note: 'テストメモ',
  groupCount: 2,
  groups: [
    {
      groupNo: 1,
      baseGoodsNo: 1,
      baseGoodsCode: 'KAO-001',
      baseGoodsName: '花王 除菌洗浄剤',
      baseSpecification: '5L',
      basePurchasePrice: 1200,
      baseGoodsPrice: 1800,
      baseContainNum: 3,
      displayOrder: 1,
      groupNote: null,
      details: [
        {
          detailNo: 1,
          goodsNo: 2,
          goodsCode: 'LION-001',
          goodsName: 'ライオン 除菌洗浄剤',
          specification: '4.5L',
          purchasePrice: 1050,
          proposedPrice: 1700,
          containNum: 3,
          profitRate: 38.2,
          detailNote: null,
          displayOrder: 1,
          supplierNo: 2,
        },
        {
          detailNo: 2,
          goodsNo: null,
          goodsCode: null,
          goodsName: 'サラヤ 除菌洗浄剤',
          specification: '5L',
          purchasePrice: 980,
          proposedPrice: 1600,
          containNum: 3,
          profitRate: 38.8,
          detailNote: '未登録商品',
          displayOrder: 2,
          supplierNo: null,
        },
      ],
    },
    {
      groupNo: 2,
      baseGoodsNo: 3,
      baseGoodsCode: 'KAO-002',
      baseGoodsName: '花王 ハンドソープ',
      baseSpecification: '2L',
      basePurchasePrice: 800,
      baseGoodsPrice: 1200,
      baseContainNum: 6,
      displayOrder: 2,
      groupNote: 'ハンドソープ切替検討',
      details: [],
    },
  ],
}
```

### MOCK_ESTIMATE_COMPARISON_SUBMITTED（提出済み, ステータス 10）

```ts
export const MOCK_ESTIMATE_COMPARISON_SUBMITTED = {
  ...MOCK_ESTIMATE_COMPARISON_DETAIL,
  comparisonNo: 2,
  comparisonStatus: '10',
  sourceEstimateNo: null,
}
```

### モック API ルート

| エンドポイント | メソッド | モックレスポンス |
|---|---|---|
| `/api/v1/estimate-comparisons` | GET | `MOCK_ESTIMATE_COMPARISONS` |
| `/api/v1/estimate-comparisons` | POST | `{ comparisonNo: 99, ...request }` |
| `/api/v1/estimate-comparisons/{no}` | GET | `MOCK_ESTIMATE_COMPARISON_DETAIL` |
| `/api/v1/estimate-comparisons/{no}` | PUT | `{ ...request }` |
| `/api/v1/estimate-comparisons/{no}` | DELETE | `204 No Content` |
| `/api/v1/estimate-comparisons/{no}/status` | PUT | `200 OK` |
| `/api/v1/estimate-comparisons/from-estimate/{no}` | POST | `{ comparisonNo: 99 }` |
| `/api/v1/masters/shops` | GET | `MOCK_SHOPS` |
| `/api/v1/masters/partners` | GET | `MOCK_PARTNERS` |
| `/api/v1/masters/destinations` | GET | `MOCK_DESTINATIONS` |
| `/api/v1/goods/available-for-sales` | GET | `MOCK_GOODS_LIST` |
| `/api/v1/auth/me` | GET | `MOCK_USER` (shopNo=0 admin) / shopNo=1 (non-admin) |

---

## テストケース一覧

### 1. 一覧画面テスト

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| L-01 | 一覧/初期表示 | ページヘッダーと新規作成ボタンが表示される | admin でログイン | `/estimate-comparisons` に遷移 | 「比較見積一覧」ヘッダーと「新規作成」ボタンが表示される | P0 |
| L-02 | 一覧/初期表示 | 検索フォームが表示される | admin でログイン | `/estimate-comparisons` に遷移 | 得意先、ステータス、日付From/To、タイトル の検索フィールドが表示される | P0 |
| L-03 | 一覧/初期表示 | 検索前はテーブルが非表示で案内メッセージ表示 | admin でログイン | `/estimate-comparisons` に遷移 | 「検索条件を入力して」メッセージ表示、table 非表示 | P0 |
| L-04 | 一覧/初期表示 | デフォルトで「作成」「修正」ステータスがチェック済み | admin でログイン | `/estimate-comparisons` に遷移 | 「作成」(00) と「修正」(20) のチェックボックスが `data-state="checked"` | P1 |
| L-05 | 一覧/初期表示 | admin の場合、店舗セレクトが表示される | admin (shopNo=0) でログイン | `/estimate-comparisons` に遷移 | 店舗選択プルダウンが表示される | P1 |
| L-06 | 一覧/検索 | 検索ボタンでテーブルが表示される | admin でログイン | 「検索」ボタンをクリック | table が表示され、テーブルヘッダー（比較見積番号, 作成日, 得意先, タイトル, グループ数, ステータス）が表示される | P0 |
| L-07 | 一覧/検索 | 検索結果にモックデータが表示される | admin でログイン、API が MOCK_ESTIMATE_COMPARISONS を返す | 「検索」ボタンをクリック | comparisonNo=1, 2, 3 のデータが表示。得意先名、タイトル、groupCount が正しい | P0 |
| L-08 | 一覧/検索 | ステータスがバッジで表示される | admin でログイン | 「検索」ボタンをクリック | 「作成」「提出済」「修正」のバッジが表示される | P1 |
| L-09 | 一覧/検索 | リセットボタンでテーブルが非表示に戻る | admin でログイン、検索済み | 「リセット」ボタンをクリック | 案内メッセージ再表示、table 非表示 | P1 |
| L-10 | 一覧/ナビ | 行クリックで詳細画面に遷移する | admin でログイン、検索結果表示中 | テーブルの1行目をクリック | URL が `/estimate-comparisons/1` に遷移 | P0 |
| L-11 | 一覧/ナビ | 「新規作成」ボタンで作成画面に遷移する | admin でログイン | 「新規作成」ボタンをクリック | URL が `/estimate-comparisons/create` に遷移 | P0 |
| L-12 | 一覧/権限 | non-admin の場合、店舗セレクトが非表示 | non-admin (shopNo=1) でログイン | `/estimate-comparisons` に遷移 | 店舗選択プルダウンが表示されない | P1 |

### 2. 詳細画面テスト

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| D-01 | 詳細/表示 | メタ情報が正しく表示される | admin でログイン、MOCK_ESTIMATE_COMPARISON_DETAIL をモック | `/estimate-comparisons/1` に遷移 | 比較見積番号(1), 日付(2026/04/01), 得意先(いしい記念病院), タイトル, メモが表示される | P0 |
| D-02 | 詳細/表示 | グループセクションが表示される | admin でログイン | `/estimate-comparisons/1` に遷移 | 「グループ1: 花王 除菌洗浄剤」「グループ2: 花王 ハンドソープ」のセクションが表示される | P0 |
| D-03 | 詳細/表示 | 基準品の情報が比較表に表示される | admin でログイン | `/estimate-comparisons/1` に遷移 | グループ1 の比較表に基準品「花王 除菌洗浄剤」の商品コード, 商品名, 規格, 仕入単価, 販売単価, 入数 が表示される | P0 |
| D-04 | 詳細/表示 | 代替提案が比較表に表示される | admin でログイン | `/estimate-comparisons/1` に遷移 | グループ1 に「ライオン 除菌洗浄剤」「サラヤ 除菌洗浄剤」の代替提案が表示される | P0 |
| D-05 | 詳細/表示 | admin は仕入単価・粗利情報が表示される | admin (shopNo=0) でログイン | `/estimate-comparisons/1` に遷移 | 仕入単価, 粗利額, 粗利率, ケース粗利 のカラムが表示される | P0 |
| D-06 | 詳細/表示 | non-admin は仕入情報が非表示 | non-admin (shopNo=1) でログイン | `/estimate-comparisons/1` に遷移 | 仕入単価, 粗利額, 粗利率, ケース粗利 のカラムが表示されない | P0 |
| D-07 | 詳細/表示 | 代替提案の粗利差分が矢印付きで表示される | admin でログイン | `/estimate-comparisons/1` に遷移 | 代替提案の粗利が基準品との差分（上矢印/下矢印）で表示される | P1 |
| D-08 | 詳細/表示 | 元見積リンクが表示される | admin でログイン、sourceEstimateNo=570 | `/estimate-comparisons/1` に遷移 | 「元見積: #570」がリンクとして表示され、クリックで `/estimates/570` に遷移 | P1 |
| D-09 | 詳細/表示 | 元見積がない場合はリンク非表示 | admin でログイン、sourceEstimateNo=null | `/estimate-comparisons/2` に遷移 (submitted mock) | 元見積リンクが表示されない | P2 |
| D-10 | 詳細/ステータス | ステータスをSelectで変更できる | admin でログイン、ステータス=00 | ステータス Select を「提出済」(10)に変更 | PUT `/estimate-comparisons/1/status` が `{ comparisonStatus: '10' }` で呼ばれる | P0 |
| D-11 | 詳細/ボタン | ステータス 00 (作成) のとき編集ボタンが表示される | admin でログイン、comparisonStatus=00 | `/estimate-comparisons/1` に遷移 | 「編集」ボタンが表示される | P0 |
| D-12 | 詳細/ボタン | ステータス 20 (修正) のとき編集ボタンが表示される | admin でログイン、comparisonStatus=20 | ステータス 20 のモックで遷移 | 「編集」ボタンが表示される | P1 |
| D-13 | 詳細/ボタン | ステータス 10 (提出済) のとき編集ボタンが非表示 | admin でログイン、comparisonStatus=10 | ステータス 10 のモックで遷移 | 「編集」ボタンが表示されない | P0 |
| D-14 | 詳細/ナビ | 編集ボタンで編集画面に遷移する | admin でログイン、ステータス=00 | 「編集」ボタンをクリック | URL が `/estimate-comparisons/1/edit` に遷移 | P1 |
| D-15 | 詳細/削除 | 削除で一覧に戻る | admin でログイン | 「削除」ボタンをクリック → 確認ダイアログで「OK」 | DELETE `/estimate-comparisons/1` が呼ばれ、URL が `/estimate-comparisons` に遷移 | P0 |
| D-16 | 詳細/表示 | 代替提案0件のグループが表示される | admin でログイン | `/estimate-comparisons/1` に遷移 | グループ2(花王 ハンドソープ)が基準品のみで表示される（代替提案なし） | P1 |
| D-17 | 詳細/ナビ | 「戻る」ボタンで一覧に遷移する | admin でログイン | 「戻る」ボタンをクリック | URL が `/estimate-comparisons` に遷移 | P2 |

### 3. 作成フォームテスト

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| C-01 | 作成/表示 | フォームヘッダーフィールドが表示される | admin でログイン | `/estimate-comparisons/create` に遷移 | 店舗, 得意先, 納品先, 日付, タイトル, メモ のフィールドが表示される | P0 |
| C-02 | 作成/表示 | admin の場合、店舗セレクトが表示される | admin (shopNo=0) でログイン | `/estimate-comparisons/create` に遷移 | 店舗選択プルダウンが表示される | P1 |
| C-03 | 作成/表示 | non-admin の場合、店舗セレクトが非表示 | non-admin (shopNo=1) でログイン | `/estimate-comparisons/create` に遷移 | 店舗選択プルダウンが非表示 | P1 |
| C-04 | 作成/グループ | 「グループ追加」ボタンでグループが追加される | admin でログイン | 「グループ追加」ボタンをクリック | 新しいグループフォームセクションが表示される（基準品名フィールドあり） | P0 |
| C-05 | 作成/グループ | グループを削除できる | admin でログイン、グループ1件追加済み | グループの「削除」ボタンをクリック | グループが削除され、「グループがありません」メッセージが表示される | P0 |
| C-06 | 作成/グループ | グループ0件で案内メッセージが表示される | admin でログイン | `/estimate-comparisons/create` に遷移 | 「グループがありません。「グループ追加」ボタンから追加してください」メッセージが表示される | P1 |
| C-07 | 作成/代替提案 | 「代替提案を追加」ボタンで明細が追加される | admin でログイン、グループ1件追加済み | グループ内の「代替提案を追加」ボタンをクリック | 代替提案入力行が追加される（商品名フィールドあり） | P0 |
| C-08 | 作成/代替提案 | 代替提案を削除できる | admin でログイン、代替提案1件追加済み | 代替提案の「削除」ボタンをクリック | 代替提案入力行が削除される | P0 |
| C-09 | 作成/商品検索 | 基準品の商品コード入力で自動検索 | admin でログイン、グループ追加済み、goods API モック設定 | 基準品の商品コード欄に「KAO-001」を入力し blur | 商品名, 規格, 仕入単価, 入数 が自動入力される | P1 |
| C-10 | 作成/商品検索 | GoodsSearchDialog から基準品を選択 | admin でログイン、グループ追加済み | 基準品の「検索」ボタン → ダイアログで商品をクリック | 基準品フィールドに選択商品の情報が設定される | P1 |
| C-11 | 作成/商品検索 | GoodsSearchDialog から代替提案を選択 | admin でログイン、代替提案追加済み | 代替提案の「検索」ボタン → ダイアログで商品をクリック | 代替提案フィールドに選択商品の情報が設定される（proposedPrice は空のまま） | P1 |
| C-12 | 作成/バリデーション | comparisonDate 未入力で保存失敗 | admin でログイン、グループ1件追加、基準品名入力済み | 日付を空のまま「保存」ボタンをクリック | バリデーションエラーが表示される | P0 |
| C-13 | 作成/バリデーション | グループ0件で保存失敗 | admin でログイン | グループを追加せず「保存」ボタンをクリック | バリデーションエラー（グループ1件以上必須） | P0 |
| C-14 | 作成/バリデーション | 基準品名 (baseGoodsName) 未入力で保存失敗 | admin でログイン、グループ追加済み | 基準品名を空のまま「保存」ボタンをクリック | バリデーションエラーが表示される | P0 |
| C-15 | 作成/バリデーション | 代替提案の goodsName 未入力で保存失敗 | admin でログイン、代替提案追加済み | 代替提案の商品名を空のまま「保存」ボタンをクリック | バリデーションエラーが表示される | P1 |
| C-16 | 作成/バリデーション | admin で shopNo 未選択で保存失敗 | admin (shopNo=0) でログイン | 店舗を未選択のまま「保存」ボタンをクリック | バリデーションエラー（shopNo 必須） | P1 |
| C-17 | 作成/保存 | 正常保存で詳細画面に遷移する | admin でログイン、全必須項目入力済み | 「保存」ボタンをクリック | POST `/estimate-comparisons` が呼ばれ、URL が `/estimate-comparisons/99` に遷移 | P0 |
| C-18 | 作成/保存 | 代替提案0件のグループで保存できる | admin でログイン、グループ追加・基準品名入力済み（代替提案なし） | 「保存」ボタンをクリック | 正常保存される（代替提案0件は許容） | P1 |

### 4. 編集フォームテスト

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| E-01 | 編集/読込 | 既存データがフォームに読み込まれる | admin でログイン、MOCK_ESTIMATE_COMPARISON_DETAIL をモック | `/estimate-comparisons/1/edit` に遷移 | 日付, 得意先, タイトル, メモ が既存値で表示。グループ2件（花王 除菌洗浄剤, 花王 ハンドソープ）が表示 | P0 |
| E-02 | 編集/読込 | 代替提案が読み込まれる | admin でログイン | `/estimate-comparisons/1/edit` に遷移 | グループ1 に「ライオン 除菌洗浄剤」「サラヤ 除菌洗浄剤」の代替提案が表示される | P0 |
| E-03 | 編集/操作 | グループを追加できる | admin でログイン、編集画面表示中 | 「グループ追加」ボタンをクリック | 3件目のグループフォームが追加される | P1 |
| E-04 | 編集/操作 | 既存グループを削除できる | admin でログイン、編集画面表示中 | グループ2(花王 ハンドソープ)の「削除」ボタンをクリック | グループが1件になる | P1 |
| E-05 | 編集/操作 | 代替提案を追加できる | admin でログイン、編集画面表示中 | グループ2 に「代替提案を追加」 | グループ2 に代替提案入力行が追加される | P1 |
| E-06 | 編集/保存 | 保存で詳細画面に遷移する | admin でログイン、編集完了 | 「保存」ボタンをクリック | PUT `/estimate-comparisons/1` が呼ばれ、URL が `/estimate-comparisons/1` に遷移 | P0 |

### 5. 見積→比較見積生成テスト

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| G-01 | 生成/表示 | ステータス 00 の見積詳細に「比較見積を作成」ボタンが表示される | admin でログイン、見積ステータス=00 のモック | `/estimates/570` に遷移 | 「比較見積を作成」ボタンが表示される | P0 |
| G-02 | 生成/表示 | ステータス 20 の見積詳細に「比較見積を作成」ボタンが表示される | admin でログイン、見積ステータス=20 のモック | `/estimates/570` に遷移 (estimateStatus=20) | 「比較見積を作成」ボタンが表示される | P1 |
| G-03 | 生成/表示 | ステータス 10 (提出済) の見積詳細にボタンが非表示 | admin でログイン、見積ステータス=10 のモック | `/estimates/570` に遷移 (estimateStatus=10) | 「比較見積を作成」ボタンが表示されない | P0 |
| G-04 | 生成/実行 | ボタンクリックで API コール → 編集画面に遷移 | admin でログイン、from-estimate API が `{ comparisonNo: 99 }` を返す | 「比較見積を作成」ボタンをクリック | POST `/estimate-comparisons/from-estimate/570` が呼ばれ、`/estimate-comparisons/99/edit` に遷移 | P0 |
| G-05 | 生成/実行 | 処理中はボタンが非活性になる | admin でログイン | 「比較見積を作成」ボタンをクリック（遅延モック） | ボタンが disabled になり、完了後に遷移する | P2 |

### 6. 印刷テスト

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| P-01 | 印刷/DOM | 印刷用コンテンツに「御見積書」ヘッダが存在する | admin でログイン | `/estimate-comparisons/1` に遷移、DOM を検査 | `hidden print:block` クラスを持つ要素内に「御見積書」テキストが存在する | P1 |
| P-02 | 印刷/DOM | 印刷用コンテンツに得意先名が表示される | admin でログイン | `/estimate-comparisons/1` に遷移、DOM を検査 | 印刷用セクション内に「いしい記念病院」が含まれる | P1 |
| P-03 | 印刷/DOM | 印刷用コンテンツに仕入情報が含まれない | admin でログイン | `/estimate-comparisons/1` に遷移、DOM を検査 | `print:block` 要素内に「仕入単価」「粗利額」「粗利率」「ケース粗利」のカラムヘッダが存在しない | P0 |
| P-04 | 印刷/DOM | 画面表示用コンテンツに `print:hidden` が設定されている | admin でログイン | `/estimate-comparisons/1` に遷移、DOM を検査 | メインコンテンツのコンテナに `print:hidden` クラスが含まれる | P1 |
| P-05 | 印刷/DOM | 印刷用コンテンツに商品名・規格・販売単価・入数が表示される | admin でログイン | `/estimate-comparisons/1` に遷移、DOM を検査 | 印刷用セクションに基準品・代替提案の商品名、規格、販売単価、入数が含まれる | P1 |

### 7. サイドバーテスト

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| S-01 | サイドバー | 「比較見積」メニューが存在する | admin でログイン | サイドバーを確認 | 「比較見積」メニュー項目が表示される | P0 |
| S-02 | サイドバー | 「比較見積」クリックで `/estimate-comparisons` に遷移 | admin でログイン | サイドバーの「比較見積」をクリック | URL が `/estimate-comparisons` に遷移 | P0 |
| S-03 | サイドバー | 比較見積ページで「比較見積」メニューがアクティブ | admin でログイン | `/estimate-comparisons` に遷移 | サイドバーの「比較見積」リンクに `data-active="true"` が設定される | P1 |

### 8. エッジケーステスト

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| EC-01 | エッジ/EC-1 | グループ0件で保存時にバリデーションエラー | admin でログイン、作成フォーム表示 | グループを追加せず「保存」をクリック | バリデーションエラー表示（「グループは1件以上必要です」等） | P0 |
| EC-02 | エッジ/EC-2 | 代替提案0件のグループで保存可能 | admin でログイン、グループ追加・基準品名入力済み | 代替提案を追加せず「保存」をクリック | 正常保存される（エラーなし） | P1 |
| EC-03 | エッジ/EC-3 | 元見積が削除済みの場合「(削除済)」表示 | admin でログイン、sourceEstimateNo=999 の詳細、見積 API が 404 を返す | `/estimate-comparisons/1` に遷移 | 「元見積: #999（削除済）」がテキストとして表示される（リンクではない） | P1 |
| EC-04 | エッジ/EC-4 | グループ0件の比較見積詳細で案内メッセージ | admin でログイン、groups=[] のモック | 詳細画面に遷移 | 「グループがありません」メッセージが表示される | P2 |
| EC-05 | エッジ/EC-5 | 未登録商品（goodsNo=null）の基準品が表示される | admin でログイン | グループの baseGoodsNo=null のデータを表示 | 商品コード欄が空、商品名は表示される | P1 |
| EC-06 | エッジ/EC-6 | 未登録商品（goodsNo=null）の代替提案が表示される | admin でログイン | detailNo=2 (goodsNo=null) のデータを表示 | 商品コード欄が空、商品名「サラヤ 除菌洗浄剤」が表示される | P1 |
| EC-07 | エッジ/EC-7 | 同じ見積から複数回比較見積を生成できる | admin でログイン | 見積詳細から「比較見積を作成」を2回クリック（2回目は別ページ遷移後に再実行） | 2回とも正常に POST が呼ばれ、異なる comparisonNo で遷移 | P2 |
| EC-08 | エッジ/EC-8 | 提出済(10)ステータスで編集ボタン非表示 | admin でログイン、ステータス=10 のモック | 詳細画面を確認 | 「編集」ボタンが存在しない | P0 |
| EC-09 | エッジ/EC-9 | 印刷レイアウトで仕入情報が非表示 | admin でログイン | 詳細画面で印刷用 DOM を確認 | print セクションに仕入単価・粗利額・粗利率・ケース粗利のカラムが含まれない | P0 |

---

## non-admin テスト用モック設定

non-admin テストでは、テストの `beforeEach` 内で `/api/v1/auth/me` を上書きする:

```ts
await page.route(
  (url) => url.pathname === '/api/v1/auth/me',
  async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ ...MOCK_USER, shopNo: 1 }),
    })
  },
)
```

---

## テスト実行コマンド

```bash
cd frontend && npx playwright test e2e/estimate-comparison-phase2.spec.ts
```

---

## カバレッジサマリー

| カテゴリ | P0 | P1 | P2 | 合計 |
|---|---|---|---|---|
| 一覧画面 | 5 | 5 | 0 | 10 (+2 admin/non-admin) |
| 詳細画面 | 6 | 6 | 2 | 14 (+3 エッジ) |
| 作成フォーム | 5 | 7 | 0 | 12 (+6 バリデーション) |
| 編集フォーム | 2 | 4 | 0 | 6 |
| 見積→比較見積生成 | 3 | 1 | 1 | 5 |
| 印刷 | 1 | 4 | 0 | 5 |
| サイドバー | 2 | 1 | 0 | 3 |
| エッジケース | 3 | 4 | 2 | 9 |
| **合計** | **27** | **32** | **5** | **64** |
