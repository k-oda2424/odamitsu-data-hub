# 得意先商品マスタ E2Eテスト計画

## 対象機能

- 一覧画面: `/partner-goods`
- 詳細画面: `/partner-goods/{partnerNo}/{destinationNo}/{goodsNo}`

## 前提

- テストファイル: `frontend/e2e/partner-goods-list.spec.ts`, `frontend/e2e/partner-goods-detail.spec.ts`
- モックデータ: `frontend/e2e/helpers/mock-api.ts` に追加
- 共通セットアップ: `mockAllApis(page)` → `loginAndGoto(page, path)`

---

## 1. モックデータ定義

`mock-api.ts` に以下を追加する。

### MOCK_PARTNERS（得意先マスタ / SearchableSelect 用）

```ts
export const MOCK_PARTNERS = [
  { partnerNo: 108, partnerCode: '022000', partnerName: 'いしい記念病院' },
  { partnerNo: 200, partnerCode: '148013', partnerName: 'クローバーハウス' },
]
```

### MOCK_DESTINATIONS（納品先 / 得意先選択後に動的取得）

```ts
export const MOCK_DESTINATIONS = [
  { destinationNo: 0, destinationName: '本社' },
  { destinationNo: 1, destinationName: '第二倉庫' },
]
```

### MOCK_PARTNER_GOODS_LIST（一覧検索結果）

```ts
export const MOCK_PARTNER_GOODS_LIST = [
  {
    partnerNo: 108,
    goodsNo: 100,
    destinationNo: 0,
    shopName: '小田光',
    companyName: 'いしい記念病院',
    goodsCode: 'ABC-001',
    goodsName: '得意先商品A',
    goodsPrice: 1500,
    reflectedEstimateNo: 570,
    lastSalesDate: '2025-12-01',
  },
  {
    partnerNo: 108,
    goodsNo: 200,
    destinationNo: 0,
    shopName: '小田光',
    companyName: 'いしい記念病院',
    goodsCode: 'DEF-002',
    goodsName: '得意先商品B',
    goodsPrice: 2300,
    reflectedEstimateNo: null,
    lastSalesDate: null,
  },
]
```

### MOCK_PARTNER_GOODS_DETAIL（詳細取得）

```ts
export const MOCK_PARTNER_GOODS_DETAIL = {
  partnerNo: 108,
  goodsNo: 100,
  destinationNo: 0,
  shopNo: 1,
  companyNo: 5,
  goodsCode: 'ABC-001',
  goodsName: '得意先商品A',
  goodsPrice: 1500,
  keyword: '衛生用品',
  orderHistory: [
    {
      orderDateTime: '2025-11-15T10:30:00',
      goodsCode: 'ABC-001',
      goodsName: '得意先商品A',
      goodsPrice: 1500,
      goodsNum: 10,
    },
    {
      orderDateTime: '2025-10-01T09:00:00',
      goodsCode: 'ABC-001',
      goodsName: '得意先商品A',
      goodsPrice: 1500,
      goodsNum: -3,
    },
    {
      orderDateTime: '2025-08-20T14:00:00',
      goodsCode: 'ABC-001',
      goodsName: '得意先商品A',
      goodsPrice: null,
      goodsNum: 5,
    },
  ],
}
```

### APIルート追加（mockAllApis 内）

```ts
// ---- Partners（得意先マスタ）----
await page.route(
  (url) => url.pathname === '/api/v1/masters/partners',
  async (route) => {
    await json(route, MOCK_PARTNERS)
  },
)

// ---- Destinations（納品先 / 得意先選択後の動的取得）----
await page.route(
  (url) => url.pathname === '/api/v1/masters/destinations',
  async (route) => {
    await json(route, MOCK_DESTINATIONS)
  },
)

// ---- Partner Goods 一覧 ----
await page.route(
  (url) => url.pathname === '/api/v1/partner-goods',
  async (route) => {
    await json(route, MOCK_PARTNER_GOODS_LIST)
  },
)

// ---- Partner Goods 詳細 ----
await page.route(
  (url) => /^\/api\/v1\/partner-goods\/\d+\/\d+\/\d+$/.test(url.pathname),
  async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      await json(route, MOCK_PARTNER_GOODS_DETAIL)
    } else if (method === 'DELETE') {
      await route.fulfill({ status: 204 })
    } else {
      await route.fallback()
    }
  },
)

// ---- Partner Goods 売価更新 ----
await page.route(
  (url) => /^\/api\/v1\/partner-goods\/\d+\/\d+\/\d+\/price$/.test(url.pathname),
  async (route) => {
    if (route.request().method() === 'PUT') {
      const body = JSON.parse(route.request().postData() || '{}')
      await json(route, { ...MOCK_PARTNER_GOODS_DETAIL, ...body })
    } else {
      await route.fallback()
    }
  },
)

// ---- Partner Goods インライン更新（PATCH）----
await page.route(
  (url) =>
    /^\/api\/v1\/partner-goods\/\d+\/\d+\/\d+$/.test(url.pathname),
  async (route) => {
    if (route.request().method() === 'PATCH') {
      const body = JSON.parse(route.request().postData() || '{}')
      await json(route, { ...MOCK_PARTNER_GOODS_DETAIL, ...body })
    } else {
      await route.fallback()
    }
  },
)
```

