# E2E Test Plan: 仕入価格管理 (Purchase Price Management)

設計書: `claudedocs/design-purchase-price-management.md`

## テスト対象画面

| 画面 | URL | テストファイル |
|------|-----|---------------|
| 仕入価格一覧 | `/purchase-prices` | `e2e/purchase-price-list.spec.ts` |
| 仕入価格変更一覧 | `/purchase-prices/changes` | `e2e/purchase-price-change-list.spec.ts` |
| 仕入価格変更一括入力 | `/purchase-prices/changes/bulk-input` | `e2e/purchase-price-bulk-input.spec.ts` |

## モックデータ追加 (`e2e/helpers/mock-api.ts`)

### 追加定数

```ts
export const MOCK_PURCHASE_PRICES = [
  {
    purchasePriceNo: 1, goodsNo: 1, goodsCode: 'SG001', goodsName: 'テスト商品A',
    makerName: 'メーカーA', supplierNo: 1, supplierName: '仕入先A',
    shopNo: 1, partnerNo: 108, destinationNo: 1,
    goodsPrice: 100, includeTaxGoodsPrice: 108, taxRate: 0.08, taxCategory: '標準',
    lastPurchaseDate: '2026-03-01', periodFrom: '2026-01-01', periodTo: null,
  },
  {
    purchasePriceNo: 2, goodsNo: 2, goodsCode: 'SG002', goodsName: 'テスト商品B',
    makerName: 'メーカーB', supplierNo: 2, supplierName: '仕入先B',
    shopNo: 1, partnerNo: 200, destinationNo: 0,
    goodsPrice: 200, includeTaxGoodsPrice: 216, taxRate: 0.08, taxCategory: '標準',
    lastPurchaseDate: '2026-02-15', periodFrom: '2025-06-01', periodTo: null,
  },
]

export const MOCK_PURCHASE_PRICE_CHANGES = [
  {
    purchasePriceChangePlanNo: 1, shopNo: 1,
    goodsCode: 'SG001', goodsName: 'テスト商品A', janCode: '4901234567890',
    supplierCode: 'SUP001', supplierName: '仕入先A',
    beforePrice: 100, afterPrice: 120,
    changePlanDate: '2026-04-01', changeReason: 'PU', changeContainNum: 24,
    partnerNo: 108, destinationNo: 1,
    partnerPriceChangePlanCreated: false, purchasePriceReflect: false,
  },
  {
    purchasePriceChangePlanNo: 2, shopNo: 1,
    goodsCode: 'SG002', goodsName: 'テスト商品B', janCode: '4901234567891',
    supplierCode: 'SUP002', supplierName: '仕入先B',
    beforePrice: 200, afterPrice: 180,
    changePlanDate: '2026-05-01', changeReason: 'PD', changeContainNum: 12,
    partnerNo: null, destinationNo: null,
    partnerPriceChangePlanCreated: false, purchasePriceReflect: true,
  },
]
```

### 追加ルート

```ts
// ---- Purchase Prices ----
await page.route(
  (url) => url.pathname === '/api/v1/purchase-prices',
  async (route) => {
    if (route.request().method() === 'GET') {
      await json(route, MOCK_PURCHASE_PRICES)
    } else {
      await route.fallback()
    }
  },
)

// ---- Purchase Price Changes ----
await page.route(
  (url) => url.pathname === '/api/v1/purchase-price-changes/bulk',
  async (route) => {
    if (route.request().method() === 'POST') {
      await json(route, { count: 2 })
    } else {
      await route.fallback()
    }
  },
)

await page.route(
  (url) => /^\/api\/v1\/purchase-price-changes\/\d+$/.test(url.pathname),
  async (route) => {
    if (route.request().method() === 'DELETE') {
      await route.fulfill({ status: 204 })
    } else {
      await route.fallback()
    }
  },
)

await page.route(
  (url) => url.pathname === '/api/v1/purchase-price-changes',
  async (route) => {
    const method = route.request().method()
    if (method === 'GET') {
      await json(route, MOCK_PURCHASE_PRICE_CHANGES)
    } else if (method === 'POST') {
      const body = JSON.parse(route.request().postData() || '{}')
      await json(route, { purchasePriceChangePlanNo: 99, ...body })
    } else {
      await route.fallback()
    }
  },
)
```

---

## テストケース

