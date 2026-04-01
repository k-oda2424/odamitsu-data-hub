# E2E Test Plan: AI見積取込・突合機能

## 対象画面
1. AI取込一覧 (`/purchase-prices/imports`)
2. 突合画面 (`/purchase-prices/imports/[importId]`)

## テスト基盤
- Playwright (Chromium headless)
- API mocking: `e2e/helpers/mock-api.ts` の `mockAllApis(page)` に quote-import 用モックを追加
- Auth: `e2e/helpers/auth.ts` の `loginAndGoto(page, '/path')`

## 必要なモックデータ

### MOCK_QUOTE_IMPORTS (一覧用)
```typescript
[
  {
    quoteImportId: 1,
    supplierName: '花王',
    supplierCode: null,        // 仕入先未突合
    supplierNo: null,
    fileName: '小田光様_26年5月価格改定御見積書.pdf',
    effectiveDate: '2026-05-01',
    changeReason: 'PU',
    totalCount: 5,
    remainingCount: 3,
    addDateTime: '2026-04-01T10:00:00',
  },
  {
    quoteImportId: 2,
    supplierName: 'ライオン',
    supplierCode: 'SUP002',    // 仕入先突合済み
    supplierNo: 2,
    fileName: 'ライオン_価格表.xlsx',
    effectiveDate: '2026-06-01',
    changeReason: 'PD',
    totalCount: 10,
    remainingCount: 0,         // 全件処理済み
    addDateTime: '2026-03-20T09:00:00',
  },
]
```

### MOCK_QUOTE_IMPORT_DETAIL (突合画面用: 仕入先未突合)
```typescript
{
  quoteImportId: 1,
  supplierName: '花王',
  supplierCode: null,
  supplierNo: null,
  fileName: '小田光様_26年5月価格改定御見積書.pdf',
  quoteDate: '2026-01-30',
  effectiveDate: '2026-05-01',
  changeReason: 'PU',
  priceType: '税抜',
  totalCount: 5,
  details: [
    {
      quoteImportDetailId: 1,
      rowNo: 1,
      janCode: '4901301508034',
      quoteGoodsName: 'クリーン&クリーンF1 ボトル',
      specification: '700mL',
      quantityPerCase: 6,
      oldPrice: 661,
      newPrice: 726,
    },
    {
      quoteImportDetailId: 2,
      rowNo: 2,
      janCode: null,                    // JANコードなし
      quoteGoodsName: 'キュキュット ハンドマイルド',
      specification: '230mL',
      quantityPerCase: 24,
      oldPrice: 150,
      newPrice: 165,
    },
    {
      quoteImportDetailId: 3,
      rowNo: 3,
      janCode: '4901301000001',
      quoteGoodsName: 'アタック ZERO ドラム',
      specification: '580g',
      quantityPerCase: 12,
      oldPrice: 300,
      newPrice: 330,
    },
  ],
}
```

### MOCK_QUOTE_IMPORT_DETAIL_SUPPLIER_MATCHED (仕入先突合済み)
同上だが `supplierCode: 'SUP001'`, `supplierNo: 1` がセット済み。

### 必要なAPIルート追加 (mock-api.ts)
| メソッド | パス | レスポンス |
|---------|------|-----------|
| GET | `/api/v1/quote-imports` | `MOCK_QUOTE_IMPORTS` |
| GET | `/api/v1/quote-imports/1` | `MOCK_QUOTE_IMPORT_DETAIL` |
| PUT | `/api/v1/quote-imports/1/supplier` | 200 OK |
| POST | `/api/v1/quote-imports/1/details/1/match` | 200 OK |
| POST | `/api/v1/quote-imports/1/details/2/create-new` | 200 OK |
| DELETE | `/api/v1/quote-imports/1/details/3` | 204 |
| POST | `/api/v1/quote-imports/1/auto-match-jan` | `{ matchedCount: 1 }` |
| DELETE | `/api/v1/quote-imports/1` | 204 |

---

## テストケース

### Screen 1: AI取込一覧 (`/purchase-prices/imports`)

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| IMP-001 | 画面表示 | 一覧ページが表示される | 1. `/purchase-prices/imports` に遷移 | 見出し「AI見積取込」が表示される。テーブルヘッダー（仕入先、ファイル名、適用日、変更理由、進捗、仕入先突合、操作）が表示される | P0 |
| IMP-002 | データ表示 | 取込バッチ一覧が表示される | 1. ページ表示 | 花王（remainingCount=3/totalCount=5）とライオン（0/10）の2行が表示される | P0 |
| IMP-003 | 進捗表示 | 進捗率が正しく表示される | 1. ページ表示 | 花王: 「2/5」(40%)のプログレスバー。ライオン: 「10/10」(100%)のプログレスバー | P0 |
| IMP-004 | Badge表示 | 仕入先突合状態Badgeが表示される | 1. ページ表示 | 花王: 「未」Badge。ライオン: 「済」Badge | P0 |
| IMP-005 | 画面遷移 | 突合画面へボタンで遷移する | 1. 花王の行の「突合画面へ」ボタンをクリック | `/purchase-prices/imports/1` に遷移する | P0 |
| IMP-006 | サイドバー | サイドバーにメニューが表示される | 1. ダッシュボードに遷移 2. サイドバーを確認 | 「AI見積取込」メニューが表示され、クリックで `/purchase-prices/imports` に遷移する | P1 |
| IMP-007 | 変更理由 | 変更理由ラベルが表示される | 1. ページ表示 | 花王: 「PU」(値上げ)ラベル。ライオン: 「PD」(値下げ)ラベル | P1 |
| IMP-008 | 削除 | 取込バッチを削除できる | 1. 削除ボタンをクリック 2. 確認ダイアログで「はい」 | DELETE API が呼ばれ、一覧から削除される | P2 |