注: PATCH と GET/DELETE は同一パス。Playwright の `page.route()` は LIFO で評価されるため、PATCH ルートを GET/DELETE ルートより後に登録するか、単一ハンドラで method 分岐する。実装時は GET/DELETE/PATCH を1つのハンドラにまとめるのが望ましい。

---

## 2. テストシナリオ: 一覧画面

### ファイル: `frontend/e2e/partner-goods-list.spec.ts`

```
test.describe('得意先商品マスタ一覧画面')
```

### 2.1 初期表示

```
test.describe('初期表示')
  test.beforeEach: mockAllApis(page) → loginAndGoto(page, '/partner-goods')
```

| # | テスト名 | 検証内容 |
|---|----------|----------|
| 1 | `ページヘッダーが表示される` | heading「得意先商品マスタ一覧」が visible |
| 2 | `検索フォームが表示される` | ラベル「ショップ」「得意先」「納品先」「商品名」「商品コード」「キーワード」が visible |
| 3 | `検索・リセットボタンが表示される` | button「検索」「リセット」が visible |
| 4 | `案内メッセージが表示され、テーブルは非表示` | 「検索条件を入力して」テキストが visible、`table` が not.toBeVisible |
| 5 | `納品先セレクトは得意先未選択時に無効` | 納品先セレクトが disabled |

### 2.2 検索機能

```
test.describe('検索機能')
  test.beforeEach: mockAllApis(page) → loginAndGoto(page, '/partner-goods')
```

| # | テスト名 | 操作 | 検証内容 |
|---|----------|------|----------|
| 1 | `検索ボタンでテーブルが表示される` | 「検索」クリック | `table` が visible |
| 2 | `テーブルヘッダーが正しく表示される` | 「検索」クリック | th に「商品番号」「ショップ」「得意先」「商品コード」「商品名」「売価」「対応見積」「最終売上日」「詳細」が存在 |
| 3 | `検索結果にデータが表示される` | 「検索」クリック | モックデータの商品名「得意先商品A」「得意先商品B」が visible |
| 4 | `売価がカンマ区切りで表示される` | 「検索」クリック | 「1,500」「2,300」がテーブル内に表示される |
| 5 | `対応見積番号がリンクとして表示される` | 「検索」クリック | 「570」がリンク（`<a>` or クリック可能要素）として表示される |
| 6 | `対応見積が未設定の行はリンクなし` | 「検索」クリック | 得意先商品B の対応見積セルが空またはハイフン |
| 7 | `最終売上日がnullの行は空表示` | 「検索」クリック | 得意先商品B の最終売上日セルが空またはハイフン |
| 8 | `商品名で検索できる` | 商品名に「商品A」入力 → 「検索」クリック | テーブルが visible |
| 9 | `商品コードで検索できる` | 商品コードに「001」入力 → 「検索」クリック | テーブルが visible |

### 2.3 リセット機能

| # | テスト名 | 操作 | 検証内容 |
|---|----------|------|----------|
| 1 | `リセットでテーブルが非表示になる` | 「検索」→ テーブル visible → 「リセット」クリック | 「検索条件を入力して」テキスト visible、`table` not.toBeVisible |
| 2 | `リセットで検索フォームがクリアされる` | 商品名に入力 → 「リセット」クリック | 商品名入力欄が空 |

### 2.4 検索結果なし

| # | テスト名 | 操作 | 検証内容 |
|---|----------|------|----------|
| 1 | `検索結果が0件の場合メッセージが表示される` | 空配列を返すモックで検索実行 | 「該当するデータがありません」等のメッセージが visible |

実装メモ: このテストでは `page.route()` で `/api/v1/partner-goods` のレスポンスを `[]` に上書きする。`mockAllApis` 後に個別ルートを再登録すれば LIFO により優先される。

### 2.5 ナビゲーション

| # | テスト名 | 操作 | 検証内容 |
|---|----------|------|----------|
| 1 | `詳細リンクで詳細画面に遷移する` | 「検索」→ テーブル表示 → 1行目の「詳細」リンクをクリック | URL が `/partner-goods/108/0/100` 形式に遷移 |

### 2.6 得意先選択と納品先の連動

| # | テスト名 | 操作 | 検証内容 |
|---|----------|------|----------|
| 1 | `得意先選択後に納品先セレクトが有効になる` | 得意先 SearchableSelect で「いしい記念病院」を選択 | 納品先セレクトが enabled、選択肢に「本社」「第二倉庫」が存在 |
| 2 | `得意先をクリアすると納品先セレクトが無効に戻る` | 得意先選択 → 得意先クリア | 納品先セレクトが disabled |

---

## 3. テストシナリオ: 詳細画面

### ファイル: `frontend/e2e/partner-goods-detail.spec.ts`

```
test.describe('得意先商品マスタ詳細画面')
```

### 3.1 初期表示

```
test.describe('初期表示')
  test.beforeEach: mockAllApis(page) → loginAndGoto(page, '/partner-goods/108/0/100')
```

