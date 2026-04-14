# テスト計画: 見積ステータス自動更新（印刷時/PDF時 → 提出済）

- **作成日**: 2026-04-10
- **対象設計書**: `claudedocs/design-estimate-status-auto-update.md`
- **対象機能**: 印刷/PDFダウンロード時のステータス自動遷移 + 確認ダイアログ

---

## 1. テスト対象

| # | 対象 | ファイル |
|---|---|---|
| 1 | `getNotifiedStatus()` ユニット関数 | `frontend/types/estimate.ts` |
| 2 | 見積詳細 — 印刷ボタン | `frontend/components/pages/estimate/detail.tsx` |
| 3 | 見積詳細 — PDFボタン | `frontend/components/pages/estimate/detail.tsx` |
| 4 | 比較見積詳細 — 印刷ボタン | `frontend/components/pages/estimate-comparison/detail.tsx` |

---

## 2. テストケース一覧

### 2-1. getNotifiedStatus ユニットテスト

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| U-01 | Unit | getNotifiedStatus('00') → '10' | なし | `getNotifiedStatus('00')` を呼び出す | `'10'`（提出済）を返す | 高 |
| U-02 | Unit | getNotifiedStatus('10') → '10' | なし | `getNotifiedStatus('10')` を呼び出す | `'10'`（変化なし）を返す | 高 |
| U-03 | Unit | getNotifiedStatus('20') → '30' | なし | `getNotifiedStatus('20')` を呼び出す | `'30'`（修正後提出済）を返す | 高 |
| U-04 | Unit | getNotifiedStatus('30') → '30' | なし | `getNotifiedStatus('30')` を呼び出す | `'30'`（変化なし）を返す | 高 |
| U-05 | Unit | getNotifiedStatus('40') → '10' | なし | `getNotifiedStatus('40')` を呼び出す | `'10'`（提出済）を返す | 中 |
| U-06 | Unit | getNotifiedStatus('50') → '10' | なし | `getNotifiedStatus('50')` を呼び出す | `'10'`（提出済）を返す | 中 |
| U-07 | Unit | getNotifiedStatus('60') → '10' | なし | `getNotifiedStatus('60')` を呼び出す | `'10'`（提出済）を返す | 中 |
| U-08 | Unit | getNotifiedStatus('70') → '10' | なし | `getNotifiedStatus('70')` を呼び出す | `'10'`（提出済）を返す | 中 |
| U-09 | Unit | getNotifiedStatus('90') → '10' | なし | `getNotifiedStatus('90')` を呼び出す | `'10'`（提出済）を返す | 低 |
| U-10 | Unit | getNotifiedStatus('99') → '10' | なし | `getNotifiedStatus('99')` を呼び出す | `'10'`（提出済）を返す | 低 |
| U-11 | Unit | getNotifiedStatus(null) → '10' | なし | `getNotifiedStatus(null)` を呼び出す | `'10'`（提出済）を返す | 低 |