### Screen 2: 突合画面 - 仕入先突合 (`/purchase-prices/imports/[importId]`)

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| MAT-001 | 画面表示 | 突合画面が表示される | 1. `/purchase-prices/imports/1` に遷移 | ヘッダー情報（ファイル名、適用日、変更理由）が表示される。仕入先突合セクションが表示される | P0 |
| MAT-002 | 仕入先突合 | 仕入先未確定時に商品突合が非活性 | 1. 仕入先未突合の取込(importId=1)の突合画面を表示 | 「仕入先を確定するまで商品の突合はできません」メッセージが表示される。商品突合テーブルの操作ボタンが非活性 | P0 |
| MAT-003 | 仕入先突合 | 仕入先を検索・選択・確定できる | 1. 仕入先検索フィールドで「仕入先A」を検索 2. 候補から選択 3. 「確定」ボタンをクリック | PUT `/api/v1/quote-imports/1/supplier` が呼ばれる。仕入先突合セクションが「確定済み」表示に変わる。商品突合テーブルが活性化する | P0 |
| MAT-004 | 仕入先突合 | 見積記載の仕入先名が表示される | 1. 突合画面を表示 | 「見積記載: 花王」が表示される | P0 |
| MAT-005 | 仕入先突合済み | 仕入先突合済みの場合、商品突合が活性 | 1. 仕入先突合済みの取込データで突合画面を表示（モックを差し替え） | 仕入先突合セクションに確定済みの仕入先名が表示される。商品突合テーブルの操作ボタンが活性 | P0 |

### Screen 2: 突合画面 - 商品突合テーブル

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| MAT-010 | テーブル表示 | ワーク明細一覧が表示される | 1. 仕入先突合済み状態で突合画面を表示 | 3件の明細行が表示される。各行に行番号、JANコード、見積商品名、規格、旧単価、新単価、操作ボタンが表示される | P0 |
| MAT-011 | テーブル表示 | JANコードなしの行は「(なし)」表示 | 1. テーブル表示 | detailId=2の行（キュキュット）のJANコード列に「(なし)」が表示される | P0 |
| MAT-012 | テーブル表示 | 操作ボタンが表示される | 1. テーブル表示 | 各行に「突合」「新規作成」「スキップ」ボタンが表示される | P0 |

### Screen 2: 突合画面 - 既存商品突合 (Step 1-A)

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| MAT-020 | 商品検索 | 突合ボタンで商品検索Popoverが表示される | 1. 明細行の「突合」ボタンをクリック | 商品検索Popoverが表示される。検索入力フィールドがある | P0 |
| MAT-021 | 商品検索 | キーワードで商品を検索できる | 1. Popoverの検索フィールドに「テスト」と入力 | 検索結果に「テスト商品A」「テスト商品B」が表示される（商品コード、商品名、単価付き） | P0 |
| MAT-022 | 商品突合 | 検索結果から商品を選択して突合できる | 1. 検索結果から「テスト商品A」をクリック | POST `/api/v1/quote-imports/1/details/1/match` が呼ばれる（goodsCode, goodsNo付き）。対象行がテーブルから消える | P0 |
| MAT-023 | 商品検索 | 「該当なし（スキップ）」選択肢が表示される | 1. Popoverを表示 | 検索結果の末尾に「該当なし（スキップ）」選択肢が表示される | P1 |