| # | テスト名 | 検証内容 |
|---|----------|----------|
| 1 | `ページヘッダーが表示される` | heading「得意先商品詳細」(仮) が visible |
| 2 | `得意先商品情報が表示される` | 「商品名」「商品コード」「売価」「キーワード」ラベルが visible |
| 3 | `モックデータの値が正しく表示される` | 「得意先商品A」「ABC-001」「1,500」(カンマ区切り)「衛生用品」が visible |
| 4 | `編集ボタンと一覧に戻るボタンが表示される` | button「編集」「一覧に戻る」が visible |
| 5 | `注文履歴テーブルが表示される` | heading「注文履歴」が visible、`table` が visible |
| 6 | `注文履歴テーブルヘッダーが正しい` | th に「注文日」「商品コード」「商品名」「売価」「数量」が存在 |

### 3.2 注文履歴データ表示

| # | テスト名 | 検証内容 |
|---|----------|----------|
| 1 | `注文履歴の通常注文が正の数量で表示される` | 1行目: 数量「10」が visible |
| 2 | `注文履歴の返品が負の数量で表示される` | 2行目: 数量「-3」が visible |
| 3 | `注文履歴の売価がnullの場合「未設定」と表示される` | 3行目: 売価セルに「未設定」が visible |
| 4 | `注文履歴が注文日降順で表示される` | 1行目の日付 > 2行目の日付 > 3行目の日付 |

### 3.3 編集モード

```
test.describe('編集モード')
  test.beforeEach: mockAllApis(page) → loginAndGoto(page, '/partner-goods/108/0/100')
```

| # | テスト名 | 操作 | 検証内容 |
|---|----------|------|----------|
| 1 | `編集ボタンで編集モードに切り替わる` | 「編集」クリック | 「保存」「キャンセル」ボタンが visible、「編集」ボタンが hidden |
| 2 | `編集モードで入力フィールドが表示される` | 「編集」クリック | 商品名・キーワード・売価の入力フィールド（input）が visible |
| 3 | `売価を変更して保存できる` | 「編集」→ 売価を「1600」に変更 → 「保存」クリック | 成功トーストが表示される、編集モードが解除される |
| 4 | `商品名を変更して保存できる` | 「編集」→ 商品名を変更 → 「保存」クリック | 成功トーストが表示される |
| 5 | `キャンセルで編集モードが解除される` | 「編集」→ 売価を変更 → 「キャンセル」クリック | 「編集」ボタンが re-visible、「保存」「キャンセル」が hidden |
| 6 | `キャンセルで変更が破棄される` | 「編集」→ 売価を「9999」に変更 → 「キャンセル」クリック | 売価表示が元の「1,500」に戻る |

### 3.4 ナビゲーション

| # | テスト名 | 操作 | 検証内容 |
|---|----------|------|----------|
| 1 | `「一覧に戻る」で一覧画面に遷移する` | 「一覧に戻る」クリック | URL が `/partner-goods` に遷移 |

---

## 4. テスト実装時の注意事項

### セレクタ方針

- テキストラベル: `page.getByText('text', { exact: true })` で部分一致を回避
- ボタン: `page.locator('button:has-text("text")')` または `page.getByRole('button', { name: 'text' })`
- テーブルヘッダー: `page.locator('th:has-text("text")')`
- 入力: `page.locator('input[placeholder="..."]')` またはラベル経由 `page.getByLabel('text')`
- SearchableSelect: Popover + Command パターンのため、トリガーボタンクリック → 候補選択の2ステップ

### 空結果テスト

`mockAllApis` の後に個別ルートを再登録すれば LIFO により優先される:

```ts
test('検索結果が0件の場合メッセージが表示される', async ({ page }) => {
  await mockAllApis(page)
  // 空配列で上書き
  await page.route(
    (url) => url.pathname === '/api/v1/partner-goods',
    async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([]),
      })
    },
  )
  await loginAndGoto(page, '/partner-goods')
  await page.locator('button:has-text("検索")').click()
  await expect(page.locator('text=該当するデータがありません')).toBeVisible()
})
```

### トースト検証

sonner (shadcn/ui Toast) のトースト要素は `[data-sonner-toast]` セレクタまたはロールで検出:

```ts
await expect(page.locator('[data-sonner-toast]')).toBeVisible({ timeout: 5000 })
```

または表示テキストで:

```ts
await expect(page.getByText('更新しました')).toBeVisible({ timeout: 5000 })
```

### カンマ区切り数値の検証

売価の表示形式（例: `1,500`）を検証する際、テーブルセル内のテキストを対象にする:

```ts
const priceCell = page.locator('table tbody tr').first().locator('td').nth(5)
await expect(priceCell).toHaveText('1,500')
```

---

## 5. テスト実行

```bash
cd frontend && npx playwright test e2e/partner-goods-list.spec.ts e2e/partner-goods-detail.spec.ts
```

個別テスト:

```bash
cd frontend && npx playwright test e2e/partner-goods-list.spec.ts -g "検索ボタンでテーブルが表示される"
```