### 2-2. 見積詳細 — 印刷ボタン（E2E）

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| E-01 | 見積印刷 | ステータス00で印刷 → 確認OK → ステータス10に更新 | 見積詳細画面（estimateStatus='00'）を表示 | 1. 印刷ボタンをクリック 2. 確認ダイアログで「OK」を選択 | 1. 確認ダイアログに「ステータスが「提出済」に更新されます」と表示される 2. `PUT /estimates/{no}/status` が `{ estimateStatus: '10' }` で呼ばれる 3. 成功トースト「ステータスを「提出済」に更新しました」が表示される | 高 |
| E-02 | 見積印刷 | ステータス20で印刷 → 確認OK → ステータス30に更新 | 見積詳細画面（estimateStatus='20'）を表示 | 1. 印刷ボタンをクリック 2. 確認ダイアログで「OK」を選択 | 1. 確認ダイアログに「ステータスが「修正後提出済」に更新されます」と表示される 2. `PUT /estimates/{no}/status` が `{ estimateStatus: '30' }` で呼ばれる 3. 成功トースト「ステータスを「修正後提出済」に更新しました」が表示される | 高 |
| E-03 | 見積印刷 | ステータス10で印刷 → 確認ダイアログなし | 見積詳細画面（estimateStatus='10'）を表示 | 1. 印刷ボタンをクリック | 1. 確認ダイアログが表示されない 2. `window.print()` が実行される 3. ステータス更新APIが呼ばれない | 高 |
| E-04 | 見積印刷 | ステータス30で印刷 → 確認ダイアログなし | 見積詳細画面（estimateStatus='30'）を表示 | 1. 印刷ボタンをクリック | 1. 確認ダイアログが表示されない 2. `window.print()` が実行される 3. ステータス更新APIが呼ばれない | 中 |
| E-05 | 見積印刷 | ステータス70で印刷 → 確認OK → ステータス10に更新 | 見積詳細画面（estimateStatus='70'）を表示 | 1. 印刷ボタンをクリック 2. 確認ダイアログで「OK」を選択 | 1. 確認ダイアログが表示される 2. `PUT /estimates/{no}/status` が `{ estimateStatus: '10' }` で呼ばれる 3. 成功トーストが表示される | 中 |
| E-06 | 見積印刷 | 印刷キャンセル → 何もしない | 見積詳細画面（estimateStatus='00'）を表示 | 1. 印刷ボタンをクリック 2. 確認ダイアログで「キャンセル」を選択 | 1. `window.print()` が実行されない 2. ステータス更新APIが呼ばれない 3. トースト表示なし | 高 |

### 2-3. 見積詳細 — PDFボタン（E2E）

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| E-07 | 見積PDF | ステータス00でPDF → 確認OK → ステータス10に更新 | 見積詳細画面（estimateStatus='00'）を表示 | 1. PDFボタンをクリック 2. 確認ダイアログで「OK」を選択 | 1. 確認ダイアログに「ステータスが「提出済」に更新されます」と表示される 2. PDFダウンロードAPIが呼ばれる 3. `PUT /estimates/{no}/status` が `{ estimateStatus: '10' }` で呼ばれる 4. 成功トーストが表示される | 高 |
| E-08 | 見積PDF | ステータス10でPDF → 確認ダイアログなし | 見積詳細画面（estimateStatus='10'）を表示 | 1. PDFボタンをクリック | 1. 確認ダイアログが表示されない 2. PDFダウンロードのみ実行される 3. ステータス更新APIが呼ばれない | 中 |
| E-09 | 見積PDF | PDFキャンセル → 何もしない | 見積詳細画面（estimateStatus='00'）を表示 | 1. PDFボタンをクリック 2. 確認ダイアログで「キャンセル」を選択 | 1. PDFダウンロードが実行されない 2. ステータス更新APIが呼ばれない | 中 |

### 2-4. 比較見積詳細 — 印刷ボタン（E2E）

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| E-10 | 比較見積印刷 | ステータス00で印刷 → 確認OK → ステータス10に更新 | 比較見積詳細画面（comparisonStatus='00'）を表示 | 1. 印刷ボタンをクリック 2. 確認ダイアログで「OK」を選択 | 1. 確認ダイアログが表示される 2. `PUT /estimate-comparisons/{no}/status` が `{ comparisonStatus: '10' }` で呼ばれる 3. 成功トーストが表示される | 高 |
| E-11 | 比較見積印刷 | ステータス10で印刷 → 確認ダイアログなし | 比較見積詳細画面（comparisonStatus='10'）を表示 | 1. 印刷ボタンをクリック | 1. 確認ダイアログが表示されない 2. ステータス更新APIが呼ばれない | 中 |
| E-12 | 比較見積印刷 | ステータス20で印刷 → 確認OK → ステータス30に更新 | 比較見積詳細画面（comparisonStatus='20'）を表示 | 1. 印刷ボタンをクリック 2. 確認ダイアログで「OK」を選択 | 1. `PUT /estimate-comparisons/{no}/status` が `{ comparisonStatus: '30' }` で呼ばれる 2. 成功トーストが表示される | 中 |