### Screen 1: 仕入価格一覧 (`/purchase-prices`)

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| PP-001 | 初期表示 | ページヘッダーが表示される | 1. `/purchase-prices` に遷移 | 「仕入価格一覧」見出しが表示される | P0 |
| PP-002 | 初期表示 | 検索フォームが表示される | 1. `/purchase-prices` に遷移 | 商品名、商品コード、仕入先の検索フィールドが表示される | P0 |
| PP-003 | 初期表示 | テーブルは初期非表示 | 1. `/purchase-prices` に遷移 | 案内メッセージ表示、テーブル非表示 | P1 |
| PP-004 | 検索 | 検索ボタンでテーブル表示 | 1. 検索ボタンをクリック | テーブルが表示され、モックデータ（テスト商品A, B）が表示される | P0 |
| PP-005 | 検索 | テーブルヘッダーが正しい | 1. 検索実行 | 商品コード, 商品名, メーカー, 仕入先, 仕入価格, 税込価格, 直近仕入日 の列がある | P0 |
| PP-006 | 検索 | 仕入先がSearchableSelectで動作する | 1. 仕入先のcomboboxをクリック 2. 選択肢を確認 | 仕入先A, 仕入先Bが選択肢に表示される | P1 |
| PP-007 | 検索 | リセットでフォームクリア | 1. 商品名を入力 2. リセットボタンをクリック | フォームクリア、テーブル非表示 | P1 |
| PP-008 | Dialog | 行クリックで変更予定Dialogが開く | 1. 検索実行 2. 先頭行をクリック | Dialogが表示され、商品コード・商品名・現在価格が表示される | P0 |
| PP-009 | Dialog | Dialog入力フィールドが正しい | 1. 行クリックでDialog表示 | 変更後価格、変更予定日、変更理由の入力欄がある | P0 |
| PP-010 | Dialog | 変更理由Selectの選択肢 | 1. Dialog内の変更理由Selectを開く | 値上(PU)、値下(PD)、販売終了(ES)が選択できる | P0 |
| PP-011 | Dialog | Dialog送信成功 | 1. Dialog表示 2. 変更後価格=120, 変更予定日=2026-04-01, 変更理由=値上を入力 3. 送信 | POST `/purchase-price-changes` が呼ばれ、Dialogが閉じる | P0 |
| PP-012 | Dialog | 変更後価格0でバリデーションエラー | 1. Dialog表示 2. 変更後価格に0を入力 3. 送信 | バリデーションエラーが表示され、送信されない | P1 |
| PP-013 | Dialog | Escapeで閉じる | 1. Dialog表示 2. Escapeキー押下 | Dialogが閉じる | P2 |

### Screen 2: 仕入価格変更一覧 (`/purchase-prices/changes`)

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| PPC-001 | 初期表示 | ページヘッダーが表示される | 1. `/purchase-prices/changes` に遷移 | 「仕入価格変更一覧」見出しが表示される | P0 |
| PPC-002 | 初期表示 | 検索フォームが表示される | 1. 遷移 | 仕入先コード、商品コード、JANコード、変更理由、変更予定日(From/To)フィールドが表示 | P0 |
| PPC-003 | 初期表示 | 一括入力ボタンが表示される | 1. 遷移 | 一括入力画面へのリンクボタンが表示される | P0 |
| PPC-004 | 検索 | 検索ボタンでテーブル表示 | 1. 検索ボタンをクリック | テーブルが表示され、モックデータ2件が表示される | P0 |
| PPC-005 | 検索 | テーブルヘッダーが正しい | 1. 検索実行 | 商品コード, 商品名, 仕入先, 変更前価格, 変更後価格, 変更予定日, 変更理由, 反映済み の列がある | P0 |
| PPC-006 | 検索 | 変更理由が表示される | 1. 検索実行 | 「値上」「値下」が変更理由列に表示される | P1 |
| PPC-007 | 検索 | 反映済みがBadgeで表示される | 1. 検索実行 | `purchasePriceReflect: true` の行に反映済みBadgeが表示される | P1 |
| PPC-008 | 検索 | リセットでフォームクリア | 1. 条件入力 2. リセット | フォームクリア、テーブル非表示 | P1 |
| PPC-009 | ナビ | 一括入力ボタンで遷移 | 1. 一括入力ボタンをクリック | `/purchase-prices/changes/bulk-input` に遷移する | P0 |

