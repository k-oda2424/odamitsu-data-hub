import { expect, test, type Page, type Route } from '@playwright/test'
import { mockAllApis } from './helpers/mock-api'
import { loginAndGoto } from './helpers/auth'

const FROM_DATE = '2026-03-21'
const TO_DATE = '2026-04-20'

interface MockRow {
  shopNo: number
  partnerNo: number
  partnerCode: string
  partnerName: string
  transactionMonth: string
  taxRate: number
  isOtakeGarbageBag: boolean
  cutoffDate: number | null
  orderNo: number | null
  taxIncludedAmount: number | null
  taxExcludedAmount: number | null
  taxIncludedAmountChange: number
  taxExcludedAmountChange: number
  invoiceAmount: number | null
  verificationDifference: number | null
  invoiceNo: number | null
  verificationResult: 0 | 1 | null
  mfExportEnabled: boolean
  verifiedManually: boolean
  verificationNote: string | null
}

function row(overrides: Partial<MockRow> = {}): MockRow {
  return {
    shopNo: 1,
    partnerNo: 101,
    partnerCode: '000100',
    partnerName: '○○商会',
    transactionMonth: TO_DATE,
    taxRate: 10,
    isOtakeGarbageBag: false,
    cutoffDate: 20,
    orderNo: null,
    taxIncludedAmount: 1100000,
    taxExcludedAmount: 1000000,
    taxIncludedAmountChange: 1100000,
    taxExcludedAmountChange: 1000000,
    invoiceAmount: 1100000,
    verificationDifference: 0,
    invoiceNo: 501,
    verificationResult: 1,
    mfExportEnabled: true,
    verifiedManually: false,
    verificationNote: null,
    ...overrides,
  }
}

async function json(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: 'application/json',
    body: JSON.stringify(data),
  })
}

async function setupArRoutes(page: Page, rows: MockRow[]) {
  await page.route(
    (url) => url.pathname === '/api/v1/finance/accounts-receivable',
    async (route) => {
      await json(route, {
        content: rows,
        number: 0,
        size: 50,
        totalElements: rows.length,
        totalPages: 1,
        first: true,
        last: true,
        numberOfElements: rows.length,
        empty: rows.length === 0,
      })
    },
  )
  await page.route(
    (url) => url.pathname === '/api/v1/finance/accounts-receivable/summary',
    async (route) => {
      const unverified = rows.filter((r) => r.verificationResult === null).length
      const unmatched = rows.filter((r) => r.verificationResult === 0).length
      const matched = rows.filter((r) => r.verificationResult === 1).length
      const diffSum = rows
        .filter((r) => r.verificationResult === 0)
        .reduce((s, r) => s + (r.verificationDifference ?? 0), 0)
      await json(route, {
        fromDate: FROM_DATE,
        toDate: TO_DATE,
        totalCount: rows.length,
        unverifiedCount: unverified,
        unmatchedCount: unmatched,
        matchedCount: matched,
        unmatchedDifferenceSum: diffSum,
      })
    },
  )
}

