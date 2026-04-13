import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

const PREVIEW_OK = {
  uploadId: 'u-123',
  fileName: '2026現金出納帳1-2.xlsx',
  totalRows: 10,
  errorCount: 0,
  unmappedClients: [],
  unknownDescriptions: [],
  rows: [
    {
      excelRowIndex: 3, transactionNo: 1, transactionDate: '2026-01-01',
      debitAccount: '仕入高', debitSubAccount: '', debitTax: '課対仕入10%', debitAmount: 1000,
      creditAccount: '現金', creditSubAccount: '', creditTax: '対象外', creditAmount: 1000,
      summary: '仕入 /株式会社A', errorType: null, errorMessage: null,
    },
  ],
}

const PREVIEW_WITH_UNMAPPED = {
  uploadId: 'u-456',
  fileName: '2026現金出納帳2-3.xlsx',
  totalRows: 5,
  errorCount: 2,
  unmappedClients: ['新規得意先ABC'],
  unknownDescriptions: [],
  rows: [
    {
      excelRowIndex: 3, transactionNo: 1, transactionDate: '2026-02-01',
      debitAccount: null, debitSubAccount: null, debitTax: null, debitAmount: null,
      creditAccount: null, creditSubAccount: null, creditTax: null, creditAmount: null,
      summary: null, errorType: 'UNMAPPED_CLIENT', errorMessage: '得意先 "新規得意先ABC" のマッピングが見つかりません',
    },
  ],
}

async function mockCashbookPreview(page: Page, payload: unknown) {
  await page.route(
    (url) => url.pathname === '/api/v1/finance/cashbook/preview',
    async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payload) })
    },
  )
}

async function mockRePreview(page: Page, uploadId: string, payload: unknown) {
  await page.route(
    (url) => url.pathname === `/api/v1/finance/cashbook/preview/${uploadId}`,
    async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payload) })
      } else {
        await route.fallback()
      }
    },
  )
}

async function uploadDummyFile(page: Page) {
  await page.setInputFiles('input[type="file"]', {
    name: 'test.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: Buffer.from('dummy-xlsx-content'),
  })
}

test.describe('現金出納帳取込', () => {
  test('CB-01: 初期表示 — アップロードUI', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/finance/cashbook-import')

    await expect(page.getByRole('heading', { name: /現金出納帳.*MoneyForward/ })).toBeVisible()
    await expect(page.getByText('Excelファイル')).toBeVisible()
    await expect(page.getByRole('button', { name: /プレビュー/ })).toBeVisible()
    await expect(page.getByRole('button', { name: /プレビュー/ })).toBeDisabled()
  })

  test('CB-02: ファイル選択 → プレビュー成功 → プレビューテーブル・CSVダウンロードボタン表示', async ({ page }) => {
    await mockAllApis(page)
    await mockCashbookPreview(page, PREVIEW_OK)
    await loginAndGoto(page, '/finance/cashbook-import')

    await uploadDummyFile(page)
    await page.getByRole('button', { name: /プレビュー/ }).click()

    await expect(page.getByText('取引件数:')).toBeVisible()
    await expect(page.getByText('10', { exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: /CSVダウンロード/ })).toBeEnabled()
    // プレビューテーブル（先頭行）
    await expect(page.getByText('仕入 /株式会社A')).toBeVisible()
  })

  test('CB-03: 未マッピング得意先あり → 警告パネル表示・CSVダウンロード無効', async ({ page }) => {
    await mockAllApis(page)
    await mockCashbookPreview(page, PREVIEW_WITH_UNMAPPED)
    await loginAndGoto(page, '/finance/cashbook-import')

    await uploadDummyFile(page)
    await page.getByRole('button', { name: /プレビュー/ }).click()

    await expect(page.getByText('未マッピング得意先')).toBeVisible()
    await expect(page.getByText('新規得意先ABC').first()).toBeVisible()
    await expect(page.getByRole('button', { name: /CSVダウンロード/ })).toBeDisabled()
  })

  test('CB-04: マッピング追加 → 再プレビュー → エラー解消', async ({ page }) => {
    await mockAllApis(page)
    await mockCashbookPreview(page, PREVIEW_WITH_UNMAPPED)
    await mockRePreview(page, 'u-456', PREVIEW_OK)
    // POST mapping
    await page.route(
      (url) => url.pathname === '/api/v1/finance/mf-client-mappings' && url.searchParams.size === 0,
      async (route) => {
        if (route.request().method() === 'POST') {
          await route.fulfill({
            status: 200, contentType: 'application/json',
            body: JSON.stringify({ id: 999, alias: '新規得意先ABC', mfClientName: '（株）ABC' }),
          })
        } else {
          await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
        }
      },
    )

    await loginAndGoto(page, '/finance/cashbook-import')
    await uploadDummyFile(page)
    await page.getByRole('button', { name: /プレビュー/ }).click()

    await page.getByRole('button', { name: 'マッピング追加' }).click()
    const dialog = page.getByRole('dialog')
    // alias は disabled input の value に入っている
    await expect(dialog.locator('input').nth(0)).toHaveValue('新規得意先ABC')
    await dialog.locator('input').nth(1).fill('（株）ABC')
    await page.getByRole('button', { name: /登録＆再プレビュー/ }).click()

    // 再プレビュー後はエラーなしパネルが消える
    await expect(page.getByText('未マッピング得意先')).toHaveCount(0)
    await expect(page.getByRole('button', { name: /CSVダウンロード/ })).toBeEnabled()
  })
})