### Screen 3: 仕入価格変更一括入力 (`/purchase-prices/changes/bulk-input`)

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| PPB-001 | 初期表示 | ページヘッダーが表示される | 1. `/purchase-prices/changes/bulk-input` に遷移 | 「仕入価格変更一括入力」見出しが表示される | P0 |
| PPB-002 | 初期表示 | ヘッダー部が表示される | 1. 遷移 | 仕入先(SearchableSelect)、変更予定日(DatePicker)、変更理由(Select)が表示される | P0 |
| PPB-003 | 初期表示 | 明細テーブルに空行がある | 1. 遷移 | 明細入力テーブルが表示され、初期行（1行以上）がある | P0 |
| PPB-004 | ヘッダー | 仕入先SearchableSelectが動作する | 1. 仕入先comboboxをクリック | 仕入先A, 仕入先Bが選択できる | P0 |
| PPB-005 | ヘッダー | 変更理由Selectの選択肢 | 1. 変更理由Selectを開く | 値上(PU)、値下(PD)、販売終了(ES)が選択できる | P0 |
| PPB-006 | 明細 | 行追加ボタンで行が追加される | 1. 「行追加」ボタンをクリック | 明細テーブルに新しい空行が追加される | P0 |
| PPB-007 | 明細 | 商品コード入力で現在価格取得 | 1. 商品コード欄に「SG001」を入力 2. フォーカスアウト | 商品名「テスト商品A」と変更前価格「100」が自動表示される | P0 |
| PPB-008 | 明細 | 削除ボタンで行削除 | 1. 行追加で2行にする 2. 1行目の削除ボタンをクリック | 1行目が削除され、1行のみになる | P1 |
| PPB-009 | 登録 | 一括登録成功 | 1. ヘッダー入力（仕入先=仕入先A, 変更予定日=2026-04-01, 変更理由=値上） 2. 明細1行に商品コード=SG001, 変更後価格=120 3. 登録ボタン | POST `/purchase-price-changes/bulk` が呼ばれ、成功トースト表示 | P0 |
| PPB-010 | 登録 | 変更後価格未入力でバリデーション | 1. ヘッダー入力済み 2. 明細の変更後価格を空のまま 3. 登録 | バリデーションエラー表示、送信されない | P1 |
| PPB-011 | 登録 | ヘッダー未入力でバリデーション | 1. 仕入先を未選択のまま 2. 登録ボタン | バリデーションエラー表示、送信されない | P1 |
| PPB-012 | 明細 | 存在しない商品コードでエラー | 1. 商品コード欄に「INVALID」を入力 2. フォーカスアウト | APIが空を返し、エラーまたは警告が表示される | P2 |
| PPB-013 | 明細 | 同一商品コード重複で警告 | 1. 2行に同じ商品コード「SG001」を入力 | 重複警告が表示される | P2 |

### 共通: サイドバーナビゲーション

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| NAV-001 | サイドバー | 仕入価格一覧メニューが表示される | 1. 任意画面でサイドバー確認 | 「仕入」配下に「仕入価格一覧」メニューがある | P0 |
| NAV-002 | サイドバー | 仕入価格変更一覧メニューが表示される | 1. サイドバー確認 | 「仕入」配下に「仕入価格変更一覧」メニューがある | P0 |
| NAV-003 | サイドバー | 仕入価格変更一括入力メニューが表示される | 1. サイドバー確認 | 「仕入」配下に「仕入価格変更一括入力」メニューがある | P1 |
| NAV-004 | サイドバー | メニュークリックで遷移 | 1. 「仕入価格一覧」をクリック | `/purchase-prices` に遷移 | P0 |
| NAV-005 | サイドバー | アクティブ状態の表示 | 1. `/purchase-prices` に遷移 | サイドバーの「仕入価格一覧」が `data-active` 状態になる | P1 |

---

## テスト実装パターン

各specファイルの基本構造:

```ts
import { test, expect } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

test.describe('仕入価格一覧画面', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/purchase-prices')
  })

  test('PP-001: ページヘッダーが表示される', async ({ page }) => {
    await expect(page.getByRole('heading', { name: '仕入価格一覧' })).toBeVisible()
  })

  // ...
})
```

## 優先度サマリ

| Priority | Count | 説明 |
|----------|-------|------|
| P0 | 21 | コア機能。初回リリース前に必須通過 |
| P1 | 12 | 重要だが代替操作あり。リリース後早期対応可 |
| P2 | 4 | エッジケース・UX改善。余裕があれば対応 |
