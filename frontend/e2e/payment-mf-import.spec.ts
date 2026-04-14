import { test, expect, type Page } from '@playwright/test'
import { loginAndGoto } from './helpers/auth'
import { mockAllApis } from './helpers/mock-api'

const PREVIEW_OK = {
  uploadId: 'u-pmf-1',
  fileName: '振込み明細08-2-20.xlsx',
  transferDate: '2026-02-20',
  transactionMonth: '2026-02-20',
  totalRows: 3,
  totalAmount: 289027 + 178233,
  matchedCount: 1,
  diffCount: 1,
  unmatchedCount: 0,
  errorCount: 0,
  unregisteredSources: [],
  rows: [
    {
      excelRowIndex: 3, paymentSupplierCode: '21', sourceName: 'イトマン ㈱', amount: 289027,
      ruleKind: 'PAYABLE', debitAccount: '買掛金', debitSubAccount: 'イトマン（株）',
      debitDepartment: null, debitTax: '対象外', debitAmount: 289027,
      creditAccount: '資金複合', creditSubAccount: null, creditDepartment: null,
      creditTax: '対象外', creditAmount: 289027,
      summary: 'イトマン（株）', tag: null,
      matchStatus: 'MATCHED', payableAmount: 289027, payableDiff: 0, supplierNo: 21,
      errorType: null, errorMessage: null,
    },
    {
      excelRowIndex: 7, paymentSupplierCode: '95', sourceName: 'オルディ 株式会社', amount: 178233,
      ruleKind: 'PAYABLE', debitAccount: '買掛金', debitSubAccount: 'オルディ株式会社',
      debitDepartment: null, debitTax: '対象外', debitAmount: 178233,
      creditAccount: '資金複合', creditSubAccount: null, creditDepartment: null,
      creditTax: '対象外', creditAmount: 178233,
      summary: 'オルディ株式会社', tag: null,
      matchStatus: 'DIFF', payableAmount: 178333, payableDiff: 100, supplierNo: 95,
      errorType: null, errorMessage: null,
    },
    {
      excelRowIndex: 0, paymentSupplierCode: null, sourceName: '振込手数料値引', amount: 17160,
      ruleKind: 'SUMMARY', debitAccount: '資金複合', debitSubAccount: null,
      debitDepartment: null, debitTax: '対象外', debitAmount: 17160,
      creditAccount: '仕入値引・戻し高', creditSubAccount: null, creditDepartment: '物販事業部',
      creditTax: '課税仕入-返還等 10%', creditAmount: 17160,
      summary: '振込手数料値引／20日払い分', tag: null,
      matchStatus: 'NA', payableAmount: null, payableDiff: null, supplierNo: null,
      errorType: null, errorMessage: null,
    },
  ],
}

const PREVIEW_WITH_UNREG = {
  ...PREVIEW_OK,
  uploadId: 'u-pmf-2',
  errorCount: 1,
  unregisteredSources: ['新規送り先XYZ'],
  rows: [
    {
      excelRowIndex: 10, paymentSupplierCode: '999', sourceName: '新規送り先XYZ', amount: 50000,
      ruleKind: null, debitAccount: null, debitSubAccount: null, debitDepartment: null,
      debitTax: null, debitAmount: null, creditAccount: null, creditSubAccount: null,
      creditDepartment: null, creditTax: null, creditAmount: null,
      summary: null, tag: null,
      matchStatus: 'UNMATCHED', payableAmount: null, payableDiff: null, supplierNo: null,
      errorType: 'UNREGISTERED', errorMessage: 'マスタに未登録: 新規送り先XYZ',
    },
  ],
}

async function mockPreview(page: Page, payload: unknown) {
  await page.route(
    (url) => url.pathname === '/api/v1/finance/payment-mf/preview',
    async (route) => route.fulfill({
      status: 200, contentType: 'application/json', body: JSON.stringify(payload),
    }),
  )
}

async function mockRePreview(page: Page, uploadId: string, payload: unknown) {
  await page.route(
    (url) => url.pathname === `/api/v1/finance/payment-mf/preview/${uploadId}`,
    async (route) => {
      if (route.request().method() === 'POST') {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(payload) })
      } else {
        await route.fallback()
      }
    },
  )
}

async function uploadDummy(page: Page) {
  await page.setInputFiles('input[type="file"]', {
    name: 'furikomi.xlsx',
    mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
    buffer: Buffer.from('dummy-xlsx'),
  })
}

test.describe('買掛仕入MF変換', () => {
  test('PMF-01: 初期表示', async ({ page }) => {
    await mockAllApis(page)
    await loginAndGoto(page, '/finance/payment-mf-import')
    await expect(page.getByRole('heading', { name: '買掛仕入MF変換' })).toBeVisible()
    await expect(page.getByRole('button', { name: /プレビュー/ })).toBeDisabled()
  })

  test('PMF-02: アップロード → プレビュー → 突合状態表示', async ({ page }) => {
    await mockAllApis(page)
    await mockPreview(page, PREVIEW_OK)
    await loginAndGoto(page, '/finance/payment-mf-import')

    await uploadDummy(page)
    await page.getByRole('button', { name: /プレビュー/ }).click()

    await expect(page.getByText('2026-02-20').first()).toBeVisible()
    await expect(page.getByText(/🟢 一致/)).toBeVisible()
    await expect(page.getByText(/🟡 差異/)).toBeVisible()
    await expect(page.getByText('振込手数料値引／20日払い分')).toBeVisible()
    await expect(page.getByRole('button', { name: /CSVダウンロード/ })).toBeEnabled()
  })

  test('PMF-03: 未登録送り先あり → 警告 + CSV無効', async ({ page }) => {
    await mockAllApis(page)
    await mockPreview(page, PREVIEW_WITH_UNREG)
    await loginAndGoto(page, '/finance/payment-mf-import')

    await uploadDummy(page)
    await page.getByRole('button', { name: /プレビュー/ }).click()

    await expect(page.getByText('マスタ未登録の送り先')).toBeVisible()
    await expect(page.getByText('新規送り先XYZ').first()).toBeVisible()
    await expect(page.getByRole('button', { name: /CSVダウンロード/ })).toBeDisabled()
  })

  test('PMF-04: ルール追加 → 再プレビューで解消', async ({ page }) => {
    await mockAllApis(page)
    await mockPreview(page, PREVIEW_WITH_UNREG)
    await mockRePreview(page, 'u-pmf-2', PREVIEW_OK)
    await page.route(
      (url) => url.pathname === '/api/v1/finance/payment-mf/rules' && url.searchParams.size === 0,
      async (route) => {
        if (route.request().method() === 'POST') {
          await route.fulfill({
            status: 200, contentType: 'application/json',
            body: JSON.stringify({ id: 999, sourceName: '新規送り先XYZ' }),
          })
        } else {
          await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' })
        }
      },
    )
    await loginAndGoto(page, '/finance/payment-mf-import')
    await uploadDummy(page)
    await page.getByRole('button', { name: /プレビュー/ }).click()

    await page.getByRole('button', { name: 'ルール追加' }).click()
    await page.getByRole('button', { name: /登録＆再プレビュー/ }).click()

    await expect(page.getByText('マスタ未登録の送り先')).toHaveCount(0)
    await expect(page.getByRole('button', { name: /CSVダウンロード/ })).toBeEnabled()
  })
})