### 2-5. エラー処理・トースト通知（E2E）

| テストID | カテゴリ | テストケース名 | 前提条件 | 操作手順 | 期待結果 | 優先度 |
|---|---|---|---|---|---|---|
| E-13 | エラー処理 | ステータス更新API失敗 → エラートースト表示 | 見積詳細画面（estimateStatus='00'）、`PUT /estimates/{no}/status` が 500 を返すようモック | 1. 印刷ボタンをクリック 2. 確認ダイアログで「OK」を選択 | 1. `window.print()` は実行される（印刷自体は完了） 2. エラートースト「ステータスの更新に失敗しました」が表示される | 高 |
| E-14 | エラー処理 | ステータス変化なし → トースト非表示 | 見積詳細画面（estimateStatus='10'）を表示 | 1. 印刷ボタンをクリック | 1. トースト通知が表示されない | 中 |

---

## 3. テスト実装方針

### 3-1. ユニットテスト (U-01 ~ U-11)

- **ファイル**: `frontend/__tests__/types/estimate.test.ts` (新規)
- **フレームワーク**: Vitest or Jest
- `getNotifiedStatus` を import して全ステータスコードを網羅テスト

### 3-2. E2Eテスト (E-01 ~ E-14)

- **ファイル**: `frontend/e2e/estimate-status-auto-update.spec.ts` (新規)
- **フレームワーク**: Playwright
- **モックパターン**: `mock-api.ts` の `MOCK_ESTIMATES` を利用し、`page.route()` で個別テスト用にステータスをオーバーライド

#### モック設定例

```typescript
// ステータスをオーバーライドする共通ヘルパー
async function mockEstimateWithStatus(page: Page, status: string) {
  await page.route(
    (url) => /^\/api\/v1\/estimates\/\d+$/.test(url.pathname) && !url.pathname.includes('/status'),
    async (route) => {
      if (route.request().method() === 'GET') {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            ...MOCK_ESTIMATES[0],
            estimateStatus: status,
            details: [],
          }),
        })
      } else {
        await route.fallback()
      }
    },
  )
}
```

#### confirm ダイアログの検証

```typescript
// ダイアログが表示されることを検証
let dialogMessage = ''
page.on('dialog', async (dialog) => {
  dialogMessage = dialog.message()
  await dialog.accept()
})

// ダイアログが表示されないことを検証（ステータス10/30の場合）
let dialogShown = false
page.on('dialog', () => { dialogShown = true })
await page.locator('button:has-text("印刷")').click()
expect(dialogShown).toBe(false)
```

#### API呼び出しの検証

```typescript
// PUT /status が正しい値で呼ばれたことを検証
let statusUpdateBody: Record<string, string> | null = null
await page.route(
  (url) => /^\/api\/v1\/estimates\/\d+\/status$/.test(url.pathname),
  async (route) => {
    statusUpdateBody = JSON.parse(route.request().postData() || '{}')
    await route.fulfill({ status: 200, contentType: 'application/json', body: '{}' })
  },
)
// ... 印刷ボタンクリック後 ...
expect(statusUpdateBody).toEqual({ estimateStatus: '10' })
```

---

## 4. 対象外

| 項目 | 理由 |
|---|---|
| バッチ `PartnerPriceChangeReflectTasklet` | コード変更なし。ステータス遷移が正しく動けばバッチは既存ロジックで動作する |
| バックエンドAPI `PUT /estimates/{no}/status` | 既存エンドポイント。変更なし |
| 手動ステータス変更（Selectドロップダウン） | 既存機能。本機能の対象外 |

---

## 5. 優先度と実行順序

| 優先度 | テストID | 合計 |
|---|---|---|
| 高 | U-01, U-02, U-03, U-04, E-01, E-02, E-03, E-06, E-07, E-10, E-13 | 11件 |
| 中 | U-05, U-06, U-07, U-08, E-04, E-05, E-08, E-09, E-11, E-12, E-14 | 11件 |
| 低 | U-09, U-10, U-11 | 3件 |

**合計: 25件**（ユニット11件 + E2E 14件）