test.describe('売掛金一覧', () => {
  test.beforeEach(async ({ page }) => {
    await mockAllApis(page)
  })

  test('一覧に行が表示される', async ({ page }) => {
    await setupArRoutes(page, [
      row({ partnerCode: '000100', partnerName: '○○商会', verificationResult: 1 }),
      row({
        partnerNo: 102,
        partnerCode: '000200',
        partnerName: '△△商店',
        verificationDifference: 3210,
        invoiceAmount: 1103210,
        verificationResult: 0,
        mfExportEnabled: false,
      }),
    ])
    await loginAndGoto(page, '/finance/accounts-receivable')
    await expect(page.getByText('○○商会')).toBeVisible()
    await expect(page.getByText('△△商店')).toBeVisible()
    await expect(page.getByText('一致', { exact: true })).toBeVisible()
    await expect(page.getByText('不一致', { exact: true })).toBeVisible()
  })

  test('サマリアラートで未検証・不一致件数が表示される', async ({ page }) => {
    await setupArRoutes(page, [
      row({ verificationResult: null, invoiceAmount: null, verificationDifference: null }),
      row({ partnerNo: 102, verificationDifference: -500, invoiceAmount: 1099500, verificationResult: 0 }),
    ])
    await loginAndGoto(page, '/finance/accounts-receivable')
    await expect(page.getByText(/未検証 1件/)).toBeVisible()
    await expect(page.getByText(/不一致 1件/)).toBeVisible()
  })

  test('手動確定ダイアログで税込・税抜・備考を入力して確定', async ({ page }) => {
    const target = row({
      partnerNo: 103,
      partnerCode: '000300',
      partnerName: '□□商会',
      invoiceAmount: 108000,
      taxIncludedAmountChange: 107500,
      taxExcludedAmountChange: 97727,
      verificationDifference: 500,
      verificationResult: 0,
      mfExportEnabled: false,
    })
    await setupArRoutes(page, [target])

    let verifyBody: Record<string, unknown> | null = null
    await page.route(
      (url) => /\/api\/v1\/finance\/accounts-receivable\/\d+\/\d+\/[\d-]+\/[\d.]+\/(true|false)\/verify$/.test(url.pathname),
      async (route) => {
        if (route.request().method() !== 'PUT') {
          await route.fallback()
          return
        }
        verifyBody = JSON.parse(route.request().postData() || '{}')
        await json(route, {
          ...target,
          taxIncludedAmount: 108000,
          taxExcludedAmount: 98181,
          verificationResult: 1,
          verifiedManually: true,
          verificationNote: '請求書No.B-002で確認',
          mfExportEnabled: true,
        })
      },
    )

    await loginAndGoto(page, '/finance/accounts-receivable')
    // 行クリックでダイアログを開く
    await page.getByText('□□商会').click()
    await expect(page.getByRole('dialog')).toBeVisible()

    await page.getByLabel('確定金額(税込)').fill('108000')
    await page.getByLabel('確定金額(税抜)').fill('98181')
    await page.getByLabel('備考（最大500字）').fill('請求書No.B-002で確認')
    await page.getByRole('button', { name: '確定', exact: true }).click()

    await expect(page.getByRole('dialog')).toBeHidden()
    expect(verifyBody).not.toBeNull()
    expect(verifyBody!.taxIncludedAmount).toBe(108000)
    expect(verifyBody!.taxExcludedAmount).toBe(98181)
    expect(verifyBody!.note).toBe('請求書No.B-002で確認')
  })

  test('手動確定済み行は admin が解除できる', async ({ page }) => {
    const target = row({
      partnerNo: 104,
      partnerCode: '000400',
      partnerName: '手動確定先',
      verificationResult: 1,
      verifiedManually: true,
      verificationNote: '確認済み',
    })
    await setupArRoutes(page, [target])

    let deleted = false
    await page.route(
      (url) => /\/manual-lock$/.test(url.pathname),
      async (route) => {
        if (route.request().method() === 'DELETE') {
          deleted = true
          await json(route, { ...target, verifiedManually: false })
        } else {
          await route.fallback()
        }
      },
    )

    await loginAndGoto(page, '/finance/accounts-receivable')
    await page.getByText('手動確定先').click()
    await expect(page.getByRole('dialog')).toBeVisible()
    const deleteResp = page.waitForResponse(
      (r) => /\/manual-lock$/.test(new URL(r.url()).pathname) && r.request().method() === 'DELETE',
    )
    await page.getByRole('button', { name: /手動確定解除/ }).click()
    await deleteResp
    expect(deleted).toBe(true)
  })

  test('再集計ボタンでダイアログが開き、締め日タイプとtargetDateを送信できる', async ({ page }) => {
    await setupArRoutes(page, [row()])

    let aggregateBody: Record<string, unknown> | null = null
    await page.route(
      (url) => url.pathname === '/api/v1/finance/accounts-receivable/aggregate',
      async (route) => {
        if (route.request().method() !== 'POST') {
          await route.fallback()
          return
        }
        aggregateBody = JSON.parse(route.request().postData() || '{}')
        await json(route, { status: 'STARTED', targetDate: '20260420', cutoffType: 'all' }, 202)
      },
    )

    await loginAndGoto(page, '/finance/accounts-receivable')
    await page.getByRole('button', { name: /再集計/ }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByRole('button', { name: /再集計実行/ }).click()

    await expect.poll(() => aggregateBody).not.toBeNull()
    expect(aggregateBody!.cutoffType).toBe('all')
    expect(typeof aggregateBody!.targetDate).toBe('string')
  })

  test('一括検証ボタンで検証APIが呼び出される', async ({ page }) => {
    await setupArRoutes(page, [row()])

    let bulkVerifyCalled = false
    await page.route(
      (url) => url.pathname === '/api/v1/finance/accounts-receivable/bulk-verify',
      async (route) => {
        if (route.request().method() !== 'POST') {
          await route.fallback()
          return
        }
        bulkVerifyCalled = true
        await json(route, {
          matchedCount: 5,
          mismatchCount: 1,
          notFoundCount: 0,
          skippedManualCount: 2,
          josamaOverwriteCount: 0,
          quarterlySpecialCount: 1,
        })
      },
    )

    await loginAndGoto(page, '/finance/accounts-receivable')
    await page.getByRole('button', { name: /一括検証/ }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByRole('button', { name: '実行', exact: true }).click()
    await expect.poll(() => bulkVerifyCalled).toBe(true)
  })

  test('MF CSV出力ボタンで export-mf-csv が呼び出される', async ({ page }) => {
    await setupArRoutes(page, [row()])

    let exportCalled = false
    await page.route(
      (url) => url.pathname === '/api/v1/finance/accounts-receivable/export-mf-csv',
      async (route) => {
        exportCalled = true
        await route.fulfill({
          status: 200,
          contentType: 'text/csv; charset=Shift_JIS',
          headers: { 'Content-Disposition': 'attachment; filename="accounts_receivable_to_sales_journal_20260321_20260420.csv"' },
          body: 'dummy csv',
        })
      },
    )

    await loginAndGoto(page, '/finance/accounts-receivable')
    await page.getByRole('button', { name: /MF CSV出力/ }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
    await page.getByRole('button', { name: 'ダウンロード', exact: true }).click()
    await expect.poll(() => exportCalled).toBe(true)
  })

  test('請求書取込ボタンで InvoiceImportDialog が開く', async ({ page }) => {
    await setupArRoutes(page, [row()])
    await loginAndGoto(page, '/finance/accounts-receivable')
    await page.getByRole('button', { name: /請求書取込/ }).click()
    await expect(page.getByRole('dialog')).toBeVisible()
  })
})