### Screen 2: 突合画面 - 新規商品作成 (Step 1-B)

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| MAT-030 | Dialog表示 | 新規作成ボタンでDialogが表示される | 1. 明細行の「新規作成」ボタンをクリック | 新規商品登録Dialogが表示される。商品マスタ情報セクションと販売商品情報セクションがある | P0 |
| MAT-031 | 初期値 | 見積データが初期値としてセットされる | 1. detailId=1の行で「新規作成」をクリック | 商品名: 「クリーン&クリーンF1 ボトル」、JANコード: 「4901301508034」、規格: 「700mL」、入数: 「6」、標準仕入単価: 「726」が初期値としてセットされている | P0 |
| MAT-032 | バリデーション | 必須フィールド未入力で登録できない | 1. Dialogで商品コードと売単価を空のまま「登録」をクリック | バリデーションエラーが表示される。商品コードと売単価が必須 | P0 |
| MAT-033 | 登録 | 必須項目入力後に登録できる | 1. 商品コードに「4901301508034」を入力 2. 売単価に「900」を入力 3. 「登録」をクリック | POST `/api/v1/quote-imports/1/details/1/create-new` が呼ばれる。Dialogが閉じる。対象行がテーブルから消える | P0 |
| MAT-034 | 初期値 | JANコードなしの行は商品コードが空 | 1. detailId=2の行（JANなし）で「新規作成」をクリック | JANコードフィールドが空。商品コードも空（人間が入力必須） | P1 |
| MAT-035 | エラー | 商品コード重複エラーが表示される | 1. 重複する商品コードを入力して「登録」 2. APIが409を返す | エラーメッセージ「商品コードが既に存在します」が表示される | P2 |

### Screen 2: 突合画面 - スキップ (Step 1-C)

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| MAT-040 | スキップ | スキップボタンで行が削除される | 1. 明細行の「スキップ」ボタンをクリック 2. 確認ダイアログで「はい」 | DELETE `/api/v1/quote-imports/1/details/3` が呼ばれる。対象行がテーブルから消える | P0 |

### Screen 2: 突合画面 - JAN一括自動突合

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| MAT-050 | 一括突合 | JAN自動突合ボタンが表示される | 1. 仕入先突合済み状態で突合画面を表示 | 「JAN自動突合」ボタンが表示される | P0 |
| MAT-051 | 一括突合 | JAN自動突合を実行できる | 1. 「JAN自動突合」ボタンをクリック | POST `/api/v1/quote-imports/1/auto-match-jan` が呼ばれる。結果メッセージ「1件を自動突合しました」が表示される。テーブルが更新される | P0 |

### Screen 2: 突合画面 - 完了状態

| TestID | Category | Test Case | Steps | Expected Result | Priority |
|--------|----------|-----------|-------|-----------------|----------|
| MAT-060 | 完了 | 全件処理完了メッセージが表示される | 1. ワーク0件の取込データで突合画面を表示（モックで details: [] を返す） | 「全件処理完了しました」メッセージが表示される。商品突合テーブルは空または非表示 | P0 |

---

## テストファイル構成

```
frontend/e2e/
├── helpers/
│   └── mock-api.ts          # MOCK_QUOTE_IMPORTS, MOCK_QUOTE_IMPORT_DETAIL 追加 + APIルート追加
└── quote-import.spec.ts     # 新規作成
```

### quote-import.spec.ts 構成案

```typescript
test.describe('AI取込一覧', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/purchase-prices/imports')
  })
  // IMP-001 ~ IMP-005
})

test.describe('突合画面 - 仕入先突合', () => {
  // MAT-001 ~ MAT-005
  // 仕入先未突合/突合済みでモック差し替えが必要
})

test.describe('突合画面 - 商品突合', () => {
  test.beforeEach(async ({ page }) => {
    // 仕入先突合済みモックをセットアップ
    await mockAllApis(page)
    // quote-imports/1 を仕入先突合済みデータで上書き
    await page.route(
      (url) => /^\/api\/v1\/quote-imports\/1$/.test(url.pathname),
      async (route) => json(route, MOCK_QUOTE_IMPORT_DETAIL_SUPPLIER_MATCHED),
    )
    await loginAndGoto(page, '/purchase-prices/imports/1')
  })
  // MAT-010 ~ MAT-060
})

test.describe('サイドバー - AI見積取込', () => {
  // IMP-006
})
```

## P0テスト一覧 (実装優先)

| TestID | 概要 |
|--------|------|
| IMP-001 | 一覧ページ表示 |
| IMP-002 | 取込バッチ一覧データ表示 |
| IMP-003 | 進捗率表示 |
| IMP-004 | 仕入先突合Badge表示 |
| IMP-005 | 突合画面への遷移 |
| MAT-001 | 突合画面表示 |
| MAT-002 | 仕入先未確定時の商品突合非活性 |
| MAT-003 | 仕入先の検索・選択・確定 |
| MAT-004 | 見積記載の仕入先名表示 |
| MAT-005 | 仕入先突合済み時の商品突合活性 |
| MAT-010 | ワーク明細一覧表示 |
| MAT-011 | JANコードなし表示 |
| MAT-012 | 操作ボタン表示 |
| MAT-020 | 商品検索Popover表示 |
| MAT-021 | キーワード商品検索 |
| MAT-022 | 商品選択で突合実行 |
| MAT-030 | 新規作成Dialog表示 |
| MAT-031 | 見積データ初期値セット |
| MAT-032 | 必須フィールドバリデーション |
| MAT-033 | 新規商品登録実行 |
| MAT-040 | スキップ実行 |
| MAT-050 | JAN自動突合ボタン表示 |
| MAT-051 | JAN自動突合実行 |
| MAT-060 | 全件処理完了メッセージ |

合計: P0 = 24件, P1 = 4件, P2 = 2件
